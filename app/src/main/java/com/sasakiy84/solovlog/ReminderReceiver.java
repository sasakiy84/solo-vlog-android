package com.sasakiy84.solovlog;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ReminderReceiver extends BroadcastReceiver {
    @androidx.media3.common.util.UnstableApi
    @Override public void onReceive(Context context,Intent intent){
        if("com.sasakiy84.solovlog.CAPTURE_REMINDER".equals(intent.getAction())&&AppNotifications.allowed(context)&&context.getSharedPreferences("daylog",Context.MODE_PRIVATE).getBoolean("hourlyReminder",false)){
            PendingIntent open=PendingIntent.getActivity(context,72,new Intent(context,MainActivity.class).putExtra("open_camera",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            context.getSystemService(NotificationManager.class).notify(72,new Notification.Builder(context,AppNotifications.REMINDER).setSmallIcon(R.drawable.ic_video_library).setContentTitle("Vlog の撮影").setContentText("動画を撮影しませんか").setContentIntent(open).setAutoCancel(true).build());
        }
        if("com.sasakiy84.solovlog.CAPTURE_REMINDER".equals(intent.getAction()))context.getSharedPreferences("daylog",Context.MODE_PRIVATE).edit().putLong("nextCaptureReminder",System.currentTimeMillis()+60*60*1000L).apply();
        AppNotifications.scheduleReminder(context);
    }
}
