package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FireSpiritEffectsTest {
    @Test void burnUsesActualPrimaryLossAndThreeInstallmentsWithoutReplay() {
        assertEquals(0, FireSpiritTarget.primaryLoss(20, 20, 1)); // fully shielded/absorbed
        assertEquals(2, FireSpiritTarget.primaryLoss(3, -50, 2.0 / 3)); // overkill + retaliation excluded
        assertEquals(4, FireSpiritTarget.primaryLoss(20, 14, 2.0 / 3));
        assertEquals(0, FireSpiritTarget.primaryLoss(20, 25, 1));
        assertEquals(0, FireSpiritTarget.primaryLoss(20, 0, Double.NaN));
        FireSpiritTarget ledger = new FireSpiritTarget();
        assertTrue(ledger.burn(100, 30, .2, 60, 20));
        assertEquals(0, ledger.drain(119).burn());
        assertEquals(2, ledger.drain(120).burn());
        assertEquals(0, ledger.drain(120).burn());
        assertEquals(2, ledger.drain(140).burn());
        assertEquals(2, ledger.drain(160).burn());
        assertEquals(0, ledger.drain(1000).burn());
    }

    @Test void independentHitsKeepTheirDamageAndLifetimeAndCombineOnlyCoincidentPulses() {
        var ledger = new FireSpiritTarget();
        ledger.burn(0, 30, .2, 60, 20);
        ledger.burn(10, 60, .2, 60, 20);
        assertEquals(2, ledger.drain(20).burn());
        assertEquals(4, ledger.drain(30).burn());
        assertEquals(12, ledger.drain(100).burn());
        assertEquals(0, ledger.burnBuckets());
        assertEquals(0, ledger.drain(100).burn());
    }

    @Test void configuredIntervalsKeepTheFinalDeadlineAndPayAnExactRemainder() {
        var ledger = new FireSpiritTarget();
        assertTrue(ledger.burn(0, 55, .2, 55, 20));
        assertEquals(4, ledger.drain(20).burn());
        assertEquals(4, ledger.drain(40).burn());
        assertEquals(0, ledger.drain(54).burn());
        assertEquals(3, ledger.drain(55).burn());
        assertTrue(ledger.idle());
        assertTrue(ledger.burn(100, 10, .2, 10, 30));
        assertEquals(0, ledger.drain(109).burn());
        assertEquals(2, ledger.drain(110).burn());
        assertFalse(ledger.burn(0, 10, .2, 24000, 1)); // a single hit cannot exceed the work budget
        assertFalse(ledger.burn(0, 10, .2, 60, 0));
    }

    @Test void emptyLedgersBecomeIdleOnlyAfterBurnAndBurstWorkHasBeenDrained() {
        var ledger = new FireSpiritTarget();
        assertTrue(ledger.idle());
        ledger.burn(0, 10, .2, 60, 20);
        assertFalse(ledger.idle());
        ledger.hit(3, true, 10, .8);
        assertFalse(ledger.idle());
        ledger.hit(3, true, 10, .8);
        ledger.hit(3, true, 10, .8);
        assertEquals(0, ledger.hits());
        assertFalse(ledger.idle()); // pending burst and burn both retain the slot
        assertEquals(8, ledger.drain(0).burst());
        assertFalse(ledger.idle());
        ledger.drain(60);
        assertTrue(ledger.idle()); // runtime can now return this slot to the owner/global budget
        ledger.hit(3, false, 10, .8);
        assertFalse(ledger.idle()); // unfinished cumulative hits intentionally retain their ledger
    }

    @Test void thirdHitQueuesExactlyOneWeaponAttributeBurstAndCooldownCannotBeBypassed() {
        var ledger = new FireSpiritTarget();
        assertFalse(ledger.hit(3, true, 10, .8));
        assertFalse(ledger.hit(3, true, 10, .8));
        assertTrue(ledger.hit(3, true, 10, .8));
        assertEquals(0, ledger.hits());
        assertEquals(8, ledger.drain(0).burst());
        assertEquals(0, ledger.drain(0).burst());
        for (int i = 0; i < 100; i++) assertFalse(ledger.hit(3, false, 10, .8));
        assertEquals(3, ledger.hits());
        assertEquals(0, ledger.drain(100).burst()); // time alone cannot proc a banked stack
        assertTrue(ledger.hit(3, true, 20, .8));
        assertEquals(16, ledger.drain(101).burst());
    }

    @Test void cooldownIsTargetScopedRetainsDeadlinesAcrossCodecAndHasAnExactBoundary() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var state = FireSpiritCooldownState.EMPTY.record(first, 100, 100);
        assertEquals(200, state.readyAt(first));
        assertFalse(state.canRecord(first, 199));
        assertTrue(state.canRecord(first, 200));
        assertTrue(state.canRecord(second, 101));
        assertSame(state, state.record(first, 199, 100));
        var json = FireSpiritCooldownState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        assertEquals(state, FireSpiritCooldownState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(FireSpiritCooldownState.EMPTY,
                FireSpiritCooldownState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
        assertEquals(300, state.record(first, 200, 100).readyAt(first));
    }

    @Test void fullCooldownMapRefusesNewTargetInsteadOfEvictingAnActiveDeadline() {
        Map<String, Long> deadlines = new HashMap<>();
        for (int i = 0; i < FireSpiritCooldownState.MAX_TARGETS; i++)
            deadlines.put(new UUID(0, i).toString(), 200L);
        var full = new FireSpiritCooldownState(deadlines);
        UUID extra = new UUID(1, 0);
        assertFalse(full.canRecord(extra, 100));
        assertSame(full, full.record(extra, 100, 100));
        var released = full.record(extra, 200, 100);
        assertEquals(Map.of(extra.toString(), 300L), released.deadlines());
        assertThrows(IllegalArgumentException.class,
                () -> new FireSpiritCooldownState(Map.of("invalid", 100L)));
        assertThrows(IllegalArgumentException.class,
                () -> new FireSpiritCooldownState(Map.of(extra.toString(), -1L)));
    }

    @Test void transientBurnWorkIsBoundedAndInvalidDamageCannotCreateWork() {
        var ledger = new FireSpiritTarget();
        for (int i = 0; i < 1000; i++) ledger.burn(i, 1, .2, 24000, 8000);
        assertEquals(FireSpiritTarget.MAX_BURN_BUCKETS, ledger.burnBuckets());
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertFalse(new FireSpiritTarget().burn(0, invalid, .2, 60, 20));
        assertFalse(new FireSpiritTarget().burn(0, -1, -.2, 60, 20));
        assertFalse(new FireSpiritTarget().burn(Long.MAX_VALUE, 1, .2, 60, 20));
        assertFalse(new FireSpiritTarget().hit(0, true, 10, .8));
    }

    @Test void savedCooldownSurvivesDeathAndOperatorResetWithoutRestoringTransientDamage() {
        UUID target = UUID.randomUUID();
        var cooldown = FireSpiritCooldownState.EMPTY.record(target, 20, 100);
        var state = FateState.empty().withFireSpirit(cooldown);
        assertEquals(cooldown, state.afterDeath().fireSpirit());
        assertEquals(cooldown, state.resetChoices().fireSpirit());
        var json = FateState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        assertEquals(cooldown, FateState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().fireSpirit());
        assertEquals(0, new FireSpiritTarget().drain(100).burn());
        assertEquals(0, new FireSpiritTarget().hits());
    }

    @Test void authoredDefinitionOwnsAllSixValuesAndIsNoLongerPlaceholder() throws Exception {
        try (var stream = getClass().getResourceAsStream("/data/myvillage/myvillage/innate_fate/fire_spirit_body.json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertFalse(json.getAsJsonObject().has("placeholder"));
            var definition = FateDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(.2, definition.effect("fire_burn_fraction"));
            assertEquals(60, definition.effect("fire_burn_ticks"));
            assertEquals(20, definition.effect("fire_burn_interval_ticks"));
            assertEquals(3, definition.effect("fire_burst_hits"));
            assertEquals(.8, definition.effect("fire_burst_multiplier"));
            assertEquals(100, definition.effect("fire_burst_cooldown_ticks"));
        }
    }
}
