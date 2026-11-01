package com.sasakiy84.solovlog;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.RangeSlider;
import java.util.List;
import java.util.Locale;

/** Keep the original file intact; previews and exports share the saved source range. */
@UnstableApi
public final class ClipTrimActivity extends ComponentActivity {
    private ClipRepository repository;
    private Clip edited;
    private TextureView video;
    private ExoPlayer player;
    private PreviewAudioButton previewAudio;
    private RangeSlider range;
    private ProgressBar loading;
    private TextView startValue,endValue,duration,previewError;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable restart=this::playRange;
    private final Runnable loop=new Runnable(){@Override public void run(){
        if(player==null)return;
        if((player.getPlayWhenReady()&&player.getPlaybackState()==Player.STATE_ENDED)||(player.isPlaying()&&player.getCurrentPosition()>=edited.trimEndMs())){player.seekTo(edited.trimStartMs());player.play();}
        main.postDelayed(this,33);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);repository=ClipRepository.get(this);
        String id=getIntent().getStringExtra("clip_id");
        if(id==null){finish();return;}
        edited=repository.forIds(java.util.List.of(id)).stream().filter(clip->clip.id().equals(id)).findFirst().orElse(null);
        if(edited==null){finish();return;}
        if(state!=null)edited=edited.withTrim(state.getLong("start"),state.getLong("end"));
        setRequestedOrientation(edited.landscape()?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        renderScreen();
    }
    @Override protected void onResume(){
        super.onResume();if(edited==null)return;
        if(RenderService.activeId!=null){Toast.makeText(this,"別の動画を処理中です",Toast.LENGTH_LONG).show();finish();return;}
        startPreview();
    }
    private void startPreview(){
        if(player!=null){playRange();return;}
        ExoPlayer created=PreviewPlayers.create(this,error->{loading.setVisibility(View.GONE);previewError.setVisibility(View.VISIBLE);android.util.Log.w("DaylogTrim","Preview playback failed",error);});player=created;player.setVideoTextureView(video);player.setVolume(previewAudio.isAudible()?1:0);player.setSeekParameters(SeekParameters.EXACT);player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.addListener(new Player.Listener(){
            @Override public void onRenderedFirstFrame(){if(player!=created)return;loading.setVisibility(View.GONE);previewError.setVisibility(View.GONE);}
        });
        loading.setVisibility(View.VISIBLE);previewError.setVisibility(View.GONE);playRange();
    }
    @Override protected void onPause(){main.removeCallbacks(restart);main.removeCallbacks(loop);if(player!=null)player.pause();super.onPause();}
    @Override protected void onStop(){main.removeCallbacks(restart);main.removeCallbacks(loop);ExoPlayer old=player;player=null;if(old!=null)old.release();super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacks(restart);main.removeCallbacks(loop);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){if(edited!=null){state.putLong("start",edited.trimStartMs());state.putLong("end",edited.trimEndMs());}super.onSaveInstanceState(state);}
    private int dp(int n){return Ui.dp(this,n);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout.LayoutParams size(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private TextView saveButton(){TextView view=Ui.text(this,"保存",16,Ui.ON_ACCENT);view.setGravity(Gravity.CENTER);Ui.touch(view,Ui.ACCENT,10);view.setOnClickListener(v->save());return view;}
    private void renderScreen(){
        LinearLayout root=column();root.setBackgroundColor(Ui.BG);root.setOnApplyWindowInsetsListener((view,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});setContentView(root);root.requestApplyInsets();
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.addView(new Ui.Icon(this,"back",Ui.INK,"戻る",this::finish),size(edited.landscape()?48:56,edited.landscape()?48:56));toolbar.addView(Ui.text(this,"トリミング",19,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));if(edited.landscape()){LinearLayout.LayoutParams saveParams=size(80,48);saveParams.rightMargin=dp(20);toolbar.addView(saveButton(),saveParams);}root.addView(toolbar,size(-1,edited.landscape()?48:56));
        FrameLayout preview=new FrameLayout(this);preview.setPadding(dp(12),dp(8),dp(12),dp(8));root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        Ui.AspectFrame frame=new Ui.AspectFrame(this,edited.landscape());preview.addView(frame,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));video=new TextureView(this);video.setKeepScreenOn(true);frame.addView(video,new FrameLayout.LayoutParams(-1,-1));
        previewAudio=new PreviewAudioButton(this,audible->{if(player!=null)player.setVolume(audible?1:0);});previewAudio.attachTo(frame);
        loading=new ProgressBar(this);loading.setIndeterminateTintList(ColorStateList.valueOf(Ui.INK));preview.addView(loading,new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER));
        previewError=Ui.text(this,"再読み込み",16,Ui.INK);previewError.setGravity(Gravity.CENTER);previewError.setContentDescription("動画の再生を再試行");Ui.touch(previewError,Ui.CARD,10);previewError.setVisibility(View.GONE);previewError.setOnClickListener(v->{previewError.setVisibility(View.GONE);loading.setVisibility(View.VISIBLE);if(player!=null){player.release();player=null;}startPreview();});preview.addView(previewError,new FrameLayout.LayoutParams(dp(160),dp(48),Gravity.CENTER));
        LinearLayout controls=column();controls.setPadding(dp(20),dp(edited.landscape()?4:12),dp(20),dp(edited.landscape()?4:12));root.addView(controls,size(-1,-2));
        LinearLayout labels=new LinearLayout(this);labels.setGravity(Gravity.CENTER_VERTICAL);startValue=Ui.text(this,"",14,Ui.MUTED);duration=Ui.text(this,"",16,Ui.INK);endValue=Ui.text(this,"",14,Ui.MUTED);startValue.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);duration.setGravity(Gravity.CENTER);endValue.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        startValue.setMinHeight(dp(edited.landscape()?24:32));duration.setMinHeight(dp(edited.landscape()?24:32));endValue.setMinHeight(dp(edited.landscape()?24:32));
        labels.addView(startValue,new LinearLayout.LayoutParams(0,-2,1));labels.addView(duration,new LinearLayout.LayoutParams(0,-2,1));labels.addView(endValue,new LinearLayout.LayoutParams(0,-2,1));
        controls.addView(labels,size(-1,-2));
        range=new RangeSlider(new ContextThemeWrapper(this,com.google.android.material.R.style.Theme_MaterialComponents_Light_NoActionBar));range.setValueFrom(0);range.setValueTo(edited.durationMs());range.setValues((float)edited.trimStartMs(),(float)edited.trimEndMs());range.setMinSeparationValue(Clip.MIN_TRIM_MS);
        range.setLabelBehavior(LabelFormatter.LABEL_GONE);range.setTrackActiveTintList(ColorStateList.valueOf(Ui.INK));range.setTrackInactiveTintList(ColorStateList.valueOf(Ui.LINE));range.setThumbTintList(ColorStateList.valueOf(Ui.INK));range.setHaloTintList(ColorStateList.valueOf(0x18202124));range.setTickVisible(false);range.setContentDescription("トリミング範囲");range.setLabelFormatter(value->seconds(Math.round(value)));controls.addView(range,size(-1,edited.landscape()?48:56));
        range.addOnChangeListener((slider,value,fromUser)->{if(!fromUser)return;List<Float> values=slider.getValues();long start=Math.round(values.get(0)/50.0)*50,end=Math.round(values.get(1)/50.0)*50;end=Math.min(edited.durationMs(),Math.max(Clip.MIN_TRIM_MS,end));start=Math.max(0,Math.min(start,end-Clip.MIN_TRIM_MS));edited=edited.withTrim(start,end);refreshLabels();main.removeCallbacks(restart);main.postDelayed(restart,100);});
        range.addOnSliderTouchListener(new RangeSlider.OnSliderTouchListener(){
            @Override public void onStartTrackingTouch(RangeSlider slider){if(player!=null)player.pause();}
            @Override public void onStopTrackingTouch(RangeSlider slider){main.removeCallbacks(restart);slider.setValues((float)edited.trimStartMs(),(float)edited.trimEndMs());playRange();}
        });
        if(!edited.landscape()){LinearLayout.LayoutParams saveParams=size(-1,56);saveParams.topMargin=dp(8);controls.addView(saveButton(),saveParams);}refreshLabels();
    }
    private String seconds(long ms){return String.format(Locale.ROOT,"%.2f 秒",ms/1000.0);}
    private void refreshLabels(){startValue.setText(seconds(edited.trimStartMs()));startValue.setContentDescription("開始 "+startValue.getText());endValue.setText(seconds(edited.trimEndMs()));endValue.setContentDescription("終了 "+endValue.getText());duration.setText(seconds(JournalRules.exportDuration(edited)));duration.setContentDescription("選択範囲 "+duration.getText());}
    private void playRange(){
        if(player==null)return;
        if(player.getMediaItemCount()==0){player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(repository.file(edited.id()))));player.prepare();}
        else if(player.getPlayerError()!=null)player.prepare();
        player.seekTo(edited.trimStartMs());player.play();main.removeCallbacks(loop);main.post(loop);
    }
    private void save(){try{repository.trim(edited);setResult(RESULT_OK,new Intent().putExtra("clip_id",edited.id()));finish();}catch(RuntimeException e){android.util.Log.e("DaylogTrim","Could not save trim",e);Toast.makeText(this,"トリミングを保存できませんでした",Toast.LENGTH_LONG).show();}}
}
