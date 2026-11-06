package com.sasakiy84.solovlog;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Run only on a dedicated test device: exercises two real Camera2 recordings. */
@RunWith(AndroidJUnit4.class)
public final class CameraRecordingTest {
    @Test(timeout=180000) public void camera2CanRecordAgainWithoutReopeningThePreview() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();
        var prefs=context.getSharedPreferences("daylog",0);
        String route=prefs.getString("cameraRoute","");
        boolean edit=prefs.getBoolean("postCaptureEdit",true);
        String quality=prefs.getString("captureQuality","auto");
        ClipRepository repository=ClipRepository.get(context);
        Set<String> originalIds=new HashSet<>();for(Clip clip:repository.all())originalIds.add(clip.id());
        MainActivity activity=null;
        try{
            instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(),Manifest.permission.CAMERA);
            String id=context.getSystemService(android.hardware.camera2.CameraManager.class).getCameraIdList()[0];
            CameraProfiles.select(context,new CameraProfiles.Route(id,null));
            prefs.edit().putBoolean("postCaptureEdit",false).putString("captureQuality","hd").commit();
            activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity screen=activity;
            for(int n=1;n<=2;n++){
                long deadline=android.os.SystemClock.elapsedRealtime()+45000;
                View[] shutter=new View[1];
                while(shutter[0]==null&&android.os.SystemClock.elapsedRealtime()<deadline){
                    instrumentation.runOnMainSync(()->shutter[0]=findShutter(screen.getWindow().getDecorView()));
                    if(shutter[0]==null)Thread.sleep(50);
                }
                assertNotNull("Camera did not become ready for recording "+n,shutter[0]);
                if(n==1)saveScreenshot(instrumentation,context,"camera-ready.png");
                instrumentation.runOnMainSync(()->shutter[0].performClick());
                if(n==1){
                    saveScreenshot(instrumentation,context,"camera-preparing.png");
                }
                final int expected=n;
                while(repository.all().stream().filter(c->!originalIds.contains(c.id())).count()<expected&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
                assertTrue("Recording was not saved",repository.all().stream().filter(c->!originalIds.contains(c.id())).count()>=expected);
            }
            for(Clip clip:repository.all())if(!originalIds.contains(clip.id())){
                try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
                    media.setDataSource(repository.file(clip.id()).getAbsolutePath());
                    assertTrue("Saved clip is too short",Long.parseLong(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))>=1800);
                    Bitmap frame=media.getFrameAtTime(500000);assertNotNull("Saved clip cannot be decoded",frame);frame.recycle();
                }
            }
        }finally{
            if(activity!=null){MainActivity screen=activity;instrumentation.runOnMainSync(screen::finish);}
            prefs.edit().putString("cameraRoute",route).putBoolean("postCaptureEdit",edit).putString("captureQuality",quality).commit();
            for(Clip clip:repository.all())if(!originalIds.contains(clip.id()))repository.delete(clip.id());
        }
    }
    private static void saveScreenshot(Instrumentation instrumentation,Context context,String name) throws Exception {
        Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(screenshot);
        try(var stream=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){screenshot.compress(Bitmap.CompressFormat.PNG,100,stream);}
        finally{screenshot.recycle();}
    }
    private static View findShutter(View view){
        if(view instanceof Ui.Shutter&&view.isEnabled()&&view.getContentDescription()!=null
            &&view.getContentDescription().toString().endsWith("秒撮影"))return view;
        if(view instanceof ViewGroup group)for(int n=0;n<group.getChildCount();n++){View found=findShutter(group.getChildAt(n));if(found!=null)return found;}
        return null;
    }
}
