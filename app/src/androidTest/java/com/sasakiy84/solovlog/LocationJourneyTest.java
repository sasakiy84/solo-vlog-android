package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Dedicated emulator only: uses public station coordinates, never the developer's location. */
@UnstableApi
@RunWith(AndroidJUnit4.class)
public final class LocationJourneyTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final ClipRepository repository=ClipRepository.get(context);
    private UiObject2 find(BySelector selector){
        UiObject2 object=device.wait(Until.findObject(selector),20000);
        if(object==null)try{
            DeviceImages.screenshot(context,device,"location-failure");
            java.io.ByteArrayOutputStream hierarchy=new java.io.ByteArrayOutputStream();device.dumpWindowHierarchy(hierarchy);
            String xml=hierarchy.toString(StandardCharsets.UTF_8);
            for(int offset=0;offset<xml.length();offset+=3000)android.util.Log.e("DaylogLocationTest",xml.substring(offset,Math.min(offset+3000,xml.length())));
        }catch(Exception diagnostic){android.util.Log.e("DaylogLocationTest","Could not capture failed UI",diagnostic);}
        assertThat(object).isNotNull();return object;
    }
    private void click(BySelector selector){long deadline=SystemClock.elapsedRealtime()+20000;while(true)try{find(selector).click();return;}catch(StaleObjectException stale){if(SystemClock.elapsedRealtime()>=deadline)throw stale;device.waitForIdle();}}
    private void await(BooleanSupplier condition){long deadline=SystemClock.elapsedRealtime()+30000;while(!condition.getAsBoolean()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);assertThat(condition.getAsBoolean()).isTrue();}
    private Clip clip(String id){return repository.all().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow();}
    private Clip record(int seconds){
        int before=repository.all().size();click(By.desc(seconds+"秒を選択"));
        UiObject2 shutter=find(By.desc(seconds+"秒撮影"));await(shutter::isEnabled);click(By.desc(seconds+"秒撮影"));
        await(()->repository.all().size()==before+1);find(By.desc("クリップのテキスト"));
        return repository.all().get(repository.all().size()-1);
    }
    private void mockFix(LocationManager manager,double latitude,double longitude){
        Location point=new Location(LocationManager.GPS_PROVIDER);point.setLatitude(latitude);point.setLongitude(longitude);point.setAccuracy(5);point.setTime(System.currentTimeMillis());point.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,point);
    }
    private Uri latestVideo(){
        try(Cursor c=context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Video.Media._ID},MediaStore.Video.Media.RELATIVE_PATH+"=?",new String[]{"Movies/Daylog/"},MediaStore.Video.Media._ID+" DESC")){
            assertThat(c).isNotNull();assertThat(c.moveToFirst()).isTrue();return Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,c.getString(0));
        }
    }
    private int saves;
    private void saved()throws Exception{MovieJourney.renderAndSave(context,device,false,++saves==1?"single":"trip");}
    private void place(String value){click(By.desc("場所を入力"));find(By.desc("場所")).setText(value);click(By.res("android","button1"));}
    private void selectPlace(String name){
        BySelector selector=By.desc("場所を表示 "+name);
        for(int attempt=0;attempt<8;attempt++){
            UiObject2 candidate=device.wait(Until.findObject(selector),500);if(candidate!=null){candidate.click();return;}
            find(By.clazz(android.widget.HorizontalScrollView.class)).scroll(androidx.test.uiautomator.Direction.RIGHT,.8f);
        }
        click(selector);
    }
    private static byte[] samples(File file) throws Exception{
        MediaExtractor extractor=new MediaExtractor();MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try{
            extractor.setDataSource(file.getAbsolutePath());int video=-1;
            for(int n=0;n<extractor.getTrackCount();n++)if(extractor.getTrackFormat(n).getString(MediaFormat.KEY_MIME).startsWith("video/")){video=n;break;}
            assertThat(video).isAtLeast(0);extractor.selectTrack(video);ByteBuffer buffer=ByteBuffer.allocate(4*1024*1024);
            while(true){buffer.clear();int size=extractor.readSampleData(buffer,0);if(size<0)break;buffer.position(0);buffer.limit(size);digest.update(buffer);extractor.advance();}
            return digest.digest();
        }finally{extractor.release();}
    }
    @SuppressLint("MissingPermission") // Mock-location AppOps is enabled on this dedicated emulator; runtime grants are exercised through UI.
    @SuppressWarnings("deprecation")
    @Test public void postCaptureInputPermissionOneTapPlacesAndGeotaggedExports() throws Exception{
        LocationManager manager=context.getSystemService(LocationManager.class);
        device.executeShellCommand("cmd location set-location-enabled true");
        device.executeShellCommand("appops set com.sasakiy84.solovlog android:mock_location allow");
        manager.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE);
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true);
        try{
            device.executeShellCommand("pm grant com.sasakiy84.solovlog android.permission.CAMERA");device.executeShellCommand("pm grant com.sasakiy84.solovlog android.permission.RECORD_AUDIO");
            // Advance earlier test fixtures, preserving every original file and caption.
            for(Clip c:repository.all()){ContentValues value=new ContentValues();value.put("captured_at",c.capturedAt()-2*24*60*60*1000L);repository.getWritableDatabase().update("clips",value,"id=?",new String[]{c.id()});}
            context.getSharedPreferences("daylog",Context.MODE_PRIVATE).edit().putBoolean("landscape",false).putBoolean("postCaptureEdit",true).putBoolean("location",false).putBoolean("sound",false).putBoolean("showTime",true).putInt("dayStart",240).commit();
            String day=JournalRules.day(System.currentTimeMillis(),240,ZoneId.systemDefault()).toString();repository.dayCaption(day,"東京・京都旅行");
            Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));DeviceImages.dismissFullscreenHint(device);
            await(()->device.getDisplayRotation()==0);device.waitForIdle();
            Clip first=record(2);find(By.desc("撮影に戻る"));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->assertThat(androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).stream().anyMatch(activity->activity instanceof CaptureEditorActivity)).isTrue());
            find(By.pkg(Pattern.compile(".*inputmethod.*")));
            DeviceImages.screenshot(context,device,"09-post-capture-keyboard");
            find(By.desc("クリップのテキスト")).setText("東京に到着");await(()->clip(first.id()).caption().equals("東京に到着"));
            String originalRotation;try(MediaMetadataRetriever metadata=new MediaMetadataRetriever()){metadata.setDataSource(repository.file(first.id()).getAbsolutePath());originalRotation=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);}
            byte[] original=samples(repository.file(first.id()));assertThat(clip(first.id()).location()).isNull();
            click(By.text("現在地を追加"));
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
                find(By.text(Pattern.compile("(?i)(don.t allow|許可しない)")));
                DeviceImages.screenshot(context,device,"09-location-permission");
                click(By.text(Pattern.compile("(?i)(don.t allow|許可しない)")));
                find(By.desc("クリップのテキスト"));assertThat(repository.file(first.id()).exists()).isTrue();assertThat(clip(first.id()).caption()).isEqualTo("東京に到着");
                click(By.text("現在地を追加"));click(By.text("続ける"));
                UiObject2 precise=find(By.text(Pattern.compile("(?i)(precise|precise location|use precise location|正確|正確な位置情報)")));
                if(!precise.isChecked())precise.click();
                click(By.res(Pattern.compile(".*:id/permission_allow_foreground_only_button")));
            }
            await(()->ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED);
            mockFix(manager,35.681236,139.767125);
            await(()->clip(first.id()).location()!=null);
            assertThat(Arrays.equals(original,samples(repository.file(first.id())))).isTrue();
            try(MediaMetadataRetriever metadata=new MediaMetadataRetriever()){
                metadata.setDataSource(repository.file(first.id()).getAbsolutePath());assertThat(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)).isNotNull();
                assertThat(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)).isEqualTo(originalRotation);
            }
            selectPlace(clip(first.id()).location().label());await(()->!clip(first.id()).place().isEmpty());
            place("東京駅");await(()->clip(first.id()).place().equals("東京駅"));DeviceImages.screenshot(context,device,"10-post-capture-editor");
            click(By.text("保存"));find(By.text("日々の記録"));DeviceImages.screenshot(context,device,"10a-edits-saved-journal");click(By.descStartsWith("クリップを再生 "));click(By.text("動画を調整"));saved();
            Uri individual=latestVideo();
            try(MediaMetadataRetriever metadata=new MediaMetadataRetriever()){
                metadata.setDataSource(context,individual);assertThat(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)).isNotNull();
            }
            click(By.desc("一覧に戻る"));click(By.desc("前の画面に戻る"));
            mockFix(manager,35.681246,139.767135);
            Clip second=record(3);await(()->clip(second.id()).location()!=null);assertThat(device.hasObject(By.text("現在地を追加"))).isFalse();click(By.desc("場所を表示 東京駅"));
            find(By.desc("クリップのテキスト")).setText("コーヒー休憩");await(()->clip(second.id()).place().equals("東京駅")&&clip(second.id()).caption().equals("コーヒー休憩"));
            DeviceImages.screenshot(context,device,"11-one-tap-place");click(By.text("保存"));find(By.text("日々の記録"));click(By.text("撮影する"));
            mockFix(manager,34.985849,135.758767);
            Clip third=record(2);await(()->clip(third.id()).location()!=null);assertThat(clip(third.id()).location().distanceMeters(clip(first.id()).location())).isGreaterThan(300000);
            assertThat(device.hasObject(By.desc("場所を表示 東京駅"))).isFalse();place("京都駅");find(By.desc("クリップのテキスト")).setText("京都に到着");await(()->clip(third.id()).caption().equals("京都に到着"));
            click(By.text("保存"));find(By.text("日々の記録"));DeviceImages.screenshot(context,device,"11a-trip-journal");click(By.text("動画を調整"));saved();Uri daily=latestVideo();
            List<Clip> trip=List.of(clip(first.id()),clip(second.id()),clip(third.id()));
            try(InputStream input=context.getContentResolver().openInputStream(daily)){
                assertThat(input).isNotNull();String container=new String(input.readAllBytes(),StandardCharsets.UTF_8);
                assertThat(container).contains(LocationMetadata.KEY);assertThat(container).contains(LocationMetadata.timeline(trip));
            }
            try(MediaMetadataRetriever metadata=new MediaMetadataRetriever()){
                metadata.setDataSource(context,daily);assertThat(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)).isNull();
                long duration=Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));assertThat(duration).isAtLeast(6500);assertThat(duration).isAtMost(7500);
                for(int n=0;n<2;n++){Bitmap frame=metadata.getFrameAtTime(n==0?1000000:5800000,MediaMetadataRetriever.OPTION_CLOSEST);assertThat(frame).isNotNull();DeviceImages.bitmap(context,frame,"12-location-export-"+(n+1));frame.recycle();}
            }
            DeviceImages.screenshot(context,device,"13-travel-saved");
            click(By.desc("一覧に戻る"));click(By.desc("前の画面に戻る"));
            int countBefore=repository.all().size();Clip removed=record(2);click(By.text("削除"));click(By.res("android","button1"));
            await(()->repository.all().size()==countBefore);assertThat(repository.file(removed.id()).exists()).isFalse();
            find(By.desc("カメラプレビュー"));
        }finally{
            manager.removeTestProvider(LocationManager.GPS_PROVIDER);device.executeShellCommand("appops set com.sasakiy84.solovlog android:mock_location default");
        }
    }
}
