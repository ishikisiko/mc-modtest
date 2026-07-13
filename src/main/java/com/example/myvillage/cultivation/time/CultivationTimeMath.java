package com.example.myvillage.cultivation.time;

import java.util.OptionalInt;

public final class CultivationTimeMath {
    private CultivationTimeMath() {
    }

    public static long saturatingAdd(long value, long increment) {
        requireNonNegative(value, "value");
        requireNonNegative(increment, "increment");
        return Long.MAX_VALUE - value < increment ? Long.MAX_VALUE : value + increment;
    }

    public static long ticksPerYear(int ticksPerDay, int daysPerYear) {
        if (ticksPerDay <= 0 || daysPerYear <= 0) {
            throw new IllegalArgumentException("Cultivation time scale values must be positive");
        }
        return Math.multiplyExact((long) ticksPerDay, daysPerYear);
    }

    public static long maximumLifespanTicks(int maximumLifespanYears, long ticksPerYear) {
        if (maximumLifespanYears <= 0 || ticksPerYear <= 0) {
            throw new IllegalArgumentException("Maximum lifespan and ticks per year must be positive");
        }
        return Math.multiplyExact((long) maximumLifespanYears, ticksPerYear);
    }

    public static long remainingTicks(long consumedTicks, long maximumTicks) {
        requireNonNegative(consumedTicks, "consumedTicks");
        requireNonNegative(maximumTicks, "maximumTicks");
        return consumedTicks >= maximumTicks ? 0 : maximumTicks - consumedTicks;
    }

    public static OptionalInt mostUrgentWarning(long remainingTicks, long ticksPerYear) {
        requireNonNegative(remainingTicks, "remainingTicks");
        if (ticksPerYear <= 0) {
            throw new IllegalArgumentException("ticksPerYear must be positive");
        }
        if (remainingTicks <= Math.multiplyExact(1L, ticksPerYear)) {
            return OptionalInt.of(1);
        }
        if (remainingTicks <= Math.multiplyExact(5L, ticksPerYear)) {
            return OptionalInt.of(5);
        }
        if (remainingTicks <= Math.multiplyExact(10L, ticksPerYear)) {
            return OptionalInt.of(10);
        }
        return OptionalInt.empty();
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative, got " + value);
        }
    }
}
