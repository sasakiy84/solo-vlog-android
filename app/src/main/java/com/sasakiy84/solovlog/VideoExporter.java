package com.sasakiy84.solovlog;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.Effects;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class VideoExporter {
    public interface Listener { void saved(Uri uri); void error(String message); }
    private final Context context;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Transformer transformer;
    public VideoExporter(Context context) { this.context = context.getApplicationContext(); }

    public void export(List<Clip> clips, ClipRepository repository, String name, String dayCaption, boolean showTime, Listener listener) {
        if (clips.isEmpty()) { listener.error("保存するクリップを選んでください。"); return; }
        if (transformer != null) { listener.error("現在の保存が終わるまでお待ちください。"); return; }
        List<EditedMediaItem> items = new ArrayList<>();
        for (Clip clip : clips) {
            if (!repository.file(clip.id()).isFile()) { listener.error("元のクリップが見つかりません。"); return; }
            MediaItem item = new MediaItem.Builder().setUri(Uri.fromFile(repository.file(clip.id())))
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(JournalRules.exportDuration(clip)).build()).build();
            EditedMediaItem.Builder edited=new EditedMediaItem.Builder(item);
            if(!dayCaption.isBlank()||showTime||!clip.caption().isBlank())
                edited.setEffects(new Effects(List.of(),List.of(new CaptionOverlay(clip,dayCaption,showTime))));
            items.add(edited.build());
        }
        EditedMediaItemSequence sequence = new EditedMediaItemSequence.Builder(items).experimentalSetForceAudioTrack(true).build();
        Composition composition = new Composition.Builder(sequence).build();
        File output;
        try { output = File.createTempFile("daylog-export-", ".mp4", context.getCacheDir()); output.delete(); }
        catch (Exception e) { listener.error("書き出し用の空き容量を確保できませんでした。"); return; }
        transformer = new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setVideoFrameProcessorFactory(new DefaultVideoFrameProcessor.Factory.Builder().setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_DEFAULT).build())
            .addListener(new Transformer.Listener() {
                @Override public void onCompleted(Composition result, ExportResult exportResult) {
                    transformer = null;
                    io.execute(() -> {
                        try { Uri uri = copyToGallery(output, name); main.post(() -> listener.saved(uri)); }
                        catch (Exception e) { main.post(() -> listener.error("ギャラリーに保存できませんでした。空き容量を確認してください。")); }
                        finally { output.delete(); }
                    });
                }
                @Override public void onError(Composition result, ExportResult exportResult, ExportException exception) {
                    Log.e("DaylogExport", "Video export failed", exception);
                    transformer = null; output.delete(); listener.error("動画をまとめられませんでした。元のクリップは残っています。");
                }
            }).build();
        try { transformer.start(composition, output.getAbsolutePath()); }
        catch (RuntimeException e) { transformer = null; output.delete(); listener.error("書き出しを始められませんでした。元のクリップは残っています。"); }
    }

    private Uri copyToGallery(File input, String name) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, name + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Daylog");
        values.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("MediaStore insertion failed");
        try {
            try (FileInputStream source = new FileInputStream(input); OutputStream target = context.getContentResolver().openOutputStream(uri)) {
                if (target == null) throw new IllegalStateException("MediaStore stream unavailable");
                source.transferTo(target);
            }
            values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);
            return uri;
        } catch (Exception e) { context.getContentResolver().delete(uri, null, null); throw e; }
    }
}
