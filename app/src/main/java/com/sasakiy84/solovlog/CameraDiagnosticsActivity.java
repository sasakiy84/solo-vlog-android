package com.sasakiy84.solovlog;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** An offline diagnosis can also select a working capture route without installing another APK. */
public final class CameraDiagnosticsActivity extends ComponentActivity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<CameraProfiles.Route> routes=new ArrayList<>();
    private final List<String> labels=new ArrayList<>();
    private final Map<String,Boolean> recordings=new HashMap<>();
    private LinearLayout root,body;
    private FrameLayout stage;
    private TextView status,choice,record,play,use,batch,share,copy,reset,custom,rotationControl;
    private ZoomControls zoom;
    private CameraEngine engine;
    private JSONObject report;
    private JSONObject attempt;
    private File trial;
    private VideoView playbackVideo;
    private int selected,generation;
    private boolean foreground,loading=true,ready,recording,batching,finished,correctionPending;
    private final Runnable timeout=()->finishAttempt(false,"応答待ちの時間を超えました");
    private final androidx.activity.result.ActivityResultLauncher<String> permission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{if(granted)scan();else failedScan("カメラの権限が必要です");});
    @Override public void onCreate(Bundle state){
        super.onCreate(state);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);build();
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)scan();else permission.launch(Manifest.permission.CAMERA);
    }
    private void build(){
        root=column();root.setBackgroundColor(Ui.BG);setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{var bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        LinearLayout header=row();header.addView(new Ui.Icon(this,"back",Ui.INK,"戻る",this::finish),params(48,48));TextView title=Ui.text(this,"カメラ診断",22,Ui.INK);title.setGravity(Gravity.CENTER_VERTICAL);header.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));root.addView(header,params(-1,56));
        ScrollView scroll=new ScrollView(this);body=column();body.setPadding(dp(20),dp(8),dp(20),dp(20));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        choice=button("カメラを読み込み中",()->choose());choice.setMinHeight(dp(56));body.addView(choice,params(-1,-2));gap(12);
        boolean wide=getSharedPreferences("daylog",0).getBoolean("landscape",false);Ui.AspectFrame frame=new Ui.AspectFrame(this,wide);stage=new FrameLayout(this);frame.addView(stage,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout centered=row();centered.setGravity(Gravity.CENTER);centered.addView(frame,params(-1,wide?220:320));body.addView(centered);gap(10);
        status=Ui.text(this,"カメラ情報を読み込み中",14,Ui.INK);status.setMinHeight(dp(44));body.addView(status,params(-1,-2));
        pair(record=button("2秒撮影",this::recordNow),play=button("録画を再生",this::play));
        gap(10);use=button("このカメラを使う",this::apply);Ui.touch(use,Ui.INK,10);use.setTextColor(Ui.BG);body.addView(use,params(-1,48));
        gap(12);batch=button("まとめて確認",()->{if(batching){cancel();status.setText("確認を中止しました");}else{batching=true;open(0,true);}});body.addView(batch,params(-1,48));
        gap(12);pair(share=button("結果を共有",this::share),copy=button("結果をコピー",()->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("カメラ診断",reportText()));toast("コピーしました");}));
        gap(12);custom=button("IDを指定",this::manualRoute);body.addView(custom,params(-1,48));
        gap(12);rotationControl=button("映像の補正",this::correctionSettings);body.addView(rotationControl,params(-1,48));
        gap(12);reset=button("自動に戻す",()->{try{CameraProfiles.select(this,null);toast("自動に戻しました");finish();}catch(Exception e){toast("設定を保存できませんでした");}});body.addView(reset,params(-1,48));
        update();
    }
    private void scan(){
        loading=true;update();io.execute(()->{
            try{
                CameraProfiles.Scan scan=CameraProfiles.scan(this);List<String> names=new ArrayList<>();
                for(CameraProfiles.Route route:scan.routes()){
                    try{float scale=CameraProfiles.scale(getSystemService(android.hardware.camera2.CameraManager.class),route);names.add(route.label()+String.format(java.util.Locale.JAPAN," · 推定%.2f×",scale));}
                    catch(Exception error){names.add(route.label()+" · 倍率不明");}
                }
                main.post(()->{
                    if(isDestroyed())return;report=scan.report();
                    try{
                        String previous=getSharedPreferences("daylog",0).getString("cameraDiagnostics","");
                        if(!previous.isEmpty()){JSONObject old=new JSONObject(previous);if(old.optString("appVersion").equals(report.optString("appVersion"))){JSONArray attempts=old.optJSONArray("attempts")==null?new JSONArray():old.getJSONArray("attempts");report.put("attempts",attempts);for(int n=0;n<attempts.length();n++){JSONObject item=attempts.getJSONObject(n);if(item.has("success"))recordings.put(item.getString("route"),item.getBoolean("success"));}}}
                        report.put("captureQuality",getSharedPreferences("daylog",0).getString("captureQuality","auto"));report.put("landscape",getSharedPreferences("daylog",0).getBoolean("landscape",false));
                    }catch(Exception e){android.util.Log.w("DaylogDiagnosis","Previous report unavailable",e);}
                    routes.clear();labels.clear();routes.add(null);labels.add("自動（CameraX）");routes.addAll(scan.routes());labels.addAll(names);
                    CameraProfiles.Route saved=CameraProfiles.selected(this);if(saved!=null&&!routes.contains(saved)){routes.add(saved);labels.add(saved.label()+" · 現在の指定");}
                    loading=false;persist();cameraXInfo();update();if(foreground){int preferred=routes.indexOf(CameraProfiles.selected(this));open(Math.max(0,preferred),false);}
                });
            }catch(Exception e){main.post(()->{if(!isDestroyed())failedScan("カメラ情報を取得できませんでした: "+e);});}
        });
    }
    private void failedScan(String message){
        loading=false;report=new JSONObject();put(report,"manufacturer",android.os.Build.MANUFACTURER);put(report,"model",android.os.Build.MODEL);put(report,"sdk",android.os.Build.VERSION.SDK_INT);
        put(report,"scanError",message);put(report,"attempts",new JSONArray());put(report,"cameras",new JSONArray());
        routes.clear();labels.clear();routes.add(null);labels.add("自動（CameraX）");selected=0;
        choice.setText(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED?"カメラを選択":"カメラを許可");status.setText(message);persist();update();
    }
    private void cameraXInfo(){
        var future=ProcessCameraProvider.getInstance(this);future.addListener(()->{
            if(isDestroyed()||report==null)return;
            try{JSONArray cameras=new JSONArray();for(var info:future.get().getAvailableCameraInfos()){
                    ZoomState state=info.getZoomState().getValue();JSONObject camera=new JSONObject().put("id",Camera2CameraInfo.from(info).getCameraId()).put("lensFacing",info.getLensFacing());
                    if(state!=null)camera.put("minZoom",state.getMinZoomRatio()).put("maxZoom",state.getMaxZoomRatio());
                    JSONArray physical=new JSONArray();for(var child:info.getPhysicalCameraInfos())physical.put(Camera2CameraInfo.from(child).getCameraId());camera.put("physicalIds",physical);cameras.put(camera);
                }report.put("cameraX",cameras);persist();
            }catch(Exception e){put(report,"cameraXError",e.toString());persist();}
        },ContextCompat.getMainExecutor(this));
    }
    private String key(int index){CameraProfiles.Route route=routes.get(index);return route==null?"auto":route.key();}
    private void choose(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){permission.launch(Manifest.permission.CAMERA);return;}
        if(loading||recording||batching||routes.isEmpty())return;
        String[] choices=new String[labels.size()];for(int i=0;i<labels.size();i++)choices[i]=labels.get(i)+(Boolean.TRUE.equals(recordings.get(key(i)))?" · 撮影OK":"");
        new AlertDialog.Builder(this).setTitle("撮影カメラ").setSingleChoiceItems(choices,selected,(dialog,which)->{dialog.dismiss();open(which,false);}).setNegativeButton("閉じる",null).show();
    }
    private void open(int index,boolean automaticRecording){
        release();selected=index;ready=false;recording=false;finished=false;choice.setText(labels.get(index));status.setText(batching?"確認中 "+(index+1)+" / "+routes.size():"カメラを準備中");
        int token=generation;attempt=new JSONObject();put(attempt,"route",key(index));if(routes.get(index)!=null)try{put(attempt,"configuration",routes.get(index).json());}catch(Exception e){android.util.Log.w("DaylogDiagnosis","Invalid route configuration",e);}put(attempt,"startedAt",System.currentTimeMillis());put(attempt,"events",new JSONArray());
        try{JSONArray attempts=report.getJSONArray("attempts");while(attempts.length()>=32)attempts.remove(0);attempts.put(attempt);}catch(Exception e){android.util.Log.w("DaylogDiagnosis","Cannot append camera attempt",e);}persist();
        JSONObject current=attempt;CameraPreview preview=new CameraPreview(this,routes.get(index),true);stage.removeAllViews();stage.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        if(zoom!=null)body.removeView(zoom);zoom=new ZoomControls(this,false,ratio->{if(engine!=null)engine.setZoom(ratio);});zoom.setBackground(Ui.rounded(this,Ui.INK,10));
        body.addView(zoom,3,params(-1,144));
        engine=new CameraEngine(this,this);CameraEngine currentEngine=engine;
        main.postDelayed(timeout,45000);update();
        currentEngine.bind(preview,getSharedPreferences("daylog",0).getBoolean("landscape",false),new CameraEngine.Listener(){
            public void ready(){if(!valid(token)||finished)return;ready=true;zoom.setAvailable(true);put(current,"preview","ready");persist();status.setText(routes.get(index)!=null&&routes.get(index).recordOnly()?"互換モード：録画中はプレビュー停止":"プレビュー表示中");update();if(automaticRecording&&!recording)recordNow();else if(!automaticRecording)main.removeCallbacks(timeout);}
            public void binding(){if(valid(token)){ready=false;zoom.setAvailable(false);update();}}
            public void zoomChanged(ZoomState state){if(valid(token))zoom.update(state);}
            public void zoomError(String message){if(valid(token))status.setText(message);}
            public void progress(float fraction,boolean stabilizing){if(valid(token))setStatus(stabilizing?"撮影準備中":"撮影中");}
            public void saved(File file,long at){if(!valid(token)||finished){file.delete();return;}verify(file,token);}
            public void error(String message){if(valid(token))finishAttempt(false,message);}
            public void diagnostic(String step,String detail){event(current,step,detail);}
        });
    }
    private boolean valid(int token){return token==generation&&foreground&&!isDestroyed();}
    private void recordNow(){
        if(!ready&&!recording&&engine==null&&finished){open(selected,true);return;}
        if(!ready||recording||engine==null)return;
        try{if(trial!=null){trial.delete();trial=null;}trial=File.createTempFile("camera-diagnosis-",".mp4",getCacheDir());trial.delete();recording=true;if(zoom!=null)zoom.setAvailable(false);main.removeCallbacks(timeout);main.postDelayed(timeout,45000);update();
            engine.start(trial,2,false,null,listenerForRecording());
        }catch(Exception e){finishAttempt(false,"確認用の動画を作成できませんでした");}
    }
    // CameraEngine uses the capture listener separately from the binding listener.
    private CameraEngine.Listener listenerForRecording(){
        int token=generation;JSONObject current=attempt;
        return new CameraEngine.Listener(){
            public void ready(){}
            public void progress(float fraction,boolean stabilizing){if(valid(token))setStatus(stabilizing?"撮影準備中":"撮影中");}
            public void saved(File file,long at){if(!valid(token)||finished){file.delete();return;}verify(file,token);}
            public void error(String message){if(valid(token))finishAttempt(false,message);}
            public void diagnostic(String step,String detail){event(current,step,detail);}
        };
    }
    private void verify(File file,int token){
        if(engine!=null){engine.unbind();engine=null;}ready=false;status.setText("録画を確認中");update();
        io.execute(()->{
            JSONObject result=new JSONObject();boolean success=false;String error="";
            try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
                media.setDataSource(file.getAbsolutePath());long duration=Long.parseLong(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                Bitmap frame=media.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);boolean decoded=frame!=null;if(frame!=null)frame.recycle();
                result.put("durationMs",duration).put("width",media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)).put("height",media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                    .put("rotation",media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)).put("bytes",file.length()).put("decodedFrame",decoded);
                success=duration>=1800&&decoded;if(!success)error=duration<1800?String.format(java.util.Locale.JAPAN,"録画が短すぎました（%.2f秒）",duration/1000f):"録画した映像を読み込めませんでした";
            }catch(Exception e){error=e.toString();}
            boolean ok=success;String detail=error;
            main.post(()->{if(!valid(token)||finished){file.delete();return;}put(attempt,"recording",result);finishAttempt(ok,ok?"2秒の録画を確認しました":detail);});
        });
    }
    private void finishAttempt(boolean success,String message){
        if(finished||routes.isEmpty())return;finished=true;
        main.removeCallbacks(timeout);recording=false;ready=false;recordings.put(key(selected),success);put(attempt,"success",success);put(attempt,"result",message);persist();
        if(engine!=null){engine.stop();engine.unbind();engine=null;}if(zoom!=null)zoom.setAvailable(false);status.setText(message);update();
        if(batching){if(selected+1<routes.size())main.postDelayed(()->{if(batching&&foreground)open(selected+1,true);},500);else{batching=false;status.setText("確認が終わりました。カメラを選択して比較できます。");update();}}
    }
    private void manualRoute(){
        if(loading||batching||recording)return;
        LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText camera=new EditText(this);camera.setSingleLine(true);camera.setHint("カメラID");camera.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});form.addView(camera,params(-1,64));
        EditText physical=new EditText(this);physical.setSingleLine(true);physical.setHint("物理レンズID（任意）");physical.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});form.addView(physical,params(-1,64));
        Switch cameraX=new Switch(this);cameraX.setText("CameraXで試す");form.addView(cameraX,params(-1,48));
        Switch single=new Switch(this);single.setText("互換モード（録画中はプレビュー停止）");form.addView(single,params(-1,56));cameraX.setOnCheckedChangeListener((v,on)->{single.setEnabled(!on);if(on)single.setChecked(false);});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("カメラIDを指定").setView(form).setPositiveButton("試す",null).setNegativeButton("キャンセル",null).create();dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String id=camera.getText().toString().strip(),child=physical.getText().toString().strip();if(id.isEmpty()){camera.setError("IDを入力してください");return;}
            CameraProfiles.Route route=new CameraProfiles.Route(id,child.isEmpty()?null:child,cameraX.isChecked(),single.isChecked());int index=routes.indexOf(route);
            if(index<0){routes.add(route);labels.add(route.label()+" · 手動指定");index=routes.size()-1;}dialog.dismiss();open(index,false);
        });
    }
    private void correctionSettings(){
        if(correctionPending||recording||batching||routes.isEmpty()||routes.get(selected)==null||routes.get(selected).cameraX())return;
        CameraProfiles.Route route=routes.get(selected);int token=generation;correctionPending=true;update();
        io.execute(()->{int[] modes=null;try{modes=getSystemService(android.hardware.camera2.CameraManager.class).getCameraCharacteristics(route.physicalId()==null?route.cameraId():route.physicalId()).get(android.hardware.camera2.CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES);}catch(Exception error){android.util.Log.w("DaylogDiagnosis","Lens correction capabilities unavailable",error);}
            int[] supported=modes==null?new int[0]:modes;main.post(()->{if(valid(token)){correctionPending=false;update();showCorrectionSettings(route,supported);}});
        });
    }
    private void showCorrectionSettings(CameraProfiles.Route route,int[] supported){
        int[] angles={route.previewCorrection(),route.videoCorrection(),route.lensCorrection()};
        LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(8));
        String[] values={"補正なし","右90°","180°","左90°"};TextView preview=button("プレビュー · "+values[angles[0]/90],()->{}),video=button("保存動画 · "+values[angles[1]/90],()->{});
        form.addView(preview,params(-1,56));form.addView(video,params(-1,56));
        for(int index=0;index<2;index++){int field=index;TextView control=index==0?preview:video;control.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(field==0?"プレビュー":"保存動画").setSingleChoiceItems(values,angles[field]/90,(d,which)->{angles[field]=which*90;control.setText((field==0?"プレビュー":"保存動画")+" · "+values[which]);d.dismiss();}).setNegativeButton("キャンセル",null).show());}
        java.util.List<Integer> lensModes=new ArrayList<>();java.util.List<String> lensNames=new ArrayList<>();lensModes.add(-1);lensNames.add("自動");
        for(int mode:new int[]{0,1,2})if(java.util.Arrays.stream(supported).anyMatch(value->value==mode)){lensModes.add(mode);lensNames.add(mode==0?"オフ":mode==1?"標準":"高品質");}
        if(!lensModes.contains(angles[2]))angles[2]=-1;
        if(lensModes.contains(1)||lensModes.contains(2)){TextView lens=button("レンズ補正 · "+lensNames.get(lensModes.indexOf(angles[2])),()->{});form.addView(lens,params(-1,56));
            lens.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("レンズ補正").setSingleChoiceItems(lensNames.toArray(new String[0]),lensModes.indexOf(angles[2]),(dialog,which)->{angles[2]=lensModes.get(which);lens.setText("レンズ補正 · "+lensNames.get(which));dialog.dismiss();}).setNegativeButton("キャンセル",null).show());
        }
        new AlertDialog.Builder(this).setTitle("映像の補正").setView(form).setPositiveButton("保存",(dialog,which)->{
            CameraProfiles.Route changed=route.withCorrection(angles[0],angles[1],angles[2]);
            try{CameraProfiles.Route saved=CameraProfiles.selected(this);if(saved!=null&&saved.key().equals(route.key()))CameraProfiles.select(this,changed);
                routes.set(selected,changed);open(selected,false);toast("保存しました");
            }catch(Exception error){toast("映像の補正を保存できませんでした");}
        }).setNegativeButton("キャンセル",null).show();
    }
    private void apply(){
        if(!Boolean.TRUE.equals(recordings.get(key(selected))))return;
        try{CameraProfiles.select(this,routes.get(selected));toast("撮影カメラを変更しました");finish();}catch(Exception e){toast("設定を保存できませんでした");}
    }
    private void play(){
        if(trial==null||!trial.isFile())return;
        boolean wide=getSharedPreferences("daylog",0).getBoolean("landscape",false);JSONObject metadata=attempt==null?null:attempt.optJSONObject("recording");
        if(metadata!=null&&metadata.optInt("width")>0&&metadata.optInt("height")>0){wide=metadata.optInt("width")>metadata.optInt("height");if(metadata.optInt("rotation")%180!=0)wide=!wide;}
        Ui.AspectFrame frame=new Ui.AspectFrame(this,wide);VideoView video=new VideoView(this);playbackVideo=video;frame.addView(video,new FrameLayout.LayoutParams(-1,-1));
        int token=generation;JSONObject current=attempt;String routeKey=key(selected);
        video.setOnInfoListener((player,what,extra)->{if(what==android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START&&player.getDuration()>=1800&&valid(token)){recordings.put(routeKey,true);put(current,"playbackRendered",true);put(current,"success",true);put(current,"result","動画の再生を確認しました");persist();status.setText("動画の再生を確認しました");update();}return false;});
        video.setOnErrorListener((player,what,extra)->{event(current,"playbackError",what+" / "+extra);toast("動画を再生できませんでした");return true;});
        video.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE);video.setOnPreparedListener(player->{player.setVolume(0,0);player.setLooping(true);video.start();});video.setVideoPath(trial.getAbsolutePath());
        LinearLayout playback=column();playback.setGravity(Gravity.CENTER_HORIZONTAL);playback.addView(frame,params(-1,wide?220:360));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("確認した録画").setView(playback).setPositiveButton("閉じる",null).create();dialog.setOnDismissListener(d->{video.stopPlayback();if(playbackVideo==video)playbackVideo=null;});dialog.show();
    }
    private void share(){
        String text;try{text=report.toString(2);}catch(Exception e){text=reportText();}String sharedText=text;share.setEnabled(false);
        io.execute(()->{try{
            File directory=new File(getCacheDir(),"camera-reports");if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Cannot create report directory");
            File file=File.createTempFile("camera-",".json",directory);java.nio.file.Files.write(file.toPath(),sharedText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            main.post(()->{if(isDestroyed())return;update();try{
                android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".camera-reports",file);
                Intent send=new Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_SUBJECT,"カメラ診断").putExtra(Intent.EXTRA_STREAM,uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                send.setClipData(ClipData.newRawUri("カメラ診断",uri));
                startActivity(Intent.createChooser(send,"診断結果を共有"));
            }catch(RuntimeException e){toast("結果をコピーして共有してください");}});
        }catch(Exception e){main.post(()->{if(!isDestroyed()){update();toast("結果を共有できませんでした。コピーをお使いください");}});}});
    }
    private String reportText(){
        try{
            if(report==null)return "カメラ情報を取得できていません";
            JSONObject shared=new JSONObject(report.toString());JSONArray attempts=shared.getJSONArray("attempts");String text=shared.toString(2);
            int omitted=0;while(text.length()>100000&&attempts.length()>1){attempts.remove(0);shared.put("omittedOlderAttempts",++omitted);text=shared.toString(2);}return text;
        }catch(Exception e){return String.valueOf(report);}
    }
    private void persist(){if(report!=null)getSharedPreferences("daylog",0).edit().putString("cameraDiagnostics",report.toString()).apply();}
    private void event(JSONObject current,String step,String detail){
        if(current==null)return;try{JSONArray events=current.getJSONArray("events");if(events.length()<32)events.put(new JSONObject().put("step",step).put("detail",detail.length()>1000?detail.substring(0,1000):detail));persist();}catch(Exception e){android.util.Log.w("DaylogDiagnosis","Cannot save camera event",e);}
    }
    private void update(){
        if(choice==null)return;boolean blocked=loading||recording||batching;
        choice.setEnabled(!blocked&&!routes.isEmpty());custom.setEnabled(!blocked);rotationControl.setEnabled(!correctionPending&&!blocked&&!routes.isEmpty()&&routes.get(selected)!=null&&!routes.get(selected).cameraX());record.setEnabled(!blocked&&!routes.isEmpty()&&(ready||finished));play.setEnabled(!blocked&&trial!=null&&trial.isFile()&&trial.length()>0);
        use.setEnabled(!blocked&&!routes.isEmpty()&&Boolean.TRUE.equals(recordings.get(key(selected))));use.setAlpha(use.isEnabled()?1:.4f);
        batch.setEnabled(!loading&&!routes.isEmpty()&&(!recording||batching));batch.setText(batching?"確認を中止":"まとめて確認");share.setEnabled(report!=null&&!recording&&!batching);copy.setEnabled(share.isEnabled());reset.setEnabled(!recording&&!batching);
    }
    private void release(){
        ++generation;correctionPending=false;main.removeCallbacks(timeout);if(engine!=null){engine.stop();engine.unbind();engine=null;}if(trial!=null){trial.delete();trial=null;}ready=false;recording=false;
    }
    private void cancel(){put(attempt,"result","中止");persist();batching=false;release();update();}
    @Override protected void onResume(){super.onResume();foreground=true;if(!loading&&!routes.isEmpty()&&engine==null&&!recording){update();}}
    @Override protected void onStop(){if(playbackVideo!=null){playbackVideo.stopPlayback();playbackVideo=null;}foreground=false;if(batching||recording)put(attempt,"result","画面を離れたため中止");persist();batching=false;boolean hadPreview=engine!=null;release();if(hadPreview)status.setText("カメラを選択すると再開できます");super.onStop();}
    @Override protected void onDestroy(){release();io.shutdown();super.onDestroy();}
    private static void put(JSONObject object,String key,Object value){if(object==null)return;try{object.put(key,value);}catch(Exception e){android.util.Log.w("DaylogDiagnosis","Cannot update report",e);}}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout row(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.HORIZONTAL);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private TextView button(String label,Runnable action){TextView view=Ui.text(this,label,15,Ui.INK);view.setGravity(Gravity.CENTER);view.setPadding(dp(10),dp(8),dp(10),dp(8));Ui.touch(view,Ui.CARD,10);view.setOnClickListener(v->action.run());return view;}
    private void pair(View first,View second){LinearLayout row=row();LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(48),1),b=new LinearLayout.LayoutParams(0,dp(48),1);b.leftMargin=dp(10);row.addView(first,a);row.addView(second,b);body.addView(row);}
    private void gap(int height){body.addView(new View(this),params(1,height));}
    private int dp(int value){return Ui.dp(this,value);}
    private LinearLayout.LayoutParams params(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private void setStatus(String value){if(!status.getText().toString().equals(value))status.setText(value);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
}
