package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemperedBonesEffectsTest {
    @Test void onlyActualEnemyHealthLossAccumulatesAndReadinessLatches() {
        TemperedBonesState partial = TemperedBonesEffects.charge(TemperedBonesState.EMPTY, 2, 20, .3, true);
        assertEquals(new TemperedBonesState(2, false), partial);
        partial = TemperedBonesEffects.charge(partial, 3, 20, .3, true);
        assertFalse(partial.ready());
        var ready = TemperedBonesEffects.charge(partial, 1, 20, .3, true);
        assertEquals(new TemperedBonesState(6, true), ready);
        // Overkill and further attacks cannot bank another retaliation behind the ready one.
        assertSame(ready, TemperedBonesEffects.charge(ready, 100, 20, .3, true));
        assertSame(ready, TemperedBonesEffects.charge(ready, 100, 40, .3, true));
    }

    @Test void environmentalSelfAlliedAndFullyAbsorbedHitsDoNotCharge() {
        var partial = new TemperedBonesState(2, false);
        assertSame(partial, TemperedBonesEffects.charge(partial, 10, 20, .3, false));
        for (double lost : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertSame(partial, TemperedBonesEffects.charge(partial, lost, 20, .3, true));
        assertSame(partial, TemperedBonesEffects.charge(partial, 1, 0, .3, true));
        assertSame(partial, TemperedBonesEffects.charge(partial, 1, 20, 0, true));
        // Shield/armor/absorption never count their incoming amount: health stayed unchanged.
        assertEquals(0, TemperedBonesEffects.primaryHealthLoss(12, 12, 1));
    }

    @Test void rejectionMissAndBlockedDamageKeepChargeAndOnlyFirstSuccessfulTargetConsumesIt() {
        var ready = new TemperedBonesState(6, true);
        assertSame(ready, TemperedBonesEffects.consume(ready, false));
        var consumed = TemperedBonesEffects.consume(ready, true);
        assertEquals(TemperedBonesState.EMPTY, consumed);
        assertSame(consumed, TemperedBonesEffects.consume(consumed, true));
        assertEquals(0, TemperedBonesEffects.bonusDamage(8, 1, consumed.ready(), true));
        assertEquals(new TemperedBonesState(1, false), TemperedBonesEffects.charge(consumed, 1, 20, .3, true));
    }

    @Test void bonusIsOneWeaponAttributeDamageAndNeverAWholeMoveOrSwordQiMultiplier() {
        assertEquals(8, TemperedBonesEffects.bonusDamage(8, 1, true, true));
        assertEquals(4, TemperedBonesEffects.bonusDamage(8, .5, true, true));
        assertEquals(0, TemperedBonesEffects.bonusDamage(8, 1, true, false));
        assertEquals(0, TemperedBonesEffects.bonusDamage(8, 1, false, true));
        assertEquals(0, TemperedBonesEffects.bonusDamage(Double.NaN, 1, true, true));
    }

    @Test void absorptionAndCommittedShieldHitsConsumeEvenWhenNoHealthWasLost() {
        var ready = new TemperedBonesState(6, true);
        // Remaining damage at final Pre can be fully paid by absorption hearts, so Post may not fire.
        assertEquals(TemperedBonesState.EMPTY, TemperedBonesEffects.consume(ready,
                TemperedBonesEffects.acceptedDamage(4, 0)));
        // A separate pill shield / raised shield provides a committed absorption receipt.
        assertEquals(TemperedBonesState.EMPTY, TemperedBonesEffects.consume(ready,
                TemperedBonesEffects.acceptedDamage(0, 4)));
        // In all these cases zero actual health loss still contributes zero charge to the defender.
        assertEquals(0, TemperedBonesEffects.primaryHealthLoss(20, 20, .5));
        assertSame(ready, TemperedBonesEffects.consume(ready, TemperedBonesEffects.acceptedDamage(0, 0)));
        assertFalse(TemperedBonesEffects.acceptedDamage(Double.NaN, Double.POSITIVE_INFINITY));
    }

    @Test void retaliationShareNeverChargesDefenderAndOverkillCountsOnlyLostHealth() {
        // A 16-damage move plus 8 weapon damage creates a primary share of 2/3.
        // Both pass together through one mitigation sequence; only the primary health loss charges.
        double share = 16.0 / (16 + 8);
        assertEquals(4, TemperedBonesEffects.primaryHealthLoss(20, 14, share), 1.0e-12);
        assertEquals(2, TemperedBonesEffects.primaryHealthLoss(3, 0, share), 1.0e-12);
        assertEquals(0, TemperedBonesEffects.primaryHealthLoss(10, 12, share));
        assertEquals(0, TemperedBonesEffects.primaryHealthLoss(10, 0, Double.NaN));
        assertEquals(0, TemperedBonesEffects.primaryHealthLoss(10, 0, 0));
    }

    @Test void chargeCodecKeepsExactProgressAcrossReloadAndLegacyDefaultsDoNotGrantCharge() {
        for (var state : List.of(TemperedBonesState.EMPTY, new TemperedBonesState(2.125, false),
                new TemperedBonesState(6, true))) {
            var json = TemperedBonesState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
            assertEquals(state, TemperedBonesState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
        assertEquals(TemperedBonesState.EMPTY,
                TemperedBonesState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
        assertThrows(IllegalArgumentException.class, () -> new TemperedBonesState(-1, false));
        assertThrows(IllegalArgumentException.class, () -> new TemperedBonesState(Double.NaN, false));
        assertThrows(IllegalArgumentException.class, () -> new TemperedBonesState(Double.POSITIVE_INFINITY, false));
        assertThrows(IllegalArgumentException.class, () -> new TemperedBonesState(0, true));
    }

    @Test void attachmentUpdatesPreserveOtherTimersAndDeathClearsCharge() {
        var initial = FateState.empty().timers(100, 12, .5, 200)
                .withTemperedBones(new TemperedBonesState(6, true));
        var consumed = initial.withTemperedBones(TemperedBonesState.EMPTY);
        assertEquals(100, consumed.recoveryReadyAt());
        assertEquals(12, consumed.healingTicks());
        assertEquals(.5, consumed.healingPerTick());
        assertEquals(200, consumed.hunterReadyAt());
        assertEquals(TemperedBonesState.EMPTY, initial.afterDeath().temperedBones());
        assertEquals(TemperedBonesState.EMPTY, initial.resetChoices().temperedBones());
    }

    @Test void definitionOwnsAllThresholdDamageAndPulseTuning() throws Exception {
        try (var stream = getClass().getResourceAsStream("/data/myvillage/myvillage/innate_fate/tempered_bones.json")) {
            assertNotNull(stream);
            var definition = FateDefinition.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))).getOrThrow();
            assertEquals(.3, definition.effect("bones_threshold"));
            assertEquals(1, definition.effect("bones_damage_bonus"));
            assertTrue(definition.effect("bones_knockback_radius") > 0);
            assertTrue(definition.effect("bones_knockback_strength") > 0);
        }
    }
}
