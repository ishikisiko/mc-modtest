package com.example.myvillage.cultivation.tribulation;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.tribulation.TribulationAttempt.Clock;
import com.example.myvillage.cultivation.tribulation.TribulationAttempt.Phase;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TribulationStateTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }
    static TribulationRules rules() throws Exception {
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/data/myvillage/tribulation/golden_core.json"))) {
            return TribulationRules.read(reader);
        }
    }
    static TribulationAttempt attempt() throws Exception {
        return TribulationAttempt.create(UUID.fromString("11111111-2222-3333-4444-555555555555"),
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), "minecraft:overworld", 12, 64, -20, 4200, 2100, 200, rules());
    }
    static CultivationProfile profile() {
        return CultivationProfile.defaultProfile().withRealmAndStage(id("foundation_establishment"), id("foundation_late"))
                .withSpiritualRoot(new SpiritualRoot(Map.of(ModCultivationRegistries.FIRE_ELEMENT_ID, 10000)))
                .learnTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .withTechniqueMastery(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID, 78)
                .withActiveCoreTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .withFoundationQuality(2).withCultivationProgress(4200).withStability(2101)
                .withCurrentSpiritualPower(123).withLifespanConsumedTicks(456).withMeditationQiReserve(57);
    }
    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("myvillage", path); }
    @Test void realRulesSelectOnlyTheGoldenTransitionAndPreserveOtherStageRoutes() throws Exception {
        var r = rules();
        assertTrue(r.source("myvillage:foundation_establishment", "myvillage:foundation_late"));
        assertEquals("myvillage:golden_core", r.targetRealm()); assertEquals("myvillage:golden_early", r.targetStage());
        for (var stage : ModCultivationRegistries.QI_REFINING_STAGE_IDS) assertFalse(r.source("myvillage:qi_refining", stage.toString()));
        assertFalse(r.source("myvillage:foundation_establishment", "myvillage:foundation_middle"));
        assertFalse(r.source("myvillage:golden_core", "myvillage:golden_early"));
        var foundation = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/myvillage/realm/foundation_establishment.json"))).getAsJsonObject();
        var transition = foundation.getAsJsonArray("stages").get(2).getAsJsonObject().getAsJsonObject("advancement");
        assertEquals(r.targetStage(), transition.get("target_stage").getAsString());
    }
    @Test void sixMainWarningsAndDurationAreDrivenByTheRealFrozenTimeline() throws Exception {
        var a = attempt(); int warnings = 0, hits = 0, main = 0;
        for (int i = 0; i < a.rules().preparationTicks(); i++) { assertEquals(0, a.clock().elapsed()); a = a.advance(true); }
        assertEquals(Phase.ACTIVE, a.clock().phase());
        while (!a.readyToSucceed()) {
            a = a.advance(true);
            for (int i = 0; i < a.plan().size(); i++) {
                var s = a.rules().strikes().get(i);
                if (a.clock().elapsed() == s.tick() - s.warningTicks()) { assertTrue(a.warning(i)); warnings++; }
            }
            if (a.due()) {
                var s = a.rules().strikes().get(a.clock().nextStrike());
                assertEquals(s.tick(), a.clock().elapsed());
                assertFalse(a.warning(a.clock().nextStrike()));
                if (s.main()) main++;
                hits++; a = a.consumeStrike();
                assertFalse(a.due());
            }
        }
        assertEquals(a.rules().strikes().size(), hits); assertEquals(hits, warnings); assertEquals(6, main);
        assertTrue((a.rules().preparationTicks() + a.clock().elapsed()) / 20.0 >= 120);
        assertTrue((a.rules().preparationTicks() + a.clock().elapsed()) / 20.0 <= 180);
    }
    @Test void disconnectAndCodecResumeSamePlanWithoutClockCatchupOrStrikeReplay() throws Exception {
        var a = attempt();
        while (a.clock().elapsed() < 620) { a = a.advance(true); if (a.due()) a = a.consumeStrike(); }
        var before = a;
        a = TribulationAttempt.decode(a.pause().encode());
        assertEquals(before.id(), a.id()); assertEquals(before.plan(), a.plan()); assertEquals(before.rules(), a.rules());
        assertEquals(before.clock().elapsed(), a.clock().elapsed()); assertEquals(before.clock().nextStrike(), a.clock().nextStrike());
        assertTrue(a.clock().paused());
        for (int i = 0; i < a.rules().resumeTicks(); i++) { a = a.advance(true); assertEquals(before.clock().elapsed(), a.clock().elapsed()); assertFalse(a.due()); }
        a = a.advance(true); assertEquals(621, a.clock().elapsed());
        assertEquals(2, a.clock().nextStrike());
        var json = TribulationManager.statusJson(a);
        assertEquals(1, json.get("schema").getAsInt()); assertEquals(a.id().toString(), json.get("attempt_id").getAsString());
        assertEquals(64, json.get("rules_sha256").getAsString().length());
        assertEquals(TribulationManager.statusJson(before).get("plan_sha256"), json.get("plan_sha256"));
    }
    @Test void failureIsNonLethalAfterAbsorptionAndAppliesStabilityExactlyOnce() throws Exception {
        assertFalse(TribulationDamage.lethal(19, 10, 10)); assertTrue(TribulationDamage.lethal(20, 10, 10));
        float capped = TribulationDamage.nonLethal(10, 10, 2);
        assertEquals(18, capped); assertEquals(2, 10 - Math.max(0, capped - 10));
        assertEquals(0, TribulationDamage.nonLethal(1, 0, 2), "低血不能被失败保护治疗");
        var a = attempt(); var p = profile();
        var first = TribulationSettlement.failure(a, p, "承受不住", true);
        assertEquals(p.stability() - a.rules().failureStabilityLoss(), first.profile().stability());
        assertEquals(p.cultivationProgress(), first.profile().cultivationProgress()); assertEquals(p.stageId(), first.profile().stageId());
        var second = TribulationSettlement.failure(TribulationAttempt.decode(first.attempt().encode()), first.profile(), "重复shutdown", true);
        assertEquals(first, second); assertTrue(second.attempt().clock().penaltyApplied());
        var recovering = second.attempt();
        for (int i = 0; i < a.rules().recoveryTicks(); i++) recovering = recovering.advance(true);
        assertEquals(Phase.FAILED, recovering.clock().phase());
        assertFalse(recovering.active());
    }
    @Test void administrativeTerminationDoesNotPunishOrAdvanceAndCancelledPreparationHasNoReward() throws Exception {
        var p = profile(); var stopped = TribulationSettlement.failure(attempt(), p, "管理员终止", false);
        assertEquals(p, stopped.profile()); assertEquals(Phase.STOPPED, stopped.attempt().clock().phase());
        assertFalse(stopped.attempt().clock().penaltyApplied());
        assertThrows(IllegalStateException.class, () -> TribulationSettlement.success(stopped.attempt(), p));
    }
    @Test void lastSettlementChangesOnlyRealmProgressStabilityAndCannotPromoteTwice() throws Exception {
        var a = attempt(); var p = profile();
        assertThrows(IllegalStateException.class, () -> TribulationSettlement.success(attempt(), p));
        while (!a.readyToSucceed()) { a = a.advance(true); if (a.due()) a = a.consumeStrike(); }
        var finished = TribulationAttempt.decode(a.encode());
        var next = TribulationSettlement.success(finished, p);
        assertEquals(p.withRealmAndStage(id("golden_core"), id("golden_early")).withCultivationProgress(0).withStability(p.stability()/2), next);
        assertEquals(p.currentSpiritualPower(), next.currentSpiritualPower()); assertEquals(p.foundationQuality(), next.foundationQuality());
        assertThrows(IllegalStateException.class, () -> TribulationSettlement.success(finished, next));
        var c = finished.clock();
        var ended = finished.withClock(new Clock(Phase.SUCCEEDED, 0, c.elapsed(), c.nextStrike(), 0, 0, false, "成功", false));
        var saved = TribulationAttempt.decode(ended.encode());
        assertFalse(saved.readyToSucceed()); assertSame(saved, saved.advance(true));
        assertThrows(IllegalStateException.class, () -> TribulationSettlement.success(saved, p));
    }
    @Test void boundaryOwnerAndFixedEchoMarksPreventBystanderDamageAndLastTickTracking() throws Exception {
        var a = attempt();
        assertTrue(a.contains(a.x() + a.rules().arenaRadius(), a.y(), a.z()));
        assertFalse(a.contains(a.x() + a.rules().arenaRadius() + .01, a.y(), a.z()));
        assertFalse(a.contains(a.x(), a.y() + a.rules().verticalRange() + .01, a.z()));
        for (int i = 0; i < a.plan().size(); i++) {
            var mark = a.plan().get(i);
            assertFalse(a.canHit(UUID.randomUUID(), i, mark.x(), mark.y(), mark.z()));
            assertTrue(a.canHit(a.owner(), i, mark.x(), mark.y(), mark.z()));
            if (!a.rules().strikes().get(i).main()) assertFalse(a.canHit(a.owner(), i, mark.x() + a.rules().echoRadius() + .01, mark.y(), mark.z()));
        }
        var plan = a.plan(); a = a.advance(false); assertEquals(1, a.clock().outside());
        a = a.advance(true); assertEquals(0, a.clock().outside()); assertEquals(plan, a.plan());
    }
    @Test void savedRulesRemainFrozenAndInvalidSchemasCannotSilentlyStartAnotherAttempt() throws Exception {
        var a = attempt(); assertEquals(a, TribulationAttempt.decode(a.encode()));
        assertThrows(RuntimeException.class, () -> TribulationAttempt.decode(a.encode().replace("\"schema_version\":1", "\"schema_version\":99")));
        var json = JsonParser.parseString(a.encode()).getAsJsonObject(); json.getAsJsonObject("clock").addProperty("next_strike", 500);
        assertThrows(RuntimeException.class, () -> TribulationAttempt.decode(json.toString()));
    }
}
