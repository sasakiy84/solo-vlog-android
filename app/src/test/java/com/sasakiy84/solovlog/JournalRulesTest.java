package com.sasakiy84.solovlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertEquals(LocalDate.of(2026,10,30),JournalRules.day(at("2026-10-31T03:59:59"),240,TOKYO));
        assertEquals(LocalDate.of(2026,10,31),JournalRules.day(at("2026-10-31T04:00:00"),240,TOKYO));
    }
    @Test public void changingBoundaryReclassifiesExistingCapture(){
        long instant=at("2026-10-31T02:30:00");
        assertEquals(LocalDate.of(2026,10,30),JournalRules.day(instant,240,TOKYO));
        assertEquals(LocalDate.of(2026,10,31),JournalRules.day(instant,120,TOKYO));
    }
    @Test public void daylightSavingUsesLocalClockRatherThanFixedHourSubtraction(){
        ZoneId newYork=ZoneId.of("America/New_York");
        long instant=ZonedDateTime.of(2026,11,1,3,30,0,0,newYork).toInstant().toEpochMilli();
        assertEquals(LocalDate.of(2026,10,31),JournalRules.day(instant,240,newYork));
    }
    @Test public void portraitAndLandscapeNeverJoinAndExcludedOriginalRemains(){
        Clip morning=new Clip("a",at("2026-10-30T09:00:00"),3100,3,false,true,TOKYO.getId());
        Clip landscape=new Clip("b",at("2026-10-30T10:00:00"),5100,5,true,true,TOKYO.getId());
        Clip excluded=new Clip("c",at("2026-10-30T11:00:00"),2000,2,false,false,TOKYO.getId());
        List<Clip> all=List.of(excluded,landscape,morning);LocalDate day=LocalDate.of(2026,10,30);
        assertEquals(List.of(morning),JournalRules.clipsFor(all,day,false,240,true));
        assertEquals(List.of(landscape),JournalRules.clipsFor(all,day,true,240,true));
        assertEquals(List.of(morning,excluded),JournalRules.clipsFor(all,day,false,240,false));
    }
    @Test public void firstClipLocksDayFormatEvenIfExcludedOrPreferencesChange(){
        Clip first=new Clip("a",at("2026-10-30T09:00:00"),2000,2,false,false,TOKYO.getId());
        Clip later=new Clip("b",at("2026-10-30T10:00:00"),2000,2,true,true,TOKYO.getId());
        assertFalse(JournalRules.orientationFor(List.of(later,first),LocalDate.of(2026,10,30),240,true));
    }
    @Test public void freshDayUsesTheConfiguredFormat(){
        Clip yesterday=new Clip("a",at("2026-10-30T09:00:00"),2000,2,false,true,TOKYO.getId());
        assertTrue(JournalRules.orientationFor(List.of(yesterday),LocalDate.of(2026,10,31),240,true));
        assertFalse(JournalRules.orientationFor(List.of(),LocalDate.of(2026,10,31),240,false));
    }
    @Test public void timestampUsesTheCaptureTimezoneAndActualClockEvenBeforeDayBoundary(){
        Clip early=new Clip("a",at("2026-10-31T03:40:00"),2000,2,false,true,TOKYO.getId());
        assertEquals("03:40",JournalRules.captureTime(early));
        Clip abroad=new Clip("b",early.capturedAt(),2000,2,false,true,"UTC");
        assertEquals("18:40",JournalRules.captureTime(abroad));
    }
    @Test public void travelRetainsTheCaptureTimezone(){
        long instant=Instant.parse("2026-10-30T00:00:00Z").toEpochMilli();
        Clip tokyo=new Clip("a",instant,2000,2,false,true,"Asia/Tokyo");
        Clip newYork=new Clip("b",instant,2000,2,false,true,"America/New_York");
        assertEquals(List.of(tokyo),JournalRules.clipsFor(List.of(tokyo,newYork),LocalDate.of(2026,10,30),false,240,false));
    }
    @Test public void selectedFormatHandlesPortraitAndLandscapeNativeSensors(){
        assertEquals(1,JournalRules.targetRotation(90,true));
        assertEquals(0,JournalRules.targetRotation(90,false));
        assertEquals(0,JournalRules.targetRotation(0,true));
        assertEquals(1,JournalRules.targetRotation(0,false));
    }
}
