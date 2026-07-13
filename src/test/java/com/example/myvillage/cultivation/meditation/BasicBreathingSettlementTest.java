package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicBreathingSettlementTest {
    @Test
    void masteryRemainderReconcilesToExactConfiguredYearRate() {
        BasicBreathingSettlement.Remainders remainders =
                BasicBreathingSettlement.Remainders.ZERO;
        long mastery = 0;

        for (int batch = 0; batch < 14_400; batch++) {
            BasicBreathingSettlement.Accrual accrual = BasicBreathingSettlement.accrue(
                    remainders, 10, 144_000);
            mastery += accrual.masteryDue();
            remainders = accrual.remainders();
        }

        assertEquals(10, mastery);
        assertEquals(BasicBreathingSettlement.Remainders.ZERO, remainders);
    }

    @Test
    void normalSettlementUsesCurrentAffinityButLocksStabilityBelowProgressCap() {
        CultivationProfile current = profile(100, 10, 77, 7).withSpiritualAffinity(23);

        BasicBreathingSettlement.Plan plan = BasicBreathingSettlement.plan(
                current, 1000, MeditationMode.NORMAL, accrual(3), false);

        assertEquals(123, plan.replacement().cultivationProgress());
        assertEquals(10, plan.replacement().stability());
        assertEquals(10, plan.replacement().learnedTechniques()
                .get(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .masteryPoints());
        assertEquals(77, plan.replacement().meditationQiReserve());
        assertEquals(23, plan.progressApplied());
        assertEquals(0, plan.stabilityApplied());
        assertFalse(plan.consumeSpiritStones());
        assertFalse(plan.downgradeToNormal());
    }

    @Test
    void normalSettlementSupportsDefaultAndZeroAffinityExactly() {
        CultivationProfile defaultAffinity = profile(100, 10, 7, 0);
        BasicBreathingSettlement.Plan defaultPlan = BasicBreathingSettlement.plan(
                defaultAffinity, 1000, MeditationMode.NORMAL, accrual(0), false);
        assertEquals(110, defaultPlan.replacement().cultivationProgress());
        assertEquals(10, defaultPlan.progressApplied());
        assertEquals(0, defaultPlan.stabilityApplied());

        CultivationProfile zeroAffinity = defaultAffinity.withSpiritualAffinity(0);
        BasicBreathingSettlement.Plan zeroPlan = BasicBreathingSettlement.plan(
                zeroAffinity, 1000, MeditationMode.NORMAL, accrual(1), false);
        assertEquals(100, zeroPlan.replacement().cultivationProgress());
        assertEquals(0, zeroPlan.progressApplied());
        assertEquals(10, zeroPlan.replacement().stability());
        assertEquals(1, zeroPlan.replacement().learnedTechniques()
                .get(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .masteryPoints());
    }

    @Test
    void fundedSpiritSettlementAppliesFiftyAndLeavesReserveInert() {
        CultivationProfile current = profile(100, 10, 41, 0).withSpiritualAffinity(3);

        BasicBreathingSettlement.Plan plan = BasicBreathingSettlement.plan(
                current, 1000, MeditationMode.SPIRIT, accrual(0), true);

        assertEquals(150, plan.replacement().cultivationProgress());
        assertEquals(10, plan.replacement().stability());
        assertEquals(41, plan.replacement().meditationQiReserve());
        assertEquals(50, plan.progressApplied());
        assertEquals(0, plan.stabilityApplied());
        assertTrue(plan.consumeSpiritStones());
        assertFalse(plan.downgradeToNormal());
    }

    @Test
    void unfundedSpiritSettlementUsesAffinityAndDowngrades() {
        CultivationProfile current = profile(100, 10, 41, 0).withSpiritualAffinity(17);

        BasicBreathingSettlement.Plan plan = BasicBreathingSettlement.plan(
                current, 1000, MeditationMode.SPIRIT, accrual(0), false);

        assertEquals(117, plan.replacement().cultivationProgress());
        assertEquals(10, plan.replacement().stability());
        assertEquals(41, plan.replacement().meditationQiReserve());
        assertEquals(17, plan.progressApplied());
        assertFalse(plan.consumeSpiritStones());
        assertTrue(plan.downgradeToNormal());
    }

    @Test
    void batchThatFirstReachesProgressCapDoesNotGrowStability() {
        CultivationProfile current = profile(995, 10, 9, 0).withSpiritualAffinity(23);

        BasicBreathingSettlement.Plan plan = BasicBreathingSettlement.plan(
                current, 1000, MeditationMode.SPIRIT, accrual(0), true);

        assertEquals(1000, plan.replacement().cultivationProgress());
        assertEquals(10, plan.replacement().stability());
        assertEquals(5, plan.progressApplied());
        assertEquals(0, plan.stabilityApplied());
        assertTrue(plan.consumeSpiritStones());
        assertEquals(9, plan.replacement().meditationQiReserve());
    }

    @Test
    void nextCappedNormalBatchUsesAffinityAndClampsToHalfProgressCap() {
        CultivationProfile current = profile(1000, 490, 42, 4).withSpiritualAffinity(23);

        BasicBreathingSettlement.Plan plan = BasicBreathingSettlement.plan(
                current, 1000, MeditationMode.NORMAL, accrual(1), false);

        assertEquals(1000, plan.replacement().cultivationProgress());
        assertEquals(500, plan.replacement().stability());
        assertEquals(10, plan.stabilityApplied());
        assertEquals(5, plan.replacement().learnedTechniques()
                .get(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .masteryPoints());
        assertFalse(plan.consumeSpiritStones());
    }

    @Test
    void cappedSpiritBatchUsesAffinityForStabilityWithoutConsumingStone() {
        CultivationProfile current = profile(1100, 100, 42, 0).withSpiritualAffinity(37);

        BasicBreathingSettlement.Plan plan = BasicBreathingSettlement.plan(
                current, 1100, MeditationMode.SPIRIT, accrual(0), false);

        assertEquals(1100, plan.replacement().cultivationProgress());
        assertEquals(137, plan.replacement().stability());
        assertEquals(37, plan.stabilityApplied());
        assertEquals(0, plan.progressApplied());
        assertFalse(plan.consumeSpiritStones());
        assertFalse(plan.downgradeToNormal());
    }

    @Test
    void legacyOverCapValuesAreNotReducedBySettlement() {
        CultivationProfile current = profile(1001, 501, 42, 4).withSpiritualAffinity(37);

        BasicBreathingSettlement.Plan plan = BasicBreathingSettlement.plan(
                current, 1000, MeditationMode.SPIRIT, accrual(1), false);

        assertEquals(1001, plan.replacement().cultivationProgress());
        assertEquals(501, plan.replacement().stability());
        assertEquals(0, plan.progressApplied());
        assertEquals(0, plan.stabilityApplied());
        assertEquals(5, plan.replacement().learnedTechniques()
                .get(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .masteryPoints());
        assertFalse(plan.consumeSpiritStones());
        assertFalse(plan.downgradeToNormal());
    }

    private static CultivationProfile profile(
            long progress, int stability, long reserve, long mastery) {
        return CultivationProfile.defaultProfile()
                .withCultivationProgress(progress)
                .withStability(stability)
                .withMeditationQiReserve(reserve)
                .learnTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .withTechniqueMastery(
                        ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID, mastery);
    }

    private static BasicBreathingSettlement.Accrual accrual(long mastery) {
        return new BasicBreathingSettlement.Accrual(
                mastery,
                BasicBreathingSettlement.Remainders.ZERO);
    }
}
