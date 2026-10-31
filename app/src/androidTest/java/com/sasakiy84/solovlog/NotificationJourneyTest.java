package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;

@UnstableApi
@RunWith(AndroidJUnit4.class)
public final class NotificationJourneyTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private UiObject2 find(androidx.test.uiautomator.BySelector selector){UiObject2 object=device.wait(Until.findObject(selector),20000);if(object==null)DeviceImages.failure(context,device,"notification-failure");assertThat(object).isNotNull();return object;}
    private void toggle(){
        var selector=By.desc("撮影リマインダー（1時間ごと）");
        for(int attempt=0;attempt<8;attempt++)try{
            UiObject2 control=device.wait(Until.findObject(selector),500);if(control!=null){control.click();return;}
            UiObject2 scroll=device.findObject(By.clazz(android.widget.ScrollView.class));if(scroll==null)break;scroll.scroll(androidx.test.uiautomator.Direction.DOWN,.8f);
        }catch(androidx.test.uiautomator.StaleObjectException refreshed){device.waitForIdle();}
        find(selector).click();
    }
    @Test public void hourlyCaptureReminderPermissionToggleAndNotificationOpensCamera()throws Exception{
        var prefs=context.getSharedPreferences("daylog",0);prefs.edit().putBoolean("hourlyReminder",false).apply();
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));DeviceImages.dismissFullscreenHint(device);
        find(By.desc("設定")).click();toggle();
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            find(By.text(Pattern.compile("(?i)(don.t allow|許可しない)"))).click();
            find(By.text("設定"));assertThat(prefs.getBoolean("hourlyReminder",false)).isFalse();toggle();
            find(By.text("続ける")).click();
            find(By.text(Pattern.compile("(?i)(allow|許可)"))).click();
        }
        long deadline=SystemClock.elapsedRealtime()+15000;while(!prefs.getBoolean("hourlyReminder",false)&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
        assertThat(prefs.getBoolean("hourlyReminder",false)).isTrue();assertThat(AppNotifications.allowed(context)).isTrue();
        long now=System.currentTimeMillis(),next=prefs.getLong("nextCaptureReminder",0);assertThat(next-now).isAtLeast(55*60*1000L);assertThat(next-now).isAtMost(60*60*1000L);
        AppNotifications.scheduleReminder(context);assertThat(prefs.getLong("nextCaptureReminder",0)).isEqualTo(next); // Opening the app must not postpone the reminder.
        new ReminderReceiver().onReceive(context,new Intent("com.sasakiy84.solovlog.CAPTURE_REMINDER"));
        deadline=SystemClock.elapsedRealtime()+10000;while(Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications()).noneMatch(n->n.getId()==72)&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
        assertThat(Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications()).anyMatch(n->n.getId()==72&&"Vlog の撮影".equals(n.getNotification().extras.getString("android.title")))).isTrue();
        device.openNotification();find(By.text("Vlog の撮影"));DeviceImages.screenshot(context,device,"20-hourly-reminder");find(By.text("Vlog の撮影")).click();find(By.desc("カメラプレビュー"));
        find(By.desc("設定")).click();toggle();deadline=SystemClock.elapsedRealtime()+10000;while(prefs.getBoolean("hourlyReminder",true)&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);assertThat(prefs.getBoolean("hourlyReminder",true)).isFalse();
    }
}
