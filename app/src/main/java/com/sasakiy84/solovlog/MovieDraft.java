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
import java.util.UUID;

/** A private render draft is independent of shared-gallery publication and original recordings. */
final class MovieDraft {
    final String id;
    String day,caption,status="editing",error="",savedUri="",sourceSignature="";
    boolean showTime=true,showText=true,showPlace=true,audio=true;
    final List<String> clipIds=new ArrayList<>();
    MovieDraft(String id){if(id==null||!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid movie identifier");this.id=id;}
    static MovieDraft create(String day,List<String> ids,String caption,boolean showTime){MovieDraft draft=new MovieDraft(UUID.randomUUID().toString());draft.day=day;draft.clipIds.addAll(ids);draft.caption=caption;draft.showTime=showTime;return draft;}
    private static File directory(Context context){File dir=new File(context.getFilesDir(),"rendered");if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Cannot create render directory");return dir;}
    static long bytes(Context context){File[] files=directory(context).listFiles();if(files==null)return 0;long total=0;for(File file:files)if(file.isFile())total+=file.length();return total;}
    File movie(Context context){return new File(directory(context),id+".mp4");}
    private AtomicFile metadata(Context context){return new AtomicFile(new File(directory(context),id+".json"));}
    static MovieDraft load(Context context,String id)throws Exception{
        MovieDraft draft=new MovieDraft(id);JSONObject data=new JSONObject(new String(draft.metadata(context).readFully(),StandardCharsets.UTF_8));
        draft.sourceSignature=data.optString("sourceSignature");draft.day=data.getString("day");draft.caption=data.optString("caption");draft.status=data.optString("status","editing");draft.error=data.optString("error");draft.savedUri=data.optString("savedUri");
        draft.showTime=data.optBoolean("showTime",true);draft.showText=data.optBoolean("showText",true);draft.showPlace=data.optBoolean("showPlace",true);draft.audio=data.optBoolean("audio",true);
        JSONArray ids=data.getJSONArray("clips");for(int n=0;n<ids.length();n++){String clip=ids.getString(n);if(!clip.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid clip identifier");draft.clipIds.add(clip);}return draft;
    }
    void save(Context context)throws Exception{
        JSONObject data=new JSONObject().put("sourceSignature",sourceSignature).put("day",day).put("caption",caption).put("status",status).put("error",error).put("savedUri",savedUri).put("showTime",showTime).put("showText",showText).put("showPlace",showPlace).put("audio",audio).put("clips",new JSONArray(clipIds));
        AtomicFile target=metadata(context);FileOutputStream stream=null;try{stream=target.startWrite();stream.write(data.toString().getBytes(StandardCharsets.UTF_8));target.finishWrite(stream);}catch(Exception e){if(stream!=null)target.failWrite(stream);throw e;}
    }
    static String signature(List<Clip> clips,String caption,boolean showTime){
        try{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest((clips.toString()+"\n"+caption+"\n"+showTime).getBytes(StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(digest);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    List<Clip> clips(ClipRepository repository){
        List<Clip> all=repository.all(),result=new ArrayList<>();for(String id:clipIds){Clip clip=all.stream().filter(c->c.id().equals(id)).findFirst().orElseThrow(()->new IllegalStateException("元動画が見つかりません"));if(!showText)clip=clip.withCaption("");if(!showPlace)clip=clip.withPlace("");result.add(clip);}return result;
    }
}
