package com.sasakiy84.solovlog;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.media3.common.util.UnstableApi;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The immediate post-capture screen owns editing and foreground location independently of the camera. */
@UnstableApi
public final class CaptureEditorActivity extends ComponentActivity {
    static final String CLIP_ID="clip_id";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private ClipRepository repository;
    private SharedPreferences prefs;
    private Clip clip;
    private String dayCaption;
    private LocationTracker locations;
    private PlaceResolver places;
    private VideoView video;
    private CaptionOverlay.Preview captions;
    private LinearLayout choices;
    private HorizontalScrollView placeScroll;
    private boolean deleted,locating,writingLocation,waitingForSettings,visible,placeGesture,pendingPlaceRefresh;
    private final Runnable save=this::saveEdits;
    private final Runnable timeout=()->{locating=false;locations.stop();refreshPlaces();if(clip.location()==null)toast("現在地を取得できませんでした");};
    private final ActivityResultLauncher<String[]> permission=registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),result->{
        boolean allowed=locations.granted();prefs.edit().putBoolean("location",allowed).apply();if(allowed)beginLocation();
    });

    @Override public void onCreate(Bundle state){
        super.onCreate(state);repository=ClipRepository.get(this);prefs=getSharedPreferences("daylog",MODE_PRIVATE);
        String id=getIntent().getStringExtra(CLIP_ID);
        clip=repository.all().stream().filter(c->c.id().equals(id)).findFirst().orElse(null);
        if(clip==null){finish();return;}
        setRequestedOrientation(clip.landscape()?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        dayCaption=repository.dayCaption(JournalRules.day(clip.capturedAt(),prefs.getInt("dayStart",240),ZoneId.of(clip.zoneId())).toString());
        places=new PlaceResolver(this);locations=new LocationTracker(this,this::locationChanged);
        render();resolvePlaces();
    }
    @Override protected void onResume(){
        super.onResume();visible=true;placeGesture=false;
        if(clip==null)return;
        if(waitingForSettings){waitingForSettings=false;if(locations.granted()&&locations.enabled()){prefs.edit().putBoolean("location",true).apply();beginLocation();}}
        else if(needsLocation()&&prefs.getBoolean("location",true)&&locations.granted()&&!locating&&!writingLocation)beginLocation();
        if(pendingPlaceRefresh)refreshPlaces();if(video!=null)video.start();
    }
    @Override protected void onStop(){
        placeGesture=false;
        if(clip!=null){main.removeCallbacks(save);saveEdits();locations.stop();main.removeCallbacks(timeout);if(!writingLocation)locating=false;video.pause();}
        visible=false;super.onStop();
    }
    @Override protected void onDestroy(){main.removeCallbacks(save);main.removeCallbacks(timeout);if(video!=null)video.stopPlayback();if(locations!=null)locations.stop();io.shutdown();super.onDestroy();}
    private int dp(int n){return Ui.dp(this,n);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout row(){LinearLayout view=new LinearLayout(this);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private LinearLayout.LayoutParams size(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private TextView button(String label,boolean selected,Runnable action){
        TextView view=Ui.text(this,label,16,selected?Ui.BG:Ui.INK);view.setGravity(Gravity.CENTER);view.setPadding(dp(16),dp(8),dp(16),dp(8));Ui.touch(view,selected?Ui.ACCENT:Ui.CARD,10);view.setOnClickListener(v->action.run());return view;
    }
    private void render(){
        LinearLayout root=column();root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        setContentView(root);root.requestApplyInsets();
        LinearLayout toolbar=row();toolbar.setPadding(dp(12),0,dp(12),0);
        toolbar.addView(new Ui.Icon(this,"back",Ui.INK,"撮影に戻る",this::finish),size(48,48));
        String time=Instant.ofEpochMilli(clip.capturedAt()).atZone(ZoneId.of(clip.zoneId())).format(DateTimeFormatter.ofPattern("HH:mm"));
        toolbar.addView(Ui.text(this,time+"  /  "+clip.plannedSeconds()+"秒",18,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));root.addView(toolbar,size(-1,56));
        ScrollView scroll=new ScrollView(this);LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(12));content.setGravity(Gravity.CENTER_HORIZONTAL);scroll.setFillViewport(false);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Ui.AspectFrame frame=new Ui.AspectFrame(this,clip.landscape());video=new VideoView(this);frame.addView(video,new FrameLayout.LayoutParams(-1,-1));
        captions=new CaptionOverlay.Preview(this,clip,dayCaption,prefs.getBoolean("showTime",true));frame.addView(captions,new FrameLayout.LayoutParams(-1,-1));
        content.addView(frame,size(-1,getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?130:180));
        video.setVideoPath(repository.file(clip.id()).getAbsolutePath());video.setOnPreparedListener(player->{player.setLooping(true);video.start();});video.setOnErrorListener((p,w,e)->{toast("動画を再生できませんでした");return true;});captions.startProbing(video,repository.file(clip.id()),io);
        LinearLayout locationLine=row();placeScroll=new HorizontalScrollView(this){
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)placeGesture=true;
                boolean handled=super.dispatchTouchEvent(event);
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP||event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL){placeGesture=false;if(pendingPlaceRefresh)post(CaptureEditorActivity.this::refreshPlaces);}
                return handled;
            }
        };placeScroll.setHorizontalScrollBarEnabled(false);choices=row();placeScroll.addView(choices);locationLine.addView(placeScroll,new LinearLayout.LayoutParams(0,dp(64),1));locationLine.addView(new Ui.Icon(this,"edit",Ui.INK,"場所を入力",this::editPlace),size(48,48));content.addView(locationLine,size(-1,64));
        EditText input=textInput(clip.caption(),"クリップのテキスト",120);input.setHint("クリップのテキスト");content.addView(input,size(-1,56));
        input.addTextChangedListener(watcher(()->{clip=clip.withCaption(input.getText().toString());changed();}));
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){saveAndReview();return true;}return false;});
        LinearLayout footer=row();footer.setPadding(dp(20),dp(8),dp(20),dp(8));TextView delete=button("削除",false,this::confirmDelete);footer.addView(delete,size(72,48));View space=new View(this);footer.addView(space,new LinearLayout.LayoutParams(0,1,1));footer.addView(button("保存",true,this::saveAndReview),size(120,48));root.addView(footer,size(-1,64));
        refreshPlaces();input.requestFocus();
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        input.getViewTreeObserver().addOnWindowFocusChangeListener(new android.view.ViewTreeObserver.OnWindowFocusChangeListener(){
            @Override public void onWindowFocusChanged(boolean focused){if(focused){input.getViewTreeObserver().removeOnWindowFocusChangeListener(this);getWindow().getInsetsController().show(WindowInsets.Type.ime());}}
        });
    }
    private EditText textInput(String value,String description,int limit){EditText input=new EditText(this);input.setText(value);input.setTextSize(16);input.setContentDescription(description);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setSingleLine(true);input.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});return input;}
    private TextWatcher watcher(Runnable changed){return new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}@Override public void onTextChanged(CharSequence s,int start,int before,int count){changed.run();}@Override public void afterTextChanged(Editable s){}};}
    private void changed(){captions.update(clip,dayCaption,prefs.getBoolean("showTime",true));main.removeCallbacks(save);main.postDelayed(save,250);}
    private boolean saveEdits(){if(deleted||clip==null)return false;try{repository.caption(clip.id(),clip.caption());repository.place(clip.id(),clip.place());return true;}catch(RuntimeException e){android.util.Log.w("DaylogEditor","Could not save edits",e);toast("編集内容を保存できませんでした");return false;}}
    private void saveAndReview(){main.removeCallbacks(save);if(!saveEdits())return;String day=JournalRules.day(clip.capturedAt(),prefs.getInt("dayStart",240),ZoneId.of(clip.zoneId())).toString();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("review_day",day).putExtra("edits_saved",true));finish();}
    private void refreshPlaces(){
        if(isFinishing()||isDestroyed())return;if(placeGesture){pendingPlaceRefresh=true;return;}pendingPlaceRefresh=false;choices.removeAllViews();LinkedHashSet<String> names=new LinkedHashSet<>();if(!clip.place().isBlank())names.add(clip.place());names.addAll(repository.nearbyPlaces(clip.location()));names.addAll(repository.placeCandidates(clip.id()));if(clip.location()!=null)names.add(clip.location().label());
        for(String name:names){TextView chip=button(name,clip.place().equals(name),()->{markPlaceChoice();clip=clip.withPlace(clip.place().equals(name)?"":name);repository.rememberPlace(clip.location(),clip.place());changed();saveEdits();refreshPlaces();placeScroll.scrollTo(0,0);});chip.setSingleLine(true);chip.setMaxWidth(dp(220));chip.setEllipsize(android.text.TextUtils.TruncateAt.END);chip.setContentDescription((clip.place().equals(name)?"場所の表示を外す ":"場所を表示 ")+name);chip.setCompoundDrawablesRelative(Ui.drawable(this,"pin",clip.place().equals(name)?Ui.BG:Ui.INK),null,null,null);chip.setCompoundDrawablePadding(dp(8));LinearLayout.LayoutParams params=size(-2,48);params.setMargins(0,0,dp(8),0);choices.addView(chip,params);}
        if(clip.location()==null){TextView current=button(locating?"取得中":locations.granted()?"位置を再取得":"現在地を追加",false,this::requestLocation);current.setEnabled(!locating);current.setAlpha(locating?.5f:1);choices.addView(current,size(-2,48));}
    }
    private void markPlaceChoice(){prefs.edit().putBoolean("placeChoice_"+clip.id(),true).apply();}
    private void selectDefaultCity(java.util.List<String> names){
        if(deleted||!prefs.getBoolean("location",true)||!locations.granted()||!clip.place().isBlank()||prefs.getBoolean("placeChoice_"+clip.id(),false))return;
        String city=PlaceResolver.defaultCity(names);if(city.isEmpty())return;
        clip=clip.withPlace(city);markPlaceChoice();repository.place(clip.id(),city);
        if(!isDestroyed()){captions.update(clip,dayCaption,prefs.getBoolean("showTime",true));refreshPlaces();}
    }
    private void resolvePlaces(){
        if(clip.location()==null)return;
        java.util.List<String> cached=repository.placeCandidates(clip.id());
        if(!cached.isEmpty()){selectDefaultCity(cached);refreshPlaces();return;}
        GeoPoint point=clip.location();
        places.resolve(point,names->{
            if(deleted||!point.equals(clip.location()))return;
            if(!names.isEmpty())repository.placeCandidates(clip.id(),names);
            selectDefaultCity(names);if(!isDestroyed())refreshPlaces();
        });
    }
    private void editPlace(){
        EditText input=textInput(clip.place(),"場所",80);LinearLayout form=column();form.setPadding(dp(20),dp(12),dp(20),dp(12));form.addView(input,size(-1,56));
        input.addTextChangedListener(watcher(()->{markPlaceChoice();clip=clip.withPlace(input.getText().toString());changed();}));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("場所").setView(form).setPositiveButton("閉じる",null).create();
        dialog.setOnDismissListener(d->{main.removeCallbacks(save);saveEdits();repository.rememberPlace(clip.location(),clip.place());refreshPlaces();placeScroll.scrollTo(0,0);});
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){dialog.dismiss();return true;}return false;});dialog.show();dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);input.requestFocus();
    }
    private void requestLocation(){
        if(locations.granted()){prefs.edit().putBoolean("location",true).apply();beginLocation();return;}
        boolean rationale=shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)||shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
        if(prefs.getBoolean("askedLocation",false)&&!rationale){new AlertDialog.Builder(this).setTitle("位置情報の権限").setMessage("端末のアプリ設定で位置情報を許可できます。")
            .setPositiveButton("設定を開く",(d,w)->{waitingForSettings=true;startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}).setNegativeButton("キャンセル",null).show();return;}
        Runnable launch=()->{prefs.edit().putBoolean("askedLocation",true).apply();permission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION});};
        if(rationale)new AlertDialog.Builder(this).setTitle("位置情報を記録").setMessage("撮影した位置を動画に記録します。取得するのは撮影画面と、現在地を追加するときだけです。")
            .setPositiveButton("続ける",(d,w)->launch.run()).setNegativeButton("キャンセル",null).show();else launch.run();
    }
    private boolean needsLocation(){return clip.location()==null||(androidx.core.content.ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED&&(clip.location().accuracyMeters()<=0||clip.location().accuracyMeters()>10));}
    private void beginLocation(){
        if(writingLocation||!needsLocation()||isFinishing())return;
        if(!locations.enabled()){new AlertDialog.Builder(this).setTitle("端末の位置情報がオフです").setPositiveButton("設定を開く",(d,w)->{waitingForSettings=true;startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));}).setNegativeButton("キャンセル",null).show();return;}
        locating=true;refreshPlaces();main.removeCallbacks(timeout);main.postDelayed(timeout,15000);locations.start();locationChanged();
    }
    private void locationChanged(){
        if(!locating||writingLocation)return;android.location.Location fix=locations.snapshot();if(fix==null)return;GeoPoint point=LocationTracker.point(fix);if(point==null)return;
        if(clip.location()!=null){float oldAccuracy=clip.location().accuracyMeters();if(oldAccuracy>0&&point.accuracyMeters()>=oldAccuracy*.8f)return;}
        boolean improve=androidx.core.content.ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED&&(point.accuracyMeters()<=0||point.accuracyMeters()>10);
        writingLocation=true;if(!improve){locations.stop();main.removeCallbacks(timeout);}String id=clip.id();
        io.execute(()->{try{repository.location(id,point);main.post(()->{writingLocation=false;locating=improve&&visible&&locating;if(isDestroyed()||isFinishing())return;clip=clip.withLocation(point);repository.rememberPlace(point,clip.place());changed();refreshPlaces();resolvePlaces();if(locating)locationChanged();});}
            catch(Exception e){android.util.Log.w("DaylogLocation","Could not save GPS metadata",e);main.post(()->{writingLocation=false;locating=false;if(!isDestroyed()){refreshPlaces();toast("位置情報を保存できませんでした");}});}});
    }
    private void confirmDelete(){new AlertDialog.Builder(this).setTitle("このクリップを削除しますか？").setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{try{repository.delete(clip.id());deleted=true;finish();}catch(RuntimeException e){toast("削除できませんでした");}}).show();}
    private void toast(String text){if(visible&&!isDestroyed())Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
}
