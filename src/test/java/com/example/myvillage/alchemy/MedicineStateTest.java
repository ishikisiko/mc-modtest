package com.example.myvillage.alchemy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicineStateTest {
    @Test void healingHasTenIntervalsAndCooldownOutlastsBenefit() {
        var rules = AlchemyRules.get();
        var healing = rules.effects();
        MedicineState state = MedicineState.EMPTY.begin("yangyuan", rules.cooldownTicks(), healing.healingDuration());
        int heals = 0;
        for (int i = 0; i < healing.healingDuration(); i++) {
            var tick = state.tick(healing.healingInterval());
            if (tick.heal()) heals++;
            state = tick.next();
        }
        assertEquals(healing.healingDuration() / healing.healingInterval(), heals);
        assertEquals("", state.effect());
        assertFalse(state.available());
        assertEquals(rules.cooldownTicks() - healing.healingDuration(), state.cooldownTicks());
        while (state.cooldownTicks() > 0) state = state.tick(healing.healingInterval()).next();
        assertEquals(MedicineState.EMPTY, state);
    }

    @Test void deathAndDamageClearBenefitButPreserveRemainingInterval() {
        MedicineState active = new MedicineState(473, "yangyuan", 73, 7);
        assertEquals(new MedicineState(473, "", 0, 0), active.clearBenefit());
        assertEquals(473, active.cooldownTicks()); // immutable original can be saved without advancement
    }

    @Test void instantMedicinesOnlyBeginTheirCooldown() {
        for (String id : new String[] {"huiling", "huiling_2", "huiling_3", "ningshen", "ningshen_2", "ningshen_3"}) {
            MedicineState state = MedicineState.EMPTY.begin(id, AlchemyRules.get().cooldownTicks(), 0, 4);
            assertEquals("", state.effect());
            assertFalse(state.available());
            assertThrows(IllegalStateException.class, () -> state.begin(id, 1, 0));
        }
    }

    @Test void invalidStatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MedicineState(-1, "", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MedicineState(0, "ningshen", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MedicineState(0, "huiling", 4, 0));
        assertThrows(IllegalArgumentException.class, () -> new MedicineState(0, "ningshen", 4, 1));
    }
    @Test void qualityChangesNeitherTimingNorBenefitInterruptionRules() {
        var rules = AlchemyRules.get();
        var healing = rules.effects();
        for (int quality = 1; quality <= 4; quality++) {
            MedicineState state = MedicineState.EMPTY.begin("yangyuan_3", rules.cooldownTicks(), healing.healingDuration(), quality);
            int heals = 0;
            for (int i = 0; i < healing.healingDuration(); i++) {
                assertEquals(quality, state.quality());
                var tick = state.tick(healing.healingInterval());
                if (tick.heal()) heals++;
                state = tick.next();
            }
            assertEquals(healing.healingDuration() / healing.healingInterval(), heals);
            assertEquals(new MedicineState(rules.cooldownTicks() - healing.healingDuration(), "", 0, 0), state);
        }
        assertThrows(IllegalArgumentException.class, () -> MedicineState.EMPTY.begin("yangyuan", 600, 200, 0));
        assertThrows(IllegalArgumentException.class, () -> new MedicineState(600, "", 0, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new MedicineState(600, "yangyuan", 200, 0, 5));
    }

}
