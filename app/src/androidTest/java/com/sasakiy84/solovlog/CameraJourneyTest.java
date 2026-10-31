package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.graphics.Bitmap;
import java.io.FileOutputStream;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import androidx.test.uiautomator.StaleObjectException;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.time.ZoneId;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Runs on a dedicated test device: real CameraX recordings, Media3 export, and screen captures. */
@androidx.media3.common.util.UnstableApi
@RunWith(AndroidJUnit4.class)
public final class CameraJourneyTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final ClipRepository repository=ClipRepository.get(context);

    private UiObject2 text(String value){UiObject2 object=device.wait(Until.findObject(By.text(value)),20000);if(object==null)DeviceImages.failure(context,device,"camera-failure");assertThat(object).isNotNull();return object;}
    private UiObject2 description(String value){UiObject2 object=device.wait(Until.findObject(By.desc(value)),20000);if(object==null)DeviceImages.failure(context,device,"camera-failure");assertThat(object).isNotNull();return object;}
    private void clickDescription(String value){
        long deadline=SystemClock.elapsedRealtime()+20000;
        while(true)try{description(value).click();return;}
        catch(StaleObjectException stale){if(SystemClock.elapsedRealtime()>=deadline)throw stale;device.waitForIdle();}
    }
    private void screenshot(String name) throws Exception {DeviceImages.screenshot(context,device,name);}
    private void publishImage(File file,String name) throws Exception {DeviceImages.publishFile(context,file,name);}
    private void awaitCaption(String id,String caption){
        long deadline=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<deadline){if(repository.all().stream().anyMatch(c->c.id().equals(id)&&c.caption().equals(caption)))return;SystemClock.sleep(100);}
        assertThat(repository.all().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow().caption()).isEqualTo(caption);
    }
    private Clip record(int duration) throws Exception {
        int count=repository.all().size();
        clickDescription(duration+"秒を選択");
        UiObject2 shutter=description(duration+"秒撮影");
        long readyDeadline=SystemClock.elapsedRealtime()+20000;
        while(!shutter.isEnabled()&&SystemClock.elapsedRealtime()<readyDeadline)SystemClock.sleep(100);
        assertThat(shutter.isEnabled()).isTrue();if(count==0)screenshot("00-camera-start");
        if(duration==5){
            var config=androidx.test.uiautomator.Configurator.getInstance();long idle=config.getWaitForIdleTimeout();config.setWaitForIdleTimeout(0);
            try{shutter.click();text("録画中");File screen=File.createTempFile("recording-",".png",context.getCacheDir());try{assertThat(device.takeScreenshot(screen)).isTrue();publishImage(screen,"01a-recording");}finally{screen.delete();}}finally{config.setWaitForIdleTimeout(idle);}
        }else shutter.click();
        long deadline=SystemClock.elapsedRealtime()+45000;
        while(repository.all().size()==count&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(200);
        List<Clip> after=repository.all();assertThat(after).hasSize(count+1);
        Clip clip=after.get(after.size()-1);
        assertThat(clip.plannedSeconds()).isEqualTo(duration);
        assertThat(clip.durationMs()).isAtLeast(duration*1000L);
        assertThat(clip.durationMs()).isAtMost(duration*1000L+2000);
        description(duration+"秒撮影");return clip;
    }

    @Test public void captureExportAndChangeSettings() throws Exception {
        device.executeShellCommand("pm grant com.sasakiy84.solovlog android.permission.CAMERA");
        device.executeShellCommand("pm grant com.sasakiy84.solovlog android.permission.RECORD_AUDIO");
        context.getSharedPreferences("daylog",Context.MODE_PRIVATE).edit().putBoolean("landscape",false).putBoolean("sound",false).putBoolean("showTime",true).putBoolean("postCaptureEdit",false).putBoolean("location",false).putInt("dayStart",240).commit();
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        DeviceImages.dismissFullscreenHint(device);
        Clip two=record(2),three=record(3),five=record(5);
        assertThat(two.landscape()).isFalse();assertThat(three.landscape()).isFalse();assertThat(five.landscape()).isFalse();
        SystemClock.sleep(5500);UiObject2 camera=description("カメラプレビュー");assertThat(camera.getVisibleBounds().width()).isAtLeast((int)(device.getDisplayWidth()*.95));assertThat(camera.getVisibleBounds().height()).isAtLeast((int)(device.getDisplayHeight()*.95));assertThat(device.hasObject(By.text("daylog"))).isFalse();screenshot("01-camera");
        clickDescription("日々の記録");text("レンダリング");
        clickDescription("1日のテキストを編集");description("1日のテキスト").setText("京都旅行 1日目");
        device.findObject(By.res("android","button1")).click();text("京都旅行 1日目");
        String day=JournalRules.day(two.capturedAt(),240,ZoneId.of(two.zoneId())).toString();
        assertThat(repository.dayCaption(day)).isEqualTo("京都旅行 1日目");
        UiObject2 first=device.wait(Until.findObject(By.desc("クリップを再生 "+JournalRules.captureTime(two)+" 2秒")),15000);assertThat(first).isNotNull();first.click();
        description("クリップのテキスト").setText("駅前で朝ごはん");screenshot("06-clip-text");
        device.findObject(By.res("android","button1")).click();
        awaitCaption(two.id(),"駅前で朝ごはん");
        screenshot("02-journal");
        UiObject2 save=device.wait(Until.findObject(By.text("レンダリング")),15000);assertThat(save).isNotNull();save.click();
        MovieJourney.renderAndSave(context,device,true,"day");
        try(Cursor cursor=context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Video.Media._ID},MediaStore.Video.Media.RELATIVE_PATH+"=?",new String[]{"Movies/Daylog/"},MediaStore.Video.Media._ID+" DESC")){
            assertThat(cursor).isNotNull();assertThat(cursor.moveToFirst()).isTrue();
            Uri uri=Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cursor.getString(0));
            try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
                media.setDataSource(context,uri);
                long duration=Long.parseLong(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                long expected=JournalRules.clipsFor(repository.all(),JournalRules.day(System.currentTimeMillis(),240,ZoneId.systemDefault()),false,240,true).stream().mapToLong(JournalRules::exportDuration).sum();
                assertThat(duration).isAtLeast(expected-500);assertThat(duration).isAtMost(expected+500);
                int width=Integer.parseInt(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int height=Integer.parseInt(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                String rotation=media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if("90".equals(rotation)||"270".equals(rotation)){int old=width;width=height;height=old;}
                assertThat(height).isGreaterThan(width);
                for(int n=0;n<3;n++){
                    Bitmap frame=media.getFrameAtTime(new long[]{1000000L,4000000L,8000000L}[n],MediaMetadataRetriever.OPTION_CLOSEST);
                    assertThat(frame).isNotNull();File image=File.createTempFile("daylog-caption-",".png",context.getCacheDir());
                    try(FileOutputStream output=new FileOutputStream(image)){assertThat(frame.compress(Bitmap.CompressFormat.PNG,100,output)).isTrue();}
                    publishImage(image,"07-export-frame-"+(n+1));frame.recycle();image.delete();
                }
            }
        }
        screenshot("03-saved");clickDescription("一覧に戻る");
        clickDescription("設定");text("1日の切り替え時刻");screenshot("04-settings");
        clickDescription("撮影時刻を表示");
        assertThat(context.getSharedPreferences("daylog",Context.MODE_PRIVATE).getBoolean("showTime",true)).isFalse();
        clickDescription("撮影時刻を表示");
        assertThat(context.getSharedPreferences("daylog",Context.MODE_PRIVATE).getBoolean("showTime",false)).isTrue();
        clickDescription("1日の切り替え時刻 04:00");
        UiObject2 toggle=device.wait(Until.findObject(By.res("android","toggle_mode")),5000);
        if(toggle!=null)toggle.click();
        UiObject2 hour=device.wait(Until.findObject(By.res("android","input_hour")),5000);
        UiObject2 minute=device.findObject(By.res("android","input_minute"));
        assertThat(hour).isNotNull();assertThat(minute).isNotNull();hour.setText("5");minute.setText("30");
        device.findObject(By.res("android","button1")).click();
        description("1日の切り替え時刻 05:30");
        assertThat(context.getSharedPreferences("daylog",Context.MODE_PRIVATE).getInt("dayStart",0)).isEqualTo(330);
        clickDescription("動画の向き");text("横").click();SystemClock.sleep(1500);
        clickDescription("前の画面に戻る");text("撮影する").click();
        Clip sameDay=record(2);assertThat(sameDay.landscape()).isFalse();
        // Advance the fixture to a fresh shooting day while retaining every original file.
        for(Clip clip:repository.all()){
            ContentValues shifted=new ContentValues();shifted.put("captured_at",clip.capturedAt()-2*24*60*60*1000L);
            repository.getWritableDatabase().update("clips",shifted,"id=?",new String[]{clip.id()});
        }
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        DeviceImages.dismissFullscreenHint(device);
        long rotationDeadline=SystemClock.elapsedRealtime()+20000;
        while(device.getDisplayRotation()!=1&&SystemClock.elapsedRealtime()<rotationDeadline)SystemClock.sleep(100);
        assertThat(device.getDisplayRotation()).isEqualTo(1);
        // The old portrait window can remain accessible until its rotation transition finishes.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();device.waitForIdle();SystemClock.sleep(1500);
        Clip horizontal=record(2);assertThat(horizontal.landscape()).isTrue();
        SystemClock.sleep(5500);screenshot("05-landscape");
        assertThat(repository.all().stream().filter(c->c.id().equals(two.id())).findFirst().orElseThrow().caption()).isEqualTo("駅前で朝ごはん");
        assertThat(repository.dayCaption(day)).isEqualTo("京都旅行 1日目");
        assertThat(repository.file(two.id()).exists()).isTrue();
        assertThat(repository.file(three.id()).exists()).isTrue();
        assertThat(repository.file(five.id()).exists()).isTrue();
    }
}
