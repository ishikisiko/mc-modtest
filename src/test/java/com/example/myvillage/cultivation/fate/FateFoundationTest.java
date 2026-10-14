package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.network.CultivationSnapshotPayload;
import com.example.myvillage.cultivation.network.FateIntentPayload;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
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
            var intent = new FateIntentPayload((byte) 2, 50, (byte) 7);
            FateIntentPayload.STREAM_CODEC.encode(buffer, intent); assertEquals(3, buffer.readableBytes());
            assertEquals(intent, FateIntentPayload.STREAM_CODEC.decode(buffer));
            var snapshot = new CultivationSnapshotPayload(CultivationProfile.defaultProfile(), roll(), 12);
            CultivationSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
            assertEquals(snapshot, CultivationSnapshotPayload.STREAM_CODEC.decode(buffer)); assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void invalidDataDoesNotEnableUnimplementedEffects() {
        assertThrows(IllegalArgumentException.class, () -> new FateDefinition("a", "b", 0, 1, "g", true, Map.of("phoenix", 1.0), Optional.empty(), 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new FateDefinition("a", "b", 0, 1, "g", true, Map.of("health", Double.NaN), Optional.empty(), 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new FateDefinition("a", "b", 0, 1, "g", true, Map.of("recovery_fraction", .3), Optional.empty(), 0, 5));
    }
}
