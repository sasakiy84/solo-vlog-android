package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import org.junit.Test;

public final class ClipTest {
    @Test public void editingTextAndPlaceKeepsTrimAudioLocationAndCaptureMetadata(){
        GeoPoint point=new GeoPoint(35.681236,139.767125,5);
        Clip original=new Clip("a",1000,2100,2,true,false,"Asia/Tokyo")
            .withTrim(350,1650).withRenderAudio(false).withLocation(point).withPlace("東京駅");
        Clip edited=original.withCaption("  朝ごはん  ");
        assertThat(edited.caption()).isEqualTo("朝ごはん");
        assertThat(edited.withCaption("").caption()).isEmpty();
        assertThat(edited.withCaption("").place()).isEqualTo("東京駅");
        edited=edited.withPlace("");
        assertThat(edited.caption()).isEqualTo("朝ごはん");
        assertThat(edited.location()).isEqualTo(point);
        assertThat(edited.capturedAt()).isEqualTo(1000);
        assertThat(edited.zoneId()).isEqualTo("Asia/Tokyo");
        assertThat(edited.landscape()).isTrue();assertThat(edited.included()).isFalse();
        assertThat(edited.trimStartMs()).isEqualTo(350);
        assertThat(JournalRules.exportDuration(edited)).isEqualTo(1300);
        assertThat(edited.renderAudio()).isFalse();
    }
    @Test public void exportKeepsRequestedDurationOrShortInterruptedRecording(){
        assertThat(JournalRules.exportDuration(new Clip("a",0,3120,3,false,true,"UTC"))).isEqualTo(3000);
        assertThat(JournalRules.exportDuration(new Clip("b",0,900,5,false,true,"UTC"))).isEqualTo(900);
    }
}
