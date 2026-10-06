package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.StudyProgress;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/** Display arithmetic for the cultivation panel. It derives nothing the server did not send. */
public final class PanelReadouts {
    public static final String SESSION_KEY = "screen.myvillage.cultivation.session.";
    public static final String WAITING_KEY = "screen.myvillage.cultivation.time_waiting";
    public static final String STUDY_KEY = "screen.myvillage.cultivation.study.";

    private PanelReadouts() {
    }

    /**
     * The translation key of the session state: the waiting text before the first status, the study keys
     * ({@code study.preparing}, {@code study.reading}) while a study session runs (its states are the normal
     * meditation ones), otherwise {@code session.<state>}.
     */
    public static String sessionKey(MeditationStatus status) {
        if (status == null) {
            return WAITING_KEY;
        }
        if (status.studying()) {
            return STUDY_KEY + (status.state().preparing() ? "preparing" : "reading");
        }
        return SESSION_KEY + status.state().name().toLowerCase(Locale.ROOT);
    }

    /** Whole percent of the manual comprehended, 0..100, rounded down (as the manual's tooltip). */
    public static int studyPercent(StudyProgress progress) {
        if (progress.points() <= 0) {
            return 0;
        }
        return (int) Math.min(100L, (long) progress.points() * 100L / progress.totalPoints());
    }

    /** Points over the total, for the study bar. */
    public static double studyFraction(StudyProgress progress) {
        return fraction(progress.points(), progress.totalPoints());
    }

    /** {@code study.gate} (next gate and its stability cost) or {@code study.no_gate} when none is left. */
    public static String studyGateKey(StudyProgress progress) {
        return STUDY_KEY + (progress.nextGatePoints() == StudyProgress.NO_GATE ? "no_gate" : "gate");
    }

    /** True when a gate lies ahead and the synced stability would not pay for it. */
    public static boolean studyGateShort(StudyProgress progress, int stability) {
        return progress.nextGatePoints() != StudyProgress.NO_GATE && stability < progress.gateStabilityCost();
    }

    /** {@code value / cap} clamped to 0..1; 0 when the cap is not positive. */
    public static double fraction(long value, long cap) {
        if (cap <= 0 || value <= 0) {
            return 0.0D;
        }
        return Math.min(1.0D, value / (double) cap);
    }

    public static long ticksPerYear(long ticksPerDay, int daysPerYear) {
        if (ticksPerDay > Long.MAX_VALUE / daysPerYear) {
            return Long.MAX_VALUE;
        }
        return ticksPerDay * daysPerYear;
    }

    public static long yearsFloor(long ticks, long ticksPerDay, int daysPerYear) {
        return ticks / ticksPerYear(ticksPerDay, daysPerYear);
    }

    public static long yearsCeil(long ticks, long ticksPerDay, int daysPerYear) {
        long ticksPerYear = ticksPerYear(ticksPerDay, daysPerYear);
        long whole = ticks / ticksPerYear;
        return ticks % ticksPerYear == 0 || whole == Long.MAX_VALUE ? whole : whole + 1;
    }

    /** 1-based calendar year: elapsed tick zero is year 1. */
    public static long calendarYear(long elapsedTicks, long ticksPerDay, int daysPerYear) {
        long year = elapsedTicks / ticksPerDay / daysPerYear;
        return year == Long.MAX_VALUE ? Long.MAX_VALUE : year + 1;
    }

    /** 1-based day within the calendar year. */
    public static long calendarDay(long elapsedTicks, long ticksPerDay, int daysPerYear) {
        return elapsedTicks / ticksPerDay % daysPerYear + 1;
    }

    /**
     * 1-based week within the calendar year. Weeks restart with every year, so a year that is not a
     * whole number of weeks ends on a short week.
     */
    public static long calendarWeek(long elapsedTicks, long ticksPerDay, int daysPerYear, int daysPerWeek) {
        return weekOfYear(calendarDay(elapsedTicks, ticksPerDay, daysPerYear), daysPerWeek);
    }

    /** 1-based day within the week of {@link #calendarWeek}. */
    public static long calendarDayOfWeek(long elapsedTicks, long ticksPerDay, int daysPerYear, int daysPerWeek) {
        return dayOfWeek(calendarDay(elapsedTicks, ticksPerDay, daysPerYear), daysPerWeek);
    }

    /** 1-based week holding the 1-based {@code dayOfYear}. */
    public static long weekOfYear(long dayOfYear, int daysPerWeek) {
        return (dayOfYear - 1) / daysPerWeek + 1;
    }

    /** 1-based day within the week holding the 1-based {@code dayOfYear}. */
    public static long dayOfWeek(long dayOfYear, int daysPerWeek) {
        return (dayOfYear - 1) % daysPerWeek + 1;
    }

    /** Position of {@code stageId} in {@code stages}, or -1 when the realm does not list it. */
    public static int stageIndex(List<RealmStageDefinition> stages, ResourceLocation stageId) {
        for (int index = 0; index < stages.size(); index++) {
            if (stages.get(index).id().equals(stageId)) {
                return index;
            }
        }
        return -1;
    }
}
