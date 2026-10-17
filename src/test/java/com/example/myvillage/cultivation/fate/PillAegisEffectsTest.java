package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.alchemy.AlchemyRules;
import com.example.myvillage.alchemy.MedicineState;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PillAegisEffectsTest {
    private static PillAegisState grant(PillAegisState current, long now) {
        return PillAegisEffects.afterConsumption(current, now, "yangyuan", true, 20, .25, 160, 600);
    }

    @Test void healingMultipliesEveryExistingQualityWithoutChangingTheMedicineClock() {
        var rules = AlchemyRules.get();
        for (int quality = 1; quality <= 4; quality++) {
            double ordinary = 40 * rules.effects().yangyuan().healFraction() * rules.qualityMultiplier(quality);
            assertEquals(ordinary * 1.5, PillAegisEffects.healingAmount("yangyuan", ordinary, .5), 1e-10);
            assertEquals(ordinary, PillAegisEffects.healingAmount("yangyuan", ordinary, 0), 1e-10);
            var current = new MedicineState(100, "yangyuan", 60, 19, quality);
            var tick = current.tick(20);
            assertTrue(tick.heal());
            assertEquals(59, tick.next().effectTicks());
            assertEquals(quality, tick.next().quality());
            assertEquals(0, current.clearBenefit().effectTicks());
            assertEquals(100, current.clearBenefit().cooldownTicks());
        }
        assertEquals(4, PillAegisEffects.healingAmount("huiling", 4, .5));
        assertEquals(4, PillAegisEffects.healingAmount("ningshen", 4, .5));
    }

    @Test void onlySuccessfulYangyuanConsumptionCreatesTheShield() {
        var empty = PillAegisState.EMPTY;
        assertEquals(empty, PillAegisEffects.afterConsumption(empty, 100, "yangyuan", false, 20, .25, 160, 600));
        for (String pill : new String[] {"huiling", "ningshen", "unknown", ""})
            assertEquals(empty, PillAegisEffects.afterConsumption(empty, 100, pill, true, 20, .25, 160, 600));
        assertEquals(new PillAegisState(5, 260, 700), grant(empty, 100));
        assertEquals(new PillAegisState(10, 260, 700),
                PillAegisEffects.afterConsumption(empty, 100, "yangyuan", true, 40, .25, 160, 600));
    }

    @Test void activeShieldCannotStackOrRefreshEvenIfCooldownAlreadyElapsed() {
        var active = new PillAegisState(3, 900, 700);
        assertEquals(active, grant(active, 800));
        var original = grant(PillAegisState.EMPTY, 100);
        assertEquals(original, grant(original, 150));
    }

    @Test void thirtySecondCooldownSurvivesConsumptionExpiryAndDeath() {
        var shield = grant(PillAegisState.EMPTY, 100);
        var consumed = PillAegisEffects.absorb(shield, 101, 8, false).next();
        assertEquals(new PillAegisState(0, 0, 700), consumed);
        assertEquals(consumed, grant(consumed, 699));
        assertEquals(consumed, shield.clearShield());
        assertEquals(consumed, shield.expire(260));
        assertEquals(new PillAegisState(5, 860, 1300), grant(consumed, 700));
    }

    @Test void shieldExpiresExactlyAtEightSecondsWithoutRefreshingOnRead() {
        var shield = grant(PillAegisState.EMPTY, 100);
        assertEquals(shield, shield.expire(259));
        assertEquals(0, shield.expire(260).shield());
        assertEquals(4, PillAegisEffects.absorb(shield, 260, 4, false).damage());
        assertEquals(700, shield.expire(260).cooldownReadyAt());
    }

    @Test void partialAndExhaustingHitsConsumeOnlyTheIndependentPool() {
        var shield = grant(PillAegisState.EMPTY, 100);
        var first = PillAegisEffects.absorb(shield, 101, 3, false);
        assertEquals(0, first.damage());
        assertEquals(3, first.blocked());
        assertEquals(new PillAegisState(2, 260, 700), first.next());
        var second = PillAegisEffects.absorb(first.next(), 102, 7, false);
        assertEquals(5, second.damage());
        assertEquals(2, second.blocked());
        assertEquals(new PillAegisState(0, 0, 700), second.next());
    }

    @Test void inputIsAlreadyMitigatedAndOrdinaryAbsorptionStillOwnsItsPool() {
        // Armor/magic/resistance have reduced a 20-damage attack to 8 before this API.
        var result = PillAegisEffects.absorb(grant(PillAegisState.EMPTY, 100), 101, 8, false);
        assertEquals(3, result.damage());
        float ordinaryAbsorption = 9;
        float vanillaConsumed = Math.min(ordinaryAbsorption, result.damage());
        assertEquals(6, ordinaryAbsorption - vanillaConsumed);
        assertEquals(0, result.damage() - vanillaConsumed);
        // Fate expiry/death never owns, removes, or rewrites the other six absorption hearts.
        assertEquals(result.next(), result.next().clearShield());
    }

    @Test void bypassAndRejectedDamageDoNotSpendTheShield() {
        var shield = grant(PillAegisState.EMPTY, 100);
        var bypass = PillAegisEffects.absorb(shield, 101, 1000, true);
        assertEquals(1000, bypass.damage());
        assertEquals(0, bypass.blocked());
        assertEquals(shield, bypass.next());
        assertEquals(shield, PillAegisEffects.absorb(shield, 101, 0, false).next());
        assertEquals(shield, PillAegisEffects.absorb(shield, 101, Float.NaN, false).next());
    }

    @Test void codecRetainsAbsoluteDeadlinesAcrossSaveLoadAndEmptyLegacyData() {
        var active = grant(PillAegisState.EMPTY, 100);
        var encoded = PillAegisState.CODEC.encodeStart(JsonOps.INSTANCE, active).getOrThrow();
        var first = PillAegisState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        var second = PillAegisState.CODEC.parse(JsonOps.INSTANCE, encoded.deepCopy()).getOrThrow();
        assertEquals(active, first);
        assertEquals(active, second);
        assertEquals(first.clearShield(), grant(second.expire(260), 699));
        assertEquals(PillAegisState.EMPTY, PillAegisState.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{}")).getOrThrow());
    }

    @Test void fateAttachmentCodecRetainsShieldAndLegacySelectedIdNeedsNoMigration() {
        var legacy = JsonParser.parseString("""
                {"revision":3,"rolls":1,"candidates":["myvillage:pill_aegis","myvillage:robust","myvillage:bright"],
                 "selected":["myvillage:pill_aegis","myvillage:robust","myvillage:bright"],"confirmed":true}
                """);
        var current = FateState.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(PillAegisState.EMPTY, current.pillAegis());
        assertTrue(current.confirmed());
        assertTrue(current.selected().contains("myvillage:pill_aegis"));
        var armed = current.withPillAegis(grant(current.pillAegis(), 100));
        var serialized = FateState.CODEC.encodeStart(JsonOps.INSTANCE, armed).getOrThrow();
        assertEquals(armed, FateState.CODEC.parse(JsonOps.INSTANCE, serialized).getOrThrow());
        assertEquals(current.selected(), armed.selected());
        assertEquals(current.revision(), armed.revision());
        var afterDeath = armed.withPillAegis(armed.pillAegis().clearShield());
        assertEquals(700, afterDeath.pillAegis().cooldownReadyAt());
        assertEquals(0, afterDeath.pillAegis().shield());
        assertEquals(current.selected(), afterDeath.selected());
    }

    @Test void malformedPoolsAndUnboundedValuesCannotBecomeBenefits() {
        for (double amount : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new PillAegisState(amount, 160, 600));
        assertThrows(IllegalArgumentException.class, () -> new PillAegisState(5, 0, 600));
        assertThrows(IllegalArgumentException.class, () -> new PillAegisState(0, 160, 600));
        assertThrows(IllegalArgumentException.class, () -> new PillAegisState(0, 0, -1));
        assertEquals(PillAegisState.EMPTY, PillAegisEffects.afterConsumption(PillAegisState.EMPTY,
                100, "yangyuan", true, Double.NaN, .25, 160, 600));
    }

    @Test void shippedExistingIdIsImplementedWithItsAuthoredValues() throws Exception {
        try (var in = getClass().getResourceAsStream("/data/myvillage/myvillage/innate_fate/pill_aegis.json")) {
            assertNotNull(in);
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(json.has("placeholder") && json.get("placeholder").getAsBoolean());
            var definition = FateDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(.5, definition.effect("pill_healing_bonus"));
            assertEquals(.25, definition.effect("pill_shield_fraction"));
            assertEquals(160, definition.effect("pill_shield_ticks"));
            assertEquals(600, definition.effect("pill_shield_cooldown"));
            assertEquals(3, definition.tier());
            assertEquals(4, definition.cost());
        }
    }
}
