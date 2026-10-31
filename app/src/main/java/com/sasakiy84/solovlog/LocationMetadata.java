package com.sasakiy84.solovlog;

import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.container.Mp4LocationData;
import androidx.media3.container.Mp4TimestampData;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Native GPS for a single place; a portable per-clip timeline for a travel compilation. */
@UnstableApi
final class LocationMetadata {
    static final String KEY="com.sasakiy84.daylog.timeline.v2";
    private LocationMetadata(){}
    static void apply(Set<Metadata.Entry> entries,List<Clip> clips,String caption,boolean embedLocation){
        // Input MP4s may carry GPS in both native and mdta tags. Rebuild metadata explicitly.
        entries.removeIf(entry->entry instanceof Mp4LocationData||entry instanceof Mp4TimestampData||entry instanceof MdtaMetadataEntry);
        GeoPoint first=clips.get(0).location();
        if(embedLocation&&first!=null&&clips.stream().allMatch(c->first.near(c.location())))entries.add(new Mp4LocationData((float)first.latitude(),(float)first.longitude()));
        long captured=Mp4TimestampData.unixTimeToMp4TimeSeconds(clips.get(0).capturedAt()+clips.get(0).trimStartMs());
        entries.add(new Mp4TimestampData(captured,Mp4TimestampData.unixTimeToMp4TimeSeconds(System.currentTimeMillis())));
        String timeline=timeline(clips,caption,embedLocation);
        if(!timeline.isEmpty())entries.add(new MdtaMetadataEntry(KEY,timeline.getBytes(StandardCharsets.UTF_8),MdtaMetadataEntry.TYPE_INDICATOR_STRING));
    }
    static String timeline(List<Clip> clips,String caption,boolean embedLocation){
        JSONArray items=new JSONArray();long start=0;
        try{
            for(Clip clip:clips){
                JSONObject item=new JSONObject().put("startMs",start).put("durationMs",JournalRules.exportDuration(clip)).put("capturedAt",clip.capturedAt()).put("trimStartMs",clip.trimStartMs()).put("zone",clip.zoneId()).put("text",clip.caption());
                if(embedLocation&&clip.location()!=null){GeoPoint p=clip.location();item.put("latitude",p.latitude()).put("longitude",p.longitude());if(p.accuracyMeters()>0)item.put("accuracyMeters",p.accuracyMeters());}
                if(embedLocation&&!clip.place().isBlank())item.put("place",clip.place());items.put(item);
                start+=JournalRules.exportDuration(clip);
            }
            return new JSONObject().put("version",2).put("dayText",caption).put("clips",items).toString();
        }catch(JSONException e){throw new IllegalStateException("Invalid location metadata",e);}
    }
}
