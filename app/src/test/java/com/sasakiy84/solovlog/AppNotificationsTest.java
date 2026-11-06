package com.sasakiy84.solovlog;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import org.junit.Test;

public final class AppNotificationsTest {
    private static long at(String time){return Instant.parse("2026-11-06T"+time+":00Z").toEpochMilli();}

    @Test public void reopeningAppKeepsAnExistingDeadline(){
        assertEquals(at("12:30"),AppNotifications.nextReminderAt(at("12:15"),at("12:30"),4));
    }

    @Test public void overdueReminderSchedulesOnceFromNowWithoutCatchingUp(){
        assertEquals(at("16:15"),AppNotifications.nextReminderAt(at("12:15"),at("08:30"),4));
    }

    @Test public void disablingRemindersRemovesEvenAFutureDeadline(){
        assertEquals(0,AppNotifications.nextReminderAt(at("12:15"),at("12:30"),0));
    }
}
