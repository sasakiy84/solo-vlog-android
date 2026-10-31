package com.sasakiy84.solovlog;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDate;
import java.util.UUID;

/** A private render draft is independent of shared-gallery publication and original recordings. */
final class MovieDraft {
    final String id;
    String day,caption,status="editing",error="",savedUri="",sourceSignature="",fileName="",outputResolution="";
    boolean showDate=false,showTime=true,showText=true,showPlace=true,audio=true;
    boolean compact=false,embedLocation=true;
    final List<String> clipIds=new ArrayList<>();
    MovieDraft(String id){if(id==null||!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid movie identifier");this.id=id;}
    static MovieDraft create(Context context,String day,List<String> ids,String caption,boolean showTime,boolean showDate){
        MovieDraft draft=new MovieDraft(UUID.randomUUID().toString());
        draft.day=LocalDate.parse(day).toString();draft.fileName=draft.day;
        draft.clipIds.addAll(ids);draft.caption=caption;draft.showTime=showTime;draft.showDate=showDate;
        var prefs=context.getSharedPreferences("daylog",0);draft.compact=prefs.getBoolean("compactExport",false);draft.embedLocation=prefs.getBoolean("embedLocation",true);
        return draft;
    }
    private static File directory(Context context){File dir=new File(context.getFilesDir(),"rendered");if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Cannot create render directory");return dir;}
    static long bytes(Context context){File[] files=directory(context).listFiles();if(files==null)return 0;long total=0;for(File file:files)if(file.isFile())total+=file.length();return total;}
    static void deleteBefore(Context context,LocalDate cutoff){
        File[] files=directory(context).listFiles((dir,name)->name.matches("[a-f0-9-]{36}\\.json"));
        if(files==null)throw new IllegalStateException("レンダリング結果を確認できませんでした");
        for(File file:files){
            LocalDate day;
            try{day=LocalDate.parse(new JSONObject(new String(new AtomicFile(file).readFully(),StandardCharsets.UTF_8)).getString("day"));}
            catch(Exception e){android.util.Log.w("DaylogDraft","Cannot read old render date",e);continue;}
            if(!day.isBefore(cutoff))continue;
            File movie=new File(file.getParentFile(),file.getName().replace(".json",".mp4"));
            if(movie.exists()&&!movie.delete())throw new IllegalStateException("レンダリング結果を削除できませんでした");
            new AtomicFile(file).delete();if(file.exists())throw new IllegalStateException("レンダリング設定を削除できませんでした");
        }
    }
    File movie(Context context){return new File(directory(context),id+".mp4");}
    private AtomicFile metadata(Context context){return new AtomicFile(new File(directory(context),id+".json"));}
    static MovieDraft load(Context context,String id)throws Exception{
        MovieDraft draft=new MovieDraft(id);JSONObject data=new JSONObject(new String(draft.metadata(context).readFully(),StandardCharsets.UTF_8));
        draft.outputResolution=data.getString("outputResolution");draft.fileName=data.getString("fileName");draft.sourceSignature=data.optString("sourceSignature");draft.day=data.getString("day");draft.caption=data.optString("caption");draft.status=data.optString("status","editing");draft.error=data.optString("error");draft.savedUri=data.optString("savedUri");
        draft.showDate=data.getBoolean("showDate");draft.showTime=data.optBoolean("showTime",true);draft.showText=data.optBoolean("showText",true);draft.showPlace=data.optBoolean("showPlace",true);draft.audio=data.optBoolean("audio",true);
        draft.compact=data.optBoolean("compact",false);draft.embedLocation=data.optBoolean("embedLocation",true);
        JSONArray ids=data.getJSONArray("clips");for(int n=0;n<ids.length();n++){String clip=ids.getString(n);if(!clip.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid clip identifier");draft.clipIds.add(clip);}return draft;
    }
    void save(Context context)throws Exception{
        JSONObject data=new JSONObject().put("outputResolution",outputResolution).put("fileName",fileName).put("sourceSignature",sourceSignature).put("day",day).put("caption",caption).put("status",status).put("error",error).put("savedUri",savedUri).put("showDate",showDate).put("showTime",showTime).put("showText",showText).put("showPlace",showPlace).put("audio",audio).put("clips",new JSONArray(clipIds));
        data.put("compact",compact).put("embedLocation",embedLocation);
        AtomicFile target=metadata(context);FileOutputStream stream=null;try{stream=target.startWrite();stream.write(data.toString().getBytes(StandardCharsets.UTF_8));target.finishWrite(stream);}catch(Exception e){if(stream!=null)target.failWrite(stream);throw e;}
    }
    static String signature(List<Clip> clips,String day,String caption,boolean showTime,boolean showDate){
        String source="captions-v4\n"+clips+"\n"+day+"\n"+caption+"\n"+showTime+"\n"+showDate;
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    List<Clip> sourceClips(ClipRepository repository){
        Map<String,Clip> available=new HashMap<>();
        for(Clip clip:repository.forIds(clipIds))available.put(clip.id(),clip);
        List<Clip> result=new ArrayList<>();
        for(String id:clipIds){
            Clip clip=available.get(id);
            if(clip==null)throw new IllegalStateException("元動画が見つかりません");
            result.add(clip);
        }
        return result;
    }
    List<Clip> clips(ClipRepository repository){return sourceClips(repository).stream().map(clip->{if(!showText)clip=clip.withCaption("");if(!showPlace)clip=clip.withPlace("");return clip;}).toList();}
}
