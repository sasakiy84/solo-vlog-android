package com.sasakiy84.solovlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ClipTest {
    @Test public void editingTextAndPlaceKeepsTrimAudioLocationAndCaptureMetadata(){
        GeoPoint point=new GeoPoint(35.681236,139.767125,5);
        Clip original=new Clip("a",1000,2100,2,true,false,"Asia/Tokyo")
            .withTrim(350,1650).withRenderAudio(false).withLocation(point).withPlace("東京駅");
        Clip edited=original.withCaption("  朝ごはん  ");
        assertEquals("朝ごはん",edited.caption());
        assertTrue(edited.withCaption("").caption().isEmpty());
        assertEquals("東京駅",edited.withCaption("").place());
        edited=edited.withPlace("");
        assertEquals("朝ごはん",edited.caption());
        assertEquals(point,edited.location());
        assertEquals(1000,edited.capturedAt());
        assertEquals("Asia/Tokyo",edited.zoneId());
        assertTrue(edited.landscape());assertFalse(edited.included());
        assertEquals(350,edited.trimStartMs());
        assertEquals(1300,JournalRules.exportDuration(edited));
        assertFalse(edited.renderAudio());
    }
    @Test public void exportKeepsRequestedDurationOrShortInterruptedRecording(){
        assertEquals(3000,JournalRules.exportDuration(new Clip("a",0,3120,3,false,true,"UTC")));
        assertEquals(900,JournalRules.exportDuration(new Clip("b",0,900,5,false,true,"UTC")));
    }
}
