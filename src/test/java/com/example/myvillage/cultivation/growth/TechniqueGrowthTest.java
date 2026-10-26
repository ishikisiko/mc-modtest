package com.example.myvillage.cultivation.growth;

import com.example.myvillage.cultivation.*;
import com.example.myvillage.cultivation.data.*;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRules.Effect;
import com.example.myvillage.cultivation.study.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TechniqueGrowthTest {
    static final ResourceLocation BASIC = id("basic_breathing"), QING = id("qingmu_changchun_gong"),
            YI = id("yimu_shenghua_jue"), SWORD = id("jianqi_jue");
    static final TechniqueGrowthRules RULES = TechniqueGrowthRules.bundled();
    static final Map<ResourceLocation, TechniqueDefinition> TECHNIQUES = techniques();
    static final Map<ResourceLocation, RealmDefinition> REALMS = realms();
    static ResourceLocation id(String path) { return ResourceLocation.parse("myvillage:" + path); }
    static JsonObject json(String path) {
        try { return JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/" + path))).getAsJsonObject(); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    static Map<ResourceLocation, TechniqueDefinition> techniques() {
        var map = new HashMap<ResourceLocation, TechniqueDefinition>();
        for (var id : List.of(BASIC, QING, YI, SWORD)) map.put(id, TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE,
                json("myvillage/technique/" + id.getPath() + ".json")).getOrThrow());
        return Map.copyOf(map);
    }
    static Map<ResourceLocation, RealmDefinition> realms() {
        var map = new HashMap<ResourceLocation, RealmDefinition>();
        for (String name : List.of("mortal", "qi_refining", "foundation_establishment", "golden_core")) {
            var path = Path.of("src/main/resources/data/myvillage/myvillage/realm/" + name + ".json");
            if (Files.exists(path)) map.put(id(name), RealmDefinition.CODEC.parse(JsonOps.INSTANCE, json("myvillage/realm/" + name + ".json")).getOrThrow());
        }
        return Map.copyOf(map);
    }
    static CultivationProfile profile() {
        return CultivationProfile.defaultProfile().withRealmAndStage(id("qi_refining"), id("qi_refining_4"))
                .withSpiritualRoot(Optional.of(new SpiritualRoot(Map.of(id("wood"), 6000, id("metal"), 4000))))
                .learnTechnique(BASIC, TechniqueCategory.CORE).withStability(100);
    }
    static CultivationProfile mastered(CultivationProfile p, ResourceLocation id) {
        if (!p.learnedTechniques().containsKey(id)) p = p.learnTechnique(id, TECHNIQUES.get(id).category());
        return p.withTechniqueMastery(id, RULES.find(id).orElseThrow().cap());
    }
    static CultivationProfile qingReady() {
        var p = mastered(mastered(profile(), BASIC), QING);
        return TechniqueGrowth.choose(RULES, QING, 0, 0, p, TECHNIQUES::get).orElseThrow().replacement();
    }

    @Test void fiveStagesComeFromTheSameBoundedConfigAsEffectsAndCosts() {
        assertEquals(4, RULES.techniques().size());
        for (var entry : RULES.techniques().entrySet()) {
            var spec = entry.getValue();
            assertEquals(0, spec.stage(0)); assertEquals(4, spec.stage(Long.MAX_VALUE));
            for (int stage = 1; stage < 5; stage++) {
                assertEquals(stage - 1, spec.stage(spec.thresholds().get(stage) - 1));
                assertEquals(stage, spec.stage(spec.thresholds().get(stage)));
            }
            assertEquals(TECHNIQUES.get(entry.getKey()).category(), spec.category());
        }
    }

    @Test void finiteTechniqueStopsChargingAndRetainsOldAboveCapMastery() {
        var p = profile(); int stones = 0;
        while (true) {
            var plan = TechniqueGrowth.practice(RULES, BASIC, p, TECHNIQUES::get);
            if (plan.isEmpty()) break;
            stones += plan.get().stoneCost(); p = plan.get().replacement();
        }
        assertEquals(180, p.learnedTechniques().get(BASIC).masteryPoints()); assertEquals(18, stones);
        assertTrue(TechniqueGrowth.practice(RULES, BASIC, p, TECHNIQUES::get).isEmpty());
        var old = new TechniqueProgress(Long.MAX_VALUE, "flow", 2, 9);
        assertEquals(old, TechniqueGrowth.gain(RULES.find(BASIC).orElseThrow(), old, Long.MAX_VALUE));
        assertTrue(RULES.find(BASIC).orElseThrow().next().isEmpty());
    }

    @Test void partialFinalBatchClampsAwardWithoutOverflow() {
        var spec = RULES.find(BASIC).orElseThrow();
        var next = TechniqueGrowth.gain(spec, new TechniqueProgress(spec.cap() - 1), Long.MAX_VALUE);
        assertEquals(spec.cap(), next.masteryPoints());
        assertThrows(IllegalArgumentException.class, () -> TechniqueGrowth.gain(spec, next, -1));
    }

    @Test void eachTechniqueGrowsIndependentlyAndUnknownOrUnlearnedIdsDoNothing() {
        var p = profile().learnTechnique(QING, TechniqueCategory.CORE);
        var result = TechniqueGrowth.practice(RULES, QING, p, TECHNIQUES::get).orElseThrow();
        assertEquals(10, result.replacement().learnedTechniques().get(QING).masteryPoints());
        assertEquals(0, result.replacement().learnedTechniques().get(BASIC).masteryPoints());
        assertTrue(TechniqueGrowth.practice(RULES, SWORD, p, TECHNIQUES::get).isEmpty());
        assertTrue(TechniqueGrowth.practice(RULES, id("unknown"), p, TECHNIQUES::get).isEmpty());
        assertTrue(TechniqueGrowth.practice(RULES, QING, p, ignored -> null).isEmpty());
    }

    @Test void firstInsightRequiresSmallSuccessAndPaysEarnedInsightOnlyOnce() {
        var p = profile();
        assertTrue(TechniqueGrowth.choose(RULES, BASIC, 0, 0, p, TECHNIQUES::get).isEmpty());
        p = p.withTechniqueMastery(BASIC, 50);
        var result = TechniqueGrowth.choose(RULES, BASIC, 0, 0, p, TECHNIQUES::get).orElseThrow();
        assertEquals(2, result.insightCost()); assertEquals(0, result.stoneCost());
        assertEquals(2, result.replacement().learnedTechniques().get(BASIC).insightSpent());
        assertTrue(TechniqueGrowth.choose(RULES, BASIC, 0, 0, result.replacement(), TECHNIQUES::get).isEmpty());
    }

    @Test void respecReplacesRatherThanStacksAndRejectsStaleRevision() {
        var p = mastered(profile(), BASIC);
        var first = TechniqueGrowth.choose(RULES, BASIC, 0, 0, p, TECHNIQUES::get).orElseThrow();
        assertEquals(12500, TechniqueGrowth.multiplier(RULES, BASIC, first.replacement(), Effect.CULTIVATION));
        assertTrue(TechniqueGrowth.choose(RULES, BASIC, 1, 0, first.replacement(), TECHNIQUES::get).isEmpty());
        var second = TechniqueGrowth.choose(RULES, BASIC, 1, 1, first.replacement(), TECHNIQUES::get).orElseThrow();
        assertEquals(RULES.respecStones(), second.stoneCost()); assertEquals(0, second.insightCost());
        assertEquals(11500, TechniqueGrowth.multiplier(RULES, BASIC, second.replacement(), Effect.CULTIVATION));
        assertEquals(13500, TechniqueGrowth.multiplier(RULES, BASIC, second.replacement(), Effect.RECOVERY));
        assertEquals(2, second.replacement().learnedTechniques().get(BASIC).insightSpent());
        assertTrue(TechniqueGrowth.choose(RULES, BASIC, 2, 2, second.replacement(), TECHNIQUES::get).isEmpty());
    }

    @Test void swordDirectionsTradeDamageForCostAndCeilPositiveCosts() {
        var p = mastered(profile(), SWORD);
        var edge = TechniqueGrowth.choose(RULES, SWORD, 0, 0, p, TECHNIQUES::get).orElseThrow().replacement();
        assertEquals(13500, TechniqueGrowth.multiplier(RULES, SWORD, edge, Effect.DAMAGE));
        assertEquals(10, TechniqueGrowth.cost(8, TechniqueGrowth.multiplier(RULES, SWORD, edge, Effect.QI_COST)));
        var reserve = TechniqueGrowth.choose(RULES, SWORD, 1, 1, edge, TECHNIQUES::get).orElseThrow().replacement();
        assertEquals(12000, TechniqueGrowth.multiplier(RULES, SWORD, reserve, Effect.DAMAGE));
        assertEquals(7, TechniqueGrowth.cost(8, TechniqueGrowth.multiplier(RULES, SWORD, reserve, Effect.QI_COST)));
        assertEquals(1, TechniqueGrowth.cost(1, 2000)); assertEquals(0, TechniqueGrowth.cost(0, 8000));
        assertThrows(ArithmeticException.class, () -> TechniqueGrowth.cost(Long.MAX_VALUE, 20000));
    }

    @Test void oldFoundationHelpsRelatedLearningOnlyAndDiscountsOnlyFirstInsight() {
        var p = mastered(profile(), BASIC);
        assertEquals(2000, TechniqueGrowth.support(RULES, QING, p, TECHNIQUES::get).orElseThrow().studyBonusBp());
        assertTrue(TechniqueGrowth.support(RULES, SWORD, p, TECHNIQUES::get).isEmpty());
        var learned = TechniqueGrowth.learn(RULES, QING, p, TECHNIQUES::get).withTechniqueMastery(QING, 100);
        var first = TechniqueGrowth.choose(RULES, QING, 0, 0, learned, TECHNIQUES::get).orElseThrow();
        assertEquals(1, first.insightCost());
        assertEquals(RULES.respecStones(), TechniqueGrowth.choose(RULES, QING, 1, 1,
                first.replacement(), TECHNIQUES::get).orElseThrow().stoneCost());
    }

    @Test void bestRelationshipWinsAllBenefitsWithoutSummingSources() {
        var p = qingReady();
        var support = TechniqueGrowth.support(RULES, YI, p, TECHNIQUES::get).orElseThrow();
        assertEquals(QING, support.source()); assertEquals(4000, support.studyBonusBp());
        var lower = TechniqueGrowth.support(RULES, YI, mastered(profile(), BASIC), TECHNIQUES::get).orElseThrow();
        assertEquals(500, lower.studyBonusBp());
        assertEquals(0, lower.inheritedMastery());
    }

    @Test void masteryAndInsightAreAdditionalPrerequisitesAndNeverBypassRealmOrRoot() {
        var p = profile().learnTechnique(QING, TechniqueCategory.CORE);
        assertEquals(Optional.of(StudyStart.Refusal.PREVIOUS_MASTERY_REQUIRED), refusal(p));
        p = mastered(p, QING);
        assertEquals(Optional.of(StudyStart.Refusal.PREVIOUS_INSIGHT_REQUIRED), refusal(p));
        p = qingReady(); assertTrue(refusal(p).isEmpty());
        assertEquals(Optional.of(StudyStart.Refusal.REALM_TOO_LOW), refusal(p.withRealmAndStage(id("qi_refining"), id("qi_refining_1"))));
        assertEquals(Optional.of(StudyStart.Refusal.AFFINITY_TOO_LOW), refusal(p.withSpiritualRoot(Optional.of(new SpiritualRoot(Map.of(id("metal"), 10000))))));
        assertEquals(Optional.of(StudyStart.Refusal.NOT_AWAKENED), refusal(p.withSpiritualRoot(Optional.empty())));
    }
    private Optional<StudyStart.Refusal> refusal(CultivationProfile p) { return StudyStart.learnRefusal(YI, p, TECHNIQUES::get, REALMS::get, id -> true); }

    @Test void learningNextChapterPreservesPredecessorAndInheritsOnlyCompatibleDirection() {
        var before = qingReady(); var old = before.learnedTechniques().get(QING);
        var learned = TechniqueGrowth.learn(RULES, YI, before, TECHNIQUES::get);
        assertEquals(old, learned.learnedTechniques().get(QING));
        assertEquals(60, learned.learnedTechniques().get(YI).masteryPoints());
        assertEquals("flow", learned.learnedTechniques().get(YI).insight());
        assertEquals(before.activeCoreTechnique(), learned.activeCoreTechnique());
        assertThrows(IllegalArgumentException.class, () -> TechniqueGrowth.learn(RULES, YI, learned, TECHNIQUES::get));
        var respec = TechniqueGrowth.choose(RULES, YI, 1, 0, learned, TECHNIQUES::get).orElseThrow();
        assertEquals(RULES.respecStones(), respec.stoneCost(), "inherited insight remains usable before own small-success stage");
    }

    @Test void studyAccelerationIsMultiplicativeAndStillPaysTheSameGates() {
        var baseline = StudySettlement.plan(0, 1000, 1, 50, 100, 100, 2000);
        var faster = StudySettlement.plan(0, 1000, 1, 50, 100, 100, 2000, 2000);
        assertEquals(120, baseline.points()); assertEquals(144, faster.points());
        var blocked = StudySettlement.plan(490, 1000, 1, 50, 0, 100, 2000, 4000);
        assertEquals(StudySettlement.Outcome.GATE_BLOCKED, blocked.outcome()); assertEquals(500, blocked.points());
        var paid = StudySettlement.plan(490, 1000, 1, 50, 50, 100, 2000, 4000);
        assertEquals(50, paid.stabilityCost());
    }

    @Test void failedStudyCommitDoesNotConsumeManualOrInstallInheritedGrowth() {
        var slot = new Slot(YI, 11999); var before = qingReady();
        var result = StudyStep.settle(YI, slot, before, TECHNIQUES::get, REALMS::get, id -> true, Optional.empty(), p -> false);
        assertEquals(StudyStep.Outcome.COMMIT_FAILED, result.outcome()); assertEquals(0, slot.consumed); assertEquals(11999, slot.points);
        assertFalse(before.learnedTechniques().containsKey(YI));
        CultivationProfile[] installed = {before};
        result = StudyStep.settle(YI, slot, before, TECHNIQUES::get, REALMS::get, id -> true, Optional.empty(), p -> { installed[0] = p; return true; });
        assertEquals(StudyStep.Outcome.COMPLETE, result.outcome()); assertEquals(1, slot.consumed);
        assertEquals(60, installed[0].learnedTechniques().get(YI).masteryPoints());
        assertEquals("flow", installed[0].learnedTechniques().get(YI).insight());
    }

    @Test void lostManualAndAlreadyLearnedCannotReapplySupportOrInheritance() {
        var before = qingReady(); var slot = new Slot(SWORD, 11999);
        assertEquals(StudyStep.Outcome.MANUAL_LOST, StudyStep.settle(YI, slot, before, TECHNIQUES::get, REALMS::get,
                id -> true, Optional.empty(), p -> fail("must not commit")).outcome());
        var learned = TechniqueGrowth.learn(RULES, YI, before, TECHNIQUES::get);
        slot = new Slot(YI, 11999);
        assertEquals(StudyStep.Outcome.REQUIREMENTS, StudyStep.settle(YI, slot, learned, TECHNIQUES::get, REALMS::get,
                id -> true, Optional.empty(), p -> fail("must not commit")).outcome());
        assertEquals(0, slot.consumed);
    }

    @Test void meditationClockCreditsOnlyTheRunningSampleCoreAndRetainsOtherProfileChanges() {
        var before = profile().learnTechnique(QING, TechniqueCategory.CORE).withActiveCoreTechnique(QING);
        var base = before.withTechniqueMastery(BASIC, 2).withCultivationProgress(40);
        var after = TechniqueGrowth.meditationMastery(RULES, before, base, 2, TECHNIQUES::get);
        assertEquals(0, after.learnedTechniques().get(BASIC).masteryPoints());
        assertEquals(2, after.learnedTechniques().get(QING).masteryPoints()); assertEquals(40, after.cultivationProgress());
    }

    @Test void oldSaveDefaultsNewFieldsAndCurrentSaveRoundTripsEveryGrowthAccount() {
        var old = TechniqueProgress.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"mastery_points\":123}" )).getOrThrow();
        assertEquals(new TechniqueProgress(123), old);
        var profile = TechniqueGrowth.learn(RULES, YI, qingReady(), TECHNIQUES::get);
        var saved = CultivationProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow();
        assertEquals(profile, CultivationProfile.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
        var preserved = profile.withTechniqueMastery(QING, 400);
        assertEquals(profile.learnedTechniques().get(QING).insight(), preserved.learnedTechniques().get(QING).insight());
        assertEquals(profile.learnedTechniques().get(QING).insightSpent(), preserved.learnedTechniques().get(QING).insightSpent());
    }

    @Test void corruptGrowthAccountsFailClosedAndUnknownHistoricalDirectionsStayInert() {
        for (String bad : List.of("{\"mastery_points\":-1}", "{\"mastery_points\":0,\"insight\":\"flow\",\"insight_spent\":-1}",
                "{\"mastery_points\":0,\"growth_revision\":-1}", "{\"mastery_points\":0,\"insight_spent\":2}"))
            assertTrue(TechniqueProgress.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(bad)).error().isPresent());
        var p = profile().withTechniqueProgress(BASIC, new TechniqueProgress(0, "retired", 0, 0));
        assertEquals(10000, TechniqueGrowth.multiplier(RULES, BASIC, p, Effect.CULTIVATION));
    }

    @Test void badRulesRejectCrossCategoryBenefitsAndMissingPredecessors() {
        var root = json("cultivation/technique_growth.json");
        root.getAsJsonObject("techniques").getAsJsonObject(SWORD.toString()).getAsJsonObject("bonuses")
                .add("cultivation", JsonParser.parseString("[0,1,2,3,4]"));
        assertThrows(IllegalArgumentException.class, () -> TechniqueGrowthRules.parse(root));
        var unknown = json("cultivation/technique_growth.json");
        unknown.getAsJsonObject("techniques").getAsJsonObject(QING.toString()).addProperty("next", "myvillage:missing");
        assertThrows(IllegalArgumentException.class, () -> TechniqueGrowthRules.parse(unknown));
    }

    static final class Slot implements ManualSlot {
        final ResourceLocation id; int points, consumed;
        Slot(ResourceLocation id, int points) { this.id = id; this.points = points; }
        public Optional<ResourceLocation> technique() { return Optional.of(id); }
        public int comprehension() { return points; }
        public void writeComprehension(int value) { points = value; }
        public void consumeOne() { consumed++; }
    }
}
