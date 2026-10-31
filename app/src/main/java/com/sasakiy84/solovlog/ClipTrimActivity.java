package com.sasakiy84.solovlog;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keep the original file intact; previews and exports share the saved source range. */
@UnstableApi
public final class ClipTrimActivity extends ComponentActivity {
    private ClipRepository repository;
    private Clip edited;
    private TextureView video;
    private CaptionOverlay.Preview captions;
    private ExoPlayer player;
    private SeekBar start,end;
    private TextView startValue,endValue,duration;
    private boolean dragging,updating;
    private String dayCaption;
    private boolean showTime,showDate;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Runnable loop=new Runnable(){@Override public void run(){
        if(player==null)return;
        if(!dragging&&player.getCurrentPosition()>=edited.trimEndMs())player.seekTo(edited.trimStartMs());
        main.postDelayed(this,20);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);repository=ClipRepository.get(this);
        String id=getIntent().getStringExtra("clip_id");
        edited=repository.all().stream().filter(clip->clip.id().equals(id)).findFirst().orElse(null);
        if(edited==null){finish();return;}
        if(state!=null)edited=edited.withTrim(state.getLong("start"),state.getLong("end"));
        setRequestedOrientation(edited.landscape()?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        var prefs=getSharedPreferences("daylog",0);showTime=prefs.getBoolean("showTime",true);showDate=prefs.getBoolean("showDate",false);
        dayCaption=repository.dayCaption(JournalRules.day(edited.capturedAt(),prefs.getInt("dayStart",240),java.time.ZoneId.of(edited.zoneId())).toString());
        renderScreen();
    }
    @Override protected void onResume(){
        super.onResume();if(edited==null)return;
        if(RenderService.activeId!=null){Toast.makeText(this,"別の動画を処理中です",Toast.LENGTH_LONG).show();finish();return;}
        player=new ExoPlayer.Builder(this).build();player.setVideoTextureView(video);player.setSeekParameters(SeekParameters.EXACT);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){if(player!=null&&state==Player.STATE_ENDED&&!dragging){player.seekTo(edited.trimStartMs());player.play();}}
            @Override public void onPlayerError(PlaybackException error){android.util.Log.w("DaylogTrim","Preview playback failed",error);Toast.makeText(ClipTrimActivity.this,"動画を再生できませんでした",Toast.LENGTH_LONG).show();}
        });
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(repository.file(edited.id()))));player.seekTo(edited.trimStartMs());player.prepare();player.play();main.post(loop);
    }
    @Override protected void onStop(){main.removeCallbacks(loop);dragging=false;if(player!=null){player.release();player=null;}super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacks(loop);io.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){if(edited!=null){state.putLong("start",edited.trimStartMs());state.putLong("end",edited.trimEndMs());}super.onSaveInstanceState(state);}
    private int dp(int n){return Ui.dp(this,n);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout.LayoutParams size(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private TextView button(String label,boolean primary,Runnable action){TextView view=Ui.text(this,label,16,primary?Ui.BG:Ui.INK);view.setGravity(Gravity.CENTER);Ui.touch(view,primary?Ui.ACCENT:Ui.CARD,10);view.setOnClickListener(v->action.run());return view;}
    private void renderScreen(){
        LinearLayout root=column();root.setBackgroundColor(Ui.BG);root.setOnApplyWindowInsetsListener((view,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});setContentView(root);root.requestApplyInsets();
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.addView(new Ui.Icon(this,"back",Ui.INK,"戻る",this::finish),size(56,56));toolbar.addView(Ui.text(this,"トリミング",19,Ui.INK));root.addView(toolbar,size(-1,56));
        ScrollView scroll=new ScrollView(this);LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(16));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Ui.AspectFrame frame=new Ui.AspectFrame(this,edited.landscape());video=new TextureView(this);frame.addView(video,new FrameLayout.LayoutParams(-1,-1));captions=new CaptionOverlay.Preview(this,edited,dayCaption,showTime,showDate);frame.addView(captions,new FrameLayout.LayoutParams(-1,-1));content.addView(frame,size(-1,edited.landscape()?180:280));captions.loadColors(repository.file(edited.id()),io);
        duration=Ui.text(this,"",16,Ui.INK);duration.setGravity(Gravity.CENTER);content.addView(duration,size(-1,48));
        startValue=Ui.text(this,"",16,Ui.INK);endValue=Ui.text(this,"",16,Ui.INK);
        start=rangeControl(content,"開始",startValue,true);end=rangeControl(content,"終了",endValue,false);
        LinearLayout actions=column();actions.setPadding(dp(20),dp(8),dp(20),dp(12));actions.addView(button("リセット",false,()->{edited=edited.withTrim(0,Math.min(edited.durationMs(),edited.plannedSeconds()*1000L));refresh();if(player!=null){player.seekTo(0);player.play();}captions.loadColors(repository.file(edited.id()),io);}),size(-1,48));
        LinearLayout.LayoutParams saveParams=size(-1,56);saveParams.topMargin=dp(12);actions.addView(button("保存",true,this::save),saveParams);root.addView(actions,size(-1,-2));refresh();
    }
    private SeekBar rangeControl(LinearLayout content,String label,TextView value,boolean isStart){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(Ui.text(this,label,16,Ui.INK),new LinearLayout.LayoutParams(0,dp(32),1));value.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);row.addView(value,size(80,32));content.addView(row,size(-1,32));
        SeekBar slider=new SeekBar(this);slider.setMax((int)edited.durationMs());slider.setContentDescription(label);slider.setProgressTintList(ColorStateList.valueOf(Ui.INK));slider.setThumbTintList(ColorStateList.valueOf(Ui.INK));content.addView(slider,size(-1,48));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                if(!fromUser||updating)return;
                long position=Math.round(progress/50.0)*50;
                edited=isStart?edited.withTrim(Math.max(0,Math.min(position,edited.trimEndMs()-Clip.MIN_TRIM_MS)),edited.trimEndMs()):edited.withTrim(edited.trimStartMs(),Math.min(edited.durationMs(),Math.max(position,edited.trimStartMs()+Clip.MIN_TRIM_MS)));
                refresh();if(player!=null){player.pause();player.seekTo(isStart?edited.trimStartMs():Math.max(edited.trimStartMs(),edited.trimEndMs()-34));if(!dragging){player.seekTo(edited.trimStartMs());player.play();}}
            }
            @Override public void onStartTrackingTouch(SeekBar bar){dragging=true;if(player!=null)player.pause();}
            @Override public void onStopTrackingTouch(SeekBar bar){dragging=false;if(player!=null){player.seekTo(edited.trimStartMs());player.play();}captions.loadColors(repository.file(edited.id()),io);}
        });return slider;
    }
    private String seconds(long ms){return String.format(Locale.ROOT,"%.2f 秒",ms/1000.0);}
    private void refresh(){updating=true;start.setProgress((int)edited.trimStartMs());end.setProgress((int)edited.trimEndMs());updating=false;startValue.setText(seconds(edited.trimStartMs()));endValue.setText(seconds(edited.trimEndMs()));duration.setText(seconds(JournalRules.exportDuration(edited)));captions.update(edited,dayCaption,showTime,showDate);}
    private void save(){try{repository.trim(edited);setResult(RESULT_OK,new Intent().putExtra("clip_id",edited.id()));finish();}catch(RuntimeException e){android.util.Log.e("DaylogTrim","Could not save trim",e);Toast.makeText(this,"トリミングを保存できませんでした",Toast.LENGTH_LONG).show();}}
}
