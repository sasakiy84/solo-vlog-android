package com.sasakiy84.solovlog;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.TextureView;
import android.widget.FrameLayout;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/** One muted, looping clip preview, shared by the capture editor and clip details. */
@UnstableApi
final class ClipPreview {
    final Ui.AspectFrame frame;
    private final CaptionOverlay.Preview captions;
    private final TextureView video;
    private final PreviewAudioButton audio;
    private final Context context;
    private final ClipRepository repository;
    private final boolean pinSource;
    private final Consumer<String> notify;
    private Clip currentClip;
    private ExoPlayer player;
    private ParcelFileDescriptor source;

    ClipPreview(Context context, ClipRepository repository, Clip clip, String dayCaption,
                boolean showTime, boolean showDate, boolean pinSource, Consumer<String> notify) {
        this.context = context;
        this.repository = repository;
        this.pinSource = pinSource;
        this.notify = notify;
        currentClip = clip;
        frame = new Ui.AspectFrame(context, clip.landscape());
        video = new TextureView(context);
        video.setKeepScreenOn(true);
        frame.addView(video, new FrameLayout.LayoutParams(-1, -1));
        captions = new CaptionOverlay.Preview(context, clip, dayCaption, showTime, showDate);
        frame.addView(captions, new FrameLayout.LayoutParams(-1, -1));
        audio = new PreviewAudioButton(context, audible -> {
            if (player != null) player.setVolume(audible ? 1 : 0);
        });
        audio.attachTo(frame);
        if (pinSource) video.setContentDescription("動画プレビュー");
        video.setOnClickListener(v -> {
            if (player == null || !player.isPlaying()) start(currentClip);
        });
    }

    void update(Clip clip, String dayCaption, boolean showTime, boolean showDate) {
        currentClip = clip;
        captions.update(clip, dayCaption, showTime, showDate);
    }

    boolean started() {
        return player != null;
    }

    void start(Clip clip) {
        stop();
        currentClip = clip;
        File file = repository.file(clip.id());
        if (pinSource) {
            // GPS remux replaces the MP4 atomically. Keep loops on the same inode with independent offsets.
            try {
                source = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            } catch (IOException e) {
                Log.w("DaylogEditor", "Could not open preview", e);
                notify.accept("動画を開けませんでした");
                return;
            }
            file = new File("/proc/self/fd/" + source.getFd());
        }
        ExoPlayer created = PreviewPlayers.create(context, error -> {
            Log.w(pinSource ? "DaylogEditor" : "DaylogPreview", "Clip playback failed", error);
            notify.accept("タップして再読み込み");
        });
        player = created;
        created.setVideoTextureView(video);
        created.setVolume(audio.isAudible() ? 1 : 0);
        created.setRepeatMode(Player.REPEAT_MODE_ONE);
        created.addListener(new Player.Listener() {
            @Override public void onRenderedFirstFrame() {
                if (player == created) captions.captureColors(video);
            }
        });
        created.setMediaItem(new MediaItem.Builder().setUri(Uri.fromFile(file))
            .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.trimStartMs()).setEndPositionMs(clip.trimEndMs()).build()).build());
        created.prepare();
        created.play();
    }

    void stop() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (source != null) {
            try {
                source.close();
            } catch (IOException e) {
                Log.w("DaylogEditor", "Could not close preview", e);
            }
            source = null;
        }
    }
}
