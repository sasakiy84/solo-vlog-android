package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.time.ZoneId;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Runs on a dedicated test device: real CameraX recordings, Media3 export, and screen captures. */
@RunWith(AndroidJUnit4.class)
public final class CameraJourneyTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final ClipRepository repository=ClipRepository.get(context);

    private UiObject2 text(String value){UiObject2 object=device.wait(Until.findObject(By.text(value)),20000);assertThat(object).isNotNull();return object;}
    private UiObject2 description(String value){UiObject2 object=device.wait(Until.findObject(By.desc(value)),20000);assertThat(object).isNotNull();return object;}
    private void screenshot(String name) throws Exception {
        File folder=new File(context.getExternalFilesDir(null),"screenshots");folder.mkdirs();
        File screenshot=new File(folder,name+".png");
        assertThat(device.takeScreenshot(screenshot)).isTrue();
        // AGP can uninstall the app after the test; shared MediaStore output survives that cleanup.
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name+".png");
        values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/DaylogTestScreenshots");values.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri image=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertThat(image).isNotNull();
        try(FileInputStream source=new FileInputStream(screenshot);OutputStream target=context.getContentResolver().openOutputStream(image)){assertThat(target).isNotNull();source.transferTo(target);}
        values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);context.getContentResolver().update(image,values,null,null);
    }
    private Clip record(int duration) throws Exception {
        int count=repository.all().size();
        description(duration+"秒を選択").click();
        text("●  READY");
        description(duration+"秒撮影").click();
        long deadline=SystemClock.elapsedRealtime()+45000;
        while(repository.all().size()==count&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(200);
        List<Clip> after=repository.all();assertThat(after).hasSize(count+1);
        Clip clip=after.get(after.size()-1);
        assertThat(clip.plannedSeconds()).isEqualTo(duration);
        assertThat(clip.durationMs()).isAtLeast(300L);
        assertThat(clip.durationMs()).isAtMost(duration*1000L+2000);
        text("●  READY");return clip;
    }

    @Test public void captureExportAndChangeSettings() throws Exception {
        device.executeShellCommand("pm grant com.sasakiy84.solovlog android.permission.CAMERA");
        device.executeShellCommand("pm grant com.sasakiy84.solovlog android.permission.RECORD_AUDIO");
        context.getSharedPreferences("daylog",Context.MODE_PRIVATE).edit().putBoolean("landscape",false).putBoolean("sound",false).putInt("dayStart",240).commit();
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        Clip two=record(2),three=record(3),five=record(5);
        assertThat(two.landscape()).isFalse();assertThat(three.landscape()).isFalse();assertThat(five.landscape()).isFalse();
        SystemClock.sleep(5500);screenshot("01-camera");
        description("日々の記録").click();text("今日を、ひとつに。");screenshot("02-journal");
        UiObject2 save=device.wait(Until.findObject(By.textStartsWith("この日の動画を保存")),15000);assertThat(save).isNotNull();save.click();
        assertThat(device.wait(Until.hasObject(By.text("今日が、一本になりました。")),180000)).isTrue();
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
            }
        }
        screenshot("03-saved");device.findObject(By.res("android","button2")).click();
        description("設定").click();text("自分の一日に、\n合わせる。");screenshot("04-settings");
        description("一日のはじまり 04:00").click();
        UiObject2 toggle=device.wait(Until.findObject(By.res("android","toggle_mode")),5000);
        if(toggle!=null)toggle.click();
        UiObject2 hour=device.wait(Until.findObject(By.res("android","input_hour")),5000);
        UiObject2 minute=device.findObject(By.res("android","input_minute"));
        assertThat(hour).isNotNull();assertThat(minute).isNotNull();hour.setText("5");minute.setText("30");
        device.findObject(By.res("android","button1")).click();
        description("一日のはじまり 05:30");
        assertThat(context.getSharedPreferences("daylog",Context.MODE_PRIVATE).getInt("dayStart",0)).isEqualTo(330);
        description("横向きで撮影").click();SystemClock.sleep(1500);
        description("前の画面に戻る").click();device.wait(Until.findObject(By.text("＋   もうひとつ、撮る")),10000).click();
        Clip horizontal=record(2);assertThat(horizontal.landscape()).isTrue();
        SystemClock.sleep(5500);screenshot("05-landscape");
        assertThat(repository.file(two.id()).exists()).isTrue();
        assertThat(repository.file(three.id()).exists()).isTrue();
        assertThat(repository.file(five.id()).exists()).isTrue();
    }
}
