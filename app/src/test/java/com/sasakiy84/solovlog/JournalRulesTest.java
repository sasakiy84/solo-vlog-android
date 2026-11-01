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
    @Test public void firstClipLocksDayFormatEvenIfExcludedOrPreferencesChange(){
        Clip first=new Clip("a",at("2026-10-30T09:00:00"),2000,2,false,false,TOKYO.getId());
        Clip later=new Clip("b",at("2026-10-30T10:00:00"),2000,2,true,true,TOKYO.getId());
        assertThat(JournalRules.orientationFor(List.of(later,first),LocalDate.of(2026,10,30),240,true)).isFalse();
    }
    @Test public void freshDayUsesTheConfiguredFormat(){
        Clip yesterday=new Clip("a",at("2026-10-30T09:00:00"),2000,2,false,true,TOKYO.getId());
        assertThat(JournalRules.orientationFor(List.of(yesterday),LocalDate.of(2026,10,31),240,true)).isTrue();
        assertThat(JournalRules.orientationFor(List.of(),LocalDate.of(2026,10,31),240,false)).isFalse();
    }
    @Test public void timestampUsesTheCaptureTimezoneAndActualClockEvenBeforeDayBoundary(){
        Clip early=new Clip("a",at("2026-10-31T03:40:00"),2000,2,false,true,TOKYO.getId());
        assertThat(JournalRules.captureTime(early)).isEqualTo("03:40");
        Clip abroad=new Clip("b",early.capturedAt(),2000,2,false,true,"UTC");
        assertThat(JournalRules.captureTime(abroad)).isEqualTo("18:40");
    }
    @Test public void travelRetainsTheCaptureTimezone(){
        long instant=Instant.parse("2026-10-30T00:00:00Z").toEpochMilli();
        Clip tokyo=new Clip("a",instant,2000,2,false,true,"Asia/Tokyo");
        Clip newYork=new Clip("b",instant,2000,2,false,true,"America/New_York");
        assertThat(JournalRules.clipsFor(List.of(tokyo,newYork),LocalDate.of(2026,10,30),false,240,false)).containsExactly(tokyo);
    }
    @Test public void selectedFormatHandlesPortraitAndLandscapeNativeSensors(){
        assertThat(JournalRules.targetRotation(90,true)).isEqualTo(1);
        assertThat(JournalRules.targetRotation(90,false)).isEqualTo(0);
        assertThat(JournalRules.targetRotation(0,true)).isEqualTo(0);
        assertThat(JournalRules.targetRotation(0,false)).isEqualTo(1);
    }
}
