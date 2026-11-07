package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.AdvancementDefinition;
import com.example.myvillage.cultivation.data.AdvancementKind;
import com.example.myvillage.cultivation.meditation.MeditationState;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import com.example.myvillage.cultivation.meditation.StudyProgress;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelReadoutsTest {
    @Test
    void releaseCeilingFollowsSyncedDefinitionsRatherThanAStageId() {
        ResourceLocation firstId = ResourceLocation.parse("addon:first");
        ResourceLocation lastId = ResourceLocation.parse("addon:last");
        RealmStageDefinition first = new RealmStageDefinition(firstId, "stage.first", 0);
        RealmStageDefinition last = new RealmStageDefinition(lastId, "stage.last", 1);
        RealmDefinition finalRealm = new RealmDefinition("realm.final", 9, 500,
                List.of(first, last), Optional.empty());
        assertTrue(PanelReadouts.releaseCeiling(finalRealm, lastId));
        assertFalse(PanelReadouts.releaseCeiling(finalRealm, firstId));
        assertFalse(PanelReadouts.releaseCeiling(finalRealm, ResourceLocation.parse("addon:unknown")));
        assertFalse(PanelReadouts.releaseCeiling(null, lastId));
        assertFalse(PanelReadouts.releaseCeiling(new RealmDefinition("realm.next", 9, 500,
                List.of(first, last), Optional.of(ResourceLocation.parse("addon:next"))), lastId));
        RealmStageDefinition capped = new RealmStageDefinition(lastId, "stage.last", 1,
                Optional.of(1000L), Optional.of(1), Optional.empty());
        assertFalse(PanelReadouts.releaseCeiling(new RealmDefinition("realm.capped", 9, 500,
                List.of(first, capped), Optional.empty()), lastId));
        RealmStageDefinition advancing = new RealmStageDefinition(lastId, "stage.last", 1,
                Optional.empty(), Optional.empty(), Optional.of(new AdvancementDefinition(
                        ResourceLocation.parse("addon:next"), ResourceLocation.parse("addon:next_stage"),
                        AdvancementKind.ORDINARY, 100, 0, 0, 0)));
        assertFalse(PanelReadouts.releaseCeiling(new RealmDefinition("realm.advancing", 9, 500,
                List.of(first, advancing), Optional.empty()), lastId));
    }

    private static final long TICKS_PER_DAY = 24_000;
    private static final int DAYS_PER_YEAR = 24;
    private static final int DAYS_PER_MONTH = 2;
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
    void calendarShowsAllTwelveTwoDayMonthsAndRestartsAtNewYear() {
        for (int month = 1; month <= 12; month++) {
            long start = (month - 1) * DAYS_PER_MONTH * TICKS_PER_DAY;
            assertMonthDate(1, month, 1, start);
            assertMonthDate(1, month, 2, start + TICKS_PER_DAY);
            assertMonthDate(1, month, 2, start + 2 * TICKS_PER_DAY - 1);
        }
        assertMonthDate(2, 1, 1, YEAR);
    }

    @Test
    void customThirtyDayMonthsStillMakeExactlyTwelveMonths() {
        long day = 24_000;
        assertEquals(1, PanelReadouts.calendarMonth(29 * day, day, 360, 30));
        assertEquals(30, PanelReadouts.calendarDayOfMonth(29 * day, day, 360, 30));
        assertEquals(2, PanelReadouts.calendarMonth(30 * day, day, 360, 30));
        assertEquals(1, PanelReadouts.calendarDayOfMonth(30 * day, day, 360, 30));
        assertEquals(12, PanelReadouts.calendarMonth(360 * day - 1, day, 360, 30));
        assertEquals(30, PanelReadouts.calendarDayOfMonth(360 * day - 1, day, 360, 30));
        assertEquals(2, PanelReadouts.calendarYear(360 * day, day, 360));
        assertEquals(1, PanelReadouts.calendarMonth(360 * day, day, 360, 30));
        assertEquals(1, PanelReadouts.calendarDayOfMonth(360 * day, day, 360, 30));
    }

    private static void assertMonthDate(long year, long month, long dayOfMonth, long elapsedTicks) {
        assertEquals(year, PanelReadouts.calendarYear(elapsedTicks, TICKS_PER_DAY, DAYS_PER_YEAR));
        assertEquals(month, PanelReadouts.calendarMonth(elapsedTicks, TICKS_PER_DAY, DAYS_PER_YEAR, DAYS_PER_MONTH));
        assertEquals(dayOfMonth,
                PanelReadouts.calendarDayOfMonth(elapsedTicks, TICKS_PER_DAY, DAYS_PER_YEAR, DAYS_PER_MONTH));
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
