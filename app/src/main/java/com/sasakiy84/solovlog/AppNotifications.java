package com.sasakiy84.solovlog;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

final class AppNotifications {
    // Preserve existing channel preferences when the reminder interval changes.
    static final String PROCESSING="rendering",REMINDER="capture-hourly";
    private AppNotifications(){}
    static void channels(Context context){NotificationManager manager=context.getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel(PROCESSING,"動画のレンダリング",NotificationManager.IMPORTANCE_LOW));manager.createNotificationChannel(new NotificationChannel(REMINDER,"撮影リマインダー",NotificationManager.IMPORTANCE_DEFAULT));}
    static boolean allowed(Context context){return ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED&&context.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    @androidx.media3.common.util.UnstableApi
    static Notification movie(Context context,String id,String title,String message,boolean ongoing,int progress){
        Intent open=new Intent(context,MovieActivity.class).putExtra("draft_id",id);PendingIntent pending=PendingIntent.getActivity(context,id.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(context,PROCESSING).setSmallIcon(R.drawable.ic_videocam).setContentTitle(title).setContentText(message).setContentIntent(pending).setOngoing(ongoing).setAutoCancel(!ongoing).setOnlyAlertOnce(ongoing);
        if(ongoing)builder.setProgress(100,Math.max(0,progress),progress<0);return builder.build();
    }
    static int reminderHours(Context context){
        var prefs=context.getSharedPreferences("daylog",Context.MODE_PRIVATE);
        if(!prefs.contains("reminderHours"))prefs.edit().putInt("reminderHours",prefs.getBoolean("hourlyReminder",false)?1:0).remove("hourlyReminder").apply();
        int hours=prefs.getInt("reminderHours",0);
        return switch(hours){case 1,4,12,24->hours;default->0;};
    }
    static void setReminderHours(Context context,int hours){
        if(hours!=0&&hours!=1&&hours!=4&&hours!=12&&hours!=24)throw new IllegalArgumentException("Unsupported reminder interval");
        var prefs=context.getSharedPreferences("daylog",Context.MODE_PRIVATE);
        prefs.edit().putInt("reminderHours",hours).remove("hourlyReminder").putLong("nextCaptureReminder",nextReminderAt(System.currentTimeMillis(),0,hours)).apply();
        if(hours==0)context.getSystemService(NotificationManager.class).cancel(72);
        scheduleReminder(context);
    }
    static long nextReminderAt(long now,long previous,int hours){
        if(hours==0)return 0;
        return previous>now?previous:now+hours*60*60*1000L;
    }
    static void scheduleReminder(Context context){
        channels(context);var prefs=context.getSharedPreferences("daylog",Context.MODE_PRIVATE);AlarmManager alarm=context.getSystemService(AlarmManager.class);PendingIntent pending=PendingIntent.getBroadcast(context,71,new Intent(context,ReminderReceiver.class).setAction("com.sasakiy84.solovlog.CAPTURE_REMINDER"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);alarm.cancel(pending);
        int hours=reminderHours(context);if(hours==0||!allowed(context))return;
        long now=System.currentTimeMillis(),previous=prefs.getLong("nextCaptureReminder",0),next=nextReminderAt(now,previous,hours);if(next!=previous)prefs.edit().putLong("nextCaptureReminder",next).apply();
        alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,android.os.SystemClock.elapsedRealtime()+Math.max(1000,next-now),pending);
    }
}
