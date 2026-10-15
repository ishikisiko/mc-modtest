package com.example.myvillage.alchemy;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.meditation.BasicBreathingSettlement;
import com.example.myvillage.cultivation.meditation.MeditationMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicineSettlementTest {
    private static BasicBreathingSettlement.Plan settle(CultivationProfile profile, MeditationMode mode, double factor) {
        return BasicBreathingSettlement.plan(profile, 1000, mode,
                new BasicBreathingSettlement.Accrual(3, BasicBreathingSettlement.Remainders.ZERO),
                true, 10000, factor);
    }
    private static CultivationProfile profile(long progress, int stability) {
        return CultivationProfile.defaultProfile()
                .learnTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .withCultivationProgress(progress).withStability(stability);
    }
    @Test void postCapGainScalesWithoutChangingMasteryOrProgressOrStoneCost() {
        var current = profile(1000, 100);
        var ordinary = settle(current, MeditationMode.SPIRIT, 1);
        var boosted = settle(current, MeditationMode.SPIRIT,
                AlchemyRules.get().effects().ningshen().stabilityMultiplier());
        assertEquals(15, boosted.stabilityApplied());
        assertEquals(10, ordinary.stabilityApplied());
        assertEquals(ordinary.progressApplied(), boosted.progressApplied());
        assertEquals(ordinary.consumeSpiritStones(), boosted.consumeSpiritStones());
        assertEquals(ordinary.replacement().learnedTechniques(), boosted.replacement().learnedTechniques());
        assertEquals(ordinary.replacement().withStability(115), boosted.replacement());
    }
    @Test void progressAndCapCrossingGainNoMedicineBenefit() {
        for (long progress : new long[] {100, 995}) {
            var current = profile(progress, 100);
            assertEquals(settle(current, MeditationMode.NORMAL, 1), settle(current, MeditationMode.NORMAL, 1.5));
            assertEquals(settle(current, MeditationMode.SPIRIT, 1), settle(current, MeditationMode.SPIRIT, 1.5));
        }
    }
    @Test void medicineCannotRaiseStageStabilityCap() {
        assertEquals(500, settle(profile(1000, 497), MeditationMode.NORMAL, 1.5).replacement().stability());
        assertEquals(0, settle(profile(1000, 500), MeditationMode.NORMAL, 1.5).stabilityApplied());
    }
    @Test void studyPlanIsUnchangedEvenIfPassedMedicineFactor() {
        assertEquals(settle(profile(1000, 100), MeditationMode.STUDY, 1),
                settle(profile(1000, 100), MeditationMode.STUDY, 1.5));
    }
}
