package com.sasakiy84.solovlog;

import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Adjust -> render privately -> watch -> explicitly save a copy to the phone. */
@UnstableApi
public final class MovieActivity extends ComponentActivity {
    private MovieDraft draft;
    private ClipRepository repository;
    private VideoExporter exporter;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<View> controls=new ArrayList<>();
    private VideoView video;
    private android.media.MediaPlayer player;
    private CaptionOverlay.Preview captions;
    private TextView state,render,save,open;
    private LinearLayout settings;
    private boolean showingResult,saving;
    private String playingSource="";
    private final Runnable poll=new Runnable(){@Override public void run(){refreshState();main.postDelayed(this,500);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);repository=ClipRepository.get(this);exporter=new VideoExporter(this);
        try{
            String id=saved!=null?saved.getString("draft_id"):getIntent().getStringExtra("draft_id");
            if(id!=null)draft=MovieDraft.load(this,id);
            else{
                List<String> ids=getIntent().getStringArrayListExtra("clips");if(ids==null||ids.isEmpty())throw new IllegalArgumentException("No clips");
                var prefs=getSharedPreferences("daylog",0);MovieDraft created=MovieDraft.create(getIntent().getStringExtra("day"),ids,getIntent().getStringExtra("caption"),prefs.getBoolean("showTime",true));
                created.sourceSignature=MovieDraft.signature(created.clips(repository),created.day,created.caption,created.showTime);
                String previous=prefs.getString("movieDraft:"+created.sourceSignature,null);draft=created;
                if(previous!=null)try{draft=MovieDraft.load(this,previous);}catch(Exception e){android.util.Log.w("DaylogMovie","Previous draft unavailable",e);}
                draft.save(this);prefs.edit().putString("movieDraft:"+created.sourceSignature,draft.id).apply();
            }
            if("rendering".equals(draft.status)&&!draft.id.equals(RenderService.activeId)){draft.status="error";draft.error="処理が中断されました。再実行できます。";draft.save(this);}
            if("saving".equals(draft.status)&&!VideoExporter.isSaving(draft.movie(this))){draft.status="ready";draft.save(this);}
            renderScreen();
        }catch(Exception e){android.util.Log.e("DaylogMovie","Could not open draft",e);Toast.makeText(this,"動画の下書きを開けませんでした",Toast.LENGTH_LONG).show();finish();}
    }
    @Override protected void onResume(){super.onResume();if(draft!=null){main.removeCallbacks(poll);main.post(poll);}}
    @Override protected void onStop(){main.removeCallbacks(poll);if(video!=null)video.pause();super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacks(poll);if(video!=null)video.stopPlayback();io.shutdown();if(exporter!=null)exporter.close();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){if(draft!=null)state.putString("draft_id",draft.id);super.onSaveInstanceState(state);}
    private int dp(int n){return Ui.dp(this,n);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout row(){LinearLayout view=new LinearLayout(this);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private LinearLayout.LayoutParams size(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private TextView button(String text,boolean primary,Runnable action){TextView view=Ui.text(this,text,16,primary?Ui.BG:Ui.INK);view.setGravity(Gravity.CENTER);view.setPadding(dp(12),dp(8),dp(12),dp(8));Ui.touch(view,primary?Ui.ACCENT:Ui.CARD,10);view.setOnClickListener(v->action.run());return view;}
    private void renderScreen(){
        LinearLayout root=column();root.setBackgroundColor(Ui.BG);root.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});setContentView(root);root.requestApplyInsets();
        LinearLayout toolbar=row();toolbar.addView(new Ui.Icon(this,"back",Ui.INK,"一覧に戻る",this::finish),size(56,56));toolbar.addView(Ui.text(this,"動画を調整",19,Ui.INK));root.addView(toolbar,size(-1,56));
        ScrollView scroll=new ScrollView(this);LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(20));content.setGravity(Gravity.CENTER_HORIZONTAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Clip first=draft.clips(repository).get(0);Ui.AspectFrame frame=new Ui.AspectFrame(this,first.landscape());video=new VideoView(this);frame.addView(video,new FrameLayout.LayoutParams(-1,-1));captions=new CaptionOverlay.Preview(this,first,draft.caption,draft.showTime);frame.addView(captions,new FrameLayout.LayoutParams(-1,-1));content.addView(frame,size(-1,getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?180:280));
        video.setOnPreparedListener(prepared->{player=prepared;prepared.setLooping(true);prepared.setVolume(draft.audio?1:0,draft.audio?1:0);video.start();});video.setOnErrorListener((p,w,e)->{toast("動画を再生できませんでした");return true;});captions.startProbing(video,repository.file(first.id()),io);
        state=Ui.text(this,"調整中",14,Ui.MUTED);state.setGravity(Gravity.CENTER);content.addView(state,size(-1,52));
        settings=column();content.addView(settings,size(-1,-2));EditText text=new EditText(this);text.setText(draft.caption);text.setHint("共通テキスト");text.setContentDescription("レンダリングの共通テキスト");text.setTextSize(16);text.setMaxLines(3);text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});settings.addView(text,size(-1,-2));controls.add(text);
        text.addTextChangedListener(new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}@Override public void onTextChanged(CharSequence s,int start,int before,int count){draft.caption=s.toString();changed();}@Override public void afterTextChanged(Editable s){}});
        toggle("撮影時刻を表示",draft.showTime,value->{draft.showTime=value;changed();});toggle("個別のテキストを表示",draft.showText,value->{draft.showText=value;changed();});toggle("場所を表示",draft.showPlace,value->{draft.showPlace=value;changed();});toggle("音声を含める",draft.audio,value->{draft.audio=value;changed();});
        LinearLayout actions=column();actions.setPadding(dp(20),dp(8),dp(20),dp(12));render=button("レンダリング",false,this::startRender);actions.addView(render,size(-1,48));save=button("保存",true,this::saveToPhone);LinearLayout.LayoutParams params=size(-1,48);params.topMargin=dp(8);actions.addView(save,params);open=button("開く",false,()->{try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(draft.savedUri),"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(RuntimeException e){toast("端末の動画アプリから開けます");}});actions.addView(open,size(-1,48));root.addView(actions,size(-1,-2));
        root.setFocusableInTouchMode(true);root.requestFocus();updateUi();
    }
    private void toggle(String label,boolean checked,java.util.function.Consumer<Boolean> changed){LinearLayout line=row();line.addView(Ui.text(this,label,16,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));Switch control=new Switch(this);control.setContentDescription(label);control.setChecked(checked);control.setOnCheckedChangeListener((v,value)->changed.accept(value));line.addView(control,size(56,48));settings.addView(line,size(-1,48));controls.add(control);}
    private void play(String path){if(!path.equals(playingSource)){playingSource=path;video.setVideoPath(path);}}
    private void changed(){if(player!=null)try{player.setVolume(draft.audio?1:0,draft.audio?1:0);}catch(IllegalStateException e){player=null;}draft.status="editing";draft.error="";draft.savedUri="";persist();updateUi();}
    private boolean persist(){try{draft.save(this);return true;}catch(Exception e){android.util.Log.e("DaylogMovie","Could not save draft",e);toast("調整を保存できませんでした");return false;}}
    private void startRender(){if(saving||"rendering".equals(draft.status))return;if(RenderService.activeId!=null){toast("別の動画を処理中です");return;}draft.status="rendering";draft.error="";if(!persist()){draft.status="editing";return;}try{RenderService.activeId=draft.id;ContextCompat.startForegroundService(this,new Intent(this,RenderService.class).putExtra("draft_id",draft.id));updateUi();}catch(RuntimeException e){RenderService.activeId=null;draft.status="error";draft.error="レンダリングを開始できませんでした";persist();updateUi();}}
    private void refreshState(){if(isFinishing())return;try{MovieDraft current=MovieDraft.load(this,draft.id);if(!current.status.equals(draft.status)||!current.savedUri.equals(draft.savedUri)){draft=current;updateUi();}else if("rendering".equals(current.status))updateUi();}catch(Exception e){android.util.Log.w("DaylogMovie","Could not read render state",e);}}
    private void updateUi(){
        if(video==null)return;boolean busy="rendering".equals(draft.status),ready=("ready".equals(draft.status)||"saving".equals(draft.status))&&draft.movie(this).isFile();saving="saving".equals(draft.status);
        for(View control:controls)control.setEnabled(!busy&&!saving);render.setEnabled(!busy&&!saving);render.setText(busy?"レンダリング中":"レンダリング");save.setVisibility(ready?View.VISIBLE:View.GONE);save.setEnabled(!saving&&draft.savedUri.isEmpty());save.setText(saving?"保存中":draft.savedUri.isEmpty()?"保存":"保存済み");open.setVisibility(draft.savedUri.isEmpty()?View.GONE:View.VISIBLE);
        state.setText(busy?"レンダリング中":ready?(draft.savedUri.isEmpty()?"レンダリング完了":"保存しました"):"error".equals(draft.status)?draft.error:"調整中");
        if(ready&&!showingResult){showingResult=true;captions.setVisibility(View.GONE);play(draft.movie(this).getAbsolutePath());MediaController controller=new MediaController(this);controller.setAnchorView(video);video.setMediaController(controller);}
        else if(!ready){Clip first=draft.clips(repository).get(0);captions.update(first,draft.caption,draft.showTime);captions.setVisibility(View.VISIBLE);if(showingResult||video.getDuration()<=0){showingResult=false;video.setMediaController(null);play(repository.file(first.id()).getAbsolutePath());}}
    }
    private void saveToPhone(){if(saving||!"ready".equals(draft.status)||!draft.savedUri.isEmpty())return;saving=true;draft.status="saving";if(!persist()){saving=false;draft.status="ready";return;}updateUi();String name="daylog_"+draft.day+"_"+draft.id.substring(0,8);exporter.saveRendered(draft.movie(this),name,new VideoExporter.Listener(){@Override public void saved(Uri uri){saving=false;draft.status="ready";draft.savedUri=uri.toString();persist();if(!isDestroyed())updateUi();}@Override public void error(String message){saving=false;draft.status="ready";persist();if(!isDestroyed()){updateUi();toast(message);}}});}
    private void toast(String message){if(!isDestroyed())Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
