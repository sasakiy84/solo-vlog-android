package com.sasakiy84.solovlog;

import android.app.Service;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.media3.common.util.UnstableApi;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Owns the render while its preview Activity is backgrounded; never publishes to MediaStore. */
@UnstableApi
public final class RenderService extends Service {
    static String activeId;
    private MovieDraft draft;
    private VideoExporter exporter;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable progress=new Runnable(){@Override public void run(){if(draft==null||exporter==null)return;int percent=exporter.progress();getSystemService(NotificationManager.class).notify(91,AppNotifications.movie(RenderService.this,draft.id,"動画をレンダリング中",percent>=0?percent+"%":"処理中",true,percent));main.postDelayed(this,1500);}};
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}String id=intent.getStringExtra("draft_id");
        if(draft!=null&&"rendering".equals(draft.status))return START_NOT_STICKY;
        if(exporter!=null)exporter.close();
        try{
            draft=MovieDraft.load(this,id);activeId=id;AppNotifications.channels(this);
            startForeground(91,AppNotifications.movie(this,id,"動画をレンダリング中","処理中",true,-1),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
            draft.status="rendering";draft.error="";draft.save(this);exporter=new VideoExporter(this);
            exporter.render(draft.clips(ClipRepository.get(this)),ClipRepository.get(this),draft.caption,draft.showTime,draft.audio,new VideoExporter.RenderListener(){
                @Override public void rendered(File output){try{Files.move(output.toPath(),draft.movie(RenderService.this).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);draft.status="ready";draft.savedUri="";draft.save(RenderService.this);finishJob();}catch(Exception e){output.delete();fail("レンダリング結果を保存できませんでした");}}
                @Override public void error(String message){fail(message);}
            });main.post(progress);
        }catch(Exception e){android.util.Log.e("DaylogRender","Could not start render",e);fail("レンダリングを開始できませんでした");}
        return START_NOT_STICKY;
    }
    private void fail(String error){if(draft!=null)try{draft.status="error";draft.error=error;draft.save(this);}catch(Exception e){android.util.Log.e("DaylogRender","Could not persist render error",e);}finishJob();}
    private void finishJob(){main.removeCallbacks(progress);activeId=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onTimeout(int startId,int type){if(exporter!=null)exporter.cancel();fail("処理時間の上限に達しました。元動画は残っています。");}
    @Override public void onDestroy(){main.removeCallbacks(progress);if(exporter!=null)exporter.close();if(draft!=null&&"rendering".equals(draft.status))try{draft.status="error";draft.error="レンダリングが中断されました。再実行できます。";draft.save(this);}catch(Exception e){android.util.Log.e("DaylogRender","Could not persist interruption",e);}activeId=null;super.onDestroy();}
}
