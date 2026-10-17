package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FatePurpleStateTest {
    private static FateState chosen() {
        var ids = List.of("myvillage:pill_aegis", "myvillage:robust", "myvillage:bright");
        return new FateState(4, 3, ids, "", ids, true, 1000, 30, .2, 2000)
                .withPillAegis(new PillAegisState(5, 100, 600))
                .withTemperedBones(new TemperedBonesState(6, true));
    }
    @Test void existingConfirmedChoicesGainEmptyOptionalCombatStateWithoutReroll() {
        var full = FateState.CODEC.encodeStart(JsonOps.INSTANCE, chosen()).getOrThrow().getAsJsonObject();
        full.remove("pill_aegis"); full.remove("tempered_bones");
        var restored = FateState.CODEC.parse(JsonOps.INSTANCE, full).getOrThrow();
        assertEquals(chosen().selected(), restored.selected());
        assertEquals(chosen().revision(), restored.revision());
        assertEquals(chosen().rolls(), restored.rolls());
        assertTrue(restored.confirmed());
        assertEquals(PillAegisState.EMPTY, restored.pillAegis());
        assertEquals(TemperedBonesState.EMPTY, restored.temperedBones());
    }
    @Test void nestedCombatStateRoundTripsAndUnrelatedTimerUpdatesKeepIt() {
        var state = chosen();
        assertEquals(state, FateState.CODEC.parse(JsonOps.INSTANCE,
                FateState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow());
        var changed = state.timers(1100, 10, .3, 2200);
        assertEquals(state.pillAegis(), changed.pillAegis());
        assertEquals(state.temperedBones(), changed.temperedBones());
        assertEquals(state.pillAegis(), state.withTemperedBones(TemperedBonesState.EMPTY).pillAegis());
        assertEquals(state.temperedBones(), state.withPillAegis(PillAegisState.EMPTY).temperedBones());
    }
    @Test void deathClearsBenefitsAndChargeButNeverRefreshesPersistentCooldowns() {
        var state = chosen(); var after = state.afterDeath();
        assertEquals(state.selected(), after.selected()); assertTrue(after.confirmed());
        assertEquals(state.revision(), after.revision());
        assertEquals(0, after.healingTicks()); assertEquals(0, after.pillAegis().shield());
        assertEquals(TemperedBonesState.EMPTY, after.temperedBones());
        assertEquals(state.pillAegis().cooldownReadyAt(), after.pillAegis().cooldownReadyAt());
        assertEquals(state.recoveryReadyAt(), after.recoveryReadyAt());
        assertEquals(state.hunterReadyAt(), after.hunterReadyAt());
    }
    @Test void operatorResetInvalidatesDraftWithoutErasingCooldown() {
        var state = chosen(); var reset = state.resetChoices();
        assertFalse(reset.confirmed()); assertTrue(reset.selected().isEmpty());
        assertTrue(reset.candidates().isEmpty()); assertEquals(state.revision() + 1, reset.revision());
        assertEquals(state.rolls(), reset.rolls());
        assertEquals(state.pillAegis().cooldownReadyAt(), reset.pillAegis().cooldownReadyAt());
        assertEquals(0, reset.pillAegis().shield());
        assertEquals(TemperedBonesState.EMPTY, reset.temperedBones());
    }
    private static FateDefinition definition(Map<String, Double> effects) {
        return new FateDefinition("name", "description", 3, 4, "test", true, effects, Optional.empty(), 0, 5);
    }
    @Test void incompleteOrUnboundedCombatDefinitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> definition(Map.of("pill_shield_fraction", .25)));
        assertThrows(IllegalArgumentException.class, () -> definition(Map.of("bones_threshold", .3)));
        assertThrows(IllegalArgumentException.class, () -> definition(Map.of("bones_knockback_radius", 17.0)));
        assertThrows(IllegalArgumentException.class, () -> definition(Map.of("power_cap_bonus", Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> definition(Map.of("skill_refund_fraction", 1.01)));
    }
}
