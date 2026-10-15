package com.example.myvillage.alchemy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicineStateTest {
    @Test void healingHasTenIntervalsAndCooldownOutlastsBenefit() {
        var rules = AlchemyRules.get();
        var healing = rules.effects().yangyuan();
        MedicineState state = MedicineState.EMPTY.begin("yangyuan", rules.sharedCooldownTicks(), healing.durationTicks());
        int heals = 0;
        for (int i = 0; i < healing.durationTicks(); i++) {
            var tick = state.tick(healing.intervalTicks());
            if (tick.heal()) heals++;
            state = tick.next();
        }
        assertEquals(healing.durationTicks() / healing.intervalTicks(), heals);
        assertEquals("", state.effect());
        assertFalse(state.available());
        assertEquals(rules.sharedCooldownTicks() - healing.durationTicks(), state.cooldownTicks());
        while (state.cooldownTicks() > 0) state = state.tick(healing.intervalTicks()).next();
        assertEquals(MedicineState.EMPTY, state);
    }

    @Test void longStabilityEffectStillRejectsStackingAfterSharedIntervalExpires() {
        var rules = AlchemyRules.get();
        MedicineState state = MedicineState.EMPTY.begin("ningshen", rules.sharedCooldownTicks(),
                rules.effects().ningshen().durationTicks());
        for (int i = 0; i < rules.sharedCooldownTicks(); i++)
            state = state.tick(rules.effects().yangyuan().intervalTicks()).next();
        assertEquals(0, state.cooldownTicks());
        assertFalse(state.available());
        MedicineState active = state;
        assertThrows(IllegalStateException.class, () -> active.begin("huiling", rules.sharedCooldownTicks(), 0));
    }

    @Test void deathAndDamageClearBenefitButPreserveRemainingInterval() {
        MedicineState active = new MedicineState(473, "yangyuan", 73, 7);
        assertEquals(new MedicineState(473, "", 0, 0), active.clearBenefit());
        assertEquals(473, active.cooldownTicks()); // immutable original can be saved without advancement
    }

    @Test void instantMedicineBeginsOnlySharedInterval() {
        MedicineState state = MedicineState.EMPTY.begin("huiling", AlchemyRules.get().sharedCooldownTicks(), 0);
        assertEquals("", state.effect());
        assertFalse(state.available());
        assertThrows(IllegalStateException.class, () -> state.begin("huiling", 1, 0));
    }

    @Test void invalidStatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MedicineState(-1, "", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MedicineState(0, "ningshen", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MedicineState(0, "huiling", 4, 0));
        assertThrows(IllegalArgumentException.class, () -> new MedicineState(0, "ningshen", 4, 1));
    }
}
