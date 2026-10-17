package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.network.CultivationSnapshotPayload;
import com.example.myvillage.cultivation.network.FateIntentPayload;
import com.example.myvillage.cultivation.network.AwakeningOpenPayload;
import com.example.myvillage.cultivation.network.AwakeningClosePayload;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FateFoundationTest {
    private static final String[] IDS = {"robust", "lightfoot", "bright", "fire_affinity", "alchemist_descendant", "beast_hunter", "unyielding", "burning_star"};
    private static Map<String, FateDefinition> defs;
    @BeforeAll static void setup() throws Exception {
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        Map<String, FateDefinition> values = new TreeMap<>();
        for (String id : IDS) try (var in = FateFoundationTest.class.getResourceAsStream("/data/myvillage/myvillage/innate_fate/" + id + ".json")) {
            assertNotNull(in); values.put("myvillage:" + id, FateDefinition.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getOrThrow());
        }
        defs = Map.copyOf(values);
    }
    @Test void allBudgetsAllLocksHaveSixUniqueCandidatesAndLegalTriple() {
        Random random = new Random(90210);
        for (int budget = 3; budget <= 18; budget++) for (String lock : defs.keySet()) for (int n = 0; n < 20; n++) {
            var ids = FateSelection.roll(defs, lock, budget, FateRules.bundled(), random);
            assertEquals(6, ids.size()); assertEquals(6, new HashSet<>(ids).size()); assertTrue(ids.contains(lock));
            assertTrue(FateSelection.hasTriple(ids, defs, budget));
        }
    }
    @Test void highTierPlaceholdersDecodeSelectAndHaveNoEffects() throws Exception {
        Map<String, FateDefinition> pool = new TreeMap<>(defs);
        for (String id : List.of("fire_spirit_body", "innate_sword_heart", "flawless_dao_heart")) {
            try (var in = FateFoundationTest.class.getResourceAsStream("/data/myvillage/myvillage/innate_fate/" + id + ".json")) {
                assertNotNull(in);
                var definition = FateDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getOrThrow();
                assertTrue(definition.effects().isEmpty());
                assertTrue(definition.eligible(root(2500, 2500, 2500, 2500)));
                pool.put("myvillage:" + id, definition);
            }
        }
        Set<String> seen = new HashSet<>();
        Random random = new Random(73019);
        for (int n = 0; n < 100; n++) {
            var choices = FateSelection.roll(pool, "", 8, FateRules.bundled(), random);
            assertTrue(FateSelection.hasTriple(choices, pool, 8));
            seen.addAll(choices);
        }
        assertTrue(seen.containsAll(pool.keySet()));
        assertEquals("", FateSelection.validate(List.of("myvillage:flawless_dao_heart",
                "myvillage:robust", "myvillage:bright"), pool, 8, true));
    }
    @Test void eligibilityRespectsPurityAndRootCount() {
        var def = new FateDefinition("name", "desc", 5, 6, "body", true, Map.of(),
                Optional.of(ResourceLocation.parse("myvillage:fire")), 8000, 3);
        assertFalse(def.eligible(Optional.empty()));
        assertTrue(def.eligible(root(8000, 1000, 1000, 0)));
        assertFalse(def.eligible(root(7900, 1100, 1000, 0)));
        assertFalse(def.eligible(root(8000, 500, 500, 1000)));
    }
    private static Optional<SpiritualRoot> root(int fire, int a, int b, int c) {
        return Optional.of(new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:fire"), fire,
                ResourceLocation.parse("myvillage:wood"), a, ResourceLocation.parse("myvillage:water"), b,
                ResourceLocation.parse("myvillage:earth"), c)));
    }
    private static FateState roll() { return FateSelection.transition(FateState.empty(), 0, 0, 0, defs, 8, FateRules.bundled(), new Random(1)); }
    @Test void steleInitializesOnlyAbsentDraftAndPreservesLockedOrConfirmedChoices() {
        var draft = FateSelection.initializeDraft(FateState.empty(), defs, 8, FateRules.bundled(), new Random(1));
        assertEquals(1, draft.rolls());
        assertEquals(6, draft.candidates().size());
        var locked = FateSelection.transition(draft, 1, draft.revision(), 0, defs, 8, FateRules.bundled(), new Random());
        var confirmed = FateSelection.transition(locked, 2, locked.revision(), validMask(locked, 8), defs, 8, FateRules.bundled(), new Random());
        for (var state : List.of(draft, locked, confirmed))
            assertSame(state, FateSelection.initializeDraft(state, Map.of(), 3, FateRules.bundled(), new Random(99)));
    }
    private static int validMask(FateState state, int budget) {
        for (int mask = 0; mask < 64; mask++) if (Integer.bitCount(mask) == 3
                && FateSelection.validate(FateSelection.mask(state.candidates(), mask), defs, budget, true).isEmpty()) return mask;
        throw new AssertionError("No triple");
    }
    @Test void confirmationRejectsStaleMasksAndMutationAfterBinding() {
        var s = roll();
        assertThrows(IllegalArgumentException.class, () -> FateSelection.transition(s, 2, 0, 7, defs, 8, FateRules.bundled(), new Random()));
        for (int bad : new int[]{-1, 0, 3, 127, 255}) assertThrows(IllegalArgumentException.class,
                () -> FateSelection.transition(s, 2, s.revision(), bad, defs, 8, FateRules.bundled(), new Random()));
        var done = FateSelection.transition(s, 2, s.revision(), validMask(s, 8), defs, 8, FateRules.bundled(), new Random());
        assertTrue(done.confirmed()); assertEquals(3, done.selected().size());
        for (int action = 0; action < 3; action++) {
            int a = action;
            assertThrows(IllegalArgumentException.class, () -> FateSelection.transition(done, a, done.revision(), 0, defs, 18, FateRules.bundled(), new Random()));
        }
    }
    @Test void singleLockAndServerBudgetAreIndependent() {
        var s = roll();
        var locked = FateSelection.transition(s, 1, s.revision(), 0, defs, 8, FateRules.bundled(), new Random());
        assertThrows(IllegalArgumentException.class, () -> FateSelection.transition(locked, 1, locked.revision(), 1, defs, 8, FateRules.bundled(), new Random()));
        var next = FateSelection.transition(locked, 0, locked.revision(), 0, defs, 3, FateRules.bundled(), new Random(2));
        assertTrue(next.candidates().contains(locked.locked())); assertTrue(FateSelection.hasTriple(next.candidates(), defs, 3));
        assertEquals("budget", FateSelection.validate(List.of("myvillage:burning_star", "myvillage:robust", "myvillage:bright"), defs, 3, true));
        assertThrows(IllegalArgumentException.class, () -> FateSelection.transition(s, 99, s.revision(), 0, defs, 8, FateRules.bundled(), new Random()));
    }
    @Test void multipleHighTierFatesConfirmWhenBudgetAndGroupsAllow() {
        Map<String, FateDefinition> pool = new TreeMap<>(defs);
        for (int tier = 3; tier <= 5; tier++) {
            pool.put("test:high_" + tier, new FateDefinition("name", "description", tier, tier + 1,
                    "high_group_" + tier, true, Map.of("gain", .1), Optional.empty(), 0, 5));
        }
        List<String> candidates = List.of("test:high_3", "test:high_4", "test:high_5",
                "myvillage:robust", "myvillage:lightfoot", "myvillage:bright");
        FateState draft = FateState.empty().choice(candidates, "", List.of(), false, 1);
        var twoHigh = FateSelection.transition(draft, 2, draft.revision(), 11, pool, 10,
                FateRules.bundled(), new Random(1));
        assertTrue(twoHigh.confirmed());
        assertEquals(List.of("test:high_3", "test:high_4", "myvillage:robust"), twoHigh.selected());
        var threeHigh = FateSelection.transition(draft, 2, draft.revision(), 7, pool, 15,
                FateRules.bundled(), new Random(1));
        assertTrue(threeHigh.confirmed());
        assertEquals(candidates.subList(0, 3), threeHigh.selected());
        assertEquals("budget", assertThrows(IllegalArgumentException.class, () ->
                FateSelection.transition(draft, 2, draft.revision(), 7, pool, 14,
                        FateRules.bundled(), new Random(1))).getMessage());
        assertEquals("count", FateSelection.validate(candidates.subList(0, 2), pool, 18, true));
        var sameGroup = new TreeMap<>(pool);
        sameGroup.put("test:high_4", new FateDefinition("name", "description", 4, 5,
                "high_group_3", true, Map.of("gain", .1), Optional.empty(), 0, 5));
        assertEquals("exclusive", assertThrows(IllegalArgumentException.class, () ->
                FateSelection.transition(draft, 2, draft.revision(), 7, sameGroup, 18,
                        FateRules.bundled(), new Random(1))).getMessage());
    }
    @Test void serializationRetainsDraftConfirmedAndCooldownState() {
        var draft = roll();
        var done = FateSelection.transition(draft, 2, draft.revision(), validMask(draft, 8), defs, 8, FateRules.bundled(), new Random())
                .timers(9000, 80, .06, 8000);
        for (var state : List.of(FateState.empty(), draft, done)) {
            var json = FateState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
            assertEquals(state, FateState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
    }
    @Test void recoveryIsNonlethalStrictThresholdAndPersistedCooldown() {
        var d = defs.get("myvillage:alchemist_descendant");
        var empty = FateState.empty();
        var state = new FateState(1, 1, List.of("myvillage:robust", "myvillage:bright", "myvillage:alchemist_descendant"), "",
                List.of("myvillage:robust", "myvillage:bright", "myvillage:alchemist_descendant"), true, 0, 0, 0, 0);
        assertEquals(empty, FateEffects.beginRecovery(empty, 100, 1, 20, .35, .3, 100, 1200));
        assertEquals(state, FateEffects.beginRecovery(state, 100, 0, 20, .35, .3, 100, 1200));
        assertEquals(state, FateEffects.beginRecovery(state, 100, 7, 20, .35, .3, 100, 1200));
        var start = FateEffects.beginRecovery(state, 100, 6, 20, d.effect("recovery_threshold"), d.effect("recovery_fraction"),
                (int) d.effect("recovery_ticks"), (int) d.effect("recovery_cooldown"));
        assertEquals(1300, start.recoveryReadyAt()); assertEquals(6, start.healingPerTick() * start.healingTicks(), 1e-9);
        var afterDeath = start.timers(start.recoveryReadyAt(), 0, 0, start.hunterReadyAt());
        assertEquals(afterDeath, FateEffects.beginRecovery(afterDeath, 1299, 1, 20, .35, .3, 100, 1200));
        assertEquals(100, FateEffects.beginRecovery(afterDeath, 1300, 1, 20, .35, .3, 100, 1200).healingTicks());
    }
    @Test void damageBonusesAddOnceAndKillRecoveryIsBoundedToCapFraction() {
        assertEquals(1, FateEffects.damageFactor(0, 0, 0));
        assertEquals(1.6, FateEffects.damageFactor(.3, .15, .15), 1e-12);
        assertEquals(8, FateEffects.recoveredPower(100, .08)); assertEquals(0, FateEffects.recoveredPower(0, .08));
        assertNotEquals(defs.get("myvillage:unyielding").effect("low_threshold"), defs.get("myvillage:burning_star").effect("low_speed_threshold"));
    }
    @Test void actualNetworkCodecsCarryOnlyIntentAndOwningSnapshot() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            var intent = new FateIntentPayload((byte) 2, 50, (byte) 7, 9);
            FateIntentPayload.STREAM_CODEC.encode(buffer, intent); assertEquals(4, buffer.readableBytes());
            assertEquals(intent, FateIntentPayload.STREAM_CODEC.decode(buffer));
            var open = new AwakeningOpenPayload(new BlockPos(-32, 80, 64), 9);
            AwakeningOpenPayload.STREAM_CODEC.encode(buffer, open);
            assertEquals(open, AwakeningOpenPayload.STREAM_CODEC.decode(buffer));
            var close = new AwakeningClosePayload(9);
            AwakeningClosePayload.STREAM_CODEC.encode(buffer, close);
            assertEquals(close, AwakeningClosePayload.STREAM_CODEC.decode(buffer));
            var snapshot = new CultivationSnapshotPayload(CultivationProfile.defaultProfile(), roll(), 12);
            CultivationSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
            assertEquals(snapshot, CultivationSnapshotPayload.STREAM_CODEC.decode(buffer)); assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void swordObsessionDefinitionIsCompleteAndCodecPreservesItsWaveReference() throws Exception {
        try (var in = FateFoundationTest.class.getResourceAsStream("/data/myvillage/myvillage/innate_fate/sword_obsession.json")) {
            assertNotNull(in);
            var definition = FateDefinition.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getOrThrow();
            assertEquals(.08, definition.effect("sword_stack_bonus"));
            assertEquals(5, definition.effect("sword_max_stacks"));
            assertEquals(100, definition.effect("sword_timeout_ticks"));
            assertEquals("myvillage:sword_obsession_wave", definition.derivedSkill().orElseThrow().toString());
            assertEquals(definition, FateDefinition.CODEC.parse(JsonOps.INSTANCE,
                    FateDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition).getOrThrow()).getOrThrow());
        }
        assertThrows(IllegalArgumentException.class, () -> new FateDefinition("a", "b", 3, 4, "g", true,
                Map.of("sword_stack_bonus", .08, "sword_max_stacks", 5.0, "sword_timeout_ticks", 100.0),
                Optional.empty(), 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new FateDefinition("a", "b", 3, 4, "g", true,
                Map.of("sword_max_stacks", 5.5), Optional.empty(), 0, 5));
    }

    @Test void invalidDataDoesNotEnableUnimplementedEffects() {
        assertThrows(IllegalArgumentException.class, () -> new FateDefinition("a", "b", 0, 1, "g", true, Map.of("phoenix", 1.0), Optional.empty(), 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new FateDefinition("a", "b", 0, 1, "g", true, Map.of("health", Double.NaN), Optional.empty(), 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new FateDefinition("a", "b", 0, 1, "g", true, Map.of("recovery_fraction", .3), Optional.empty(), 0, 5));
    }
}
