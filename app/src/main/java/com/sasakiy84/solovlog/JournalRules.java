package com.sasakiy84.solovlog;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

public final class JournalRules {
    private JournalRules() {}

    public static LocalDate day(long capturedAt, int startMinutes, ZoneId zone) {
        if (startMinutes < 0 || startMinutes >= 1440) throw new IllegalArgumentException("Invalid day boundary");
        ZonedDateTime local = Instant.ofEpochMilli(capturedAt).atZone(zone);
        LocalTime boundary = LocalTime.of(startMinutes / 60, startMinutes % 60);
        return local.toLocalTime().isBefore(boundary) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
    }

    public static List<Clip> clipsFor(List<Clip> clips, LocalDate day, boolean landscape,
                                      int startMinutes, boolean includedOnly) {
        return clips.stream().filter(c -> c.landscape() == landscape)
            .filter(c -> !includedOnly || c.included())
            .filter(c -> day(c.capturedAt(), startMinutes, ZoneId.of(c.zoneId())).equals(day))
            .sorted(Comparator.comparingLong(Clip::capturedAt).thenComparing(Clip::id)).toList();
    }

    /** A day's first clip fixes its format even if preferences or selection later change. */
    public static boolean orientationFor(List<Clip> clips, LocalDate day, int startMinutes, boolean fallback) {
        return clips.stream()
            .filter(c -> day(c.capturedAt(),startMinutes,ZoneId.of(c.zoneId())).equals(day))
            .min(Comparator.comparingLong(Clip::capturedAt).thenComparing(Clip::id))
            .map(Clip::landscape).orElse(fallback);
    }

    public static String captureDate(Clip clip){
        return Instant.ofEpochMilli(clip.capturedAt()).atZone(ZoneId.of(clip.zoneId())).format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    }
    public static String captureTime(Clip clip){
        return Instant.ofEpochMilli(clip.capturedAt()).atZone(ZoneId.of(clip.zoneId()))
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }
    public static long exportDuration(Clip clip) {
        return Math.min(clip.durationMs(), clip.plannedSeconds() * 1000L);
    }

    /** Surface rotation is expressed in quarter turns (0 or 1), including front-facing sensors. */
    public static int targetRotation(int sensorDegrees, boolean landscape) {
        boolean naturalPortrait = sensorDegrees % 180 != 0;
        return landscape == naturalPortrait ? 1 : 0;
    }
}
