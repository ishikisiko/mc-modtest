package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;

class WindStepStateTest {
    @Test void missingStateNeverAwardsAnAirChargeInAnOldAirborneSave() {
        assertEquals(WindStepState.EMPTY, WindStepState.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow());
        assertFalse(WindStepState.EMPTY.canAirDodge(100));
        assertTrue(WindStepState.EMPTY.landed().canAirDodge(100));
    }

    @Test void oneAirDodgePerLandingAndAllDodgesShareTheCooldown() {
        var ready = WindStepState.EMPTY.landed();
        var ground = ready.dodged(false, 100, 24);
        assertFalse(ground.airSpent());
        assertFalse(ground.canAirDodge(123));
        assertTrue(ground.canAirDodge(124));
        var air = ground.dodged(true, 124, 24);
        assertTrue(air.airSpent());
        assertFalse(air.canAirDodge(1000));
        var landed = air.landed();
        assertFalse(landed.canAirDodge(147));
        assertTrue(landed.canAirDodge(148));
    }

    @Test void reconnectAndRestartRoundTripSpentChargeAndCooldown() {
        var spent = WindStepState.EMPTY.landed().dodged(true, 200, 30);
        var restored = WindStepState.CODEC.parse(JsonOps.INSTANCE,
                WindStepState.CODEC.encodeStart(JsonOps.INSTANCE, spent).getOrThrow()).getOrThrow();
        assertEquals(spent, restored);
        assertFalse(restored.canAirDodge(10000));
        assertEquals(230, restored.landed().readyAt());
    }

    @Test void clearingBenefitsNeverRefreshesCooldownAndArithmeticSaturates() {
        var state = new WindStepState(false, 1000);
        assertEquals(new WindStepState(true, 1000), state.clearAirCharge());
        assertEquals(1000, state.dodged(false, 100, 5).readyAt());
        assertEquals(Long.MAX_VALUE, state.dodged(true, Long.MAX_VALUE - 2, 24).readyAt());
        assertThrows(IllegalArgumentException.class, () -> new WindStepState(false, -1));
    }

    @Test void fullFateCodecPreservesAirStateAcrossUnrelatedUpdatesAndDeathClearsOnlyCharge() {
        var ids = List.of("myvillage:wind_step", "myvillage:robust", "myvillage:bright");
        var state = new FateState(4, 3, ids, "", ids, true, 1000, 30, .2, 2000)
                .withWindStep(new WindStepState(false, 3000));
        var encoded = FateState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().getAsJsonObject();
        assertEquals(state, FateState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertEquals(state.windStep(), state.timers(1100, 10, .3, 2200).windStep());
        assertEquals(new WindStepState(true, 3000), state.afterDeath().windStep());
        assertEquals(new WindStepState(true, 3000), state.resetChoices().windStep());
        encoded.remove("wind_step");
        var legacy = FateState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(state.selected(), legacy.selected());
        assertEquals(state.revision(), legacy.revision());
        assertEquals(WindStepState.EMPTY, legacy.windStep());
    }
}
