package com.sasakiy84.solovlog;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.location.Location;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.view.Surface;
import androidx.camera.core.Camera;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.CameraControl;
import androidx.camera.core.ZoomState;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.DynamicRange;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import java.io.File;

/** The selected format fixes the output rotation, independent of sensor-orientation events. */
public final class CameraEngine {
    public interface Listener {
        void ready();
        default void binding() {}
        default void zoomChanged(ZoomState state) {}
        default void zoomError(String message) {}
        void progress(float fraction, boolean stabilizing);
        void saved(File file, long capturedAt);
        void error(String message);
    }
    private final Context context;
    private final LifecycleOwner owner;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Recorder recorder;
    private String actualQuality,requestedQuality;
    private ProcessCameraProvider provider;
    private Camera boundCamera;
    private CameraLenses lenses;
    private CameraLenses.Lens activeLens;
    private androidx.lifecycle.Observer<ZoomState> zoomObserver;
    private float requestedZoom=1;
    private int zoomRequest;
    private Preview boundPreview;
    private VideoCapture<Recorder> boundVideo;
    private PreviewView preview;
    private Recording recording;
    private boolean busy,front,horizontal,bound;
    private long started,capturedAt;
    private Runnable arm,tick;
    private Listener bindingListener;

    public CameraEngine(Context context,LifecycleOwner owner){this.context=context;this.owner=owner;}
    public void bind(PreviewView target,boolean landscape,Listener listener){
        if(preview==target&&bound&&horizontal==landscape){bindingListener=listener;listener.ready();setZoom(requestedZoom);return;}
        preview=target;horizontal=landscape;bindingListener=listener;listener.binding();
        target.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        target.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        var future=ProcessCameraProvider.getInstance(context);
        future.addListener(()->{
            if(preview!=target)return;
            if(bound){listener.ready();return;}
            try{
                provider=future.get();releaseUseCases();bound=false;
                CameraSelector selector=front?CameraSelector.DEFAULT_FRONT_CAMERA:CameraSelector.DEFAULT_BACK_CAMERA;
                if(!provider.hasCamera(selector)){
                    selector=front?CameraSelector.DEFAULT_BACK_CAMERA:CameraSelector.DEFAULT_FRONT_CAMERA;
                    if(!provider.hasCamera(selector))throw new IllegalStateException("No available camera");
                    front=!front;
                }
                if(lenses==null)lenses=CameraLenses.discover(provider,selector,front);
                activeLens=lenses.forRatio(requestedZoom);selector=activeLens.selector();
                CameraInfo info=provider.getCameraInfo(selector);
                String requested=context.getSharedPreferences("daylog",Context.MODE_PRIVATE).getString("captureQuality","auto");
                java.util.List<Quality> order=switch(requested){
                    case "fhd"->java.util.List.of(Quality.FHD,Quality.HD,Quality.SD,Quality.UHD);
                    case "hd"->java.util.List.of(Quality.HD,Quality.SD,Quality.FHD,Quality.UHD);
                    default->java.util.List.of(Quality.UHD,Quality.FHD,Quality.HD,Quality.SD);
                };
                java.util.List<Quality> supported=Recorder.getVideoCapabilities(info).getSupportedQualities(DynamicRange.SDR);
                Quality selected=order.stream().filter(supported::contains).filter(activeLens::supports).findFirst().orElseThrow(()->new IllegalStateException("No recording quality"));
                requestedQuality=requested;
                actualQuality=selected==Quality.UHD?"4K":selected==Quality.FHD?"1080p":selected==Quality.HD?"720p":"480p";
                recorder=new Recorder.Builder().setQualitySelector(QualitySelector.from(selected,FallbackStrategy.lowerQualityOrHigherThan(selected)))
                    .setTargetVideoEncodingBitRate(selected==Quality.UHD?60_000_000:selected==Quality.FHD?24_000_000:selected==Quality.HD?12_000_000:4_000_000).build();
                int rotation=JournalRules.targetRotation(info.getSensorRotationDegrees(Surface.ROTATION_0),landscape);
                Preview.Builder previewBuilder=new Preview.Builder().setTargetRotation(rotation);
                VideoCapture.Builder<Recorder> videoBuilder=new VideoCapture.Builder<>(recorder).setTargetRotation(rotation).setTargetFrameRate(new Range<>(30,30));
                if(activeLens.physicalId()!=null){
                    new Camera2Interop.Extender<>(previewBuilder).setPhysicalCameraId(activeLens.physicalId());
                    new Camera2Interop.Extender<>(videoBuilder).setPhysicalCameraId(activeLens.physicalId());
                }
                Preview viewfinder=previewBuilder.build();
                viewfinder.setSurfaceProvider(target.getSurfaceProvider());
                VideoCapture<Recorder> video=videoBuilder.build();
                ViewPort viewport=new ViewPort.Builder(new Rational(landscape?16:9,landscape?9:16),rotation).setScaleType(ViewPort.FILL_CENTER).build();
                Camera camera=provider.bindToLifecycle(owner,selector,new UseCaseGroup.Builder().addUseCase(viewfinder).addUseCase(video).setViewPort(viewport).build());
                boundCamera=camera;boundPreview=viewfinder;boundVideo=video;bound=true;
                zoomObserver=state->{if(boundCamera==camera&&bound&&state!=null)bindingListener.zoomChanged(lenses.displayed(state,activeLens,busy));};
                camera.getCameraInfo().getZoomState().observe(owner,zoomObserver);
                setZoom(requestedZoom);listener.ready();
            }catch(Exception e){bound=false;releaseUseCases();Log.e("DaylogCamera","Camera binding failed",e);listener.error("カメラを開けませんでした。権限と端末のカメラを確認してください。");}
        },ContextCompat.getMainExecutor(context));
    }
    public void unbind(){preview=null;bound=false;releaseUseCases();}
    private void releaseUseCases(){
        zoomRequest++;
        if(boundCamera!=null&&zoomObserver!=null)boundCamera.getCameraInfo().getZoomState().removeObserver(zoomObserver);
        boundCamera=null;zoomObserver=null;
        // A finishing Activity must never unbind a replacement Activity's camera.
        if(provider!=null&&boundPreview!=null&&boundVideo!=null)provider.unbind(boundPreview,boundVideo);
        boundPreview=null;boundVideo=null;
    }
    public void flip(){
        if(provider==null||preview==null)throw new IllegalStateException("Camera is not ready");
        CameraSelector next=front?CameraSelector.DEFAULT_BACK_CAMERA:CameraSelector.DEFAULT_FRONT_CAMERA;
        try { if(!provider.hasCamera(next))throw new IllegalStateException("Camera unavailable"); }
        catch(CameraInfoUnavailableException e){throw new IllegalStateException("Camera unavailable",e);}
        front=!front;lenses=null;requestedZoom=1;bound=false;bind(preview,horizontal,bindingListener);
    }
    public void setZoom(float ratio){
        Camera camera=boundCamera;if(camera==null||!bound||!Float.isFinite(ratio))return;
        if(!busy&&lenses.forRatio(ratio)!=activeLens){requestedZoom=ratio;bound=false;bind(preview,horizontal,bindingListener);return;}
        ZoomState state=camera.getCameraInfo().getZoomState().getValue();if(state==null)return;
        float scale=activeLens.scale();
        requestedZoom=Math.max(state.getMinZoomRatio(),Math.min(state.getMaxZoomRatio(),ratio/scale))*scale;
        if(Math.abs(state.getZoomRatio()-requestedZoom/scale)<.0001f)return;
        int request=++zoomRequest;var result=camera.getCameraControl().setZoomRatio(requestedZoom/scale);
        result.addListener(()->{
            if(request!=zoomRequest||boundCamera!=camera)return;
            try{result.get();}
            catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(java.util.concurrent.CancellationException e){/* A newer gesture supersedes this request. */}
            catch(java.util.concurrent.ExecutionException e){
                if(e.getCause() instanceof CameraControl.OperationCanceledException)return;
                Log.w("DaylogCamera","Zoom could not be applied",e.getCause());
                ZoomState current=camera.getCameraInfo().getZoomState().getValue();
                if(current!=null){requestedZoom=current.getZoomRatio()*activeLens.scale();bindingListener.zoomChanged(lenses.displayed(current,activeLens,busy));}
                bindingListener.zoomError("倍率を変更できませんでした");
            }
        },ContextCompat.getMainExecutor(context));
    }
    private void publishZoom(){
        if(boundCamera==null||lenses==null)return;ZoomState state=boundCamera.getCameraInfo().getZoomState().getValue();
        if(state!=null)bindingListener.zoomChanged(lenses.displayed(state,activeLens,busy));
    }
    public String actualQuality(){return context.getSharedPreferences("daylog",Context.MODE_PRIVATE).getString("captureQuality","auto").equals(requestedQuality)?actualQuality:null;}
    public boolean busy(){return busy;}
    public void start(File target,int seconds,boolean audio,Location location,Listener listener){
        if(busy||!bound)return;busy=true;publishZoom();listener.progress(0,true);
        // Avoid including the physical tap in the saved clip.
        arm=()->{
            arm=null;
            try{
                capturedAt=System.currentTimeMillis();
                FileOutputOptions.Builder options=new FileOutputOptions.Builder(target);if(location!=null)options.setLocation(location);
                PendingRecording pending=recorder.prepareRecording(context,options.build());
                if(audio&&ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)pending=pending.withAudioEnabled();
                // If no first frame arrives, Start is never emitted; still allow recovery.
                tick=()->{if(recording!=null)stop();};handler.postDelayed(tick,12000);
                recording=pending.start(ContextCompat.getMainExecutor(context),event->{
                    if(event instanceof VideoRecordEvent.Start){
                        if(tick!=null)handler.removeCallbacks(tick);
                        capturedAt=System.currentTimeMillis();started=SystemClock.elapsedRealtime();
                        tick=new Runnable(){
                            @Override public void run(){
                                if(recording==null)return;
                                float fraction=Math.min(1f,(SystemClock.elapsedRealtime()-started)/(seconds*1000f));
                                listener.progress(fraction,false);
                                if(SystemClock.elapsedRealtime()-started>seconds*1000L+8000)stop();else handler.postDelayed(this,40);
                            }
                        };handler.post(tick);
                    }else if(event instanceof VideoRecordEvent.Status status){
                        // Stats advance after encoded samples have been written to the muxer.
                        // A duration upper limit can instead discard the last sparse frame too early.
                        long neededNanos=(seconds*1000L+100)*1_000_000L;
                        if(recording!=null&&status.getRecordingStats().getRecordedDurationNanos()>=neededNanos)stop();
                    }else if(event instanceof VideoRecordEvent.Finalize end){
                        if(tick!=null)handler.removeCallbacks(tick);recording=null;busy=false;publishZoom();
                        if(!end.hasError()||end.getError()==VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED||end.getError()==VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE)listener.saved(target,capturedAt);
                        else{Log.e("DaylogCamera","Recording failed, code="+end.getError(),end.getCause());target.delete();listener.error("撮影を保存できませんでした。空き容量とカメラの状態を確認してください。");}
                    }
                });
            }catch(SecurityException e){if(tick!=null)handler.removeCallbacks(tick);recording=null;busy=false;publishZoom();target.delete();listener.error("カメラ・マイクの権限が変更されました。設定を確認してください。");}
            catch(RuntimeException e){if(tick!=null)handler.removeCallbacks(tick);Log.e("DaylogCamera","Recording start failed",e);recording=null;busy=false;publishZoom();target.delete();listener.error("撮影を始められませんでした。カメラ・マイクの権限を確認してください。");}
        };handler.postDelayed(arm,500);
    }
    public void stop(){
        if(recording!=null){if(tick!=null)handler.removeCallbacks(tick);recording.stop();}
        if(arm!=null){handler.removeCallbacks(arm);arm=null;busy=false;publishZoom();}
    }
}
