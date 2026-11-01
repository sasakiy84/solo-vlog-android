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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.media3.common.util.UnstableApi;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private ClipPreview preview;
    private PlaceChoicesView choices;
    private TextView currentLocation;
    private boolean deleted,locating,writingLocation,waitingForSettings,visible;
    private final Runnable save=this::saveEdits;
    private final Runnable timeout=()->{locating=false;locations.stop();refreshPlaces();if(clip.location()==null)toast("現在地を取得できませんでした");};
    private final ActivityResultLauncher<String[]> permission=registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),result->{
        boolean allowed=locations.granted();prefs.edit().putBoolean("location",allowed).apply();if(allowed)beginLocation();
    });

    @Override public void onCreate(Bundle state){
        super.onCreate(state);repository=ClipRepository.get(this);prefs=getSharedPreferences("daylog",MODE_PRIVATE);
        String id=getIntent().getStringExtra(CLIP_ID);
        if(id==null){finish();return;}
        clip=repository.forIds(java.util.List.of(id)).stream().filter(c->c.id().equals(id)).findFirst().orElse(null);
        if(clip==null){finish();return;}
        setRequestedOrientation(clip.landscape()?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        dayCaption=repository.dayCaption(JournalRules.day(clip.capturedAt(),prefs.getInt("dayStart",240),ZoneId.of(clip.zoneId())).toString());
        places=new PlaceResolver(this);locations=new LocationTracker(this,this::locationChanged);
        render();resolvePlaces();
    }
    @Override protected void onResume(){
        super.onResume();visible=true;
        if(clip==null)return;
        if(waitingForSettings){waitingForSettings=false;if(locations.granted()&&locations.enabled()){prefs.edit().putBoolean("location",true).apply();beginLocation();}}
        else if(needsLocation()&&prefs.getBoolean("location",true)&&locations.granted()&&!locating&&!writingLocation)beginLocation();
        startPreview();
    }
    @Override protected void onStop(){
        if(clip!=null){main.removeCallbacks(save);saveEdits();locations.stop();main.removeCallbacks(timeout);if(!writingLocation)locating=false;releasePreview();}
        visible=false;super.onStop();
    }
    @Override protected void onDestroy(){main.removeCallbacks(save);main.removeCallbacks(timeout);releasePreview();if(locations!=null)locations.stop();io.shutdown();super.onDestroy();}
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
        preview=new ClipPreview(this,repository,clip,dayCaption,prefs.getBoolean("showTime",true),prefs.getBoolean("showDate",false),true,this::toast);
        content.addView(preview.frame,size(-1,getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?130:180));

        choices=new PlaceChoicesView(this);LinearLayout.LayoutParams locationParams=size(-1,-2);locationParams.topMargin=dp(12);locationParams.bottomMargin=dp(8);content.addView(choices,locationParams);
        currentLocation=button("現在地を追加",false,this::requestLocation);content.addView(currentLocation,size(-1,48));
        EditText input=textInput(clip.caption(),"クリップのテキスト",120);input.setHint("クリップのテキスト");input.setTextSize(20);input.setSingleLine(false);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setMinLines(1);input.setMaxLines(4);input.setMinHeight(dp(56));input.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);input.setPadding(dp(4),dp(12),dp(4),dp(6));content.addView(input,size(-1,-2));
        input.addTextChangedListener(watcher(()->{clip=clip.withCaption(input.getText().toString());changed();}));
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){saveAndReview();return true;}return false;});
        LinearLayout footer=row();footer.setPadding(dp(20),dp(8),dp(20),dp(8));
        Ui.Icon delete=new Ui.Icon(this,"delete",Ui.INK,"削除",this::confirmDelete);delete.setTooltipText("削除");Ui.touch(delete,Ui.CARD,10);footer.addView(delete,size(48,48));
        Ui.Icon retake=new Ui.Icon(this,"retake",Ui.INK,"撮り直す",this::retake);retake.setTooltipText("撮り直す");Ui.touch(retake,Ui.CARD,10);
        LinearLayout.LayoutParams retakeParams=size(48,48);retakeParams.setMarginStart(dp(12));footer.addView(retake,retakeParams);
        View space=new View(this);footer.addView(space,new LinearLayout.LayoutParams(0,1,1));
        TextView saveButton=button("保存",true,this::saveAndReview);saveButton.setSingleLine(true);LinearLayout.LayoutParams saveParams=size(120,48);saveParams.setMarginStart(dp(16));footer.addView(saveButton,saveParams);root.addView(footer,size(-1,64));
        refreshPlaces();input.requestFocus();
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        input.getViewTreeObserver().addOnWindowFocusChangeListener(new android.view.ViewTreeObserver.OnWindowFocusChangeListener(){
            @Override public void onWindowFocusChanged(boolean focused){if(focused){input.getViewTreeObserver().removeOnWindowFocusChangeListener(this);getWindow().getInsetsController().show(WindowInsets.Type.ime());}}
        });
    }
    private void startPreview(){if(visible&&preview!=null&&!preview.started())preview.start(clip);}
    private void releasePreview(){if(preview!=null)preview.stop();}
    private EditText textInput(String value,String description,int limit){EditText input=new EditText(this);input.setText(value);input.setTextSize(16);input.setContentDescription(description);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setSingleLine(true);input.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});return input;}
    private TextWatcher watcher(Runnable changed){return new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}@Override public void onTextChanged(CharSequence s,int start,int before,int count){changed.run();}@Override public void afterTextChanged(Editable s){}};}
    private void changed(){preview.update(clip,dayCaption,prefs.getBoolean("showTime",true),prefs.getBoolean("showDate",false));main.removeCallbacks(save);main.postDelayed(save,250);}
    private boolean saveEdits(){if(deleted||clip==null)return false;try{repository.caption(clip.id(),clip.caption());repository.place(clip.id(),clip.place());return true;}catch(RuntimeException e){android.util.Log.w("DaylogEditor","Could not save edits",e);toast("編集内容を保存できませんでした");return false;}}
    private void retake(){
        if(writingLocation){toast("位置情報を保存しています");return;}
        main.removeCallbacks(save);if(!saveEdits())return;
        startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("retake_clip",clip.id()));finish();
    }
    private void saveAndReview(){main.removeCallbacks(save);if(!saveEdits())return;String day=JournalRules.day(clip.capturedAt(),prefs.getInt("dayStart",240),ZoneId.of(clip.zoneId())).toString();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("review_day",day).putExtra("edits_saved",true));finish();}
    private void refreshPlaces(){
        if(isFinishing()||isDestroyed())return;
        choices.show(repository.placeCandidates(clip.id()),clip.place(),this::selectPlace,this::otherPlaces);
        currentLocation.setVisibility(clip.location()==null?View.VISIBLE:View.GONE);currentLocation.setText(locating?"取得中":locations.granted()?"位置を再取得":"現在地を追加");currentLocation.setEnabled(!locating);
    }
    private void selectPlace(String name){markPlaceChoice();clip=clip.withPlace(clip.place().equals(name)?"":name);repository.rememberPlace(clip.location(),clip.place());changed();saveEdits();refreshPlaces();}
    private void otherPlaces(){
        java.util.List<String> names=repository.placeCandidates(clip.id()).others(clip,repository);java.util.List<String> labels=new java.util.ArrayList<>(names);labels.add("手入力");
        new AlertDialog.Builder(this).setTitle("その他").setItems(labels.toArray(new String[0]),(d,which)->{if(which==names.size())editPlace();else selectPlace(names.get(which));}).setNegativeButton("キャンセル",null).show();
    }
    private void markPlaceChoice(){prefs.edit().putBoolean("placeChoice_"+clip.id(),true).apply();}
    private void selectDefaultCity(PlaceSuggestions names){
        if(deleted||!prefs.getBoolean("location",true)||!locations.granted()||!clip.place().isBlank()||prefs.getBoolean("placeChoice_"+clip.id(),false))return;
        String city=names.city();if(city.isEmpty())return;
        clip=clip.withPlace(city);markPlaceChoice();repository.place(clip.id(),city);
        if(!isDestroyed()){preview.update(clip,dayCaption,prefs.getBoolean("showTime",true),prefs.getBoolean("showDate",false));refreshPlaces();}
    }
    private void resolvePlaces(){
        if(clip.location()==null)return;
        PlaceSuggestions cached=repository.placeCandidates(clip.id());
        if(!cached.isEmpty()){selectDefaultCity(cached);refreshPlaces();}
        GeoPoint point=clip.location();
        places.resolve(point,names->{
            if(deleted||!point.equals(clip.location()))return;
            PlaceSuggestions merged=names.withFallback(repository.placeCandidates(clip.id()));
            if(!merged.isEmpty())repository.placeCandidates(clip.id(),merged);
            selectDefaultCity(merged);if(!isDestroyed())refreshPlaces();
        });
    }
    private void editPlace(){
        EditText input=textInput(clip.place(),"場所",80);LinearLayout form=column();form.setPadding(dp(20),dp(12),dp(20),dp(12));form.addView(input,size(-1,56));
        input.addTextChangedListener(watcher(()->{markPlaceChoice();clip=clip.withPlace(input.getText().toString());changed();}));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("場所").setView(form).setPositiveButton("閉じる",null).create();
        dialog.setOnDismissListener(d->{main.removeCallbacks(save);saveEdits();repository.rememberPlace(clip.location(),clip.place());refreshPlaces();});
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
