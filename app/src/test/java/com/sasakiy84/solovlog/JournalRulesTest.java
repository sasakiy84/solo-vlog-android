package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import java.time.*;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JournalRulesTest {
    private static final ZoneId TOKYO=ZoneId.of("Asia/Tokyo");
    private static long at(String local){return LocalDateTime.parse(local).atZone(TOKYO).toInstant().toEpochMilli();}

    @Test public void justBeforeBoundaryBelongsToPreviousDay(){
        assertThat(JournalRules.day(at("2026-10-31T03:59:59"),240,TOKYO)).isEqualTo(LocalDate.of(2026,10,30));
        assertThat(JournalRules.day(at("2026-10-31T04:00:00"),240,TOKYO)).isEqualTo(LocalDate.of(2026,10,31));
    }
    @Test public void midnightBoundaryUsesCalendarDate(){assertThat(JournalRules.day(at("2026-10-31T00:00:00"),0,TOKYO)).isEqualTo(LocalDate.of(2026,10,31));}
    @Test public void changingBoundaryReclassifiesExistingCapture(){
        long instant=at("2026-10-31T02:30:00");
        assertThat(JournalRules.day(instant,240,TOKYO)).isEqualTo(LocalDate.of(2026,10,30));
        assertThat(JournalRules.day(instant,120,TOKYO)).isEqualTo(LocalDate.of(2026,10,31));
    }
    @Test public void daylightSavingUsesLocalClockRatherThanFixedHourSubtraction(){
        ZoneId newYork=ZoneId.of("America/New_York");
        long instant=ZonedDateTime.of(2026,11,1,3,30,0,0,newYork).toInstant().toEpochMilli();
        assertThat(JournalRules.day(instant,240,newYork)).isEqualTo(LocalDate.of(2026,10,31));
    }
    @Test public void portraitAndLandscapeNeverJoinAndExcludedOriginalRemains(){
        Clip morning=new Clip("a",at("2026-10-30T09:00:00"),3100,3,false,true,TOKYO.getId());
        Clip landscape=new Clip("b",at("2026-10-30T10:00:00"),5100,5,true,true,TOKYO.getId());
        Clip excluded=new Clip("c",at("2026-10-30T11:00:00"),2000,2,false,false,TOKYO.getId());
        List<Clip> all=List.of(excluded,landscape,morning);LocalDate day=LocalDate.of(2026,10,30);
        assertThat(JournalRules.clipsFor(all,day,false,240,true)).containsExactly(morning);
        assertThat(JournalRules.clipsFor(all,day,true,240,true)).containsExactly(landscape);
        assertThat(JournalRules.clipsFor(all,day,false,240,false)).containsExactly(morning,excluded).inOrder();
    }
    @Test public void exportKeepsRequestedDurationOrShortInterruptedRecording(){
        assertThat(JournalRules.exportDuration(new Clip("a",0,3120,3,false,true,"UTC"))).isEqualTo(3000);
        assertThat(JournalRules.exportDuration(new Clip("b",0,900,5,false,true,"UTC"))).isEqualTo(900);
    }
    @Test public void travelRetainsTheCaptureTimezone(){
        long instant=Instant.parse("2026-10-30T00:00:00Z").toEpochMilli();
        Clip tokyo=new Clip("a",instant,2000,2,false,true,"Asia/Tokyo");
        Clip newYork=new Clip("b",instant,2000,2,false,true,"America/New_York");
        assertThat(JournalRules.clipsFor(List.of(tokyo,newYork),LocalDate.of(2026,10,30),false,240,false)).containsExactly(tokyo);
    }
    @Test public void selectedFormatHandlesPortraitAndLandscapeNativeSensors(){
        for(int sensor:new int[]{0,90,180,270}){
            assertThat((sensor+90*JournalRules.targetRotation(sensor,true))%180).isEqualTo(0);
            assertThat((sensor+90*JournalRules.targetRotation(sensor,false))%180).isEqualTo(90);
        }
    }
}
