package com.sasakiy84.solovlog;

import android.content.Context;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Range;
import androidx.camera.core.CameraSelector;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.CameraController;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.camera.view.video.AudioConfig;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import java.io.File;

/** CameraX owns the camera lifecycle; timing begins only after recording has actually started. */
public final class CameraEngine {
    public interface Listener {
        void ready();
        void progress(float fraction, boolean stabilizing);
        void saved(File file, long capturedAt);
        void error(String message);
    }
    private final Context context;
    private final LifecycleOwner owner;
    private final LifecycleCameraController controller;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PreviewView preview;
    private Recording recording;
    private boolean busy;
    private boolean front;
    private long started;
    private long capturedAt;
    private Runnable arm;
    private Runnable tick;

    public CameraEngine(Context context, LifecycleOwner owner) {
        this.context = context; this.owner = owner;
        controller = new LifecycleCameraController(context);
        controller.setEnabledUseCases(CameraController.VIDEO_CAPTURE);
        controller.setVideoCaptureQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)));
        controller.setVideoCaptureTargetFrameRate(new Range<>(30, 30));
    }
    public void bind(PreviewView target, Listener listener) {
        if (preview == target && controller.getCameraInfo() != null) { listener.ready(); return; }
        preview = target;
        target.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        target.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        controller.getInitializationFuture().addListener(() -> {
            if (preview != target) return;
            try {
                controller.getInitializationFuture().get();
                target.setController(controller);
                controller.bindToLifecycle(owner);
                listener.ready();
            } catch (Exception e) { listener.error("カメラを開けませんでした。権限と端末のカメラを確認してください。"); }
        }, ContextCompat.getMainExecutor(context));
    }
    public void unbind() { preview = null; controller.unbind(); }
    public void flip() {
        CameraSelector next = front ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
        if (!controller.hasCamera(next)) throw new IllegalStateException("このカメラは使えません");
        controller.setCameraSelector(next); front = !front;
    }
    public boolean busy() { return busy; }
    public void start(File target, int seconds, boolean audio, Listener listener) {
        if (busy) return;
        busy = true;
        listener.progress(0, true);
        // Let the phone settle after the tap; this delay is not part of the saved clip.
        arm = () -> {
            arm = null;
            try {
                capturedAt = System.currentTimeMillis();
                AudioConfig audioConfig = AudioConfig.AUDIO_DISABLED;
                if (audio && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    audioConfig = AudioConfig.create(true);
                }
                recording = controller.startRecording(new FileOutputOptions.Builder(target).build(), audioConfig,
                    ContextCompat.getMainExecutor(context), event -> {
                        if (event instanceof VideoRecordEvent.Start) {
                            capturedAt = System.currentTimeMillis(); started = SystemClock.elapsedRealtime();
                            tick = new Runnable() {
                                @Override public void run() {
                                    if (recording == null) return;
                                    float fraction = Math.min(1f, (SystemClock.elapsedRealtime() - started) / (seconds * 1000f));
                                    listener.progress(fraction, false);
                                    if (fraction >= 1) stop(); else handler.postDelayed(this, 40);
                                }
                            };
                            handler.post(tick);
                        } else if (event instanceof VideoRecordEvent.Finalize end) {
                            if (tick != null) handler.removeCallbacks(tick);
                            recording = null; busy = false;
                            if (!end.hasError() || end.getError() == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) listener.saved(target, capturedAt);
                            else {
                                target.delete();
                                listener.error("撮影を保存できませんでした。空き容量とカメラの状態を確認してください。");
                            }
                        }
                    });
            } catch (SecurityException e) {
                recording = null; busy = false; target.delete();
                listener.error("カメラ・マイクの権限が変更されました。設定を確認してください。");
            } catch (RuntimeException e) {
                recording = null; busy = false; target.delete();
                listener.error("撮影を始められませんでした。カメラ・マイクの権限を確認してください。");
            }
        };
        handler.postDelayed(arm, 350);
    }
    public void stop() {
        if (recording != null) { if (tick != null) handler.removeCallbacks(tick); recording.stop(); }
        if (arm != null) { handler.removeCallbacks(arm); arm = null; busy = false; }
    }
}
