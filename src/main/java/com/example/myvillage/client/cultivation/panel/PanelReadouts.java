package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.data.RealmStageDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Display arithmetic for the cultivation panel. It derives nothing the server did not send. */
public final class PanelReadouts {
    private PanelReadouts() {
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
