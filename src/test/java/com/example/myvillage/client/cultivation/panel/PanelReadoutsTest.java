package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PanelReadoutsTest {
    private static final long TICKS_PER_DAY = 24_000;
    private static final int DAYS_PER_YEAR = 24;
    private static final int DAYS_PER_WEEK = 6;
    private static final long YEAR = TICKS_PER_DAY * DAYS_PER_YEAR;

    @Test
    void fractionClampsAndTreatsAMissingCapAsEmpty() {
        assertEquals(0.0D, PanelReadouts.fraction(0, 1000));
        assertEquals(0.25D, PanelReadouts.fraction(250, 1000));
        assertEquals(1.0D, PanelReadouts.fraction(1000, 1000));
        assertEquals(1.0D, PanelReadouts.fraction(5000, 1000));
        assertEquals(0.0D, PanelReadouts.fraction(-5, 1000));
        assertEquals(0.0D, PanelReadouts.fraction(10, 0));
    }

    @Test
    void calendarStartsAtYearOneDayOne() {
        assertEquals(1, PanelReadouts.calendarYear(0, TICKS_PER_DAY, DAYS_PER_YEAR));
        assertEquals(1, PanelReadouts.calendarDay(0, TICKS_PER_DAY, DAYS_PER_YEAR));
        assertEquals(1, PanelReadouts.calendarYear(YEAR - 1, TICKS_PER_DAY, DAYS_PER_YEAR));
        assertEquals(24, PanelReadouts.calendarDay(YEAR - 1, TICKS_PER_DAY, DAYS_PER_YEAR));
        assertEquals(2, PanelReadouts.calendarYear(YEAR, TICKS_PER_DAY, DAYS_PER_YEAR));
        assertEquals(1, PanelReadouts.calendarDay(YEAR, TICKS_PER_DAY, DAYS_PER_YEAR));
    }

    @Test
    void weeksSplitTheYearIntoSixDayRuns() {
        assertWeekDate(1, 1, 1, 0);
        assertWeekDate(1, 1, 6, 5 * TICKS_PER_DAY);
        assertWeekDate(1, 2, 1, 6 * TICKS_PER_DAY);
        assertWeekDate(1, 4, 6, 23 * TICKS_PER_DAY + TICKS_PER_DAY - 1);
        assertWeekDate(2, 1, 1, 24 * TICKS_PER_DAY);
    }

    @Test
    void aYearThatIsNotWholeWeeksEndsOnAShortWeekAndRestartsTheCount() {
        long day = 24_000;
        // Ten days a year, four a week: weeks of 4, 4 and 2 days.
        assertEquals(3, PanelReadouts.calendarWeek(8 * day, day, 10, 4));
        assertEquals(1, PanelReadouts.calendarDayOfWeek(8 * day, day, 10, 4));
        assertEquals(3, PanelReadouts.calendarWeek(9 * day, day, 10, 4));
        assertEquals(2, PanelReadouts.calendarDayOfWeek(9 * day, day, 10, 4));
        assertEquals(1, PanelReadouts.calendarWeek(10 * day, day, 10, 4));
        assertEquals(1, PanelReadouts.calendarDayOfWeek(10 * day, day, 10, 4));
        assertEquals(2, PanelReadouts.calendarYear(10 * day, day, 10));
    }

    private static void assertWeekDate(long year, long week, long dayOfWeek, long elapsedTicks) {
        assertEquals(year, PanelReadouts.calendarYear(elapsedTicks, TICKS_PER_DAY, DAYS_PER_YEAR));
        assertEquals(week, PanelReadouts.calendarWeek(elapsedTicks, TICKS_PER_DAY, DAYS_PER_YEAR, DAYS_PER_WEEK));
        assertEquals(dayOfWeek,
                PanelReadouts.calendarDayOfWeek(elapsedTicks, TICKS_PER_DAY, DAYS_PER_YEAR, DAYS_PER_WEEK));
    }

    @Test
    void consumedYearsRoundDownAndRemainingYearsRoundUp() {
        assertEquals(0, PanelReadouts.yearsFloor(YEAR - 1, TICKS_PER_DAY, DAYS_PER_YEAR));
        assertEquals(1, PanelReadouts.yearsFloor(YEAR, TICKS_PER_DAY, DAYS_PER_YEAR));
        assertEquals(80, PanelReadouts.yearsCeil(80 * YEAR, TICKS_PER_DAY, DAYS_PER_YEAR));
        assertEquals(80, PanelReadouts.yearsCeil(79 * YEAR + 1, TICKS_PER_DAY, DAYS_PER_YEAR));
        assertEquals(0, PanelReadouts.yearsCeil(0, TICKS_PER_DAY, DAYS_PER_YEAR));
    }

    @Test
    void anOverflowingYearLengthSaturates() {
        assertEquals(Long.MAX_VALUE, PanelReadouts.ticksPerYear(Long.MAX_VALUE, 2));
        assertEquals(0, PanelReadouts.yearsFloor(1_000_000, Long.MAX_VALUE, 2));
        assertEquals(1, PanelReadouts.yearsCeil(1_000_000, Long.MAX_VALUE, 2));
    }

    @Test
    void stageIndexFindsTheStageOrReportsItMissing() {
        List<RealmStageDefinition> stages = List.of(
                new RealmStageDefinition(ModCultivationRegistries.QI_REFINING_1_STAGE_ID, "stage.one", 0),
                new RealmStageDefinition(ModCultivationRegistries.QI_REFINING_2_STAGE_ID, "stage.two", 1));

        assertEquals(0, PanelReadouts.stageIndex(stages, ModCultivationRegistries.QI_REFINING_1_STAGE_ID));
        assertEquals(1, PanelReadouts.stageIndex(stages, ModCultivationRegistries.QI_REFINING_2_STAGE_ID));
        assertEquals(-1, PanelReadouts.stageIndex(stages, ModCultivationRegistries.QI_REFINING_3_STAGE_ID));
        assertEquals(-1, PanelReadouts.stageIndex(List.of(), ModCultivationRegistries.QI_REFINING_1_STAGE_ID));
    }
}
