package com.example.myvillage.cultivation.technique;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechniqueInheritanceServiceTest {
    @Test
    void unawakenedAndWrongStageFailWithoutCommit() {
        AtomicInteger commits = new AtomicInteger();
        TechniqueInheritanceService.Outcome unawakened = inherit(
                CultivationProfile.defaultProfile(), commits, true);
        CultivationProfile wrongStage = CultivationProfile.defaultProfile()
                .withSpiritualRoot(TechniqueRequirementEvaluatorTest.root(1));
        TechniqueInheritanceService.Outcome wrong = inherit(wrongStage, commits, true);

        assertEquals(TechniqueInheritanceService.Status.NOT_AWAKENED, unawakened.status());
        assertEquals(TechniqueInheritanceService.Status.REQUIREMENTS_NOT_MET, wrong.status());
        assertEquals(0, commits.get());
    }

    @Test
    void successAddsZeroMasteryAndPreservesEveryOtherField() {
        Map<ResourceLocation, TechniqueProgress> learned = new LinkedHashMap<>();
        learned.put(TechniqueRequirementEvaluatorTest.id("other"), new TechniqueProgress(88));
        CultivationProfile current = new CultivationProfile(
                CultivationProfile.CURRENT_SCHEMA_VERSION,
                ModCultivationRegistries.MORTAL_REALM_ID,
                ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID,
                91,
                33,
                17,
                29,
                1234,
                56,
                Optional.of(TechniqueRequirementEvaluatorTest.root(3)),
                learned);
        AtomicInteger commits = new AtomicInteger();
        AtomicReference<CultivationProfile> committed = new AtomicReference<>();

        TechniqueInheritanceService.Outcome outcome = TechniqueInheritanceService.inheritBasicBreathing(
                current,
                TechniqueRequirementEvaluatorTest.basicBreathing(),
                TechniqueRequirementEvaluatorTest::realm,
                replacement -> {
                    commits.incrementAndGet();
                    committed.set(replacement);
                    return true;
                });

        assertEquals(TechniqueInheritanceService.Status.SUCCESS, outcome.status());
        assertEquals(1, commits.get());
        assertEquals(outcome.profile(), committed.get());
        assertEquals(0, outcome.profile().learnedTechniques()
                .get(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID).masteryPoints());
        assertEquals(new TechniqueProgress(88), outcome.profile().learnedTechniques()
                .get(TechniqueRequirementEvaluatorTest.id("other")));
        assertEquals(current.schemaVersion(), outcome.profile().schemaVersion());
        assertEquals(current.realmId(), outcome.profile().realmId());
        assertEquals(current.stageId(), outcome.profile().stageId());
        assertEquals(current.cultivationProgress(), outcome.profile().cultivationProgress());
        assertEquals(current.stability(), outcome.profile().stability());
        assertEquals(current.currentSpiritualPower(), outcome.profile().currentSpiritualPower());
        assertEquals(current.spiritualAffinity(), outcome.profile().spiritualAffinity());
        assertEquals(current.lifespanConsumedTicks(), outcome.profile().lifespanConsumedTicks());
        assertEquals(current.meditationQiReserve(), outcome.profile().meditationQiReserve());
        assertEquals(current.spiritualRoot(), outcome.profile().spiritualRoot());
    }

    @Test
    void repeatPreservesNonzeroMasteryEvenAfterEligibilityChanges() {
        CultivationProfile learned = TechniqueRequirementEvaluatorTest.sensedProfile(
                        TechniqueRequirementEvaluatorTest.root(1))
                .learnTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .withTechniqueMastery(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID, 350)
                .withoutSpiritualRoot()
                .withRealmAndStage(
                        ModCultivationRegistries.MORTAL_REALM_ID,
                        ModCultivationRegistries.MORTAL_UNAWAKENED_STAGE_ID);
        AtomicInteger commits = new AtomicInteger();

        TechniqueInheritanceService.Outcome outcome = inherit(learned, commits, true);

        assertEquals(TechniqueInheritanceService.Status.ALREADY_LEARNED, outcome.status());
        assertEquals(350, outcome.profile().learnedTechniques()
                .get(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID).masteryPoints());
        assertEquals(learned, outcome.profile());
        assertEquals(0, commits.get());
    }

    @Test
    void missingDefinitionPreservesSavedTechnique() {
        CultivationProfile learned = TechniqueRequirementEvaluatorTest.sensedProfile(
                        TechniqueRequirementEvaluatorTest.root(1))
                .learnTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .withTechniqueMastery(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID, 5);
        AtomicInteger commits = new AtomicInteger();

        TechniqueInheritanceService.Outcome outcome = TechniqueInheritanceService.inheritBasicBreathing(
                learned,
                null,
                TechniqueRequirementEvaluatorTest::realm,
                replacement -> {
                    commits.incrementAndGet();
                    return true;
                });

        assertEquals(TechniqueInheritanceService.Status.TECHNIQUE_NOT_REGISTERED, outcome.status());
        assertEquals(learned, outcome.profile());
        assertEquals(0, commits.get());
    }

    @Test
    void rejectedCommitLeavesProfileUnchanged() {
        CultivationProfile current = TechniqueRequirementEvaluatorTest.sensedProfile(
                TechniqueRequirementEvaluatorTest.root(2));
        AtomicInteger commits = new AtomicInteger();

        TechniqueInheritanceService.Outcome outcome = inherit(current, commits, false);

        assertEquals(TechniqueInheritanceService.Status.UPDATE_REJECTED, outcome.status());
        assertEquals(current, outcome.profile());
        assertEquals(1, commits.get());
        assertTrue(current.learnedTechniques().isEmpty());
    }

    private static TechniqueInheritanceService.Outcome inherit(
            CultivationProfile profile,
            AtomicInteger commits,
            boolean commitResult) {
        return TechniqueInheritanceService.inheritBasicBreathing(
                profile,
                TechniqueRequirementEvaluatorTest.basicBreathing(),
                TechniqueRequirementEvaluatorTest::realm,
                replacement -> {
                    commits.incrementAndGet();
                    return commitResult;
                });
    }
}
