package com.sasakiy84.solovlog;

import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.container.Mp4LocationData;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Native GPS for a single place; a portable per-clip timeline for a travel compilation. */
@UnstableApi
final class LocationMetadata {
    static final String KEY="com.sasakiy84.daylog.locations.v1";
    private LocationMetadata(){}
    static void apply(Set<Metadata.Entry> entries,List<Clip> clips){
        entries.removeIf(entry->entry instanceof Mp4LocationData||entry instanceof MdtaMetadataEntry mdta&&KEY.equals(mdta.key));
        GeoPoint first=clips.get(0).location();
        if(first!=null&&clips.stream().allMatch(c->first.near(c.location())))entries.add(new Mp4LocationData((float)first.latitude(),(float)first.longitude()));
        String timeline=timeline(clips);
        if(!timeline.isEmpty())entries.add(new MdtaMetadataEntry(KEY,timeline.getBytes(StandardCharsets.UTF_8),MdtaMetadataEntry.TYPE_INDICATOR_STRING));
    }
    static String timeline(List<Clip> clips){
        JSONArray items=new JSONArray();long start=0;
        try{
            for(Clip clip:clips){
                if(clip.location()!=null||!clip.place().isBlank()){
                    JSONObject item=new JSONObject().put("startMs",start).put("durationMs",JournalRules.exportDuration(clip)).put("capturedAt",clip.capturedAt()+clip.trimStartMs()).put("zone",clip.zoneId());
                    if(clip.location()!=null){GeoPoint p=clip.location();item.put("latitude",p.latitude()).put("longitude",p.longitude());if(p.accuracyMeters()>0)item.put("accuracyMeters",p.accuracyMeters());}
                    if(!clip.place().isBlank())item.put("place",clip.place());items.put(item);
                }
                start+=JournalRules.exportDuration(clip);
            }
            return items.length()==0?"":new JSONObject().put("version",1).put("clips",items).toString();
        }catch(JSONException e){throw new IllegalStateException("Invalid location metadata",e);}
    }
}
