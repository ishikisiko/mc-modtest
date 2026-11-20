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
    private static final Path DEFINITIONS = Path.of("src/main/resources/data/yuanxian/yuanxian");

    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void requiredBaselineMatchesSixRealmsAndTwentyThreeStages() throws Exception {
        Map<ResourceLocation, RealmDefinition> realms = realms();
        assertEquals(ModCultivationRegistries.REQUIRED_REALM_IDS, realms.keySet());
        assertEquals(6, realms.size());
        assertEquals(23, ModCultivationRegistries.REQUIRED_STAGE_OWNERS.size());
        for (var owner : ModCultivationRegistries.REQUIRED_STAGE_OWNERS.entrySet()) {
            assertTrue(realms.get(owner.getValue()).containsStage(owner.getKey()), owner.toString());
        }
        assertEquals(23, realms.values().stream().mapToInt(realm -> realm.stages().size()).sum());
        assertTrue(realms.containsKey(id("nascent_soul")));
        assertTrue(realms.containsKey(id("spirit_transformation")));
    }

    @Test
    void allTransitionsPreserveUnrelatedStateAndUseTheExistingStatsPolicy() throws Exception {
        Map<ResourceLocation, RealmDefinition> realms = realms();
        RegistryAccess access = access(realms);
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
            var oldStats = CultivationStats.base(before);
            var newStats = CultivationStats.base(next);
            woundedHealth = RealmHealthPolicy.refreshed(woundedHealth, (float) oldStats.health(), (float) newStats.health());
            assertEquals(11, woundedHealth, "Advancement must not heal");
            if (!before.realmId().equals(next.realmId())) {
                assertTrue(realms.get(next.realmId()).maximumLifespanYears() > sourceRealm.maximumLifespanYears());
                if (before.realmId().equals(ModCultivationRegistries.QI_REFINING_REALM_ID)
                        || before.realmId().equals(ModCultivationRegistries.FOUNDATION_ESTABLISHMENT_REALM_ID)
                        || before.realmId().equals(ModCultivationRegistries.NASCENT_SOUL_REALM_ID)) {
                    assertEquals(AdvancementKind.BOTTLENECK, rule.kind());
                } else {
                    assertEquals(AdvancementKind.ORDINARY, rule.kind());
                }
            }
            reached.add(next.stageId());
            current = next;
            assertTrue(reached.size() <= 21, "Unexpected future-stage transition");
        }
        List<ResourceLocation> expected = new ArrayList<>(ModCultivationRegistries.QI_REFINING_STAGE_IDS);
        expected.addAll(List.of(id("foundation_early"), id("foundation_middle"), id("foundation_late"),
                id("golden_early"), id("golden_middle"), id("golden_late"),
                id("nascent_early"), id("nascent_middle"), id("nascent_late"),
                id("spirit_early"), id("spirit_middle"), id("spirit_late")));
        assertEquals(expected, reached);
        assertEquals(id("spirit_transformation"), current.realmId());
        assertEquals(id("spirit_late"), current.stageId());
        assertEquals(1600, CultivationStats.base(current).power());
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
        assertEquals(21, checked);
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
    void spiritLateIsTheOnlyCeilingAndKeepsItsPowerCapacityAndProfileWireVersion() throws Exception {
        Map<ResourceLocation, RealmDefinition> realms = realms();
        for (var realm : realms.values()) {
            for (var stage : realm.stages()) {
                assertEquals(stage.id().equals(id("spirit_late")), PanelReadouts.releaseCeiling(realm, stage.id()));
            }
        }
        RealmDefinition golden = realms.get(id("golden_core"));
        RealmDefinition spirit = realms.get(id("spirit_transformation"));
        RealmStageDefinition late = stage(spirit, id("spirit_late"));
        assertTrue(spirit.nextRealm().isEmpty());
        assertEquals(id("nascent_soul"), golden.nextRealm().orElseThrow());
        assertTrue(late.cultivationCap().isEmpty());
        assertTrue(late.advancement().isEmpty());
        assertTrue(late.spiritStoneCost().isEmpty());
        assertEquals(0, late.spiritualPowerCap(), "容量只由 progression 数表提供");
        CultivationProfile original = profile("golden_core", "golden_late").withFoundationQuality(4).withGoldenCoreQuality(3).withCurrentSpiritualPower(413);
        var encoded = CultivationProfile.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(6, encoded.getAsJsonObject().get("schema_version").getAsInt());
        assertEquals(original, CultivationProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertEquals(560, CultivationService.spiritualPowerCap(access(realms), original));
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
        for (String id : List.of("mortal", "qi_refining", "foundation_establishment", "golden_core", "nascent_soul", "spirit_transformation")) {
            result.put(id(id), RealmDefinition.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(Files.readString(DEFINITIONS.resolve("realm/" + id + ".json")))).getOrThrow());
        }
        return result;
    }

    @Test
    void deathCanCrossRealmBoundaryAndRetainsTheExactOldQuality() throws Exception {
        var registry = access(realms());
        var before = profile("golden_core", "golden_early").withFoundationQuality(4).withGoldenCoreQuality(3)
                .withCultivationProgress(1800).withStability(1000);
        var dropped = CultivationRecovery.death(registry, before);
        assertEquals(id("foundation_middle"), dropped.stageId());
        assertEquals(0, dropped.cultivationProgress());
        assertEquals(0, dropped.stability());
        assertEquals(4, dropped.foundationQuality());
        assertEquals(3, dropped.goldenCoreQuality());
        assertTrue(CultivationRecovery.canRestore(registry, dropped));
        assertTrue(CultivationRecovery.covers(registry, dropped, CultivationRecovery.stage(before)));
        assertFalse(CultivationRecovery.covers(registry, dropped, new CultivationMemory.Stage(id("golden_core"), id("golden_middle"))));
        assertEquals(before.learnedTechniques(), dropped.learnedTechniques());
    }

    @Test
    void intactSoulProtectsExactlyOneDeathAndReformationRearmsIt() throws Exception {
        var registry = access(realms());
        var original = profile("nascent_soul", "nascent_middle").withCultivationProgress(2100).withStability(2000);
        var reached = CultivationRecovery.remember(registry, original.withMemory(original.memory().form(3)));
        var firstDeath = CultivationRecovery.death(registry, reached);
        assertEquals(id("nascent_middle"), firstDeath.stageId());
        assertEquals(2100, firstDeath.cultivationProgress());
        assertEquals(2000, firstDeath.stability());
        assertFalse(firstDeath.memory().intact());
        assertEquals(3, firstDeath.memory().nascentQuality());
        var secondDeath = CultivationRecovery.death(registry, firstDeath);
        assertEquals(id("golden_late"), secondDeath.stageId());
        assertFalse(CultivationRecovery.canReform(registry, secondDeath));
        var recovered = CultivationRecovery.remember(registry,
                secondDeath.withRealmAndStage(id("nascent_soul"), id("nascent_middle")));
        assertFalse(recovered.memory().intact());
        assertTrue(CultivationRecovery.canReform(registry, recovered));
        var memory = recovered.memory().reform(2160, 2160);
        var reformed = recovered.withMemory(memory);
        assertEquals(reformed.memory(), CultivationProfile.CODEC.parse(JsonOps.INSTANCE,
                CultivationProfile.CODEC.encodeStart(JsonOps.INSTANCE, reformed).getOrThrow()).getOrThrow().memory());
        assertEquals(id("nascent_middle"), CultivationRecovery.death(registry, reformed).stageId());
    }

    @Test
    void awakenedCharactersCanRecoverFromTheBottomOfTheLadder() throws Exception {
        var registry = access(realms());
        var dropped = CultivationRecovery.death(registry, profile("qi_refining", "qi_refining_1"));
        assertEquals(id("mortal_qi_sensed"), dropped.stageId());
        assertTrue(dropped.awakened());
        assertTrue(CultivationRecovery.canRestore(registry, dropped));
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
        return ResourceLocation.fromNamespaceAndPath("yuanxian", path);
    }
}
