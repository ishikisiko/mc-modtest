package com.example.myvillage.cultivation.study;

import com.example.myvillage.cultivation.SpiritualRoot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static com.example.myvillage.cultivation.study.StudyFixtures.FIRE;
import static com.example.myvillage.cultivation.study.StudyFixtures.HUANG_DEFINITION;
import static com.example.myvillage.cultivation.study.StudyFixtures.METAL;
import static com.example.myvillage.cultivation.study.StudyFixtures.RULES;
import static com.example.myvillage.cultivation.study.StudyFixtures.XUAN_DEFINITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StudySettlementTest {
    @Test
    void gainIsAffinityTimesElementBonusRoundedDownWithoutGradeMultiplier() {
        assertEquals(10, StudySettlement.gain(10, 0));
        assertEquals(11, StudySettlement.gain(10, 1_500));
        assertEquals(17, StudySettlement.gain(15, 1_500));
        assertEquals(0, StudySettlement.gain(0, 1_500));
        assertEquals(new StudySettlement.Plan(110, 0, StudySettlement.Outcome.CONTINUE, -1, 0),
                StudySettlement.plan(100, 12_000, 1, 50, 0, 10, 0));
    }

    @Test
    void elementBonusComesFromTheShippedRulesAtTheThresholdOnly() {
        assertEquals(1_500, RULES.roots().elementThresholdBp());
        assertEquals(1_500, StudyRules.elementBonusBasisPoints(
                XUAN_DEFINITION, root(1_500, 8_500), Optional.of(RULES)));
        assertEquals(0, StudyRules.elementBonusBasisPoints(
                XUAN_DEFINITION, root(1_499, 8_501), Optional.of(RULES)));
        assertEquals(0, StudyRules.elementBonusBasisPoints(
                XUAN_DEFINITION, Optional.empty(), Optional.of(RULES)));
        assertEquals(0, StudyRules.elementBonusBasisPoints(
                XUAN_DEFINITION, root(10_000, 0), Optional.empty()));
        assertEquals(1_500, StudyRules.elementBonusBasisPoints(
                HUANG_DEFINITION, root(6_000, 4_000), Optional.of(RULES)));
    }

    @Test
    void gatesSitEvenlyAndTheNextOneIsTheFirstNotPassed() {
        assertEquals(6_000, StudySettlement.gatePoints(12_000, 1, 1));
        assertEquals(12_000, StudySettlement.gatePoints(36_000, 2, 1));
        assertEquals(24_000, StudySettlement.gatePoints(36_000, 2, 2));
        assertEquals(24_000, StudySettlement.gatePoints(96_000, 3, 1));
        assertEquals(72_000, StudySettlement.gatePoints(96_000, 3, 3));
        assertEquals(6_000, StudySettlement.nextGate(0, 12_000, 1));
        assertEquals(6_000, StudySettlement.nextGate(6_000, 12_000, 1));
        assertEquals(-1, StudySettlement.nextGate(6_001, 12_000, 1));
        assertEquals(-1, StudySettlement.nextGate(0, 4_000, 0));
        assertEquals(24_000, StudySettlement.nextGate(12_001, 36_000, 2));
        assertThrows(IllegalArgumentException.class, () -> StudySettlement.gatePoints(12_000, 1, 2));
    }

    @Test
    void crossingAGatePaysOnceOrStopsExactlyOnItWithTheShortfall() {
        // 玄阶 12000 points, one gate at 6000 costing 50, affinity 10 with a metal match: +11 a batch.
        assertEquals(new StudySettlement.Plan(6_000, 0, StudySettlement.Outcome.GATE_BLOCKED, 6_000, 10),
                StudySettlement.plan(5_995, 12_000, 1, 50, 40, 10, 1_500));
        assertEquals(new StudySettlement.Plan(6_006, 50, StudySettlement.Outcome.CONTINUE, -1, 0),
                StudySettlement.plan(5_995, 12_000, 1, 50, 60, 10, 1_500));
        // Landing exactly on the gate does not pass it; the next batch does, and pays then.
        assertEquals(new StudySettlement.Plan(6_000, 0, StudySettlement.Outcome.CONTINUE, -1, 0),
                StudySettlement.plan(5_989, 12_000, 1, 50, 0, 10, 1_500));
        assertEquals(new StudySettlement.Plan(6_000, 0, StudySettlement.Outcome.GATE_BLOCKED, 6_000, 50),
                StudySettlement.plan(6_000, 12_000, 1, 50, 0, 10, 1_500));
        assertEquals(new StudySettlement.Plan(6_011, 50, StudySettlement.Outcome.CONTINUE, -1, 0),
                StudySettlement.plan(6_000, 12_000, 1, 50, 50, 10, 1_500));
        // A passed gate costs nothing again.
        assertEquals(new StudySettlement.Plan(6_022, 0, StudySettlement.Outcome.CONTINUE, -1, 0),
                StudySettlement.plan(6_011, 12_000, 1, 50, 0, 10, 1_500));
    }

    @Test
    void severalGatesInOneBatchPayInOrderAndStopAtTheFirstUnaffordable() {
        // Gates of 30 points at 10, 20: a 25-point batch from 5 crosses both.
        assertEquals(new StudySettlement.Plan(30, 8, StudySettlement.Outcome.COMPLETE, -1, 0),
                StudySettlement.plan(5, 30, 2, 4, 8, 25, 0));
        assertEquals(new StudySettlement.Plan(20, 4, StudySettlement.Outcome.GATE_BLOCKED, 20, 1),
                StudySettlement.plan(5, 30, 2, 4, 7, 25, 0));
        assertEquals(new StudySettlement.Plan(10, 0, StudySettlement.Outcome.GATE_BLOCKED, 10, 4),
                StudySettlement.plan(5, 30, 2, 4, 0, 25, 0));
    }

    @Test
    void reachingTheTotalCompletesAndClamps() {
        assertEquals(new StudySettlement.Plan(12_000, 0, StudySettlement.Outcome.COMPLETE, -1, 0),
                StudySettlement.plan(11_995, 12_000, 1, 50, 0, 10, 1_500));
        assertEquals(new StudySettlement.Plan(4_000, 0, StudySettlement.Outcome.COMPLETE, -1, 0),
                StudySettlement.plan(3_990, 4_000, 0, 0, 0, 10, 0));
        assertEquals(new StudySettlement.Plan(4_000, 0, StudySettlement.Outcome.COMPLETE, -1, 0),
                StudySettlement.plan(9_999, 4_000, 0, 0, 0, 10, 0));
        assertEquals(new StudySettlement.Plan(3_989, 0, StudySettlement.Outcome.CONTINUE, -1, 0),
                StudySettlement.plan(3_979, 4_000, 0, 0, 0, 10, 0));
    }

    @Test
    void fullStudyOfAHuangManualTakesFourHundredBatchesAtAffinityTen() {
        int points = 0;
        int batches = 0;
        StudySettlement.Plan plan;
        do {
            plan = StudySettlement.plan(points, 4_000, 0, 0, 0, 10, 0);
            points = plan.points();
            batches++;
        } while (plan.outcome() == StudySettlement.Outcome.CONTINUE);
        assertEquals(StudySettlement.Outcome.COMPLETE, plan.outcome());
        assertEquals(400, batches);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> StudySettlement.plan(0, 0, 0, 0, 0, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> StudySettlement.plan(-1, 10, 0, 0, 0, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> StudySettlement.plan(0, 10, -1, 0, 0, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> StudySettlement.plan(0, 10, 0, -1, 0, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> StudySettlement.plan(0, 10, 0, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> StudySettlement.plan(0, 10, 0, 0, 0, 10, -1));
    }

    private static Optional<SpiritualRoot> root(int metal, int fire) {
        return Optional.of(new SpiritualRoot(Map.of(METAL, metal, FIRE, fire)));
    }
}
