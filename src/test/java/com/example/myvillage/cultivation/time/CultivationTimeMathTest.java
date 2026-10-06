package com.example.myvillage.cultivation.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CultivationTimeMathTest {
    @Test
    void defaultScaleAndRealmMaximumUseCheckedProducts() {
        CultivationServerConfig.Scale scale = new CultivationServerConfig.Scale(
                CultivationServerConfig.DEFAULT_TICKS_PER_DAY,
                CultivationServerConfig.DEFAULT_DAYS_PER_YEAR,
                CultivationServerConfig.DEFAULT_DAYS_PER_WEEK);

        assertEquals(24, scale.daysPerYear());
        assertEquals(6, scale.daysPerWeek());
        assertTrue(scale.wholeWeeksPerYear());
        assertEquals(576_000L, scale.ticksPerYear());
        assertEquals(46_080_000L, scale.maximumLifespanTicks(80));
        assertThrows(
                ArithmeticException.class,
                () -> CultivationTimeMath.maximumLifespanTicks(Integer.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    void weekLengthIsPositiveAndMayLeaveAShortLastWeek() {
        assertEquals(6, new CultivationServerConfig.Scale(24_000, 24).daysPerWeek());
        assertFalse(new CultivationServerConfig.Scale(24_000, 10, 4).wholeWeeksPerYear());
        assertThrows(IllegalArgumentException.class, () -> new CultivationServerConfig.Scale(24_000, 24, 0));
        assertThrows(IllegalArgumentException.class, () -> new CultivationTimeStatus(
                0, 0, 24_000, 24, 0, false, 0, 0, false));
    }

    @Test
    void nonNegativeAdditionSaturatesInsteadOfWrapping() {
        assertEquals(42L, CultivationTimeMath.saturatingAdd(40, 2));
        assertEquals(Long.MAX_VALUE, CultivationTimeMath.saturatingAdd(Long.MAX_VALUE - 1, 2));
        assertThrows(IllegalArgumentException.class, () -> CultivationTimeMath.saturatingAdd(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> CultivationTimeMath.saturatingAdd(1, -1));
    }

    @Test
    void warningThresholdsAreRelativeToRemainingYears() {
        long year = 144_000;

        assertFalse(CultivationTimeMath.mostUrgentWarning(10 * year + 1, year).isPresent());
        assertEquals(10, CultivationTimeMath.mostUrgentWarning(10 * year, year).orElseThrow());
        assertEquals(5, CultivationTimeMath.mostUrgentWarning(5 * year, year).orElseThrow());
        assertEquals(1, CultivationTimeMath.mostUrgentWarning(year, year).orElseThrow());
        assertEquals(1, CultivationTimeMath.mostUrgentWarning(0, year).orElseThrow());
    }

    @Test
    void statusCalendarStartsAtYearOneDayOneAndDerivesExhaustionSafely() {
        CultivationTimeStatus start = new CultivationTimeStatus(
                0, 0, 24_000, 24, 6, true, 46_080_000, 46_080_000, false);
        CultivationTimeStatus later = new CultivationTimeStatus(
                576_000 + 24_000, 46_080_000, 24_000, 24, 6, true, 46_080_000, 0, true);

        assertEquals(1, start.calendarYear());
        assertEquals(1, start.calendarDay());
        assertEquals(2, later.calendarYear());
        assertEquals(2, later.calendarDay());
        assertTrue(later.exhausted());
        assertThrows(IllegalArgumentException.class, () -> new CultivationTimeStatus(
                0, 0, 24_000, 24, 6, false, 1, 0, false));
    }

    @Test
    void exhaustedStatusUsesItsOwnMarkerInsteadOfTheOneYearWarning() {
        CultivationTimeStatus oneYear = new CultivationTimeStatus(
                0, 10_000, 24_000, 24, 6, true, 586_000, 576_000, false);
        CultivationTimeStatus exhausted = new CultivationTimeStatus(
                0, 586_000, 24_000, 24, 6, true, 586_000, 0, true);

        assertEquals(1, CultivationTimeRuntime.warningMarker(oneYear));
        assertEquals(
                CultivationTimeRuntime.EXHAUSTED_WARNING_MARKER,
                CultivationTimeRuntime.warningMarker(exhausted));
    }

    @Test
    void lifespanAccumulatorBatchesAtSixHundredAndRetainsFailedAmount() {
        CultivationTimeRuntime.PendingLifespan pending =
                new CultivationTimeRuntime.PendingLifespan();

        for (int tick = 0; tick < CultivationTimeRuntime.COMMIT_INTERVAL_TICKS; tick++) {
            pending.addEligibleTick();
        }
        assertEquals(600, pending.ticks());
        assertEquals(600, pending.ticksSinceAttempt());

        pending.resetAttemptInterval();
        assertEquals(600, pending.ticks());
        assertEquals(0, pending.ticksSinceAttempt());

        pending.removeCommitted(600);
        assertEquals(0, pending.ticks());
        assertThrows(IllegalArgumentException.class, () -> pending.removeCommitted(1));
    }

    @Test
    void sharedCalendarAdvancesWheneverAnyPlayerIsOnline() {
        // Game mode is not an input: one creative or spectator player keeps the world clock running.
        assertFalse(CultivationTimeRuntime.calendarAdvances(0));
        assertTrue(CultivationTimeRuntime.calendarAdvances(1));
        assertTrue(CultivationTimeRuntime.calendarAdvances(5));
    }
}
