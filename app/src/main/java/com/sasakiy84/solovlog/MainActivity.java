package com.sasakiy84.solovlog;

import android.Manifest;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.LruCache;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class MainActivity extends ComponentActivity {
    private enum Screen { CAMERA, JOURNAL, SETTINGS }
    private Screen screen=Screen.CAMERA, settingsReturn=Screen.CAMERA;
    private SharedPreferences prefs;
    private ClipRepository repository;
    private CameraEngine camera;
    private VideoExporter exporter;
    private LocationTracker locations;
    private PlaceResolver places;
    private Ui.Icon locationIcon;
    private android.location.Location captureLocation;
    private boolean foreground,editorOpen;
    private Runnable locationAfterPermission,pendingLocationAction,pendingLocationFailure,locationAfterSettings,locationSettingsCancelled,activeEditorSave;
    private final Runnable locationTimeout=()->{pendingLocationAction=null;Runnable failed=pendingLocationFailure;pendingLocationFailure=null;if(failed!=null)failed.run();if(locations!=null&&editorOpen)locations.stop();if(foreground)toast("現在地を取得できませんでした");};
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> thumbnails=new LruCache<>(24);
    private LinearLayout root;
    private PreviewView preview;
    private Ui.Shutter shutter;
    private TextView liveLabel,cameraCount,cameraHint,undo;
    private final List<View> captureControls=new ArrayList<>();
    private final List<TextView> durationChips=new ArrayList<>();
    private boolean captureBusy,cameraReady,exporting;
    private int selectedSeconds=3;
    private LocalDate selectedDay;
    private boolean journalLandscape;
    private Clip lastSaved;

    private final ActivityResultLauncher<String[]> permissions=registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(),result->{
            if(!granted(Manifest.permission.RECORD_AUDIO))prefs.edit().putBoolean("sound",false).apply();
            render();
        });

    private final ActivityResultLauncher<String[]> locationPermissions=registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(),result->{
            boolean allowed=locations.granted();prefs.edit().putBoolean("location",allowed).apply();
            Runnable ready=locationAfterPermission;locationAfterPermission=null;
            if(allowed){if(ready!=null)ready.run();else deviceLocationReady(this::locationContextChanged,this::locationContextChanged);}else locationContextChanged();
        });

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("daylog",MODE_PRIVATE);
        repository=ClipRepository.get(this);camera=new CameraEngine(this,this);exporter=new VideoExporter(this);
        places=new PlaceResolver(this);locations=new LocationTracker(this,()->{
            updateLocationIcon();if(pendingLocationAction!=null&&locations.snapshot()!=null){Runnable action=pendingLocationAction;pendingLocationAction=null;pendingLocationFailure=null;main.removeCallbacks(locationTimeout);action.run();}
        });
        selectedSeconds=prefs.getInt("seconds",3);
        if(selectedSeconds!=2&&selectedSeconds!=3&&selectedSeconds!=5)selectedSeconds=3;
        journalLandscape=landscape();selectedDay=today();
        if(state!=null){
            screen=Screen.valueOf(state.getString("screen","CAMERA"));
            settingsReturn=Screen.valueOf(state.getString("return","CAMERA"));
            selectedDay=LocalDate.parse(state.getString("day",today().toString()));
            journalLandscape=state.getBoolean("journalLandscape",landscape());
        }
        applyOrientation();
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){
                if(captureBusy){stopCapture();return;}
                if(screen==Screen.SETTINGS)navigate(settingsReturn);
                else if(screen==Screen.JOURNAL)navigate(Screen.CAMERA);
                else{setEnabled(false);getOnBackPressedDispatcher().onBackPressed();}
            }
        });
        render();
    }
    @Override protected void onResume(){super.onResume();foreground=true;
        if(locationAfterSettings!=null){Runnable ready=locationAfterSettings,cancel=locationSettingsCancelled;locationAfterSettings=null;locationSettingsCancelled=null;if(locations.granted()&&locations.enabled())ready.run();else if(cancel!=null)cancel.run();}
        if(locationAfterPermission!=null&&locations.granted()){Runnable ready=locationAfterPermission;locationAfterPermission=null;prefs.edit().putBoolean("location",true).apply();ready.run();}
        if(screen==Screen.CAMERA&&preview!=null&&!captureBusy){applyOrientation();resumeCamera();}
    }
    @Override protected void onStop(){if(activeEditorSave!=null){main.removeCallbacks(activeEditorSave);activeEditorSave.run();}foreground=false;if(locations!=null)locations.stop();pendingLocationAction=null;Runnable failed=pendingLocationFailure;pendingLocationFailure=null;if(failed!=null)failed.run();main.removeCallbacks(locationTimeout);if(camera!=null&&camera.busy())stopCapture();super.onStop();}
    @Override protected void onDestroy(){if(camera!=null){camera.stop();camera.unbind();}if(!captureBusy)io.shutdown();super.onDestroy();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(!captureBusy)render();}
    @Override public void onSaveInstanceState(Bundle state){
        state.putString("screen",screen.name());state.putString("return",settingsReturn.name());
        state.putString("day",selectedDay.toString());state.putBoolean("journalLandscape",journalLandscape);super.onSaveInstanceState(state);
    }
    private int dp(float value){return Ui.dp(this,value);}
    private boolean landscape(){return orientationFor(today());}
    private boolean orientationFor(LocalDate day){return JournalRules.orientationFor(repository.all(),day,boundary(),prefs.getBoolean("landscape",false));}
    private int boundary(){return prefs.getInt("dayStart",240);}
    private boolean granted(String p){return ContextCompat.checkSelfPermission(this,p)==PackageManager.PERMISSION_GRANTED;}
    private boolean sound(){return prefs.getBoolean("sound",true)&&granted(Manifest.permission.RECORD_AUDIO);}
    private LocalDate today(){return JournalRules.day(System.currentTimeMillis(),boundary(),ZoneId.systemDefault());}
    private List<Clip> clips(LocalDate day,boolean horizontal,boolean selected){return JournalRules.clipsFor(repository.all(),day,horizontal,boundary(),selected);}
    private String seconds(long millis){return String.format(Locale.JAPAN,"%.1f",millis/1000f).replace(".0","");}
    private void applyOrientation(){setRequestedOrientation(landscape()?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);}
    private void navigate(Screen next){
        if(captureBusy||exporting)return;
        if(next==Screen.SETTINGS)settingsReturn=screen;
        if(next==Screen.JOURNAL&&screen==Screen.CAMERA){selectedDay=today();journalLandscape=landscape();}
        if(next==Screen.CAMERA)applyOrientation();
        screen=next;render();
    }
    private void render(){
        cameraReady=false;preview=null;locationIcon=null;if(locations!=null)locations.stop();captureControls.clear();durationChips.clear();if(!camera.busy())camera.unbind();
        root=column();root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        setContentView(root);root.requestApplyInsets();
        if(screen==Screen.CAMERA)renderCamera();else if(screen==Screen.JOURNAL)renderJournal();else renderSettingsBody();
    }
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private TextView text(String value,int size,int color){return Ui.text(this,value,size,color);}
    private TextView bold(String value,int size,int color){TextView v=text(value,size,color);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    private void gap(LinearLayout parent,int h){parent.addView(new View(this),lp(1,h));}
    private void padded(View view,int n){view.setPadding(dp(n),dp(n),dp(n),dp(n));}
    private TextView button(String label,boolean primary,Runnable action){
        TextView v=bold(label,15,primary?Ui.BG:Ui.INK);v.setGravity(Gravity.CENTER);Ui.touch(v,primary?Ui.ACCENT:Ui.CARD,10);
        v.setPadding(dp(16),dp(12),dp(16),dp(12));v.setMinHeight(dp(48));v.setOnClickListener(view->action.run());return v;
    }
    private TextView button(String label,String iconKind,boolean primary,Runnable action){
        TextView v=button(label,primary,action);v.setCompoundDrawablesRelative(Ui.drawable(this,iconKind,primary?Ui.BG:Ui.INK),null,null,null);v.setCompoundDrawablePadding(dp(8));return v;
    }
    private Ui.Icon icon(String kind,String label,Runnable action){return new Ui.Icon(this,kind,Ui.INK,label,action);}
    private void toast(String message){if(!isDestroyed())Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private LinearLayout toolbar(String title,Runnable back,boolean settings){
        LinearLayout bar=row();bar.setPadding(dp(12),dp(5),dp(12),dp(5));
        if(back!=null)bar.addView(icon("back","前の画面に戻る",back),lp(48,48));
        TextView label=bold(title,title.equals("daylog")?23:18,Ui.INK);label.setGravity(Gravity.CENTER_VERTICAL);
        if(back==null)label.setPadding(dp(10),0,0,0);
        bar.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));
        if(settings)bar.addView(icon("gear","設定",()->navigate(Screen.SETTINGS)),lp(48,48));
        root.addView(bar,lp(-1,62));return bar;
    }

    private void renderCamera(){
        boolean wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout bar=toolbar("daylog",null,true);cameraCount=text(cameraSummary(),12,Ui.MUTED);bar.addView(cameraCount,1,lp(-2,-2));
        LinearLayout body=wide?row():column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout stage=new FrameLayout(this);
        LinearLayout.LayoutParams stageParams=wide?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1);
        stageParams.setMargins(dp(20),dp(wide?4:10),dp(wide?0:20),dp(8));body.addView(stage,stageParams);
        Ui.AspectFrame frame=new Ui.AspectFrame(this,landscape());frame.setBackground(Ui.rounded(this,Ui.CARD,12));frame.setClipToOutline(true);
        stage.addView(frame,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        preview=new PreviewView(this);frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        if(prefs.getBoolean("grid",true))frame.addView(new Ui.Grid(this),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout overlay=row();overlay.setPadding(dp(16),dp(8),dp(8),dp(8));
        liveLabel=bold("準備中",11,Color.WHITE);liveLabel.setGravity(Gravity.CENTER_VERTICAL);
        liveLabel.setPadding(dp(10),0,dp(10),0);liveLabel.setBackground(Ui.rounded(this,0x99000000,16));overlay.addView(liveLabel,lp(-2,32));overlay.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        Ui.Icon audio=new Ui.Icon(this,sound()?"mic":"muted",Color.WHITE,sound()?"音声をオフ":"音声をオン",this::toggleSound);
        locationIcon=new Ui.Icon(this,locationEnabled()?"pin":"pin_off",Color.WHITE,"位置情報をオン",this::toggleLocation);Ui.touch(locationIcon,0x99000000,24);overlay.addView(locationIcon,lp(48,48));captureControls.add(locationIcon);updateLocationIcon();
        Ui.touch(audio,0x99000000,24);overlay.addView(audio,lp(48,48));captureControls.add(audio);frame.addView(overlay,new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP));
        if(!granted(Manifest.permission.CAMERA)){
            LinearLayout prompt=column();prompt.setGravity(Gravity.CENTER);padded(prompt,22);prompt.setBackgroundColor(Ui.BG);
            prompt.addView(bold("カメラへのアクセス",24,Ui.INK));gap(prompt,12);
            TextView explanation=text("撮影するにはカメラを許可してください。",13,Ui.MUTED);explanation.setGravity(Gravity.CENTER);prompt.addView(explanation);gap(prompt,24);
            prompt.addView(button("カメラを許可",true,()->requestPermissions(false)),lp(-1,-2));frame.addView(prompt,new FrameLayout.LayoutParams(-1,-1));
        }
        LinearLayout controls=column();controls.setGravity(Gravity.CENTER);controls.setPadding(dp(12),dp(4),dp(12),dp(8));body.addView(controls,wide?lp(190,-1):lp(-1,-2));

        LinearLayout lengths=row();lengths.setGravity(Gravity.CENTER);
        for(int duration:new int[]{2,3,5}){
            TextView chip=bold(duration+" s",16,duration==selectedSeconds?Ui.BG:Ui.MUTED);chip.setGravity(Gravity.CENTER);
            chip.setBackground(Ui.rounded(this,duration==selectedSeconds?Ui.ACCENT:Ui.CARD,22));chip.setSelected(duration==selectedSeconds);chip.setContentDescription(duration+"秒を選択");chip.setFocusable(true);chip.setTag(duration);durationChips.add(chip);
            LinearLayout.LayoutParams params=lp(wide?48:70,48);params.setMargins(dp(4),0,dp(4),0);lengths.addView(chip,params);
            chip.setOnClickListener(v->{if(!captureBusy){selectedSeconds=duration;prefs.edit().putInt("seconds",duration).apply();resetCaptureControls();}});captureControls.add(chip);
        }
        controls.addView(lengths,lp(-1,52));gap(controls,wide?6:8);
        LinearLayout capture=row();capture.setGravity(Gravity.CENTER);
        Ui.Icon library=icon("library","日々の記録",()->navigate(Screen.JOURNAL));
        shutter=new Ui.Shutter(this);shutter.state(false,0,selectedSeconds);shutter.setOnClickListener(v->{if(captureBusy)stopCapture();else startCapture();});
        Ui.Icon flip=icon("flip","カメラを切り替える",()->{if(!captureBusy)try{camera.flip();}catch(RuntimeException e){toast("このカメラは使えません");}});
        if(!wide)capture.addView(library,lp(64,64));
        LinearLayout.LayoutParams shutterParams=lp(88,88);if(!wide)shutterParams.setMargins(dp(32),0,dp(32),0);capture.addView(shutter,shutterParams);
        if(!wide)capture.addView(flip,lp(64,64));controls.addView(capture,lp(-1,92));captureControls.add(library);captureControls.add(flip);
        if(wide){LinearLayout side=row();side.setGravity(Gravity.CENTER);side.addView(library,lp(64,48));side.addView(flip,lp(64,48));controls.addView(side);}
        cameraHint=text("",11,Ui.MUTED);cameraHint.setGravity(Gravity.CENTER);controls.addView(cameraHint,lp(-1,20));
        undo=button("",false,()->{
            if(lastSaved!=null&&!captureBusy)try{repository.delete(lastSaved.id());lastSaved=null;undo.setVisibility(View.GONE);cameraCount.setText(cameraSummary());}catch(RuntimeException e){toast("取り消せませんでした");}
        });undo.setVisibility(View.GONE);controls.addView(undo,lp(-1,44));
        shutter.setEnabled(false);preview.post(this::resumeCamera);
    }
    private String cameraSummary(){List<Clip> list=clips(today(),landscape(),false);return today().format(DateTimeFormatter.ofPattern("MM.dd"))+"  ·  "+list.size()+"本";}
    private CameraEngine.Listener cameraListener(){
        return new CameraEngine.Listener(){
            @Override public void ready(){if(screen==Screen.CAMERA&&!isDestroyed()){cameraReady=true;if(shutter!=null)shutter.setEnabled(true);if(liveLabel!=null)liveLabel.setVisibility(View.INVISIBLE);}}
            @Override public void progress(float fraction,boolean stabilizing){
                if(isDestroyed()||screen!=Screen.CAMERA)return;
                shutter.state(true,fraction,selectedSeconds);liveLabel.setVisibility(View.VISIBLE);
                liveLabel.setText(stabilizing?"準備中":fraction>=1?"保存中":"録画中  "+String.format(Locale.US,"%02d",Math.max(1,(int)Math.ceil(selectedSeconds*(1-fraction)))));
                liveLabel.setTextColor(Color.WHITE);
            }
            @Override public void saved(File file,long capturedAt){saveCapture(file,capturedAt);}
            @Override public void error(String message){
                boolean wasRecording=captureBusy;captureBusy=false;
                if(!isDestroyed()){
                    if(screen==Screen.CAMERA&&shutter!=null){cameraReady=cameraReady&&wasRecording;resetCaptureControls();cameraHint.setText("カメラ・権限・空き容量を確認してください");}
                    toast(message);
                }
            }
        };
    }
    private void bindCamera(){if(editorOpen||preview==null||screen!=Screen.CAMERA||captureBusy||!granted(Manifest.permission.CAMERA))return;camera.bind(preview,landscape(),cameraListener());}
    private void startCapture(){
        if(!granted(Manifest.permission.CAMERA)){requestPermissions(false);return;}
        if(!cameraReady){toast("カメラを準備しています");return;}
        captureBusy=true;undo.setVisibility(View.GONE);for(View view:captureControls)view.setEnabled(false);
        shutter.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        captureLocation=locationEnabled()?locations.snapshot():null;
        camera.start(repository.file(UUID.randomUUID().toString()),selectedSeconds,sound(),captureLocation,cameraListener());
    }
    private void resetCaptureControls(){
        for(View view:captureControls)view.setEnabled(true);
        shutter.state(false,0,selectedSeconds);shutter.setEnabled(cameraReady);liveLabel.setText(cameraReady?"撮影できます":"準備中");liveLabel.setTextColor(Color.WHITE);liveLabel.setVisibility(cameraReady?View.INVISIBLE:View.VISIBLE);
        cameraHint.setText("");cameraCount.setText(cameraSummary());
        for(TextView chip:durationChips){boolean selected=(Integer)chip.getTag()==selectedSeconds;chip.setSelected(selected);chip.setTextColor(selected?Ui.BG:Ui.MUTED);chip.setBackground(Ui.rounded(this,selected?Ui.ACCENT:Ui.CARD,22));}
    }
    private void stopCapture(){camera.stop();if(!camera.busy()){captureBusy=false;if(!isDestroyed()&&screen==Screen.CAMERA)render();}}
    private void saveCapture(File file,long capturedAt){
        int planned=selectedSeconds;android.location.Location recordedLocation=captureLocation;String zone=ZoneId.systemDefault().getId();
        io.execute(()->{
            try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
                media.setDataSource(file.getAbsolutePath());
                long duration=Long.parseLong(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                int width=Integer.parseInt(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int height=Integer.parseInt(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                String rotation=media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if("90".equals(rotation)||"270".equals(rotation)){int old=width;width=height;height=old;}
                if(duration<200){
                    file.delete();main.post(()->{captureBusy=false;if(!isDestroyed()&&screen==Screen.CAMERA){resetCaptureControls();toast("撮影が短すぎました。もう一度撮ってみてください。");}});return;
                }
                Clip clip=new Clip(file.getName().replace(".mp4",""),capturedAt,duration,planned,width>height,true,zone).withLocation(LocationTracker.point(recordedLocation));
                repository.add(clip);lastSaved=clip;
                main.post(()->{
                    captureBusy=false;if(isDestroyed()){io.shutdown();return;}
                    if(clip.location()!=null)resolvePlaces(clip,names->{});
                    if(screen==Screen.CAMERA){
                        resetCaptureControls();
                        if(foreground&&prefs.getBoolean("postCaptureEdit",true))review(clip,true);
                        else{undo.setText("保存しました    取り消す");undo.setVisibility(View.VISIBLE);TextView currentUndo=undo;main.postDelayed(()->currentUndo.setVisibility(View.GONE),5000);toast("保存しました");}
                    }
                });
            }catch(Exception e){main.post(()->{captureBusy=false;if(!isDestroyed()){render();toast("クリップを保存できませんでした。空き容量を確認してください。");}});}
        });
    }
    private boolean locationEnabled(){return prefs.getBoolean("location",false)&&locations.granted();}
    private void resumeCamera(){
        if(foreground&&screen==Screen.CAMERA&&!captureBusy&&!editorOpen){bindCamera();if(locationEnabled())locations.start();}
    }
    private void updateLocationIcon(){
        if(locationIcon==null)return;
        boolean active=locationEnabled();locationIcon.setImageDrawable(Ui.drawable(this,active?"pin":"pin_off",Color.WHITE));
        locationIcon.setContentDescription(active?"位置情報をオフ":"位置情報をオン");locationIcon.setSelected(active);
        locationIcon.setAlpha(active&&locations.snapshot()==null?.55f:1f);
    }
    private void locationContextChanged(){
        updateLocationIcon();
        if(screen==Screen.SETTINGS&&!editorOpen)renderSettingsBody();else resumeCamera();
    }
    private void toggleLocation(){
        if(captureBusy)return;
        if(locationEnabled()){prefs.edit().putBoolean("location",false).apply();locations.stop();locationContextChanged();}
        else requestLocation(null);
    }
    private void requestLocation(Runnable ready){
        if(locations.granted()){
            prefs.edit().putBoolean("location",true).apply();if(ready!=null)ready.run();else deviceLocationReady(this::locationContextChanged,this::locationContextChanged);return;
        }
        boolean rationale=shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)||shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
        if(prefs.getBoolean("askedLocation",false)&&!rationale){
            new AlertDialog.Builder(this).setTitle("位置情報の権限").setMessage("端末のアプリ設定で位置情報を許可できます。")
                .setPositiveButton("設定を開く",(d,w)->{locationAfterPermission=ready==null?()->deviceLocationReady(this::locationContextChanged,this::locationContextChanged):ready;startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));})
                .setNegativeButton("キャンセル",null).show();return;
        }
        Runnable launch=()->{locationAfterPermission=ready;prefs.edit().putBoolean("askedLocation",true).apply();locationPermissions.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION});};
        if(rationale)new AlertDialog.Builder(this).setTitle("位置情報を記録").setMessage("撮影した位置を動画に記録します。取得するのは撮影画面と、現在地を追加するときだけです。")
            .setPositiveButton("続ける",(d,w)->launch.run()).setNegativeButton("キャンセル",null).show();else launch.run();
    }
    private void deviceLocationReady(Runnable ready,Runnable cancel){
        if(locations.enabled()){ready.run();return;}
        new AlertDialog.Builder(this).setTitle("端末の位置情報がオフです").setPositiveButton("設定を開く",(d,w)->{
            locationAfterSettings=ready;locationSettingsCancelled=cancel;startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }).setNegativeButton("キャンセル",(d,w)->cancel.run()).show();
    }
    private void currentLocation(java.util.function.Consumer<android.location.Location> ready){
        if(!locations.enabled()){deviceLocationReady(()->currentLocation(ready),()->ready.accept(null));return;}
        android.location.Location snapshot=locations.snapshot();if(snapshot!=null){ready.accept(snapshot);return;}
        pendingLocationFailure=()->ready.accept(null);pendingLocationAction=()->{android.location.Location fix=locations.snapshot();if(fix!=null)ready.accept(fix);if(editorOpen)locations.stop();};
        main.removeCallbacks(locationTimeout);main.postDelayed(locationTimeout,15000);locations.start();
    }
    private void resolvePlaces(Clip clip,java.util.function.Consumer<List<String>> ready){
        if(clip.location()==null){ready.accept(List.of());return;}
        List<String> cached=repository.placeCandidates(clip.id());
        if(!cached.isEmpty()){ready.accept(cached);return;}
        places.resolve(clip.location(),names->{if(!names.isEmpty())repository.placeCandidates(clip.id(),names);ready.accept(names);});
    }

    private void requestPermissions(boolean audioOnly){
        String permission=audioOnly?Manifest.permission.RECORD_AUDIO:Manifest.permission.CAMERA,asked=audioOnly?"askedAudio":"askedCamera";
        if(prefs.getBoolean(asked,false)&&!granted(permission)&&!shouldShowRequestPermissionRationale(permission)){
            new AlertDialog.Builder(this).setTitle(audioOnly?"マイクの権限":"カメラの権限").setMessage("端末のアプリ設定で権限を許可できます。")
                .setPositiveButton("設定を開く",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("今はしない",null).show();return;
        }
        Runnable launch=()->{prefs.edit().putBoolean(asked,true).putBoolean("askedAudio",true).apply();if(audioOnly)prefs.edit().putBoolean("sound",true).apply();permissions.launch(audioOnly?new String[]{Manifest.permission.RECORD_AUDIO}:new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO});};
        if(shouldShowRequestPermissionRationale(permission))new AlertDialog.Builder(this).setTitle(audioOnly?"マイクの権限":"カメラの権限").setMessage(audioOnly?"環境音を残すためにマイクを使います。オフでも撮影できます。":"プレビューと撮影にカメラを使います。マイクを許可すると環境音も残せます。")
            .setPositiveButton("続ける",(d,w)->launch.run()).setNegativeButton("今はしない",null).show();
        else launch.run();
    }
    private void toggleSound(){
        if(captureBusy)return;
        if(sound()){prefs.edit().putBoolean("sound",false).apply();render();}
        else if(granted(Manifest.permission.RECORD_AUDIO)){prefs.edit().putBoolean("sound",true).apply();render();}
        else requestPermissions(true);
    }

    private LinearLayout scrollBody(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=column();content.setPadding(dp(22),dp(14),dp(22),dp(28));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));return content;
    }
    private void renderJournal(){
        toolbar("日々の記録",()->navigate(Screen.CAMERA),true);
        LinearLayout content=scrollBody();
        TreeSet<LocalDate> days=new TreeSet<>(Comparator.reverseOrder());days.add(today());
        for(Clip clip:repository.all())days.add(JournalRules.day(clip.capturedAt(),boundary(),ZoneId.of(clip.zoneId())));
        HorizontalScrollView dates=new HorizontalScrollView(this);dates.setHorizontalScrollBarEnabled(false);LinearLayout dateRow=row();dates.addView(dateRow);
        for(LocalDate date:days){
            TextView chip=bold(date.format(DateTimeFormatter.ofPattern("MM.dd")),14,date.equals(selectedDay)?Ui.BG:Ui.MUTED);
            chip.setGravity(Gravity.CENTER);Ui.touch(chip,date.equals(selectedDay)?Ui.ACCENT:Ui.CARD,10);
            LinearLayout.LayoutParams params=lp(74,48);params.setMargins(0,0,dp(9),0);dateRow.addView(chip,params);chip.setOnClickListener(v->{selectedDay=date;render();});
        }
        content.addView(dates,lp(-1,48));gap(content,20);
        String dayCaption=repository.dayCaption(selectedDay.toString());
        LinearLayout dayText=row();
        TextView title=text(dayCaption.isEmpty()?"1日のテキスト":dayCaption,16,dayCaption.isEmpty()?Ui.MUTED:Ui.INK);
        dayText.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        dayText.addView(new Ui.Icon(this,"edit",Ui.MUTED,null,null),lp(48,48));
        Ui.touch(dayText,Ui.BG,8);dayText.setContentDescription("1日のテキストを編集");dayText.setOnClickListener(v->editDayCaption());
        content.addView(dayText,lp(-1,-2));gap(content,12);
        journalLandscape=orientationFor(selectedDay);
        List<Clip> list=clips(selectedDay,journalLandscape,false),selected=clips(selectedDay,journalLandscape,true);
        long total=selected.stream().mapToLong(JournalRules::exportDuration).sum();
        LinearLayout summary=row();
        TextView count=bold(selected.size()+"本 · "+seconds(total)+"秒",16,Ui.INK);count.setGravity(Gravity.CENTER_VERTICAL);
        summary.addView(count,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView save=button("動画を保存","download",true,()->export(selected,selectedDay.toString(),journalLandscape));
        save.setEnabled(!selected.isEmpty());save.setAlpha(selected.isEmpty()?.4f:1f);summary.addView(save,lp(-2,48));content.addView(summary);gap(content,20);
        if(list.isEmpty()){TextView empty=text("動画がありません",14,Ui.MUTED);empty.setGravity(Gravity.CENTER);content.addView(empty,lp(-1,120));}
        // Boundary changes can regroup older, opposite-format footage. Keep it available individually.
        List<Clip> other=clips(selectedDay,!journalLandscape,false);
        int columns=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?3:2;
        for(int index=0;index<list.size();index+=columns){
            LinearLayout cards=row();cards.setGravity(Gravity.TOP);
            for(int n=0;n<columns;n++){
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(n==0?0:dp(10),0,0,dp(14));
                cards.addView(index+n<list.size()?clipCard(list.get(index+n)):new View(this),params);
            }
            content.addView(cards);
        }
        for(Clip clip:other){TextView retained=button(clipTime(clip)+" · "+(clip.landscape()?"横":"縦"),false,()->review(clip));content.addView(retained,lp(-1,48));}
        TextView capture=button("撮影する","camera",true,()->navigate(Screen.CAMERA));LinearLayout.LayoutParams params=lp(-1,54);params.setMargins(dp(22),dp(8),dp(22),dp(12));root.addView(capture,params);
    }
    private View clipCard(Clip clip){
        LinearLayout card=column();FrameLayout frame=new FrameLayout(this);frame.setBackground(Ui.rounded(this,Ui.CARD,10));frame.setClipToOutline(true);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        frame.setContentDescription("クリップを再生 "+clipTime(clip)+" "+seconds(JournalRules.exportDuration(clip))+"秒");frame.setFocusable(true);frame.setOnClickListener(v->review(clip));card.addView(frame,lp(-1,journalLandscape?124:188));
        Ui.Icon check=new Ui.Icon(this,clip.included()?"check":"unchecked",Ui.INK,clip.included()?"このクリップを動画から除外":"このクリップを動画に含める",()->{repository.include(clip.id(),!clip.included());render();});
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);frame.addView(check,cp);
        gap(card,9);LinearLayout meta=row();meta.addView(bold(clipTime(clip),14,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));meta.addView(text(seconds(JournalRules.exportDuration(clip))+"s",12,Ui.MUTED));card.addView(meta);
        Bitmap cached=thumbnails.get(clip.id());
        if(cached!=null)image.setImageBitmap(cached);
        else io.execute(()->{
            try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
                media.setDataSource(repository.file(clip.id()).getAbsolutePath());
                Bitmap bitmap=media.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,400,400);
                if(bitmap!=null){thumbnails.put(clip.id(),bitmap);main.post(()->{if(!isDestroyed())image.setImageBitmap(bitmap);});}
            }catch(Exception e){main.post(()->{if(!isDestroyed())image.setContentDescription("プレビューを読み込めません。タップして再生");});}
        });
        return card;
    }
    private String clipTime(Clip clip){return Instant.ofEpochMilli(clip.capturedAt()).atZone(ZoneId.of(clip.zoneId())).format(DateTimeFormatter.ofPattern("HH:mm"));}
    private EditText captionInput(String value,String label,int limit){
        EditText input=new EditText(this);input.setText(value);input.setHint(label);input.setContentDescription(label);
        input.setTextSize(16);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(3);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});return input;
    }
    private final class EditorPreview {
        final Ui.AspectFrame frame;
        final VideoView video;
        final CaptionOverlay.Preview captions;
        EditorPreview(Clip clip,String day){
            frame=new Ui.AspectFrame(MainActivity.this,clip.landscape());
            video=new VideoView(MainActivity.this);frame.addView(video,new FrameLayout.LayoutParams(-1,-1));
            captions=new CaptionOverlay.Preview(MainActivity.this,clip,day,prefs.getBoolean("showTime",true));frame.addView(captions,new FrameLayout.LayoutParams(-1,-1));
            MediaController controller=new MediaController(MainActivity.this);controller.setAnchorView(video);video.setMediaController(controller);
            video.setVideoPath(repository.file(clip.id()).getAbsolutePath());
            video.setOnPreparedListener(player->{player.setLooping(true);video.start();});
            video.setOnErrorListener((p,w,e)->{toast("動画を再生できませんでした");return true;});
        }
        void start(Clip clip){captions.startProbing(video,repository.file(clip.id()),io);}
    }
    private void focusEditor(EditText input,AlertDialog dialog){
        input.setSingleLine(true);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){dialog.dismiss();return true;}return false;});
        input.requestFocus();input.post(()->{if(dialog.isShowing())getSystemService(android.view.inputmethod.InputMethodManager.class).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
    }
    private void editDayCaption(){
        LocalDate day=selectedDay;LinearLayout content=column();padded(content,16);content.setGravity(Gravity.CENTER_HORIZONTAL);
        List<Clip> clips=clips(day,orientationFor(day),false);Clip first=clips.isEmpty()?null:clips.get(0);
        EditorPreview panel=first==null?null:new EditorPreview(first,repository.dayCaption(day.toString()));
        if(panel!=null){content.addView(panel.frame,lp(-1,180));gap(content,12);}
        EditText input=captionInput(repository.dayCaption(day.toString()),"1日のテキスト",80);input.setHint(null);content.addView(input,lp(-1,-2));
        Runnable save=()->{try{repository.dayCaption(day.toString(),input.getText().toString());}catch(RuntimeException e){toast("テキストを保存できませんでした");}};activeEditorSave=save;
        input.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                if(panel!=null)panel.captions.update(first,s.toString(),prefs.getBoolean("showTime",true));main.removeCallbacks(save);main.postDelayed(save,250);
            }
            @Override public void afterTextChanged(Editable s){}
        });
        ScrollView scroll=new ScrollView(this);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("1日のテキスト").setView(scroll).setPositiveButton("閉じる",null).create();
        dialog.setOnDismissListener(d->{main.removeCallbacks(save);save.run();if(activeEditorSave==save)activeEditorSave=null;if(panel!=null)panel.video.stopPlayback();if(!isDestroyed())render();});
        dialog.show();if(panel!=null)panel.start(first);focusEditor(input,dialog);
    }
    private void review(Clip clip){review(clip,false);}
    private void review(Clip clip,boolean justCaptured){
        if(editorOpen||isDestroyed())return;editorOpen=true;cameraReady=false;camera.unbind();locations.stop();
        Runnable[] refreshChoices={null};Clip[] edited={clip};boolean[] deleted={false},leavingForExport={false},locating={false};
        String day=JournalRules.day(clip.capturedAt(),boundary(),ZoneId.of(clip.zoneId())).toString();
        LinearLayout content=column();padded(content,16);content.setGravity(Gravity.CENTER_HORIZONTAL);
        EditorPreview panel=new EditorPreview(clip,repository.dayCaption(day));
        content.addView(panel.frame,lp(-1,getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?130:justCaptured?180:280));
        gap(content,10);
        HorizontalScrollView placeScroll=new HorizontalScrollView(this);placeScroll.setHorizontalScrollBarEnabled(false);LinearLayout choices=row();placeScroll.addView(choices);
        LinearLayout locationLine=row();locationLine.addView(placeScroll,new LinearLayout.LayoutParams(0,dp(52),1));content.addView(locationLine,lp(-1,52));gap(content,8);
        EditText input=captionInput(clip.caption(),"クリップのテキスト",120);content.addView(input,lp(-1,-2));
        Runnable save=()->{if(!deleted[0])try{repository.caption(clip.id(),edited[0].caption());repository.place(clip.id(),edited[0].place());}catch(RuntimeException e){toast("編集内容を保存できませんでした");}};activeEditorSave=save;
        Runnable refreshPreview=()->panel.captions.update(edited[0],repository.dayCaption(day),prefs.getBoolean("showTime",true));
        ScrollView scroll=new ScrollView(this);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(clipTime(clip)+"  /  "+seconds(JournalRules.exportDuration(clip))+"秒").setView(scroll)
            .setPositiveButton(justCaptured?"次を撮る":"閉じる",null).setNeutralButton("削除",null).create();
        TextView saveVideo=button("このクリップを保存","download",true,()->{if(locating[0])return;save.run();leavingForExport[0]=true;dialog.dismiss();export(List.of(edited[0]),"clip",clip.landscape());});
        java.util.function.Consumer<String> selectPlace=name->{edited[0]=edited[0].withPlace(edited[0].place().equals(name)?"":name);repository.rememberPlace(edited[0].location(),edited[0].place());save.run();refreshPreview.run();refreshChoices[0].run();};
        java.util.function.Consumer<android.location.Location> attachLocation=fix->{
            if(!dialog.isShowing())return;if(fix==null){locating[0]=false;refreshChoices[0].run();return;}GeoPoint point=LocationTracker.point(fix);locating[0]=true;refreshChoices[0].run();
            io.execute(()->{
                try{repository.location(clip.id(),point);main.post(()->{
                    if(!dialog.isShowing())return;locating[0]=false;edited[0]=edited[0].withLocation(point);repository.rememberPlace(point,edited[0].place());refreshPreview.run();refreshChoices[0].run();
                    resolvePlaces(edited[0],names->{if(dialog.isShowing())refreshChoices[0].run();});
                });}catch(Exception e){android.util.Log.w("DaylogLocation","Could not save GPS metadata",e);main.post(()->{locating[0]=false;if(dialog.isShowing()){toast("位置情報を保存できませんでした");refreshChoices[0].run();}});}
            });
        };
        Runnable addCurrent=()->requestLocation(()->{locating[0]=true;refreshChoices[0].run();currentLocation(attachLocation);});
        refreshChoices[0]=()->{
            saveVideo.setEnabled(!locating[0]);saveVideo.setAlpha(locating[0]?.5f:1f);choices.removeAllViews();java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<>();
            if(!edited[0].place().isBlank())names.add(edited[0].place());names.addAll(repository.nearbyPlaces(edited[0].location()));names.addAll(repository.placeCandidates(clip.id()));
            if(edited[0].location()!=null)names.add(edited[0].location().label());
            for(String name:names){TextView chip=button(name,"pin",edited[0].place().equals(name),()->selectPlace.accept(name));chip.setContentDescription((edited[0].place().equals(name)?"場所の表示を外す ":"場所を表示 ")+name);chip.setSingleLine(true);chip.setMaxWidth(dp(220));chip.setEllipsize(android.text.TextUtils.TruncateAt.END);chip.setSelected(edited[0].place().equals(name));LinearLayout.LayoutParams params=lp(-2,48);params.setMargins(0,0,dp(8),0);choices.addView(chip,params);}
            if(justCaptured&&edited[0].location()==null){TextView current=button("現在地を追加","pin",false,addCurrent);current.setEnabled(!locating[0]);current.setAlpha(locating[0]?.5f:1f);LinearLayout.LayoutParams params=lp(-2,48);params.setMargins(0,0,dp(8),0);choices.addView(current,params);}
            if(names.isEmpty()&&!justCaptured)choices.addView(button("場所を入力","pin",false,()->editPlaceName(edited,save,refreshPreview,refreshChoices[0])),lp(-2,48));
        };
        locationLine.addView(icon("edit","場所を入力",()->editPlaceName(edited,save,refreshPreview,refreshChoices[0])),lp(48,48));
        input.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){edited[0]=edited[0].withCaption(s.toString());refreshPreview.run();main.removeCallbacks(save);main.postDelayed(save,250);}
            @Override public void afterTextChanged(Editable s){}
        });
        gap(content,12);content.addView(saveVideo,lp(-1,48));
        dialog.setOnDismissListener(d->{
            main.removeCallbacks(save);save.run();if(activeEditorSave==save)activeEditorSave=null;panel.video.stopPlayback();editorOpen=false;pendingLocationAction=null;pendingLocationFailure=null;locationAfterPermission=null;locationAfterSettings=null;locationSettingsCancelled=null;main.removeCallbacks(locationTimeout);locations.stop();
            if(!isDestroyed()){if(screen==Screen.JOURNAL)render();else{if(shutter!=null)resetCaptureControls();if(!leavingForExport[0])resumeCamera();}}
        });
        dialog.show();dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new AlertDialog.Builder(this).setTitle("このクリップを削除しますか？")
            .setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{try{repository.delete(clip.id());deleted[0]=true;thumbnails.remove(clip.id());dialog.dismiss();}catch(RuntimeException e){toast("削除できませんでした");}}).show());
        refreshChoices[0].run();panel.start(clip);
        if(clip.location()!=null)resolvePlaces(clip,names->{if(dialog.isShowing())refreshChoices[0].run();});
        else if(justCaptured&&locationEnabled())addCurrent.run();
        if(justCaptured)focusEditor(input,dialog);
    }
    private void editPlaceName(Clip[] edited,Runnable save,Runnable refreshPreview,Runnable refreshChoices){
        LinearLayout form=column();padded(form,20);EditText input=captionInput(edited[0].place(),"場所",80);input.setHint(null);form.addView(input,lp(-1,-2));
        input.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){edited[0]=edited[0].withPlace(s.toString());refreshPreview.run();main.removeCallbacks(save);main.postDelayed(save,250);}
            @Override public void afterTextChanged(Editable s){}
        });
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("場所").setView(form).setPositiveButton("閉じる",null).create();
        dialog.setOnDismissListener(d->{main.removeCallbacks(save);edited[0]=edited[0].withPlace(input.getText().toString());repository.rememberPlace(edited[0].location(),edited[0].place());save.run();refreshPreview.run();refreshChoices.run();});
        dialog.show();focusEditor(input,dialog);
    }
    private void export(List<Clip> selected,String day,boolean horizontal){
        if(exporting)return;if(selected.isEmpty()){toast("クリップを選択してください");return;}exporting=true;
        LinearLayout content=column();padded(content,24);content.setGravity(Gravity.CENTER);content.addView(new ProgressBar(this),lp(48,48));gap(content,16);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("動画を保存中").setView(content).setCancelable(false).create();dialog.show();
        String name="daylog_"+day+"_"+(horizontal?"landscape":"portrait")+"_"+LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        String captionDay=JournalRules.day(selected.get(0).capturedAt(),boundary(),ZoneId.of(selected.get(0).zoneId())).toString();
        exporter.export(selected,repository,name,repository.dayCaption(captionDay),prefs.getBoolean("showTime",true),new VideoExporter.Listener(){
            @Override public void saved(Uri uri){
                exporting=false;if(isDestroyed())return;dialog.dismiss();AlertDialog savedDialog=new AlertDialog.Builder(MainActivity.this).setTitle("保存しました").setMessage("Movies / Daylog に保存しました。")
                    .setPositiveButton("開く",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(RuntimeException e){toast("ギャラリーから動画を開けます");}}).setNegativeButton("閉じる",null).create();savedDialog.setOnDismissListener(d->resumeCamera());savedDialog.show();
            }
            @Override public void error(String message){exporting=false;if(!isDestroyed()){dialog.dismiss();toast(message);resumeCamera();}}
        });
    }

    private void renderSettingsBody(){
        root.removeAllViews();toolbar("設定",()->navigate(settingsReturn),false);
        LinearLayout content=scrollBody();
        LinearLayout format=preferenceRow("動画の向き",prefs.getBoolean("landscape",false)?"横":"縦");
        format.setContentDescription("動画の向き");
        format.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("動画の向き")
            .setSingleChoiceItems(new String[]{"縦","横"},prefs.getBoolean("landscape",false)?1:0,(dialog,which)->{
                prefs.edit().putBoolean("landscape",which==1).apply();dialog.dismiss();applyOrientation();renderSettingsBody();
                if(landscape()!=(which==1))toast("次の撮影日から適用します");
            })
            .setNegativeButton("キャンセル",null).show());
        content.addView(format);gap(content,1);
        String time=String.format(Locale.JAPAN,"%02d:%02d",boundary()/60,boundary()%60);
        LinearLayout day=preferenceRow("1日の切り替え時刻",time);day.setContentDescription("1日の切り替え時刻 "+time);
        day.setOnClickListener(v->new TimePickerDialog(this,(picker,h,m)->{prefs.edit().putInt("dayStart",h*60+m).apply();applyOrientation();renderSettingsBody();},boundary()/60,boundary()%60,true).show());
        content.addView(day);gap(content,24);
        content.addView(settingToggle("撮影後に編集",prefs.getBoolean("postCaptureEdit",true),()->{prefs.edit().putBoolean("postCaptureEdit",!prefs.getBoolean("postCaptureEdit",true)).apply();renderSettingsBody();}));gap(content,1);
        content.addView(settingToggle("位置情報を記録",locationEnabled(),this::toggleLocation));gap(content,1);
        content.addView(settingToggle("撮影時刻を表示",prefs.getBoolean("showTime",true),()->{prefs.edit().putBoolean("showTime",!prefs.getBoolean("showTime",true)).apply();renderSettingsBody();}));gap(content,1);
        content.addView(settingToggle("音声",sound(),this::toggleSound));gap(content,1);
        content.addView(settingToggle("グリッド",prefs.getBoolean("grid",true),()->{prefs.edit().putBoolean("grid",!prefs.getBoolean("grid",true)).apply();renderSettingsBody();}));gap(content,32);
        content.addView(bold("ストレージ",14,Ui.INK));gap(content,12);
        content.addView(text(repository.all().size()+"本 · "+String.format(Locale.JAPAN,"%.1f MB",repository.bytes()/1048576f),14,Ui.MUTED));gap(content,12);
        content.addView(text("アプリを削除すると、書き出していない動画も削除されます。",12,Ui.MUTED));
    }
    private LinearLayout preferenceRow(String title,String value){
        LinearLayout item=row();item.setPadding(dp(4),dp(12),dp(4),dp(12));Ui.touch(item,Ui.BG,8);
        TextView heading=text(title,16,Ui.INK);heading.setGravity(Gravity.CENTER_VERTICAL);item.addView(heading,new LinearLayout.LayoutParams(0,dp(40),1));
        TextView label=text(value,15,Ui.MUTED);label.setGravity(Gravity.CENTER_VERTICAL);item.addView(label,lp(-2,40));
        item.addView(new Ui.Icon(this,"chevron",Ui.MUTED,null,null),lp(40,40));return item;
    }
    private View settingToggle(String title,boolean checked,Runnable action){
        LinearLayout item=row();item.setPadding(dp(4),dp(8),dp(4),dp(8));Ui.touch(item,Ui.BG,8);
        item.addView(text(title,16,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);toggle.setChecked(checked);toggle.setContentDescription(title);
        toggle.setOnClickListener(v->action.run());item.addView(toggle,lp(52,48));item.setOnClickListener(v->action.run());return item;
    }
}
