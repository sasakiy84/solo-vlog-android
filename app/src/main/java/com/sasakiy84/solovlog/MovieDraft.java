package com.sasakiy84.solovlog;

import android.content.Context;
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
    int days=1,dayBoundary=240;
    final Map<String,String> dayCaptions=new java.util.TreeMap<>();
    boolean showDate=false,showTime=true,showText=true,showPlace=true,audio=true;
    boolean compact=false,embedLocation=true;
    final List<String> clipIds=new ArrayList<>();
    MovieDraft(String id){if(id==null||!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid movie identifier");this.id=id;}
    static MovieDraft create(Context context,String day,List<String> ids,String caption,boolean showTime,boolean showDate){
        MovieDraft draft=new MovieDraft(UUID.randomUUID().toString());
        draft.day=LocalDate.parse(day).toString();draft.fileName=draft.day;
        draft.clipIds.addAll(ids);draft.caption=caption;draft.showTime=showTime;draft.showDate=showDate;
        var prefs=context.getSharedPreferences("daylog",0);draft.dayBoundary=prefs.getInt("dayStart",240);draft.compact=prefs.getBoolean("compactExport",false);draft.embedLocation=prefs.getBoolean("embedLocation",true);
        return draft;
    }
    static MovieDraft createWeek(Context context,String start,List<String> ids,boolean landscape){
        var prefs=context.getSharedPreferences("daylog",0);
        MovieDraft draft=create(context,start,ids,"",prefs.getBoolean("showTime",true),prefs.getBoolean("showDate",false));
        draft.days=7;LocalDate first=LocalDate.parse(start);
        draft.fileName=start+"_"+first.plusDays(6)+(landscape?"-landscape":"-portrait");
        ClipRepository repository=ClipRepository.get(context);
        for(int i=0;i<7;i++){String day=first.plusDays(i).toString();draft.dayCaptions.put(day,repository.dayCaption(day));}
        return draft;
    }
    String captionFor(Clip clip){
        if(days==1)return caption;
        String date=JournalRules.day(clip.capturedAt(),dayBoundary,java.time.ZoneId.of(clip.zoneId())).toString();
        String text=dayCaptions.getOrDefault(date,"");
        return caption.isBlank()?text:text.isBlank()?caption:caption+"\n"+text;
    }
    static String signature(List<Clip> clips,String day,String caption,boolean showTime,boolean showDate){
        String source="captions-v5\n"+clips+"\n"+day+"\n"+caption+"\n"+showTime+"\n"+showDate;
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
    List<Clip> clips(ClipRepository repository){return displayClips(sourceClips(repository));}
    List<Clip> displayClips(List<Clip> source){return source.stream().map(clip->{if(!showText)clip=clip.withCaption("");if(!showPlace)clip=clip.withPlace("");return clip;}).toList();}
}
