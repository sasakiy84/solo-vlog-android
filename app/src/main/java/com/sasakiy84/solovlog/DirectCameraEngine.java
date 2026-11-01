package com.sasakiy84.solovlog;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.location.Location;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import androidx.camera.viewfinder.view.ViewfinderView;
import androidx.camera.viewfinder.core.ViewfinderSurfaceRequest;
import androidx.camera.viewfinder.core.ViewfinderSurfaceSession;
import androidx.camera.viewfinder.core.TransformationInfo;
import androidx.camera.viewfinder.core.ImplementationMode;
import androidx.camera.viewfinder.core.ScaleType;
import androidx.camera.viewfinder.core.camera2.Camera2TransformationInfo;
import androidx.camera.core.ZoomState;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** A direct Camera2 path for public cameras that CameraX cannot bind. All hardware work is serialized. */
final class DirectCameraEngine {
    private final Context context;
    private final CameraProfiles.Route route;
    private final boolean landscape;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HandlerThread thread=new HandlerThread("DaylogCamera2");
    private final Handler worker;
    private final CameraManager manager;
    private CameraDevice device;
    private CameraCaptureSession session;
    private CaptureRequest.Builder request;
    private CameraCharacteristics source,parent;
    private Surface previewSurface;
    private MediaRecorder recorder;
    private File output;
    private ViewfinderView viewfinder;
    private ViewfinderSurfaceSession previewSession;
    private com.google.common.util.concurrent.ListenableFuture<ViewfinderSurfaceSession> surfaceFuture;
    private Size videoSize,previewSize;
    private List<Size> sizes=List.of();
    private int sizeIndex,rotation,sessionGeneration,openRetries,recordedFrames;
    private long capturedAt,started,firstSensorTime,lastSensorTime;
    private int seconds;
    private boolean opening,recording,audio,physicalCrop,maxDurationReached;
    private Location location;
    private volatile boolean closed,ready,busy;
    private float scale=1,nativeMin=1,nativeMax=1,zoom=1;
    private CameraEngine.Listener listener;
    private volatile float pendingZoom;
    private final java.util.concurrent.atomic.AtomicBoolean zoomScheduled=new java.util.concurrent.atomic.AtomicBoolean();
    private CameraCaptureSession.CaptureCallback captureCallback;
    private final Runnable applyPendingZoom=new Runnable(){@Override public void run(){
        zoomScheduled.set(false);
        if(closed||!ready)return;
        float target=Math.max(nativeMin,Math.min(nativeMax,pendingZoom/scale));
        if(Math.abs(target-zoom)<.0001f)return;zoom=target;
        try{if(request!=null&&session!=null){applyZoom();session.setRepeatingRequest(request.build(),captureCallback,worker);}publishZoom();}
        catch(Exception e){event("zoomError",e.toString());callback(()->listener.zoomError("倍率を変更できませんでした"));}
    }};
    private final Runnable timeout=()->fail("カメラの応答がありませんでした",null);
    private final Runnable arm=this::prepareRecording;
    private final Runnable tick=new Runnable(){public void run(){
        if(!recording||closed)return;
        long span=lastSensorTime-firstSensorTime;
        float progress=firstSensorTime==0?0:Math.min(.95f,span/(seconds*1_000_000_000f));
        callback(()->listener.progress(progress,false));
        if(SystemClock.elapsedRealtime()-started>seconds*1000L+20000)fail("録画フレームが届きませんでした",null);
        else worker.postDelayed(this,40);
    }};
    DirectCameraEngine(Context context,CameraProfiles.Route route,boolean landscape,CameraEngine.Listener listener){
        this.context=context;this.route=route;this.landscape=landscape;this.listener=listener;manager=context.getSystemService(CameraManager.class);
        thread.start();worker=new Handler(thread.getLooper());
    }
    boolean matches(CameraProfiles.Route candidate,boolean wide){return !closed&&route.equals(candidate)&&landscape==wide;}
    boolean busy(){return busy;}
    boolean frontFacing(){return source!=null&&Integer.valueOf(CameraCharacteristics.LENS_FACING_FRONT).equals(source.get(CameraCharacteristics.LENS_FACING));}
    String quality(){return videoSize==null?null:videoSize.getWidth()+"×"+videoSize.getHeight();}
    void bind(ViewfinderView view,CameraEngine.Listener listener){
        this.listener=listener;
        if(viewfinder==view&&ready){transform();callback(listener::ready);publishZoom();return;}
        viewfinder=view;listener.binding();
        view.addOnLayoutChangeListener((v,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->transform());
        worker.post(this::open);
    }
    private void open(){
        if(closed||opening||device!=null)return;opening=true;
        try{
            parent=manager.getCameraCharacteristics(route.cameraId());source=route.physicalId()==null?parent:manager.getCameraCharacteristics(route.physicalId());
            var exposureState=exposure();
            if(exposureState.supported())exposureIndex=Math.max(exposureState.minimum(),Math.min(exposureState.maximum(),Math.round(initialExposure/exposureState.step())));
            scale=CameraProfiles.scale(manager,route);
            Range<Float> nativeRange=parent.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if(route.physicalId()==null&&nativeRange!=null){nativeMin=nativeRange.getLower();nativeMax=nativeRange.getUpper();}
            else{nativeMin=1;Float max=source.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);nativeMax=max==null?1:max;
                physicalCrop=route.physicalId()!=null&&parent.getAvailablePhysicalCameraRequestKeys()!=null&&parent.getAvailablePhysicalCameraRequestKeys().contains(CaptureRequest.SCALER_CROP_REGION);if(route.physicalId()!=null&&!physicalCrop)nativeMax=1;}
            zoom=nativeMin;
            Integer orientation=source.get(CameraCharacteristics.SENSOR_ORIENTATION);int sensor=orientation==null?90:orientation;
            rotation=JournalRules.targetRotation(sensor,landscape)==1?90:0;
            boolean front=Integer.valueOf(CameraCharacteristics.LENS_FACING_FRONT).equals(source.get(CameraCharacteristics.LENS_FACING));
            int outputDegrees=(sensor+(front?rotation:-rotation)+360)%360;
            // Encoder orientation metadata is independent of display rotation.
            rotation=(outputDegrees+route.videoCorrection())%360;
            var map=source.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);if(map==null)throw new IllegalStateException("No stream configuration");
            Size[] supported=map.getOutputSizes(MediaRecorder.class);if(supported==null)supported=map.getOutputSizes(android.graphics.ImageFormat.PRIVATE);
            if(supported==null)throw new IllegalStateException("No recording sizes");
            String quality=context.getSharedPreferences("daylog",0).getString("captureQuality","auto");int limit=quality.equals("hd")?1280:quality.equals("fhd")?1920:3840;
            sizes=Arrays.stream(supported).filter(s->s.getWidth()<=limit&&s.getWidth()>=640&&Math.abs(s.getWidth()/(float)s.getHeight()-16f/9)<.04)
                .sorted(Comparator.comparingInt(Size::getWidth).reversed()).distinct().toList();
            if(sizes.isEmpty())throw new IllegalStateException("No 16:9 recording size");sizeIndex=0;videoSize=sizes.get(0);
            Size[] previews=map.getOutputSizes(SurfaceTexture.class);if(previews==null)throw new IllegalStateException("No preview sizes");
            previewSize=Arrays.stream(previews).filter(s->s.getWidth()<=1920&&Math.abs(s.getWidth()/(float)s.getHeight()-16f/9)<.04)
                .min(Comparator.comparingInt(s->Math.abs(s.getWidth()-1280))).orElseThrow(()->new IllegalStateException("No 16:9 preview"));
            event("open",route.key()+", scale="+scale+", zoom="+nativeMin+".."+nativeMax+", recording="+videoSize+", preview="+previewSize+", sensorOrientation="+sensor+", orientationHint="+rotation+", distortionCorrectionModes="+Arrays.toString(source.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)));
            worker.removeCallbacks(timeout);worker.postDelayed(timeout,15000);
            if(previewSession!=null){openCamera();return;}
            main.post(()->{
                if(closed)return;
                transform();viewfinder.setScaleType(ScaleType.FIT_CENTER);
                surfaceFuture=viewfinder.requestSurfaceSessionAsync(new ViewfinderSurfaceRequest(previewSize.getWidth(),previewSize.getHeight(),ImplementationMode.EMBEDDED));
                var future=surfaceFuture;
                future.addListener(()->{
                    try{
                        ViewfinderSurfaceSession supplied=future.get();
                        if(closed){closePreviewSession(supplied);return;}
                        if(!worker.post(()->{if(closed){closePreviewSession(supplied);return;}previewSession=supplied;previewSurface=supplied.getSurface();openCamera();}))closePreviewSession(supplied);
                    }catch(java.util.concurrent.CancellationException ignored){/* Closing the camera cancels the request. */}
                    catch(Exception e){worker.post(()->fail("プレビューを準備できませんでした",e));}
                },context.getMainExecutor());
            });
        }catch(Exception e){cameraOpenFailed(e);}
    }
    private void openCamera(){
        if(closed)return;
        try{
            manager.openCamera(route.cameraId(),context.getMainExecutor(),new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice camera){if(closed){camera.close();return;}worker.post(()->{if(closed){camera.close();return;}device=camera;configure(false);});}
                public void onDisconnected(CameraDevice camera){camera.close();if(!closed)worker.post(()->fail("カメラが切断されました",null));}
                public void onError(CameraDevice camera,int error){camera.close();if(!closed)worker.post(()->{
                    if((error==ERROR_CAMERA_IN_USE||error==ERROR_MAX_CAMERAS_IN_USE)&&openRetries++<3){opening=false;event("openRetry","error="+error);worker.postDelayed(DirectCameraEngine.this::open,700);}
                    else fail("カメラを開けませんでした（"+error+"）",null);
                });}
            });
        }catch(SecurityException e){cameraOpenFailed(e);}
        catch(Exception e){cameraOpenFailed(e);}
    }
    private void cameraOpenFailed(Exception error){
        if(error instanceof CameraAccessException e&&(e.getReason()==CameraAccessException.CAMERA_IN_USE||e.getReason()==CameraAccessException.MAX_CAMERAS_IN_USE)&&openRetries++<3){
            opening=false;event("openRetry",e.toString());worker.postDelayed(this::open,700);
        }else fail("このカメラを開けませんでした",error);
    }
    private void configure(boolean record){
        if(closed||device==null)return;
        int generation=++sessionGeneration;if(session!=null){session.close();session=null;}
        try{
            List<OutputConfiguration> outputs=new ArrayList<>();
            if(!record||!route.recordOnly()){OutputConfiguration preview=new OutputConfiguration(previewSurface);if(route.physicalId()!=null)preview.setPhysicalCameraId(route.physicalId());outputs.add(preview);}
            if(record){OutputConfiguration video=new OutputConfiguration(recorder.getSurface());if(route.physicalId()!=null)video.setPhysicalCameraId(route.physicalId());outputs.add(video);}
            device.createCaptureSession(new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,outputs,context.getMainExecutor(),new CameraCaptureSession.StateCallback(){
                public void onConfigured(CameraCaptureSession configured){
                    if(closed||generation!=sessionGeneration){configured.close();return;}
                    worker.post(()->{
                        if(closed||generation!=sessionGeneration){configured.close();return;}session=configured;
                        try{
                            request=device.createCaptureRequest(record?CameraDevice.TEMPLATE_RECORD:CameraDevice.TEMPLATE_PREVIEW);if(!record||!route.recordOnly())request.addTarget(previewSurface);if(record)request.addTarget(recorder.getSurface());
                            request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                            int[] distortionModes=source.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES);
                            if(route.lensCorrection()>=0&&distortionModes!=null&&Arrays.stream(distortionModes).anyMatch(mode->mode==route.lensCorrection()))request.set(CaptureRequest.DISTORTION_CORRECTION_MODE,route.lensCorrection());
                            int[] rotateModes=source.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES);
                            if(rotateModes!=null&&Arrays.stream(rotateModes).anyMatch(mode->mode==CaptureRequest.SCALER_ROTATE_AND_CROP_NONE))request.set(CaptureRequest.SCALER_ROTATE_AND_CROP,CaptureRequest.SCALER_ROTATE_AND_CROP_NONE);
                            int[] autofocus=source.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
                            if(autofocus!=null&&Arrays.stream(autofocus).anyMatch(m->m==CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO))request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                            // Keep the full available field of view during diagnosis and normal recording.
                            request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);

                            request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,exposureIndex);
                            applyZoom();captureCallback=new CameraCaptureSession.CaptureCallback(){
                                private boolean first;private int captureFailures;
                                @Override public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure failure){if(!closed&&generation==sessionGeneration&&captureFailures++<5)event("captureFailed","reason="+failure.getReason()+", frame="+failure.getFrameNumber()+", captured="+failure.wasImageCaptured());}
                                @Override public void onCaptureBufferLost(CameraCaptureSession s,CaptureRequest r,Surface target,long frame){if(!closed&&generation==sessionGeneration&&captureFailures++<5)event("captureBufferLost","target="+(target==previewSurface?"preview":"recording")+", frame="+frame);}
                                public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result){
                                    if(closed||generation!=sessionGeneration)return;
                                    if(record){Long timestamp=result.get(CaptureResult.SENSOR_TIMESTAMP);if(timestamp!=null){if(firstSensorTime==0){firstSensorTime=timestamp;capturedAt=System.currentTimeMillis();}lastSensorTime=timestamp;recordedFrames++;}}
                                    if(first)return;first=true;
                                    event("captureResult","physical="+result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)+", zoom="+result.get(CaptureResult.CONTROL_ZOOM_RATIO)+", crop="+result.get(CaptureResult.SCALER_CROP_REGION)+", videoStabilization="+result.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)+", rotateAndCrop="+result.get(CaptureResult.SCALER_ROTATE_AND_CROP)+", distortionCorrection="+result.get(CaptureResult.DISTORTION_CORRECTION_MODE));
                                    for(var physical:result.getPhysicalCameraResults().entrySet())event("physicalCaptureResult",physical.getKey()+": zoom="+physical.getValue().get(CaptureResult.CONTROL_ZOOM_RATIO)+", crop="+physical.getValue().get(CaptureResult.SCALER_CROP_REGION));
                                    if(!record){worker.removeCallbacks(timeout);opening=false;ready=true;publishZoom();callback(()->listener.ready());}
                                }
                            };session.setRepeatingRequest(request.build(),captureCallback,worker);
                            if(record){firstSensorTime=0;lastSensorTime=0;recordedFrames=0;recorder.start();recording=true;worker.removeCallbacks(timeout);capturedAt=System.currentTimeMillis();started=SystemClock.elapsedRealtime();event("recording",videoSize+", orientationHint="+rotation);worker.post(tick);}
                        }catch(Exception e){if(record)retryRecording(e);else fail("プレビューを開始できませんでした",e);}
                    });
                }
                public void onConfigureFailed(CameraCaptureSession failed){failed.close();if(!closed&&generation==sessionGeneration)worker.post(()->{if(record)retryRecording(new IllegalStateException("Session configuration failed"));else fail("このカメラの映像を表示できませんでした",null);});}
            }));
        }catch(Exception e){if(record)retryRecording(e);else fail("カメラの構成に失敗しました",e);}
    }
    private volatile int exposureIndex;
    private volatile float initialExposure;
    void initialExposure(float ev){initialExposure=ev;var state=exposure();if(state.supported())setExposure(Math.round(ev/state.step()));}
    CameraEngine.Exposure exposure(){
        if(parent==null)return new CameraEngine.Exposure(0,0,0,0);
        var range=parent.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        var step=parent.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        return range==null||step==null?new CameraEngine.Exposure(0,0,0,0):new CameraEngine.Exposure(range.getLower(),range.getUpper(),step.floatValue(),exposureIndex);
    }
    void setExposure(int index){
        worker.post(()->{
            if(closed)return;
            var state=exposure();exposureIndex=Math.max(state.minimum(),Math.min(state.maximum(),index));
            try{if(request!=null&&session!=null){request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,exposureIndex);session.setRepeatingRequest(request.build(),captureCallback,worker);}}
            catch(Exception e){event("exposureError",e.toString());callback(()->listener.zoomError("明るさを変更できませんでした"));}
        });
    }
    void setZoom(float ratio){
        if(closed||!Float.isFinite(ratio))return;pendingZoom=ratio;
        if(zoomScheduled.compareAndSet(false,true))worker.postDelayed(applyPendingZoom,33);
    }
    private void applyZoom(){
        if(route.physicalId()==null&&parent.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)!=null){request.set(CaptureRequest.CONTROL_ZOOM_RATIO,zoom);return;}
        Rect active=source.get(route.lensCorrection()==0?CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE:CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);if(active==null)active=source.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);if(active==null)return;
        int w=Math.round(active.width()/zoom),h=Math.round(active.height()/zoom);Rect crop=new Rect(active.centerX()-w/2,active.centerY()-h/2,active.centerX()+w/2,active.centerY()+h/2);
        if(route.physicalId()==null)request.set(CaptureRequest.SCALER_CROP_REGION,crop);
        else if(physicalCrop)request.setPhysicalCameraKey(CaptureRequest.SCALER_CROP_REGION,crop,route.physicalId());
    }
    private void publishZoom(){float value=zoom*scale,min=nativeMin*scale,max=nativeMax*scale;
        ZoomState state=new ZoomState(){public float getZoomRatio(){return value;}public float getMinZoomRatio(){return min;}public float getMaxZoomRatio(){return max;}public float getLinearZoom(){return max>min?(value-min)/(max-min):0;}};
        callback(()->listener.zoomChanged(state));
    }
    void start(File file,int seconds,boolean audio,Location location,CameraEngine.Listener listener){
        if(busy)return;if(closed||!ready){listener.binding();listener.error("カメラを準備しています。もう一度お試しください");return;}busy=true;this.listener=listener;this.output=file;this.seconds=seconds;this.audio=audio;this.location=location;
        listener.progress(0,true);worker.postDelayed(arm,500);
    }
    private void prepareRecording(){
        if(closed||!busy)return;
        try{
            recorder=new MediaRecorder(context);
            boolean sound=audio&&ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
            if(sound)recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setOutputFile(output.getAbsolutePath());
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);recorder.setVideoSize(videoSize.getWidth(),videoSize.getHeight());recorder.setVideoFrameRate(30);
            recorder.setVideoEncodingBitRate(videoSize.getWidth()>=3840?60_000_000:videoSize.getWidth()>=1920?24_000_000:12_000_000);
            if(sound){recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioEncodingBitRate(128000);recorder.setAudioSamplingRate(48000);}
            recorder.setMaxDuration(seconds*1000+150);maxDurationReached=false;
            recorder.setOnInfoListener((r,what,extra)->{if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)worker.post(()->{if(recorder==r&&recording){maxDurationReached=true;event("encodedDurationLimit","durationMs="+(seconds*1000+150));finishRecording(true);}});});
            recorder.setOrientationHint(rotation);if(location!=null)recorder.setLocation((float)location.getLatitude(),(float)location.getLongitude());
            recorder.setOnErrorListener((r,what,extra)->worker.post(()->{if(recorder==r&&recording)fail("撮影中にエラーが発生しました（"+what+"）",null);}));
            recorder.prepare();worker.removeCallbacks(timeout);worker.postDelayed(timeout,20000);configure(true);
        }catch(Exception e){retryRecording(e);}
    }
    private void retryRecording(Exception error){
        event("recordingConfigurationError",videoSize+": "+error);releaseRecorder();
        if(closed||!busy)return;
        if(++sizeIndex<sizes.size()){videoSize=sizes.get(sizeIndex);prepareRecording();}else fail("このカメラで動画を撮影できませんでした",error);
    }
    void stop(){worker.post(()->{
        worker.removeCallbacks(arm);if(recording)finishRecording(true);else if(busy){busy=false;++sessionGeneration;releaseRecorder();if(output!=null)output.delete();ready=false;callback(()->{listener.binding();listener.error("撮影を中止しました");});if(!closed)configure(false);}
    });}
    private void finishRecording(boolean resume){
        if(!recording)return;
        worker.removeCallbacks(tick);worker.removeCallbacks(timeout);
        File finished=output;long at=capturedAt;boolean success=false;event("recordingFrames","count="+recordedFrames+", sensorDurationMs="+(lastSensorTime-firstSensorTime)/1_000_000L);
        try{if(session!=null){session.stopRepeating();session.abortCaptures();}}catch(Exception e){event("stopStreamError",e.toString());}
        try{recorder.stop();success=true;}
        catch(RuntimeException e){event("recordingStopError",e.toString());if(maxDurationReached){releaseRecorder();
            try(android.media.MediaMetadataRetriever media=new android.media.MediaMetadataRetriever()){media.setDataSource(finished.getAbsolutePath());success=Long.parseLong(media.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION))>=seconds*1000L-200;}
            catch(Exception invalid){event("recordingFileError",invalid.toString());}
        }}
        recording=false;busy=false;releaseRecorder();
        if(success){event("recorded","bytes="+finished.length());main.post(()->listener.saved(finished,at));}
        else{if(finished!=null)finished.delete();callback(()->listener.error("動画を保存できませんでした"));}
        if(resume&&!closed){ready=false;callback(()->listener.binding());configure(false);}
    }
    private void releaseRecorder(){if(recorder!=null){try{recorder.reset();}catch(RuntimeException e){event("recorderResetError",e.toString());}recorder.release();recorder=null;}recording=false;}
    private void fail(String message,Exception error){
        if(closed)return;boolean wasBusy=busy;worker.removeCallbacks(arm);worker.removeCallbacks(timeout);worker.removeCallbacks(tick);ready=false;opening=false;busy=false;
        event("error",message+(error==null?"":": "+error));
        if(session!=null){session.close();session=null;}if(device!=null){device.close();device=null;}releaseRecorder();releasePreview();if(output!=null)output.delete();
        callback(()->{listener.binding();listener.error(message);});
        if(wasBusy&&!closed)worker.postDelayed(this::open,500);
    }
    void close(){
        if(closed)return;closed=true;ready=false;
        main.post(()->{if(surfaceFuture!=null)surfaceFuture.cancel(false);});
        worker.post(()->{worker.removeCallbacksAndMessages(null);boolean pending=busy&&!recording;if(recording)finishRecording(false);busy=false;++sessionGeneration;
            if(session!=null){session.close();session=null;}if(device!=null){device.close();device=null;}releaseRecorder();releasePreview();
            if(pending&&output!=null)output.delete();thread.quitSafely();});
    }
    private void callback(Runnable action){main.post(()->{if(!closed)action.run();});}
    private void event(String stage,String detail){main.post(()->listener.diagnostic(stage,detail));}
    private void releasePreview(){
        previewSurface=null;
        if(previewSession!=null){ViewfinderSurfaceSession released=previewSession;previewSession=null;main.post(()->closePreviewSession(released));}
    }
    private void closePreviewSession(ViewfinderSurfaceSession supplied){
        try{supplied.close();}catch(Exception e){event("previewReleaseError",e.toString());}
    }
    private void transform(){
        if(closed||viewfinder==null||source==null)return;
        TransformationInfo camera=Camera2TransformationInfo.createFromCharacteristics(source);
        int display=viewfinder.getDisplay()==null?0:viewfinder.getDisplay().getRotation()*90;
        int angle=Math.floorMod(camera.getSourceRotation()-display+route.previewCorrection(),360);
        viewfinder.setTransformationInfo(new TransformationInfo(angle,camera.isSourceMirroredHorizontally(),camera.isSourceMirroredVertically()));
        event("previewTransform","renderer=JetpackViewfinder, sensor="+camera.getSourceRotation()+", display="+display+", correction="+route.previewCorrection()+", view="+viewfinder.getWidth()+"x"+viewfinder.getHeight()+", buffer="+previewSize+", scale=FIT_CENTER");
    }
}
