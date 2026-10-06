package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.meditation.MeditationState;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import com.example.myvillage.cultivation.meditation.StudyProgress;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static final ResourceLocation STUDIED = ResourceLocation.fromNamespaceAndPath("myvillage", "gengjin_jianjue");

    @Test
    void studyPercentRoundsDownLikeTheManualTooltip() {
        assertEquals(0, PanelReadouts.studyPercent(new StudyProgress(STUDIED, 0, 12_000, 6_000, 50)));
        assertEquals(0, PanelReadouts.studyPercent(new StudyProgress(STUDIED, 119, 12_000, 6_000, 50)));
        assertEquals(1, PanelReadouts.studyPercent(new StudyProgress(STUDIED, 120, 12_000, 6_000, 50)));
        assertEquals(37, PanelReadouts.studyPercent(new StudyProgress(STUDIED, 4_479, 12_000, 6_000, 50)));
        assertEquals(99, PanelReadouts.studyPercent(new StudyProgress(STUDIED, 95_999, 96_000, StudyProgress.NO_GATE, 150)));
        assertEquals(100, PanelReadouts.studyPercent(new StudyProgress(STUDIED, 4_000, 4_000, StudyProgress.NO_GATE, 0)));
        assertEquals(0.5D, PanelReadouts.studyFraction(new StudyProgress(STUDIED, 6_000, 12_000, 6_000, 50)));
    }

    @Test
    void studyGateTextNamesTheNextGateOrNone() {
        StudyProgress gated = new StudyProgress(STUDIED, 5_990, 12_000, 6_000, 50);
        StudyProgress passed = new StudyProgress(STUDIED, 6_010, 12_000, StudyProgress.NO_GATE, 50);
        StudyProgress gateless = new StudyProgress(STUDIED, 100, 4_000, StudyProgress.NO_GATE, 0);

        assertEquals("screen.myvillage.cultivation.study.gate", PanelReadouts.studyGateKey(gated));
        assertEquals("screen.myvillage.cultivation.study.no_gate", PanelReadouts.studyGateKey(passed));
        assertEquals("screen.myvillage.cultivation.study.no_gate", PanelReadouts.studyGateKey(gateless));
        assertTrue(PanelReadouts.studyGateShort(gated, 49));
        assertFalse(PanelReadouts.studyGateShort(gated, 50));
        assertFalse(PanelReadouts.studyGateShort(passed, 0));
        assertFalse(PanelReadouts.studyGateShort(gateless, 0));
    }

    @Test
    void sessionTextReadsStudyWhileAManualIsRead() {
        StudyProgress progress = new StudyProgress(STUDIED, 0, 12_000, 6_000, 50);

        assertEquals("screen.myvillage.cultivation.time_waiting", PanelReadouts.sessionKey(null));
        assertEquals("screen.myvillage.cultivation.study.preparing", PanelReadouts.sessionKey(MeditationStatus.study(
                MeditationState.PREPARING_NORMAL, 40, MeditationStopReason.STUDY_ACCEPTED, progress)));
        assertEquals("screen.myvillage.cultivation.study.reading", PanelReadouts.sessionKey(MeditationStatus.study(
                MeditationState.MEDITATING_NORMAL, 0, MeditationStopReason.NONE, progress)));
        assertEquals("screen.myvillage.cultivation.session.preparing_normal", PanelReadouts.sessionKey(
                new MeditationStatus(MeditationState.PREPARING_NORMAL, 40, MeditationStopReason.START_ACCEPTED)));
        assertEquals("screen.myvillage.cultivation.session.meditating_normal", PanelReadouts.sessionKey(
                new MeditationStatus(MeditationState.MEDITATING_NORMAL, 0, MeditationStopReason.NONE)));
        assertEquals("screen.myvillage.cultivation.session.idle", PanelReadouts.sessionKey(
                MeditationStatus.idle(MeditationStopReason.STUDY_COMPLETE)));
    }
}
