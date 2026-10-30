package com.sasakiy84.solovlog;

/** Capture metadata stays independent of the current day-boundary and orientation settings. */
public record Clip(String id, long capturedAt, long durationMs, int plannedSeconds,
                   boolean landscape, boolean included, String zoneId) {}
