package com.sasakiy84.solovlog;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.media3.common.util.UnstableApi;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Owns rendering in the background; individual clips can also request a gallery copy. */
@UnstableApi
public final class RenderService extends Service {
    private static final String TAG="DaylogRender";
    private static final int NOTIFICATION_ID=91;
    static String activeId;
    private MovieDraft draft;
    private boolean saveAfterRender;
    private VideoExporter exporter;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable progress=new Runnable(){
        @Override public void run(){
            if(draft==null||exporter==null)return;
            boolean saving="saving".equals(draft.status);
            int percent=saving?-1:exporter.progress();
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,AppNotifications.movie(
                RenderService.this,draft.id,saving?"動画を保存中":"動画をレンダリング中",
                percent>=0?percent+"%":"処理中",true,percent));
            main.postDelayed(this,1500);
        }
    };

    @Override public IBinder onBind(Intent intent){return null;}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if(draft!=null&&("rendering".equals(draft.status)||"saving".equals(draft.status)))return START_NOT_STICKY;
        if(exporter!=null)exporter.close();
        try{
            draft=MovieDraft.load(this,intent.getStringExtra("draft_id"));
            saveAfterRender=intent.getBooleanExtra("save_after_render",false);
            activeId=draft.id;
            AppNotifications.channels(this);
            startForeground(NOTIFICATION_ID,AppNotifications.movie(this,draft.id,"動画をレンダリング中","処理中",true,-1),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
            draft.status="rendering";draft.error="";draft.save(this);
            exporter=new VideoExporter(this);
            ClipRepository repository=ClipRepository.get(this);
            exporter.render(draft.clips(repository),repository,draft.caption,draft.showTime,draft.showDate,draft.audio,
                new VideoExporter.RenderListener(){
                    @Override public void rendered(File output){completeRender(output);}
                    @Override public void error(String message){fail(message);}
                });
            main.post(progress);
        }catch(Exception e){
            Log.e(TAG,"Could not start render",e);
            fail("レンダリングを開始できませんでした");
        }
        return START_NOT_STICKY;
    }

    private void completeRender(File output){
        try{
            File movie=draft.movie(this);
            Files.move(output.toPath(),movie.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
                media.setDataSource(movie.getAbsolutePath());
                int width=Integer.parseInt(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int height=Integer.parseInt(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                draft.outputResolution=Math.max(width,height)>=3840?"4K":Math.min(width,height)+"p";
            }catch(Exception e){
                draft.outputResolution="";
                Log.w(TAG,"Output resolution unavailable",e);
            }
            draft.status=saveAfterRender?"saving":"ready";
            draft.savedUri="";
            draft.save(this);
            if(!saveAfterRender){finishJob();return;}
            exporter.saveRendered(movie,draft.fileName,new VideoExporter.Listener(){
                @Override public void saved(Uri uri){
                    try{
                        draft.status="ready";draft.savedUri=uri.toString();draft.save(RenderService.this);
                        finishJob();
                    }catch(Exception e){fail("保存結果を記録できませんでした");}
                }
                @Override public void error(String message){fail(message);}
            });
        }catch(Exception e){
            output.delete();
            Log.e(TAG,"Could not keep render result",e);
            fail("レンダリング結果を保存できませんでした");
        }
    }

    private void fail(String message){
        if(draft!=null)try{
            draft.status="error";draft.error=message;draft.save(this);
        }catch(Exception e){Log.e(TAG,"Could not persist render error",e);}
        finishJob();
    }

    private void finishJob(){
        main.removeCallbacks(progress);activeId=null;
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }

    @Override public void onTimeout(int startId,int type){
        if(exporter!=null)exporter.cancel();
        fail("処理時間の上限に達しました。元動画は残っています。");
    }

    @Override public void onDestroy(){
        main.removeCallbacks(progress);
        if(exporter!=null)exporter.close();
        if(draft!=null&&("rendering".equals(draft.status)||"saving".equals(draft.status)))try{
            draft.status="error";draft.error="レンダリングが中断されました。再実行できます。";draft.save(this);
        }catch(Exception e){Log.e(TAG,"Could not persist interruption",e);}
        activeId=null;
        super.onDestroy();
    }
}
