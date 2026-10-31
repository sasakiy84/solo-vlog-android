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
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.view.TextureView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
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
    private TextureView video;
    private ExoPlayer player;
    private PreviewAudioButton previewAudio;
    private int previewIndex;
    private long previewPosition;
    private String previewClipId="",observedRenderId;
    private CaptionOverlay.Preview captions;
    private TextView state,render,save;
    private LinearLayout settings,fileNameSection;
    private EditText fileNameInput;
    private boolean showingResult,saving,keyboardVisible,foreground;
    private final Runnable poll=new Runnable(){@Override public void run(){refreshState();main.postDelayed(this,500);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);repository=ClipRepository.get(this);exporter=new VideoExporter(this);
        try{
            String id=saved!=null?saved.getString("draft_id"):getIntent().getStringExtra("draft_id");
            if(id!=null)draft=MovieDraft.load(this,id);
            else{
                List<String> ids=getIntent().getStringArrayListExtra("clips");if(ids==null||ids.isEmpty())throw new IllegalArgumentException("No clips");
                var prefs=getSharedPreferences("daylog",0);MovieDraft created=MovieDraft.create(this,getIntent().getStringExtra("day"),ids,getIntent().getStringExtra("caption"),prefs.getBoolean("showTime",true),prefs.getBoolean("showDate",false));
                created.sourceSignature=MovieDraft.signature(created.clips(repository),created.day,created.caption,created.showTime,created.showDate)+":"+created.compact+":"+created.embedLocation;
                String cachedId=prefs.getString("movieDraft:"+created.sourceSignature,null);draft=created;
                if(cachedId!=null)try{draft=MovieDraft.load(this,cachedId);}catch(Exception e){android.util.Log.w("DaylogMovie","Cached draft unavailable",e);}
                draft.save(this);prefs.edit().putString("movieDraft:"+created.sourceSignature,draft.id).apply();
            }
            if("rendering".equals(draft.status)&&!draft.id.equals(RenderService.activeId)){draft.status="error";draft.error="処理が中断されました。再実行できます。";draft.save(this);}
            if("saving".equals(draft.status)&&!VideoExporter.isSaving(draft.movie(this))){draft.status="ready";draft.save(this);}
            if(saved!=null){previewIndex=saved.getInt("preview_index",0);previewPosition=saved.getLong("preview_position",0);}
            renderScreen();
        }catch(Exception e){android.util.Log.e("DaylogMovie","Could not open draft",e);Toast.makeText(this,"動画の下書きを開けませんでした",Toast.LENGTH_LONG).show();finish();}
    }
    @Override protected void onResume(){super.onResume();foreground=true;if(draft!=null){refreshSavedCopy();updateUi();main.removeCallbacks(poll);main.post(poll);}}
    @Override protected void onStop(){foreground=false;main.removeCallbacks(poll);releasePlayer();super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacks(poll);releasePlayer();io.shutdown();if(exporter!=null)exporter.close();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){if(draft!=null)state.putString("draft_id",draft.id);if(player!=null&&!showingResult){previewIndex=player.getCurrentMediaItemIndex();previewPosition=player.getCurrentPosition();}state.putInt("preview_index",previewIndex);state.putLong("preview_position",previewPosition);super.onSaveInstanceState(state);}
    private int dp(int n){return Ui.dp(this,n);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout row(){LinearLayout view=new LinearLayout(this);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private LinearLayout.LayoutParams size(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private TextView button(String text,boolean primary,Runnable action){TextView view=Ui.text(this,text,16,primary?Ui.BG:Ui.INK);view.setGravity(Gravity.CENTER);view.setPadding(dp(12),dp(8),dp(12),dp(8));Ui.touch(view,primary?Ui.ACCENT:Ui.CARD,10);view.setOnClickListener(v->action.run());return view;}
    private void renderScreen(){
        LinearLayout root=column();root.setBackgroundColor(Ui.BG);root.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);boolean visible=insets.isVisible(WindowInsets.Type.ime());if(visible!=keyboardVisible){keyboardVisible=visible;if(render!=null)updateUi();}return insets;});setContentView(root);root.requestApplyInsets();
        LinearLayout toolbar=row();toolbar.addView(new Ui.Icon(this,"back",Ui.INK,"一覧に戻る",this::finish),size(56,56));toolbar.addView(Ui.text(this,"レンダリング",19,Ui.INK));root.addView(toolbar,size(-1,56));
        ScrollView scroll=new ScrollView(this);LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(20));content.setGravity(Gravity.CENTER_HORIZONTAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Clip first=draft.clips(repository).get(0);Ui.AspectFrame frame=new Ui.AspectFrame(this,first.landscape());video=new TextureView(this);frame.addView(video,new FrameLayout.LayoutParams(-1,-1));captions=new CaptionOverlay.Preview(this,first,draft.caption,draft.showTime,draft.showDate);frame.addView(captions,new FrameLayout.LayoutParams(-1,-1));previewAudio=new PreviewAudioButton(this,audible->{if(player!=null)player.setVolume(audible&&previewHasAudio()?1:0);});previewAudio.attachTo(frame);content.addView(frame,size(-1,getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?180:280));
        state=Ui.text(this,"",14,Ui.MUTED);state.setGravity(Gravity.CENTER);content.addView(state,size(-1,52));
        fileNameSection=column();LinearLayout.LayoutParams nameParams=size(-1,-2);nameParams.topMargin=dp(12);nameParams.bottomMargin=dp(12);content.addView(fileNameSection,nameParams);
        fileNameSection.addView(Ui.text(this,"保存名",14,Ui.MUTED),size(-1,-2));
        LinearLayout nameRow=row();fileNameInput=new EditText(this);fileNameInput.setText(draft.fileName);fileNameInput.setContentDescription("保存名");fileNameInput.setSingleLine(true);fileNameInput.setTextSize(16);fileNameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);fileNameInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);fileNameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});nameRow.addView(fileNameInput,new LinearLayout.LayoutParams(0,dp(64),1));TextView extension=Ui.text(this,".mp4",16,Ui.MUTED);extension.setPadding(dp(8),0,dp(8),0);nameRow.addView(extension,size(-2,64));extension.setGravity(Gravity.CENTER_VERTICAL);fileNameSection.addView(nameRow,size(-1,64));controls.add(fileNameInput);
        fileNameInput.addTextChangedListener(new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}@Override public void onTextChanged(CharSequence s,int start,int before,int count){draft.fileName=s.toString();draft.savedUri="";persist();updateUi();}@Override public void afterTextChanged(Editable s){}});
        settings=column();content.addView(settings,size(-1,-2));EditText text=new EditText(this);text.setText(draft.caption);text.setHint("1日のテキスト");text.setContentDescription("1日のテキスト");text.setTextSize(16);text.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);text.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);text.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){getWindow().getInsetsController().hide(WindowInsets.Type.ime());text.clearFocus();return true;}return false;});text.setMinLines(1);text.setMaxLines(4);text.setMinHeight(dp(56));text.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);text.setPadding(dp(4),dp(12),dp(4),dp(6));text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});LinearLayout.LayoutParams textParams=size(-1,-2);textParams.topMargin=dp(8);textParams.bottomMargin=dp(16);settings.addView(text,textParams);controls.add(text);
        text.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                draft.caption=s.toString();
                try{repository.dayCaption(draft.day,draft.caption);}
                catch(RuntimeException e){android.util.Log.w("DaylogMovie","Day caption could not be saved",e);toast("テキストを保存できませんでした");}
                changed();
            }
            @Override public void afterTextChanged(Editable s){}
        });
        toggle("年月日を表示",draft.showDate,value->{draft.showDate=value;changed();});toggle("撮影時刻を表示",draft.showTime,value->{draft.showTime=value;changed();});toggle("クリップのテキストを表示",draft.showText,value->{draft.showText=value;changed();});toggle("場所を表示",draft.showPlace,value->{draft.showPlace=value;changed();});toggle("音声を含める",draft.audio,value->{draft.audio=value;changed();});
        toggle("容量優先 · 1080p",draft.compact,value->{draft.compact=value;changed();});
        toggle("位置情報を埋め込む",draft.embedLocation,value->{draft.embedLocation=value;changed();});
        LinearLayout actions=column();actions.setPadding(dp(20),dp(8),dp(20),dp(12));render=button("レンダリング",true,this::startRender);actions.addView(render,size(-1,56));save=button("保存",true,()->{if(draft.savedUri.isEmpty())saveToPhone();else openSavedMovie();});LinearLayout.LayoutParams params=size(-1,56);params.topMargin=dp(12);actions.addView(save,params);root.addView(actions,size(-1,-2));
        root.setFocusableInTouchMode(true);root.requestFocus();updateUi();
    }
    private void toggle(String label,boolean checked,java.util.function.Consumer<Boolean> changed){LinearLayout line=row();line.addView(Ui.text(this,label,16,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));Switch control=new Switch(this);control.setContentDescription(label);control.setChecked(checked);control.setOnCheckedChangeListener((v,value)->changed.accept(value));line.addView(control,size(56,48));settings.addView(line,size(-1,56));controls.add(control);}
    private void ensurePlayer(){
        if(player!=null)return;
        ExoPlayer created=new ExoPlayer.Builder(this).build();player=created;created.setVideoTextureView(video);created.setRepeatMode(Player.REPEAT_MODE_ALL);created.setVolume(previewAudio.isAudible()&&previewHasAudio()?1:0);
        created.addListener(new Player.Listener(){
            @Override public void onMediaItemTransition(MediaItem item,int reason){if(player==created&&!showingResult)updatePreviewCaption();}
            @Override public void onPlayerError(PlaybackException error){if(player!=created)return;android.util.Log.w("DaylogMovie","Preview playback failed",error);toast("動画を再生できませんでした");}
        });
    }
    private void releasePlayer(){
        if(player==null)return;
        if(!showingResult){previewIndex=player.getCurrentMediaItemIndex();previewPosition=player.getCurrentPosition();}
        ExoPlayer old=player;player=null;old.release();previewClipId="";
    }
    private void updatePreviewCaption(){
        if(player==null||showingResult)return;List<Clip> clips=draft.clips(repository);int index=Math.max(0,Math.min(player.getCurrentMediaItemIndex(),clips.size()-1));Clip clip=clips.get(index);
        player.setVolume(previewAudio.isAudible()&&draft.audio&&clip.renderAudio()?1:0);
        captions.update(clip,draft.caption,draft.showTime,draft.showDate);
        if(!clip.id().equals(previewClipId)){previewClipId=clip.id();captions.loadColors(repository.file(clip.id()),io);}
    }
    private boolean previewHasAudio(){
        if(!draft.audio)return false;if(showingResult||player==null)return true;
        List<Clip> clips=draft.clips(repository);int index=player.getCurrentMediaItemIndex();
        return index>=0&&index<clips.size()&&clips.get(index).renderAudio();
    }
    private void changed(){if(player!=null)player.setVolume(previewAudio.isAudible()&&previewHasAudio()?1:0);draft.status="editing";draft.error="";draft.savedUri="";persist();updateUi();}
    private boolean persist(){try{draft.save(this);return true;}catch(Exception e){android.util.Log.e("DaylogMovie","Could not save draft",e);toast("調整を保存できませんでした");return false;}}
    private void startRender(){if(saving||"rendering".equals(draft.status))return;if(RenderService.activeId!=null){toast("別の動画を処理中です");return;}draft.status="rendering";draft.error="";if(!persist()){draft.status="editing";return;}try{RenderService.activeId=draft.id;ContextCompat.startForegroundService(this,new Intent(this,RenderService.class).putExtra("draft_id",draft.id));updateUi();}catch(RuntimeException e){RenderService.activeId=null;draft.status="error";draft.error="レンダリングを開始できませんでした";persist();updateUi();}}
    private void refreshSavedCopy(){
        if(draft.savedUri.isEmpty())return;
        try(android.database.Cursor cursor=getContentResolver().query(Uri.parse(draft.savedUri),new String[]{android.provider.MediaStore.Video.Media._ID},null,null,null)){
            if(cursor==null||!cursor.moveToFirst()){draft.savedUri="";persist();updateUi();}
        }catch(RuntimeException e){android.util.Log.w("DaylogMovie","Saved copy unavailable",e);draft.savedUri="";persist();updateUi();}
    }
    private void refreshState(){if(isFinishing())return;try{MovieDraft current=MovieDraft.load(this,draft.id);if(!current.status.equals(draft.status)||!current.savedUri.equals(draft.savedUri)||!java.util.Objects.equals(observedRenderId,RenderService.activeId)){draft=current;updateUi();}else if("rendering".equals(current.status))updateUi();}catch(Exception e){android.util.Log.w("DaylogMovie","Could not read render state",e);}}
    private void updateUi(){
        if(video==null)return;observedRenderId=RenderService.activeId;boolean busy="rendering".equals(draft.status)||observedRenderId!=null,ready=("ready".equals(draft.status)||"saving".equals(draft.status))&&draft.movie(this).isFile();saving="saving".equals(draft.status);
        for(View control:controls)control.setEnabled(!busy&&!saving);render.setVisibility(keyboardVisible&&ready?View.GONE:View.VISIBLE);render.setEnabled(!busy&&!saving);render.setAlpha(busy||saving?.45f:1f);render.setText(busy?"レンダリング中":"レンダリング");save.setVisibility(ready?View.VISIBLE:View.GONE);save.setEnabled(!saving);save.setText(saving?"保存中":draft.savedUri.isEmpty()?"保存":"開く");
        fileNameSection.setVisibility(ready?View.VISIBLE:View.GONE);previewAudio.setVisibility(!busy&&draft.audio?View.VISIBLE:View.GONE);
        state.setText(busy?"レンダリング中":ready?(draft.savedUri.isEmpty()?"レンダリング完了"+(draft.outputResolution.isEmpty()?"":" · "+draft.outputResolution):""):"error".equals(draft.status)?draft.error:"");
        state.setVisibility(state.getText().length()==0?View.GONE:View.VISIBLE);
        if(!foreground){releasePlayer();return;}
        if(busy){releasePlayer();video.setVisibility(View.INVISIBLE);captions.setVisibility(View.GONE);return;}
        video.setVisibility(View.VISIBLE);boolean missing=player==null;ensurePlayer();
        if(ready){
            captions.setVisibility(View.GONE);
            if(!showingResult||missing){showingResult=true;player.setMediaItem(MediaItem.fromUri(Uri.fromFile(draft.movie(this))));player.prepare();player.play();}
        }else{
            captions.setVisibility(View.VISIBLE);
            if(showingResult||missing){
                showingResult=false;List<MediaItem> playlist=new ArrayList<>();
                for(Clip clip:draft.clips(repository))playlist.add(new MediaItem.Builder().setUri(Uri.fromFile(repository.file(clip.id())))
                    .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.trimStartMs()).setEndPositionMs(clip.trimEndMs()).build()).build());
                int index=Math.max(0,Math.min(previewIndex,playlist.size()-1));long position=Math.min(previewPosition,Math.max(0,JournalRules.exportDuration(draft.clips(repository).get(index))-1));
                player.setMediaItems(playlist,index,position);player.prepare();player.play();
            }
            updatePreviewCaption();
        }
    }
    private void openSavedMovie(){try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(draft.savedUri),"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(RuntimeException e){toast("端末の動画アプリから開けます");}}
    private void saveToPhone(){if(saving||!"ready".equals(draft.status)||!draft.savedUri.isEmpty())return;String name;
        try{name=VideoExporter.fileName(draft.fileName);}catch(IllegalArgumentException e){fileNameInput.setError("保存名を確認してください");fileNameInput.requestFocus();return;}
        if(!name.equals(draft.fileName)){draft.fileName=name;fileNameInput.setText(name);}
        saving=true;draft.status="saving";if(!persist()){saving=false;draft.status="ready";return;}updateUi();exporter.saveRendered(draft.movie(this),name,new VideoExporter.Listener(){@Override public void saved(Uri uri){saving=false;draft.status="ready";draft.savedUri=uri.toString();persist();if(!isDestroyed())updateUi();}@Override public void error(String message){saving=false;draft.status="ready";persist();if(!isDestroyed()){updateUi();toast(message);}}});}
    private void toast(String message){if(!isDestroyed())Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
