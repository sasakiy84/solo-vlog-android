package com.sasakiy84.solovlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.container.Mp4LocationData;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@UnstableApi
@RunWith(AndroidJUnit4.class)
public final class LocationMetadataTest {
    @Test public void travelTimelineKeepsOffsetsAcrossClipsWithoutGpsAndRemovesMisleadingSingleLocation() throws Exception{
        GeoPoint tokyo=new GeoPoint(35.681236,139.767125,5),kyoto=new GeoPoint(34.985849,135.758767,5);
        Clip first=new Clip("a",1,2200,2,false,true,"Asia/Tokyo").withLocation(tokyo).withPlace("東京駅");
        Clip middle=new Clip("b",2,3200,3,false,true,"Asia/Tokyo");
        Clip last=new Clip("c",3,2100,2,false,true,"Asia/Tokyo").withLocation(kyoto).withPlace("京都駅");
        List<Clip> clips=List.of(first,middle,last);
        JSONArray timeline=new JSONObject(LocationMetadata.timeline(clips,"",true)).getJSONArray("clips");
        assertEquals(3,timeline.length());assertEquals(0,timeline.getJSONObject(0).getLong("startMs"));
        assertEquals(5000,timeline.getJSONObject(2).getLong("startMs"));
        assertEquals("京都駅",timeline.getJSONObject(2).getString("place"));
        Set<Metadata.Entry> entries=new HashSet<>();entries.add(new Mp4LocationData(35,139));LocationMetadata.apply(entries,clips,"",true);
        assertFalse(entries.stream().anyMatch(e->e instanceof Mp4LocationData));
        assertTrue(entries.stream().anyMatch(e->e instanceof MdtaMetadataEntry m&&m.key.equals("com.sasakiy84.daylog.timeline.v2")));
        LocationMetadata.apply(entries,List.of(first),"",true);
        assertTrue(entries.stream().anyMatch(e->e instanceof Mp4LocationData));
        LocationMetadata.apply(entries,List.of(first),"",false);
        assertFalse(entries.stream().anyMatch(e->e instanceof Mp4LocationData));
        JSONArray privateTimeline=new JSONObject(LocationMetadata.timeline(clips,"",false)).getJSONArray("clips");
        for(int n=0;n<privateTimeline.length();n++){
            JSONObject item=privateTimeline.getJSONObject(n);
            assertFalse(item.has("latitude")||item.has("longitude")||item.has("place"));
        }
        assertEquals(5000,privateTimeline.getJSONObject(2).getLong("startMs"));
    }
    @Test public void manualPlaceWithoutPermissionDoesNotInventCoordinates() throws Exception{
        Clip clip=new Clip("a",1,2000,2,false,true,"UTC").withPlace("京都駅");
        JSONObject item=new JSONObject(LocationMetadata.timeline(List.of(clip),"",true)).getJSONArray("clips").getJSONObject(0);
        assertEquals("京都駅",item.getString("place"));assertFalse(item.has("latitude"));
    }
}
