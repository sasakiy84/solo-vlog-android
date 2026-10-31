package com.sasakiy84.solovlog;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

final class NotificationAccess {
    private final ComponentActivity activity;
    private final ActivityResultLauncher<String> launcher;
    private Runnable granted;
    NotificationAccess(ComponentActivity activity,Runnable changed){this.activity=activity;launcher=activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(),allowed->{Runnable action=granted;granted=null;if(allowed&&action!=null)action.run();changed.run();});}
    void request(Runnable action){
        if(AppNotifications.allowed(activity)){action.run();return;}
        var prefs=activity.getSharedPreferences("daylog",0);boolean rationale=activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);
        if(prefs.getBoolean("askedNotifications",false)&&!rationale){new AlertDialog.Builder(activity).setTitle("通知の設定").setMessage("端末の設定から通知を許可できます。")
            .setPositiveButton("設定を開く",(d,w)->activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.getPackageName()))).setNegativeButton("キャンセル",null).show();return;}
        Runnable launch=()->{granted=action;prefs.edit().putBoolean("askedNotifications",true).apply();launcher.launch(Manifest.permission.POST_NOTIFICATIONS);};
        if(rationale)new AlertDialog.Builder(activity).setTitle("通知を許可").setMessage("1時間ごとに、動画の撮影を促す通知を出します。")
            .setPositiveButton("続ける",(d,w)->launch.run()).setNegativeButton("キャンセル",null).show();else launch.run();
    }
}
