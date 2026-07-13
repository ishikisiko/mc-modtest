package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.data.AdvancementDefinition;
import com.example.myvillage.cultivation.data.AdvancementKind;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdvancementProfileTransitionTest {
    @Test
    void deterministicSuccessResetsStageProgressAndPreservesAllUnrelatedFields() {
        CultivationProfile current = sensedProfile()
                .withCultivationProgress(1000)
                .withStability(501);
        AdvancementContext context = ordinaryContext();

        CultivationProfile replacement = AdvancementProfileTransition.successReplacement(
                current, context);

        assertEquals(ModCultivationRegistries.QI_REFINING_REALM_ID, replacement.realmId());
        assertEquals(ModCultivationRegistries.QI_REFINING_1_STAGE_ID, replacement.stageId());
        assertEquals(0, replacement.cultivationProgress());
        assertEquals(250, replacement.stability());
        assertEquals(current.currentSpiritualPower(), replacement.currentSpiritualPower());
        assertEquals(current.spiritualAffinity(), replacement.spiritualAffinity());
        assertEquals(current.lifespanConsumedTicks(), replacement.lifespanConsumedTicks());
        assertEquals(current.meditationQiReserve(), replacement.meditationQiReserve());
        assertEquals(current.spiritualRoot(), replacement.spiritualRoot());
        assertEquals(current.learnedTechniques(), replacement.learnedTechniques());
    }

    @Test
    void ordinaryInterruptionIsFreeAndBottleneckLossClampsAtZero() {
        CultivationProfile ordinary = sensedProfile().withCultivationProgress(1000).withStability(500);
        assertSame(
                ordinary,
                AdvancementProfileTransition.interruptionPenaltyReplacement(
                        ordinary, ordinaryContext()));

        CultivationProfile bottleneck = sensedProfile()
                .withRealmAndStage(
                        ModCultivationRegistries.QI_REFINING_REALM_ID,
                        ModCultivationRegistries.QI_REFINING_3_STAGE_ID)
                .withCultivationProgress(1_300)
                .withStability(3);
        AdvancementContext bottleneckContext = new AdvancementContext(
                ModCultivationRegistries.QI_REFINING_REALM_ID,
                ModCultivationRegistries.QI_REFINING_3_STAGE_ID,
                1_300,
                new AdvancementDefinition(
                        ModCultivationRegistries.QI_REFINING_REALM_ID,
                        ModCultivationRegistries.QI_REFINING_4_STAGE_ID,
                        AdvancementKind.BOTTLENECK,
                        200,
                        650,
                        325,
                        5));

        CultivationProfile penalized = AdvancementProfileTransition
                .interruptionPenaltyReplacement(bottleneck, bottleneckContext);
        assertEquals(0, penalized.stability());
        assertEquals(bottleneck.cultivationProgress(), penalized.cultivationProgress());
    }

    @Test
    void successFailsClosedWhenSourceOrGatesNoLongerMatch() {
        AdvancementContext context = ordinaryContext();

        assertThrows(IllegalArgumentException.class, () ->
                AdvancementProfileTransition.successReplacement(
                        sensedProfile().withCultivationProgress(999).withStability(500), context));
        assertThrows(IllegalArgumentException.class, () ->
                AdvancementProfileTransition.successReplacement(
                        sensedProfile().withCultivationProgress(1000).withStability(499), context));
        assertThrows(IllegalArgumentException.class, () ->
                AdvancementProfileTransition.successReplacement(
                        sensedProfile()
                                .withRealmAndStage(
                                        ModCultivationRegistries.QI_REFINING_REALM_ID,
                                        ModCultivationRegistries.QI_REFINING_1_STAGE_ID)
                                .withCultivationProgress(1000)
                                .withStability(500),
                        context));
    }

    private static AdvancementContext ordinaryContext() {
        return new AdvancementContext(
                ModCultivationRegistries.MORTAL_REALM_ID,
                ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID,
                1000,
                new AdvancementDefinition(
                        ModCultivationRegistries.QI_REFINING_REALM_ID,
                        ModCultivationRegistries.QI_REFINING_1_STAGE_ID,
                        AdvancementKind.ORDINARY,
                        100,
                        500,
                        250,
                        0));
    }

    private static CultivationProfile sensedProfile() {
        return CultivationProfile.defaultProfile()
                .withRealmAndStage(
                        ModCultivationRegistries.MORTAL_REALM_ID,
                        ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID)
                .withCurrentSpiritualPower(77)
                .withSpiritualAffinity(47)
                .withLifespanConsumedTicks(1_234)
                .withMeditationQiReserve(55)
                .withSpiritualRoot(new SpiritualRoot(Map.of(
                        ModCultivationRegistries.FIRE_ELEMENT_ID, 10_000)))
                .learnTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .withTechniqueMastery(
                        ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID, 99);
    }
}
