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

/** Only public camera IDs and physical IDs advertised by their logical parent are inspected. */
final class CameraProfiles {
    record Route(String cameraId,String physicalId,boolean cameraX,boolean recordOnly) {
        Route{if(cameraId==null||cameraId.isBlank())throw new IllegalArgumentException("Missing camera ID");if(cameraX)recordOnly=false;}
        Route(String cameraId,String physicalId){this(cameraId,physicalId,false,false);}
        String key(){return (cameraX?"camerax:":"camera2:")+cameraId+(physicalId==null?"":"/"+physicalId)+(recordOnly?":single":"");}
        String label(){return (cameraX?"CameraX ":"Camera2 ")+cameraId+(physicalId==null?"":" / "+physicalId)+(recordOnly?" · 互換":"");}
        JSONObject json() throws Exception{return new JSONObject().put("cameraId",cameraId).put("physicalId",physicalId==null?JSONObject.NULL:physicalId).put("cameraX",cameraX).put("recordOnly",recordOnly);}
    }
    record Scan(List<Route> routes,JSONObject report) {}
    static Route selected(Context context){
        String raw=context.getSharedPreferences("daylog",0).getString("cameraRoute","");
        if(raw.isEmpty())return null;
        try{JSONObject value=new JSONObject(raw);return new Route(value.getString("cameraId"),value.isNull("physicalId")?null:value.getString("physicalId"),value.optBoolean("cameraX",false),value.optBoolean("recordOnly",false));}
        catch(Exception e){android.util.Log.w("DaylogCamera","Invalid saved camera route",e);return null;}
    }
    static void select(Context context,Route route) throws Exception{
        context.getSharedPreferences("daylog",0).edit().putString("cameraRoute",route==null?"":route.json().toString()).apply();
    }
    static Scan scan(Context context) throws Exception{
        CameraManager manager=context.getSystemService(CameraManager.class);List<Route> routes=new ArrayList<>();JSONArray cameras=new JSONArray();
        for(String id:manager.getCameraIdList()){
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
            .put("physicalRequestKeys",String.valueOf(info.getAvailablePhysicalCameraRequestKeys()));
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
        return sensor!=null&&sensor.getWidth()>0&&values!=null&&values.length>0?values[0]/sensor.getWidth():0;
    }
}
