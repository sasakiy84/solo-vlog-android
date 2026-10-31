package com.sasakiy84.solovlog;

import android.hardware.camera2.CameraCharacteristics;
import android.util.SizeF;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import java.util.ArrayList;
import java.util.List;

/** CameraX zoom ratios are local to a lens; the controls use one range across rear lenses. */
final class CameraLenses {
    record Lens(CameraSelector selector,float scale,float minimum,float maximum,CameraInfo physicalInfo) {
        String physicalId(){return physicalInfo==null?null:Camera2CameraInfo.from(physicalInfo).getCameraId();}
        boolean supports(androidx.camera.video.Quality quality){
            if(physicalInfo==null)return true;
            var map=Camera2CameraInfo.from(physicalInfo).getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(map==null)return true;android.util.Size[] sizes=map.getOutputSizes(android.graphics.ImageFormat.PRIVATE);if(sizes==null)return true;
            int width=quality==androidx.camera.video.Quality.UHD?3840:quality==androidx.camera.video.Quality.FHD?1920:quality==androidx.camera.video.Quality.HD?1280:640;
            int height=quality==androidx.camera.video.Quality.UHD?2160:quality==androidx.camera.video.Quality.FHD?1080:quality==androidx.camera.video.Quality.HD?720:480;
            for(android.util.Size size:sizes)if(size.getWidth()>=width&&size.getHeight()>=height)return true;return false;
        }
    }
    final Lens standard,wide;
    private CameraLenses(Lens standard,Lens wide){this.standard=standard;this.wide=wide;}
    static CameraLenses forced(CameraSelector selector,CameraInfo info,String physicalId,float scale){
        CameraInfo physical=null;
        if(physicalId!=null){for(CameraInfo child:info.getPhysicalCameraInfos())if(Camera2CameraInfo.from(child).getCameraId().equals(physicalId)){physical=child;break;}
            if(physical==null)throw new IllegalArgumentException("CameraX cannot expose this physical camera");}
        Lens range=lens(selector,info,scale);
        return new CameraLenses(new Lens(selector,scale,range.minimum(),range.maximum(),physical),null);
    }
    static CameraLenses discover(ProcessCameraProvider provider,CameraSelector selector,boolean front){
        CameraInfo main=provider.getCameraInfo(selector);ZoomState zoom=main.getZoomState().getValue();
        if(front||zoom.getMinZoomRatio()<=.6f)return new CameraLenses(lens(selector,main,1),null);
        List<CameraInfo> candidates=new ArrayList<>(provider.getAvailableCameraInfos());
        java.util.Set<CameraInfo> physical=main.getPhysicalCameraInfos();candidates.addAll(physical);
        CameraInfo widest=null;float smallest=.85f;
        for(CameraInfo info:candidates){
            if(info==main||info.getLensFacing()!=CameraSelector.LENS_FACING_BACK)continue;
            float relative=fieldOfViewRatio(info,main);
            if(relative>0&&relative<smallest){widest=info;smallest=relative;}
        }
        if(widest!=null){
            boolean internal=physical.contains(widest);
            CameraSelector wideSelector=internal?selector:widest.getCameraSelector();
            Lens range=lens(wideSelector,internal?main:widest,smallest);
            Lens wide=new Lens(wideSelector,range.scale(),range.minimum(),range.maximum(),internal?widest:null);
            return new CameraLenses(lens(selector,main,1),wide);
        }
        // Preserve the actual field-of-view scale when no wider camera is available.
        return new CameraLenses(lens(selector,main,1),null);
    }
    private static Lens lens(CameraSelector selector,CameraInfo info,float scale){
        ZoomState state=info.getZoomState().getValue();
        return new Lens(selector,scale,state.getMinZoomRatio()*scale,state.getMaxZoomRatio()*scale,null);
    }
    private static float fieldOfViewRatio(CameraInfo candidate,CameraInfo main){
        float a=equivalentFocalLength(candidate),b=equivalentFocalLength(main);
        return a>0&&b>0?a/b:0;
    }
    private static float equivalentFocalLength(CameraInfo info){
        Camera2CameraInfo camera=Camera2CameraInfo.from(info);
        SizeF sensor=camera.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        float[] focal=camera.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if(sensor==null||focal==null||focal.length==0||sensor.getWidth()<=0)return 0;
        float shortest=focal[0];for(float f:focal)shortest=Math.min(shortest,f);
        return shortest/sensor.getWidth();
    }
    Lens forRatio(float ratio){return wide!=null&&ratio<standard.minimum()?wide:standard;}
    Lens forRatio(float ratio,Lens current){
        if(wide==null||ratio==1)return standard;
        if(current==wide&&ratio<standard.minimum()*1.04f)return wide;
        if(current==standard&&ratio>standard.minimum()*.98f)return standard;
        return forRatio(ratio);
    }
    ZoomState displayed(ZoomState nativeState,Lens active,boolean recording){
        float minimum=recording?nativeState.getMinZoomRatio()*active.scale():Math.min(standard.minimum(),wide==null?standard.minimum():wide.minimum());
        float maximum=recording?nativeState.getMaxZoomRatio()*active.scale():Math.max(standard.maximum(),wide==null?standard.maximum():wide.maximum());
        float ratio=nativeState.getZoomRatio()*active.scale();
        return new ZoomState(){
            public float getZoomRatio(){return ratio;}
            public float getMinZoomRatio(){return minimum;}
            public float getMaxZoomRatio(){return maximum;}
            public float getLinearZoom(){return maximum>minimum?(ratio-minimum)/(maximum-minimum):0;}
        };
    }
}
