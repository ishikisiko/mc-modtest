package com.example.myvillage.cultivation.time;

public record CultivationTimeStatus(
        long elapsedCalendarTicks,
        long effectiveLifespanConsumedTicks,
        int ticksPerDay,
        int daysPerYear,
        boolean realmResolved,
        long maximumLifespanTicks,
        long remainingLifespanTicks,
        boolean exhausted) {
    public CultivationTimeStatus {
        if (elapsedCalendarTicks < 0 || effectiveLifespanConsumedTicks < 0) {
            throw new IllegalArgumentException("Cultivation time counters must be non-negative");
        }
        CultivationTimeMath.ticksPerYear(ticksPerDay, daysPerYear);
        if (maximumLifespanTicks < 0 || remainingLifespanTicks < 0) {
            throw new IllegalArgumentException("Lifespan status values must be non-negative");
        }
        if (!realmResolved && (maximumLifespanTicks != 0 || remainingLifespanTicks != 0 || exhausted)) {
            throw new IllegalArgumentException("An unresolved realm cannot expose derived lifespan values");
        }
        if (realmResolved && remainingLifespanTicks > maximumLifespanTicks) {
            throw new IllegalArgumentException("Remaining lifespan cannot exceed the maximum");
        }
    }

    public long ticksPerYear() {
        return CultivationTimeMath.ticksPerYear(ticksPerDay, daysPerYear);
    }

    public long calendarYear() {
        long zeroBased = elapsedCalendarTicks / ticksPerYear();
        return zeroBased == Long.MAX_VALUE ? Long.MAX_VALUE : zeroBased + 1;
    }

    public int calendarDay() {
        long dayIndex = (elapsedCalendarTicks / ticksPerDay) % daysPerYear;
        return (int) dayIndex + 1;
    }
}
