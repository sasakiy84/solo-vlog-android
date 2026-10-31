package com.sasakiy84.solovlog;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.SizeF;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Prefer advertised cameras, with a bounded fallback for unlisted rear lenses. */
final class CameraProfiles {
    record Route(String cameraId,String physicalId,boolean cameraX,boolean recordOnly,int previewCorrection,int videoCorrection,int lensCorrection) {
        Route{if(lensCorrection< -1||lensCorrection>2)throw new IllegalArgumentException("Invalid lens correction");if(previewCorrection%90!=0||videoCorrection%90!=0)throw new IllegalArgumentException("Invalid camera rotation");previewCorrection=Math.floorMod(previewCorrection,360);videoCorrection=Math.floorMod(videoCorrection,360);if(cameraId==null||cameraId.isBlank())throw new IllegalArgumentException("Missing camera ID");if(cameraX)recordOnly=false;}
        Route(String cameraId,String physicalId){this(cameraId,physicalId,false,false,0,0,-1);}
        Route(String cameraId,String physicalId,boolean cameraX,boolean recordOnly){this(cameraId,physicalId,cameraX,recordOnly,0,0,-1);}
        Route withCorrection(int preview,int video,int lens){return new Route(cameraId,physicalId,cameraX,recordOnly,preview,video,lens);}
        String key(){return (cameraX?"camerax:":"camera2:")+cameraId+(physicalId==null?"":"/"+physicalId)+(recordOnly?":single":"");}
        String label(){return "ID "+cameraId+(physicalId==null?"":" / "+physicalId)+" · "+(cameraX?"CameraX":"Camera2")+(recordOnly?" · 互換":"");}
        JSONObject json() throws Exception{return new JSONObject().put("cameraId",cameraId).put("physicalId",physicalId==null?JSONObject.NULL:physicalId).put("cameraX",cameraX).put("recordOnly",recordOnly).put("previewCorrection",previewCorrection).put("videoCorrection",videoCorrection).put("lensCorrection",lensCorrection);}
    }
    record Scan(List<Route> routes,JSONObject report) {}
    record RearPair(Route standard,Route wide,float minimum,float maximum) {
        Route forRatio(float ratio){return ratio<1?wide:standard;}
    }
    /** Public lenses stay on CameraX; unlisted numeric IDs are a bounded metadata-only fallback. */
    static RearPair rearPair(Context context,Route selected){
        if(selected!=null&&(selected.cameraX()||selected.physicalId()!=null))return null;
        CameraManager manager=context.getSystemService(CameraManager.class);
        try{
            List<String> publicIds=List.of(manager.getCameraIdList());
            for(String referenceId:publicIds){
                CameraCharacteristics reference=manager.getCameraCharacteristics(referenceId);
                if(!Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(reference.get(CameraCharacteristics.LENS_FACING)))continue;
                var range=reference.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                if(range==null||range.getLower()!=1f||focal(reference)<=0)return null;
                Route standard=selected!=null&&selected.cameraId().equals(referenceId)?selected:new Route(referenceId,null);
                if(selected!=null){
                    CameraCharacteristics info=manager.getCameraCharacteristics(selected.cameraId());float ratio=focal(info)/focal(reference);
                    if(isWideVideoCamera(info,ratio))return pair(standard,selected,info,ratio,range.getUpper());
                    if(!selected.cameraId().equals(referenceId))return null;
                }
                // CameraX already supports advertised logical/physical rear lenses.
                java.util.Set<String> advertised=new java.util.LinkedHashSet<>(publicIds);advertised.addAll(reference.getPhysicalCameraIds());
                for(String id:advertised){
                    CameraCharacteristics info=manager.getCameraCharacteristics(id);
                    float ratio=focal(info)/focal(reference);
                    if(isWideVideoCamera(info,ratio))return selected==null?null:pair(standard,new Route(publicIds.contains(id)?id:referenceId,publicIds.contains(id)?null:id),info,ratio,range.getUpper());
                }
                Route widest=null;CameraCharacteristics widestInfo=null;float smallest=.85f;
                // Camera IDs are opaque; this probes common numeric aliases without opening devices.
                // Unknown string IDs cannot be guessed and remain available through manual selection.
                for(int number=0;number<20;number++){
                    String id=Integer.toString(number);if(advertised.contains(id))continue;
                    try{
                        CameraCharacteristics info=manager.getCameraCharacteristics(id);float ratio=focal(info)/focal(reference);
                        if(isWideVideoCamera(info,ratio)&&ratio<smallest){widest=new Route(id,null);widestInfo=info;smallest=ratio;}
                    }catch(android.hardware.camera2.CameraAccessException|IllegalArgumentException|SecurityException unavailable){android.util.Log.d("DaylogCamera","Unlisted camera unavailable: "+id);}
                }
                return widest==null?null:pair(standard,widest,widestInfo,smallest,range.getUpper());
            }
        }catch(Exception e){android.util.Log.w("DaylogCamera","Wide camera discovery unavailable",e);}
        return null;
    }
    private static boolean isWideVideoCamera(CameraCharacteristics info,float ratio){
        if(!Float.isFinite(ratio)||ratio<=0||ratio>=.85f||!Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(info.get(CameraCharacteristics.LENS_FACING)))return false;
        var map=info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);if(map==null)return false;
        var video=map.getOutputSizes(android.media.MediaRecorder.class);var preview=map.getOutputSizes(android.graphics.SurfaceTexture.class);
        return video!=null&&preview!=null&&Arrays.stream(video).anyMatch(s->s.getWidth()>=640&&Math.abs(s.getWidth()/(float)s.getHeight()-16f/9)<.04)
            &&Arrays.stream(preview).anyMatch(s->s.getWidth()<=1920&&Math.abs(s.getWidth()/(float)s.getHeight()-16f/9)<.04);
    }
    private static RearPair pair(Route standard,Route wide,CameraCharacteristics info,float ratio,float maximum){
        var range=info.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        return new RearPair(standard,wide,ratio*(range==null?1:range.getLower()),maximum);
    }
    static Route selected(Context context){
        String raw=context.getSharedPreferences("daylog",0).getString("cameraRoute","");
        if(raw.isEmpty())return null;
        try{JSONObject value=new JSONObject(raw);return new Route(value.getString("cameraId"),value.isNull("physicalId")?null:value.getString("physicalId"),value.optBoolean("cameraX",false),value.optBoolean("recordOnly",false),value.optInt("previewCorrection",0),value.optInt("videoCorrection",0),value.optInt("lensCorrection",-1));}
        catch(Exception e){android.util.Log.w("DaylogCamera","Invalid saved camera route",e);return null;}
    }
    static void select(Context context,Route route) throws Exception{
        context.getSharedPreferences("daylog",0).edit().putString("cameraRoute",route==null?"":route.json().toString()).apply();
    }
    static Scan scan(Context context) throws Exception{
        CameraManager manager=context.getSystemService(CameraManager.class);List<Route> routes=new ArrayList<>();JSONArray cameras=new JSONArray();
        java.util.Set<String> ids=new java.util.LinkedHashSet<>(List.of(manager.getCameraIdList()));
        RearPair pair=rearPair(context,selected(context));if(pair!=null)ids.add(pair.wide().cameraId());
        for(String id:ids){
            JSONObject camera=new JSONObject().put("id",id);
            try{
                CameraCharacteristics info=manager.getCameraCharacteristics(id);camera.put("characteristics",describe(info));
                if(Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(info.get(CameraCharacteristics.LENS_FACING)))addRoutes(routes,id,null);
                JSONArray physical=new JSONArray();
                for(String child:info.getPhysicalCameraIds()){
                    JSONObject item=new JSONObject().put("id",child);
                    try{CameraCharacteristics details=manager.getCameraCharacteristics(child);item.put("characteristics",describe(details));
                        if(Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(details.get(CameraCharacteristics.LENS_FACING)))addRoutes(routes,id,child);
                    }catch(Exception e){item.put("error",e.toString());}physical.put(item);
                }
                camera.put("physicalCameras",physical);
            }catch(Exception e){camera.put("error",e.toString());}cameras.put(camera);
        }
        JSONObject report=new JSONObject().put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL)
            .put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT).put("build",Build.DISPLAY)
            .put("appVersion",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName)
            .put("cameras",cameras).put("attempts",new JSONArray())
            .put("cameraPermission",androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED)
            .put("microphonePermission",androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED);
        return new Scan(List.copyOf(routes),report);
    }
    private static void addRoutes(List<Route> routes,String id,String child){
        routes.add(new Route(id,child,true,false));routes.add(new Route(id,child,false,false));routes.add(new Route(id,child,false,true));
    }
    private static JSONObject describe(CameraCharacteristics info) throws Exception{
        var map=info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return new JSONObject().put("facing",info.get(CameraCharacteristics.LENS_FACING)).put("sensorOrientation",info.get(CameraCharacteristics.SENSOR_ORIENTATION))
            .put("hardwareLevel",info.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)).put("capabilities",Arrays.toString(info.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)))
            .put("focalLengthsMm",Arrays.toString(info.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)))
            .put("sensorMm",String.valueOf(info.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)))
            .put("activeArray",String.valueOf(info.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)))
            .put("zoomRatioRange",String.valueOf(info.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)))
            .put("maximumDigitalZoom",info.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM))
            .put("videoStabilizationModes",Arrays.toString(info.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)))
            .put("recorderSizes",map==null?"":Arrays.toString(map.getOutputSizes(android.media.MediaRecorder.class)))
            .put("previewSizes",map==null?"":Arrays.toString(map.getOutputSizes(android.graphics.SurfaceTexture.class)))
            .put("physicalRequestKeys",String.valueOf(info.getAvailablePhysicalCameraRequestKeys()))
            .put("rotateAndCropModes",Arrays.toString(info.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES)))
            .put("distortionCorrectionModes",Arrays.toString(info.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)));
    }
    static float scale(CameraManager manager,Route route) throws Exception{
        CameraCharacteristics selected=manager.getCameraCharacteristics(route.physicalId()==null?route.cameraId():route.physicalId());
        for(String id:manager.getCameraIdList()){
            CameraCharacteristics reference=manager.getCameraCharacteristics(id);
            if(java.util.Objects.equals(selected.get(CameraCharacteristics.LENS_FACING),reference.get(CameraCharacteristics.LENS_FACING))){
                float a=focal(selected),b=focal(reference);return a>0&&b>0?a/b:1;
            }
        }
        return 1;
    }
    private static float focal(CameraCharacteristics info){
        SizeF sensor=info.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);float[] values=info.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if(sensor==null||sensor.getWidth()<=0||values==null)return 0;
        float shortest=Float.POSITIVE_INFINITY;for(float value:values)if(Float.isFinite(value)&&value>0)shortest=Math.min(shortest,value);
        return Float.isFinite(shortest)?shortest/sensor.getWidth():0;
    }
}
