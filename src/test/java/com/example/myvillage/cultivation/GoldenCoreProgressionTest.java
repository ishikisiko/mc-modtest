package com.example.myvillage.cultivation;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.client.cultivation.panel.PanelReadouts;
import com.example.myvillage.cultivation.data.AdvancementKind;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.meditation.AdvancementContext;
import com.example.myvillage.cultivation.meditation.AdvancementProfileTransition;
import com.example.myvillage.cultivation.meditation.BasicBreathingSettlement;
import com.example.myvillage.cultivation.meditation.MeditationMode;
import com.example.myvillage.cultivation.qi.SpiritualPower;
import com.example.myvillage.cultivation.technique.TechniqueRequirementEvaluator;
import com.example.myvillage.cultivation.time.CultivationTimeMath;
import com.example.myvillage.stats.RealmStats;
import com.example.myvillage.stats.StatsRules;
import com.example.myvillage.stats.runtime.RealmHealthPolicy;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual authored data and numerical transitions; live service/input evidence remains separate. */
class GoldenCoreProgressionTest {
    private static final Path DEFINITIONS = Path.of("src/main/resources/data/myvillage/myvillage");

    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void requiredBaselineMatchesAllFourRealmsAndSeventeenStages() throws Exception {
        Map<ResourceLocation, RealmDefinition> realms = realms();
        assertEquals(ModCultivationRegistries.REQUIRED_REALM_IDS, realms.keySet());
        assertEquals(4, realms.size());
        assertEquals(17, ModCultivationRegistries.REQUIRED_STAGE_OWNERS.size());
        for (var owner : ModCultivationRegistries.REQUIRED_STAGE_OWNERS.entrySet()) {
            assertTrue(realms.get(owner.getValue()).containsStage(owner.getKey()), owner.toString());
        }
        assertEquals(17, realms.values().stream().mapToInt(realm -> realm.stages().size()).sum());
        assertFalse(realms.containsKey(id("nascent_soul")));
    }

    @Test
    void allFifteenTransitionsPreserveUnrelatedStateAndUseTheExistingStatsPolicy() throws Exception {
        Map<ResourceLocation, RealmDefinition> realms = realms();
        RegistryAccess access = access(realms);
        StatsRules rules = StatsRules.bundled();
        CultivationProfile current = profile("mortal", "mortal_qi_sensed");
        List<ResourceLocation> reached = new ArrayList<>();
        var seen = new LinkedHashSet<ResourceLocation>();
        float woundedHealth = 11;
        while (true) {
            assertTrue(seen.add(current.stageId()), "Cycle at " + current.stageId());
            RealmDefinition sourceRealm = realms.get(current.realmId());
            RealmStageDefinition source = stage(sourceRealm, current.stageId());
            if (source.advancement().isEmpty()) {
                break;
            }
            var rule = source.advancement().orElseThrow();
            long cap = source.cultivationCap().orElseThrow();
            CultivationProfile before = current.withCultivationProgress(cap).withStability(rule.requiredStability());
            CultivationProfile next = AdvancementProfileTransition.successReplacement(before,
                    new AdvancementContext(before.realmId(), before.stageId(), cap, rule));
            assertEquals(0, next.cultivationProgress());
            assertEquals(before.stability() / 2, next.stability());
            assertEquals(before.currentSpiritualPower(), next.currentSpiritualPower());
            assertEquals(before.lifespanConsumedTicks(), next.lifespanConsumedTicks());
            assertEquals(before.meditationQiReserve(), next.meditationQiReserve());
            assertEquals(before.spiritualAffinity(), next.spiritualAffinity());
            assertEquals(before.spiritualRoot(), next.spiritualRoot());
            assertEquals(before.learnedTechniques(), next.learnedTechniques());
            assertEquals(before.activeCoreTechnique(), next.activeCoreTechnique());
            long targetPowerCap = CultivationService.spiritualPowerCap(access, next);
            assertEquals(next.currentSpiritualPower(), SpiritualPower.clamp(next.currentSpiritualPower(), targetPowerCap));
            assertTrue(next.currentSpiritualPower() < targetPowerCap, "No automatic refill");
            var oldStats = RealmStats.derived(rules, rules.playerKey(before.realmId().toString(), before.stageId().toString()), 20, 2);
            var newStats = RealmStats.derived(rules, rules.playerKey(next.realmId().toString(), next.stageId().toString()), 20, 2);
            woundedHealth = RealmHealthPolicy.refreshed(woundedHealth, (float) oldStats.health(), (float) newStats.health());
            assertEquals(11, woundedHealth, "Advancement must not heal");
            if (!before.realmId().equals(next.realmId())) {
                assertTrue(realms.get(next.realmId()).maximumLifespanYears() > sourceRealm.maximumLifespanYears());
                if (!before.realmId().equals(ModCultivationRegistries.MORTAL_REALM_ID)) {
                    assertEquals(AdvancementKind.BOTTLENECK, rule.kind());
                }
            }
            reached.add(next.stageId());
            current = next;
            assertTrue(reached.size() <= 15, "Unexpected future-stage transition");
        }
        List<ResourceLocation> expected = new ArrayList<>(ModCultivationRegistries.QI_REFINING_STAGE_IDS);
        expected.addAll(List.of(id("foundation_early"), id("foundation_middle"), id("foundation_late"),
                id("golden_early"), id("golden_middle"), id("golden_late")));
        assertEquals(expected, reached);
        assertEquals(id("golden_core"), current.realmId());
        assertEquals(id("golden_late"), current.stageId());
        assertEquals(6.6, rules.growth(rules.playerKey(current.realmId().toString(), current.stageId().toString())), 1e-12);
    }

    @Test
    void everyPlayableStageClampsFinalProgressThenConsolidatesWithoutSpendingStones() throws Exception {
        int checked = 0;
        var accrual = new BasicBreathingSettlement.Accrual(0, new BasicBreathingSettlement.Remainders(0));
        for (var realm : realms().entrySet()) {
            for (var stage : realm.getValue().stages()) {
                if (stage.cultivationCap().isEmpty()) {
                    continue;
                }
                long cap = stage.cultivationCap().orElseThrow();
                CultivationProfile current = profile(realm.getKey().getPath(), stage.id().getPath())
                        .withCultivationProgress(cap - 1).withStability(0);
                for (MeditationMode mode : List.of(MeditationMode.NORMAL, MeditationMode.SPIRIT)) {
                    var finalProgress = BasicBreathingSettlement.plan(current, cap, mode, accrual, true);
                    assertEquals(cap, finalProgress.replacement().cultivationProgress());
                    assertEquals(1, finalProgress.progressApplied());
                    assertEquals(0, finalProgress.stabilityApplied());
                    assertEquals(mode == MeditationMode.SPIRIT, finalProgress.consumeSpiritStones());
                    int stabilityCap = stage.stabilityCap().orElseThrow();
                    var consolidation = BasicBreathingSettlement.plan(finalProgress.replacement()
                            .withStability(stabilityCap - 1), cap, mode, accrual, true);
                    assertEquals(stabilityCap, consolidation.replacement().stability());
                    assertEquals(1, consolidation.stabilityApplied());
                    assertFalse(consolidation.consumeSpiritStones());
                }
                checked++;
            }
        }
        assertEquals(15, checked);
    }

    @Test
    void basicBreathingRemainsEligibleAcrossTheNewLadderAndAliasesKeepStageOrder() throws Exception {
        Map<ResourceLocation, RealmDefinition> realms = realms();
        TechniqueDefinition breathing = TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString(Files.readString(DEFINITIONS.resolve("technique/basic_breathing.json")))).getOrThrow();
        for (var realm : realms.entrySet()) {
            for (var stage : realm.getValue().stages()) {
                CultivationProfile profile = profile(realm.getKey().getPath(), stage.id().getPath());
                var alias = StatsRules.bundled().playerKey(realm.getKey().toString(), stage.id().toString());
                assertEquals(stage.sortOrder(), alias.stage());
                if (!stage.id().equals(ModCultivationRegistries.MORTAL_UNAWAKENED_STAGE_ID)) {
                    assertTrue(TechniqueRequirementEvaluator.evaluate(profile, breathing, realms::get).satisfied());
                }
            }
        }
    }

    @Test
    void goldenLateIsTheOnlyCeilingAndKeepsItsPowerCapacityAndProfileWireVersion() throws Exception {
        Map<ResourceLocation, RealmDefinition> realms = realms();
        for (var realm : realms.values()) {
            for (var stage : realm.stages()) {
                assertEquals(stage.id().equals(id("golden_late")), PanelReadouts.releaseCeiling(realm, stage.id()));
            }
        }
        RealmDefinition golden = realms.get(id("golden_core"));
        RealmStageDefinition late = stage(golden, id("golden_late"));
        assertTrue(golden.nextRealm().isEmpty());
        assertTrue(late.cultivationCap().isEmpty());
        assertTrue(late.advancement().isEmpty());
        assertTrue(late.spiritStoneCost().isEmpty());
        assertEquals(600, late.spiritualPowerCap());
        CultivationProfile original = profile("golden_core", "golden_late").withCurrentSpiritualPower(413);
        var encoded = CultivationProfile.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(4, encoded.getAsJsonObject().get("schema_version").getAsInt());
        assertEquals(original, CultivationProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertEquals(600, CultivationService.spiritualPowerCap(access(realms), original));
        assertEquals(413, original.currentSpiritualPower());
        assertEquals(500, golden.maximumLifespanYears());
        assertEquals(500L * 24_000 * 24 - original.lifespanConsumedTicks(), CultivationTimeMath.remainingTicks(
                original.lifespanConsumedTicks(), CultivationTimeMath.maximumLifespanTicks(500, 24_000L * 24)));
    }

    @Test
    void derivedStabilityRejectsCapsBeyondItsIntegerStorageLimit() {
        assertEquals(Integer.MAX_VALUE, RealmStageDefinition.stabilityCapFor(4_294_967_295L));
        assertThrows(IllegalArgumentException.class, () -> RealmStageDefinition.stabilityCapFor(4_294_967_296L));
    }

    private static Map<ResourceLocation, RealmDefinition> realms() throws Exception {
        Map<ResourceLocation, RealmDefinition> result = new LinkedHashMap<>();
        for (String id : List.of("mortal", "qi_refining", "foundation_establishment", "golden_core")) {
            result.put(id(id), RealmDefinition.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(Files.readString(DEFINITIONS.resolve("realm/" + id + ".json")))).getOrThrow());
        }
        return result;
    }

    private static RegistryAccess access(Map<ResourceLocation, RealmDefinition> realms) {
        var registry = new MappedRegistry<>(ModCultivationRegistries.REALMS, Lifecycle.stable());
        realms.forEach((id, definition) -> Registry.registerForHolder(registry, id, definition));
        registry.freeze();
        return new RegistryAccess.ImmutableRegistryAccess(List.of(registry));
    }

    private static RealmStageDefinition stage(RealmDefinition realm, ResourceLocation id) {
        return realm.stages().stream().filter(stage -> stage.id().equals(id)).findFirst().orElseThrow();
    }

    private static CultivationProfile profile(String realm, String stage) {
        return CultivationProfile.defaultProfile().withRealmAndStage(id(realm), id(stage))
                .withCurrentSpiritualPower(17).withLifespanConsumedTicks(1234)
                .withSpiritualAffinity(47).withMeditationQiReserve(55)
                .withSpiritualRoot(new SpiritualRoot(Map.of(ModCultivationRegistries.FIRE_ELEMENT_ID, 10_000)))
                .learnTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .withTechniqueMastery(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID, 99)
                .withActiveCoreTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
