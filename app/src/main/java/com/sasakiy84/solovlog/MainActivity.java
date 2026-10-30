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

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("daylog",MODE_PRIVATE);
        repository=ClipRepository.get(this);camera=new CameraEngine(this,this);exporter=new VideoExporter(this);
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
    @Override protected void onResume(){super.onResume();if(screen==Screen.CAMERA&&preview!=null&&!captureBusy)bindCamera();}
    @Override protected void onStop(){if(camera!=null&&camera.busy())stopCapture();super.onStop();}
    @Override protected void onDestroy(){if(camera!=null){camera.stop();camera.unbind();}if(!captureBusy)io.shutdown();super.onDestroy();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(!captureBusy)render();}
    @Override public void onSaveInstanceState(Bundle state){
        state.putString("screen",screen.name());state.putString("return",settingsReturn.name());
        state.putString("day",selectedDay.toString());state.putBoolean("journalLandscape",journalLandscape);super.onSaveInstanceState(state);
    }
    private int dp(float value){return Ui.dp(this,value);}
    private boolean landscape(){return prefs.getBoolean("landscape",false);}
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
        screen=next;render();
    }
    private void render(){
        cameraReady=false;preview=null;captureControls.clear();durationChips.clear();if(!camera.busy())camera.unbind();
        root=column();root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            root.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
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
        TextView v=bold(label,15,primary?Ui.BG:Ui.INK);v.setGravity(Gravity.CENTER);Ui.touch(v,primary?Ui.LIME:Ui.CARD,18);
        v.setPadding(dp(16),dp(12),dp(16),dp(12));v.setMinHeight(dp(48));v.setOnClickListener(view->action.run());return v;
    }
    private Ui.Icon icon(String kind,String label,Runnable action){return new Ui.Icon(this,kind,Ui.INK,label,action);}
    private void toast(String message){if(!isDestroyed())Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private LinearLayout toolbar(String title,Runnable back,boolean settings){
        LinearLayout bar=row();bar.setPadding(dp(12),dp(5),dp(12),dp(5));
        if(back!=null)bar.addView(icon("back","前の画面に戻る",back),lp(48,48));
        TextView label=bold(title,title.equals("daylog")?29:17,Ui.INK);label.setGravity(Gravity.CENTER_VERTICAL);
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
        Ui.AspectFrame frame=new Ui.AspectFrame(this,landscape());frame.setBackground(Ui.rounded(this,Ui.CARD,24));frame.setClipToOutline(true);
        stage.addView(frame,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        preview=new PreviewView(this);frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        if(prefs.getBoolean("grid",true))frame.addView(new Ui.Grid(this),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout overlay=row();overlay.setPadding(dp(16),dp(8),dp(8),dp(8));
        liveLabel=bold("●  STANDBY",11,Ui.INK);liveLabel.setLetterSpacing(.12f);liveLabel.setGravity(Gravity.CENTER_VERTICAL);
        liveLabel.setPadding(dp(10),0,dp(10),0);liveLabel.setBackground(Ui.rounded(this,0x99000000,16));overlay.addView(liveLabel,lp(-2,32));overlay.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        Ui.Icon audio=icon(sound()?"mic":"muted",sound()?"環境音をオフ":"環境音をオン",this::toggleSound);
        Ui.touch(audio,0x99000000,24);overlay.addView(audio,lp(48,48));captureControls.add(audio);frame.addView(overlay,new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP));
        TextView label=text(landscape()?"16:9   /   横で残す":"9:16   /   縦で残す",11,0xDDFFFFFF);
        label.setPadding(dp(10),dp(6),dp(10),dp(6));label.setBackground(Ui.rounded(this,0x99000000,14));FrameLayout.LayoutParams formatParams=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);formatParams.setMargins(dp(16),0,0,dp(16));frame.addView(label,formatParams);
        if(!granted(Manifest.permission.CAMERA)){
            LinearLayout prompt=column();prompt.setGravity(Gravity.CENTER);padded(prompt,22);prompt.setBackgroundColor(0xE620251D);
            prompt.addView(bold("今日の一瞬を。",24,Ui.INK));gap(prompt,12);
            TextView explanation=text("気になったものを、短く撮る。\nあとは、ひとつの記録に。",13,Ui.MUTED);explanation.setGravity(Gravity.CENTER);prompt.addView(explanation);gap(prompt,24);
            prompt.addView(button("カメラを使う",true,()->requestPermissions(false)),lp(-1,-2));frame.addView(prompt,new FrameLayout.LayoutParams(-1,-1));
        }
        LinearLayout controls=column();controls.setGravity(Gravity.CENTER);controls.setPadding(dp(12),dp(4),dp(12),dp(8));body.addView(controls,wide?lp(190,-1):lp(-1,-2));

        LinearLayout lengths=row();lengths.setGravity(Gravity.CENTER);
        for(int duration:new int[]{2,3,5}){
            TextView chip=bold(duration+" s",16,duration==selectedSeconds?Ui.BG:Ui.MUTED);chip.setGravity(Gravity.CENTER);
            chip.setBackground(Ui.rounded(this,duration==selectedSeconds?Ui.LIME:Ui.CARD,22));chip.setSelected(duration==selectedSeconds);chip.setContentDescription(duration+"秒を選択");chip.setFocusable(true);chip.setTag(duration);durationChips.add(chip);
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
        cameraHint=text("タップで"+selectedSeconds+"秒。今日のかけらに。",11,Ui.MUTED);cameraHint.setGravity(Gravity.CENTER);controls.addView(cameraHint,lp(-1,28));
        undo=button("",false,()->{
            if(lastSaved!=null&&!captureBusy)try{repository.delete(lastSaved.id());lastSaved=null;undo.setVisibility(View.GONE);cameraCount.setText(cameraSummary());}catch(RuntimeException e){toast("取り消せませんでした");}
        });undo.setVisibility(View.GONE);controls.addView(undo,lp(-1,44));
        shutter.setEnabled(false);preview.post(this::bindCamera);
    }
    private String cameraSummary(){List<Clip> list=clips(today(),landscape(),false);return today().format(DateTimeFormatter.ofPattern("MM.dd"))+"  /  "+list.size()+" clips";}
    private CameraEngine.Listener cameraListener(){
        return new CameraEngine.Listener(){
            @Override public void ready(){if(screen==Screen.CAMERA&&!isDestroyed()){cameraReady=true;if(shutter!=null)shutter.setEnabled(true);if(liveLabel!=null)liveLabel.setText("●  READY");}}
            @Override public void progress(float fraction,boolean stabilizing){
                if(isDestroyed()||screen!=Screen.CAMERA)return;
                shutter.state(true,fraction,selectedSeconds);
                liveLabel.setText(stabilizing?"●  STEADY":fraction>=1?"●  SAVE":"●  REC   "+String.format(Locale.US,"%02d",Math.max(1,(int)Math.ceil(selectedSeconds*(1-fraction)))));
                liveLabel.setTextColor(stabilizing?Ui.LIME:0xFFEAA38F);cameraHint.setText(stabilizing?"そのまま、少しだけ。":fraction>=1?"かけらを保存しています":"撮影中 · タップで途中まで保存");
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
    private void bindCamera(){if(preview==null||screen!=Screen.CAMERA||captureBusy||!granted(Manifest.permission.CAMERA))return;camera.bind(preview,landscape(),cameraListener());}
    private void startCapture(){
        if(!granted(Manifest.permission.CAMERA)){requestPermissions(false);return;}
        if(!cameraReady){toast("カメラを準備しています");return;}
        captureBusy=true;undo.setVisibility(View.GONE);for(View view:captureControls)view.setEnabled(false);
        shutter.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        camera.start(repository.file(UUID.randomUUID().toString()),selectedSeconds,sound(),cameraListener());
    }
    private void resetCaptureControls(){
        for(View view:captureControls)view.setEnabled(true);
        shutter.state(false,0,selectedSeconds);shutter.setEnabled(cameraReady);liveLabel.setText(cameraReady?"●  READY":"●  STANDBY");liveLabel.setTextColor(Ui.INK);
        cameraHint.setText("タップで"+selectedSeconds+"秒。今日のかけらに。");cameraCount.setText(cameraSummary());
        for(TextView chip:durationChips){boolean selected=(Integer)chip.getTag()==selectedSeconds;chip.setSelected(selected);chip.setTextColor(selected?Ui.BG:Ui.MUTED);chip.setBackground(Ui.rounded(this,selected?Ui.LIME:Ui.CARD,22));}
    }
    private void stopCapture(){camera.stop();if(!camera.busy()){captureBusy=false;if(!isDestroyed()&&screen==Screen.CAMERA)render();}}
    private void saveCapture(File file,long capturedAt){
        int planned=selectedSeconds;String zone=ZoneId.systemDefault().getId();
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
                Clip clip=new Clip(file.getName().replace(".mp4",""),capturedAt,duration,planned,width>height,true,zone);
                repository.add(clip);lastSaved=clip;
                main.post(()->{
                    captureBusy=false;if(isDestroyed()){io.shutdown();return;}
                    if(screen==Screen.CAMERA){resetCaptureControls();undo.setText(seconds(JournalRules.exportDuration(clip))+"秒、残しました。    取り消す");undo.setVisibility(View.VISIBLE);TextView currentUndo=undo;main.postDelayed(()->currentUndo.setVisibility(View.GONE),5000);}
                    toast("保存しました");
                });
            }catch(Exception e){main.post(()->{captureBusy=false;if(!isDestroyed()){render();toast("クリップを保存できませんでした。空き容量を確認してください。");}});}
        });
    }
    private void requestPermissions(boolean audioOnly){
        String permission=audioOnly?Manifest.permission.RECORD_AUDIO:Manifest.permission.CAMERA,asked=audioOnly?"askedAudio":"askedCamera";
        if(prefs.getBoolean(asked,false)&&!granted(permission)&&!shouldShowRequestPermissionRationale(permission)){
            new AlertDialog.Builder(this).setTitle(audioOnly?"マイクの権限":"カメラの権限").setMessage("端末のアプリ設定で権限を許可できます。")
                .setPositiveButton("設定を開く",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("今はしない",null).show();return;
        }
        Runnable launch=()->{prefs.edit().putBoolean(asked,true).putBoolean("askedAudio",true).apply();if(audioOnly)prefs.edit().putBoolean("sound",true).apply();permissions.launch(audioOnly?new String[]{Manifest.permission.RECORD_AUDIO}:new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO});};
        if(shouldShowRequestPermissionRationale(permission))new AlertDialog.Builder(this).setTitle(audioOnly?"その場の音も、一緒に。":"一瞬を撮るために").setMessage(audioOnly?"環境音を残すためにマイクを使います。オフでも撮影できます。":"プレビューと撮影にカメラを使います。マイクを許可すると環境音も残せます。")
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
            TextView chip=bold(date.format(DateTimeFormatter.ofPattern("MM.dd"))+"\n"+(date.equals(today())?"今日":date.format(DateTimeFormatter.ofPattern("EEE",Locale.ENGLISH))),14,date.equals(selectedDay)?Ui.BG:Ui.MUTED);
            chip.setGravity(Gravity.CENTER);chip.setLineSpacing(dp(6),1);Ui.touch(chip,date.equals(selectedDay)?Ui.LIME:Ui.CARD,18);
            LinearLayout.LayoutParams params=lp(74,76);params.setMargins(0,0,dp(9),0);dateRow.addView(chip,params);chip.setOnClickListener(v->{selectedDay=date;render();});
        }
        content.addView(dates,lp(-1,80));gap(content,20);
        LinearLayout formats=row();
        for(boolean horizontal:new boolean[]{false,true}){
            int count=clips(selectedDay,horizontal,false).size();TextView chip=button((horizontal?"横":"縦")+"  "+count,journalLandscape==horizontal,()->{journalLandscape=horizontal;render();});
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1);params.setMargins(horizontal?dp(5):0,0,horizontal?0:dp(5),0);formats.addView(chip,params);
        }
        content.addView(formats);gap(content,18);
        List<Clip> list=clips(selectedDay,journalLandscape,false),selected=clips(selectedDay,journalLandscape,true);
        long total=selected.stream().mapToLong(JournalRules::exportDuration).sum();
        LinearLayout hero=column();padded(hero,22);hero.setBackground(Ui.rounded(this,Ui.LIME,24));
        hero.addView(text(selectedDay.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))+"   /   "+(journalLandscape?"16:9":"9:16"),12,0xFF536342));gap(hero,12);
        hero.addView(bold(list.isEmpty()?"まだ、まっさらな一日。":"今日を、ひとつに。",23,Ui.BG));gap(hero,8);
        hero.addView(text(selected.size()+" clips     /     "+seconds(total)+" sec",14,0xFF536342));gap(hero,20);
        TextView save=button(list.isEmpty()?"最初の一瞬を撮る":"この日の動画を保存  ↗",false,()->{if(list.isEmpty())navigate(Screen.CAMERA);else export(selected,selectedDay.toString(),journalLandscape);});
        save.setBackground(Ui.rounded(this,Ui.BG,14));hero.addView(save,lp(-1,52));content.addView(hero);gap(content,24);
        LinearLayout heading=row();heading.addView(bold("この日のかけら",15,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));heading.addView(text("✓ を外すと動画から除外",10,Ui.MUTED));content.addView(heading);gap(content,14);
        if(list.isEmpty())content.addView(text("目にとまった光、食べたもの、帰り道。\nひとつだけでも、今日の記録に。",14,Ui.MUTED));
        int columns=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?3:2;
        for(int index=0;index<list.size();index+=columns){
            LinearLayout cards=row();cards.setGravity(Gravity.TOP);
            for(int n=0;n<columns;n++){
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(n==0?0:dp(10),0,0,dp(14));
                cards.addView(index+n<list.size()?clipCard(list.get(index+n)):new View(this),params);
            }
            content.addView(cards);
        }
        TextView capture=button("＋   もうひとつ、撮る",true,()->navigate(Screen.CAMERA));LinearLayout.LayoutParams params=lp(-1,54);params.setMargins(dp(22),dp(8),dp(22),dp(12));root.addView(capture,params);
    }
    private View clipCard(Clip clip){
        LinearLayout card=column();FrameLayout frame=new FrameLayout(this);frame.setBackground(Ui.rounded(this,Ui.CARD,18));frame.setClipToOutline(true);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        frame.setContentDescription("クリップを再生 "+clipTime(clip));frame.setFocusable(true);frame.setOnClickListener(v->review(clip));card.addView(frame,lp(-1,journalLandscape?124:188));
        Ui.Icon check=new Ui.Icon(this,clip.included()?"check":"add",clip.included()?Ui.BG:Ui.INK,clip.included()?"このクリップを動画から除外":"このクリップを動画に含める",()->{repository.include(clip.id(),!clip.included());render();});
        check.setBackground(Ui.rounded(this,clip.included()?Ui.LIME:0xCC20251D,22));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP|Gravity.END);cp.setMargins(dp(6),dp(6),dp(6),0);frame.addView(check,cp);
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
    private void review(Clip clip){
        LinearLayout content=column();padded(content,16);VideoView video=new VideoView(this);content.addView(video,lp(-1,getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?180:330));
        MediaController controller=new MediaController(this);controller.setAnchorView(video);video.setMediaController(controller);video.setVideoPath(repository.file(clip.id()).getAbsolutePath());
        video.setOnPreparedListener(player->{player.setLooping(true);video.start();});video.setOnErrorListener((p,w,e)->{toast("動画を再生できませんでした");return true;});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(clipTime(clip)+"  /  "+seconds(clip.durationMs())+"秒").setView(content).setPositiveButton("閉じる",null).create();
        gap(content,12);content.addView(button("このクリップを保存",true,()->{dialog.dismiss();export(List.of(clip),"clip",clip.landscape());}),lp(-1,52));gap(content,8);
        content.addView(button("クリップを削除",false,()->{
            dialog.dismiss();new AlertDialog.Builder(this).setTitle("このクリップを削除しますか？").setMessage("元の動画は元に戻せません。書き出し済みの動画は残ります。").setNegativeButton("残す",null)
                .setPositiveButton("削除",(d,w)->{try{repository.delete(clip.id());thumbnails.remove(clip.id());render();}catch(RuntimeException e){toast("削除できませんでした");}}).show();
        }),lp(-1,48));dialog.setOnDismissListener(d->video.stopPlayback());dialog.show();
    }
    private void export(List<Clip> selected,String day,boolean horizontal){
        if(exporting)return;if(selected.isEmpty()){toast("✓ を付けてクリップを選んでください");return;}exporting=true;
        LinearLayout content=column();padded(content,24);content.setGravity(Gravity.CENTER);content.addView(new ProgressBar(this),lp(48,48));gap(content,16);content.addView(text("撮った順に、ひとつの動画へ。",14,Ui.INK));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("一日を、まとめています").setView(content).setCancelable(false).create();dialog.show();
        String name="daylog_"+day+"_"+(horizontal?"landscape":"portrait")+"_"+LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        exporter.export(selected,repository,name,new VideoExporter.Listener(){
            @Override public void saved(Uri uri){
                exporting=false;if(isDestroyed())return;dialog.dismiss();new AlertDialog.Builder(MainActivity.this).setTitle("今日が、一本になりました。").setMessage("ギャラリーの Movies / Daylog に保存しました。元のクリップも残っています。")
                    .setPositiveButton("動画を見る",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(RuntimeException e){toast("ギャラリーから動画を開けます");}}).setNegativeButton("閉じる",null).show();
            }
            @Override public void error(String message){exporting=false;if(!isDestroyed()){dialog.dismiss();toast(message);}}
        });
    }

    private void renderSettingsBody(){
        root.removeAllViews();toolbar("記録の設定",()->navigate(settingsReturn),false);
        LinearLayout content=scrollBody();content.addView(bold("自分の一日に、\n合わせる。",29,Ui.INK));gap(content,28);content.addView(text("記録のかたち",12,Ui.MUTED));gap(content,12);
        LinearLayout formats=row();
        for(boolean horizontal:new boolean[]{false,true}){
            LinearLayout card=column();card.setGravity(Gravity.CENTER);padded(card,16);card.setBackground(Ui.outlined(this,landscape()==horizontal?0xFF29331F:Ui.CARD,landscape()==horizontal?Ui.LIME:Ui.LINE,20));
            card.addView(new Ui.Icon(this,horizontal?"landscape":"portrait",landscape()==horizontal?Ui.LIME:Ui.MUTED,null,null),lp(52,52));gap(card,10);TextView formatCaption=bold(horizontal?"横  16:9":"縦  9:16",15,Ui.INK);formatCaption.setGravity(Gravity.CENTER);card.addView(formatCaption);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(132),1);params.setMargins(horizontal?dp(6):0,0,horizontal?0:dp(6),0);formats.addView(card,params);
            card.setFocusable(true);card.setContentDescription(horizontal?"横向きで撮影":"縦向きで撮影");card.setSelected(landscape()==horizontal);
            card.setOnClickListener(v->{prefs.edit().putBoolean("landscape",horizontal).apply();journalLandscape=horizontal;applyOrientation();renderSettingsBody();});
        }
        content.addView(formats);gap(content,12);content.addView(text("縦と横は、別々の動画にまとめます。",12,Ui.MUTED));gap(content,28);
        content.addView(text("一日の区切り",12,Ui.MUTED));gap(content,12);
        LinearLayout day=row();padded(day,18);Ui.touch(day,Ui.CARD,20);day.addView(new Ui.Icon(this,"moon",Ui.LIME,null,null),lp(40,40));
        LinearLayout copy=column();copy.addView(bold("一日のはじまり",15,Ui.INK));gap(copy,5);copy.addView(text("この時刻までは、前日の記録に。",11,Ui.MUTED));day.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        day.addView(bold(String.format(Locale.JAPAN,"%02d:%02d",boundary()/60,boundary()%60),24,Ui.LIME));
        day.setContentDescription("一日のはじまり "+String.format(Locale.JAPAN,"%02d:%02d",boundary()/60,boundary()%60));
        day.setOnClickListener(v->new TimePickerDialog(this,(picker,h,m)->{prefs.edit().putInt("dayStart",h*60+m).apply();renderSettingsBody();},boundary()/60,boundary()%60,true).show());
        content.addView(day);gap(content,10);content.addView(text("変更すると、これまでの記録も振り分け直します。",11,Ui.MUTED));gap(content,28);
        content.addView(text("撮影",12,Ui.MUTED));gap(content,12);
        content.addView(settingToggle("環境音を残す","その場の空気も、一緒に。",sound(),this::toggleSound));gap(content,10);
        content.addView(settingToggle("構図のグリッド","余白をそろえて、静かな映像に。",prefs.getBoolean("grid",true),()->{prefs.edit().putBoolean("grid",!prefs.getBoolean("grid",true)).apply();renderSettingsBody();}));gap(content,28);
        LinearLayout storage=column();padded(storage,20);storage.setBackground(Ui.outlined(this,Ui.BG,Ui.LINE,20));storage.addView(bold("この端末に、残す。",18,Ui.INK));gap(storage,10);
        storage.addView(text(repository.all().size()+" clips  /  "+String.format(Locale.JAPAN,"%.1f MB",repository.bytes()/1048576f),13,Ui.LIME));gap(storage,12);
        storage.addView(text("クリップはアプリ内に保存されます。\n残したい一日は「動画を保存」でギャラリーへ。\nアプリを削除すると、未書き出しのクリップも削除されます。",12,Ui.MUTED));content.addView(storage);gap(content,28);
        TextView signature=text("daylog   /   small moments, kept.",11,Ui.MUTED);signature.setGravity(Gravity.CENTER);content.addView(signature);
    }
    private View settingToggle(String title,String description,boolean checked,Runnable action){
        LinearLayout item=row();padded(item,18);Ui.touch(item,Ui.CARD,20);LinearLayout copy=column();copy.addView(bold(title,15,Ui.INK));gap(copy,5);copy.addView(text(description,11,Ui.MUTED));item.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);toggle.setChecked(checked);toggle.setContentDescription(title);toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(checked?Ui.LIME:Ui.MUTED));
        toggle.setOnClickListener(v->action.run());item.addView(toggle,lp(52,48));item.setOnClickListener(v->action.run());return item;
    }
}
