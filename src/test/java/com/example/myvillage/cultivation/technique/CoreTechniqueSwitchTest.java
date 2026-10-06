package com.example.myvillage.cultivation.technique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueLineage;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CoreTechniqueSwitchTest {
    private static final ResourceLocation BREATHING = ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID;
    private static final ResourceLocation YINQI = id("gengjin_yinqi_fa");
    private static final ResourceLocation JIANJUE = id("gengjin_jianjue");
    private static final ResourceLocation JIANDIAN = id("tiangang_jiandian");
    private static final ResourceLocation STRANGER = id("qingmu_changchun_gong");
    private static final int LOSS = 3_000;

    /** basic_breathing; a chain yinqi (core) -> jianjue (active) -> jiandian (core); an unrelated core. */
    private static final Map<ResourceLocation, TechniqueDefinition> TECHNIQUES = new HashMap<>(Map.of(
            BREATHING, technique(TechniqueCategory.CORE, 0, null),
            YINQI, technique(TechniqueCategory.CORE, 1, null),
            JIANJUE, technique(TechniqueCategory.ACTIVE, 2, YINQI),
            JIANDIAN, technique(TechniqueCategory.CORE, 3, JIANJUE),
            STRANGER, technique(TechniqueCategory.CORE, 1, null)));
    private static final Function<ResourceLocation, TechniqueDefinition> LOOKUP = TECHNIQUES::get;

    @Test
    void unknownNotLearnedAndNonCoreTargetsAreRejectedWithoutChange() {
        CultivationProfile current = profile(1_000, BREATHING, YINQI, JIANJUE);

        assertRejected(current, id("missing"), CoreTechniqueSwitch.Status.UNKNOWN_TECHNIQUE);
        assertRejected(current, null, CoreTechniqueSwitch.Status.UNKNOWN_TECHNIQUE);
        assertRejected(current, STRANGER, CoreTechniqueSwitch.Status.NOT_LEARNED);
        assertRejected(current, JIANJUE, CoreTechniqueSwitch.Status.NOT_CORE);
    }

    @Test
    void theRunningTechniqueAgainSucceedsWithoutChange() {
        CultivationProfile current = profile(1_000, BREATHING, YINQI);

        CoreTechniqueSwitch.Plan plan = CoreTechniqueSwitch.plan(current, BREATHING, LOOKUP, LOSS);

        assertEquals(CoreTechniqueSwitch.Status.ALREADY_ACTIVE, plan.status());
        assertTrue(plan.success());
        assertFalse(plan.changed());
        assertSame(current, plan.replacement());
    }

    @Test
    void switchingOutsideAChainDispersesTheConfiguredFraction() {
        CultivationProfile current = profile(1_001, BREATHING, YINQI, STRANGER)
                .withStability(40);

        CoreTechniqueSwitch.Plan plan = CoreTechniqueSwitch.plan(current, YINQI, LOOKUP, LOSS);

        assertEquals(CoreTechniqueSwitch.Status.SWITCHED, plan.status());
        assertEquals(300, plan.progressLost());
        assertEquals(701, plan.replacement().cultivationProgress());
        assertEquals(Optional.of(YINQI), plan.replacement().activeCoreTechnique());
        assertEquals(40, plan.replacement().stability());
        assertEquals(current.learnedTechniques(), plan.replacement().learnedTechniques());
        assertEquals(Optional.of(BREATHING), current.activeCoreTechnique());
        assertEquals(1_001, current.cultivationProgress());

        CoreTechniqueSwitch.Plan stranger = CoreTechniqueSwitch.plan(
                profile(1_000, YINQI, STRANGER), STRANGER, LOOKUP, LOSS);
        assertEquals(300, stranger.progressLost());
        assertFalse(stranger.sameLineage());
    }

    @Test
    void switchingAlongAHeritageChainLosesNothingInEitherDirection() {
        CultivationProfile onYinqi = profile(1_200, YINQI, JIANJUE, JIANDIAN);

        CoreTechniqueSwitch.Plan up = CoreTechniqueSwitch.plan(onYinqi, JIANDIAN, LOOKUP, LOSS);
        assertEquals(CoreTechniqueSwitch.Status.SWITCHED, up.status());
        assertTrue(up.sameLineage());
        assertEquals(0, up.progressLost());
        assertEquals(1_200, up.replacement().cultivationProgress());
        assertEquals(Optional.of(JIANDIAN), up.replacement().activeCoreTechnique());

        CoreTechniqueSwitch.Plan down = CoreTechniqueSwitch.plan(up.replacement(), YINQI, LOOKUP, LOSS);
        assertTrue(down.sameLineage());
        assertEquals(0, down.progressLost());
        assertEquals(Optional.of(YINQI), down.replacement().activeCoreTechnique());
    }

    @Test
    void directLineageNeighboursAreOneChain() {
        Map<ResourceLocation, TechniqueDefinition> pair = Map.of(
                YINQI, technique(TechniqueCategory.CORE, 1, null),
                JIANDIAN, technique(TechniqueCategory.CORE, 2, YINQI));
        assertTrue(CoreTechniqueSwitch.sameLineage(YINQI, JIANDIAN, pair::get));
        assertTrue(CoreTechniqueSwitch.sameLineage(JIANDIAN, YINQI, pair::get));
        assertFalse(CoreTechniqueSwitch.sameLineage(YINQI, STRANGER, LOOKUP));
    }

    @Test
    void cyclicLineageTerminates() {
        Map<ResourceLocation, TechniqueDefinition> cycle = Map.of(
                YINQI, technique(TechniqueCategory.CORE, 1, JIANDIAN),
                JIANDIAN, technique(TechniqueCategory.CORE, 1, YINQI));
        assertTrue(CoreTechniqueSwitch.sameLineage(YINQI, JIANDIAN, cycle::get));
        assertFalse(CoreTechniqueSwitch.precedes(STRANGER, YINQI, cycle::get));
    }

    @Test
    void firstCoreWithNothingRunningLosesNothing() {
        CultivationProfile current = CultivationProfile.defaultProfile()
                .learnTechnique(YINQI)
                .withCultivationProgress(500);
        assertTrue(current.activeCoreTechnique().isEmpty());

        CoreTechniqueSwitch.Plan plan = CoreTechniqueSwitch.plan(current, YINQI, LOOKUP, LOSS);

        assertEquals(CoreTechniqueSwitch.Status.SWITCHED, plan.status());
        assertEquals(0, plan.progressLost());
        assertEquals(500, plan.replacement().cultivationProgress());
    }

    @Test
    void lossArithmeticFloorsAndHandlesLargeValues() {
        assertEquals(0, CoreTechniqueSwitch.lostProgress(3, LOSS));
        assertEquals(1, CoreTechniqueSwitch.lostProgress(4, LOSS));
        assertEquals(0, CoreTechniqueSwitch.lostProgress(1_000, 0));
        assertEquals(1_000, CoreTechniqueSwitch.lostProgress(1_000, 10_000));
        assertEquals(Long.MAX_VALUE / 10_000 * 3_000 + Long.MAX_VALUE % 10_000 * 3_000 / 10_000,
                CoreTechniqueSwitch.lostProgress(Long.MAX_VALUE, LOSS));
        assertEquals(3_000, CoreTechniqueSwitch.basisPoints(0.3));
        assertEquals(0, CoreTechniqueSwitch.basisPoints(0.0));
    }

    private static void assertRejected(
            CultivationProfile current, ResourceLocation target, CoreTechniqueSwitch.Status status) {
        CoreTechniqueSwitch.Plan plan = CoreTechniqueSwitch.plan(current, target, LOOKUP, LOSS);
        assertEquals(status, plan.status());
        assertFalse(plan.success());
        assertSame(current, plan.replacement());
        assertEquals(0, plan.progressLost());
    }

    /** A profile with {@code progress}, running {@code active}, that has learned it and {@code others}. */
    private static CultivationProfile profile(long progress, ResourceLocation active, ResourceLocation... others) {
        CultivationProfile profile = CultivationProfile.defaultProfile()
                .learnTechnique(active, TechniqueCategory.CORE)
                .withCultivationProgress(progress);
        for (ResourceLocation other : others) {
            profile = profile.learnTechnique(other);
        }
        return profile;
    }

    private static TechniqueDefinition technique(TechniqueCategory category, int grade, ResourceLocation previous) {
        return new TechniqueDefinition(
                "cultivation.technique.test",
                category,
                grade,
                List.of(),
                TechniqueRequirements.none(),
                Optional.empty(),
                Optional.ofNullable(previous).map(TechniqueLineage::new),
                Optional.empty());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
