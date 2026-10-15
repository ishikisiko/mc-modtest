package com.example.myvillage.alchemy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicineEffectsTest {
    @Test void spiritualRecoveryUsesBoundedFractionWithoutOverflow() {
        double fraction = AlchemyRules.get().effects().huiling().powerFraction();
        assertEquals(25, MedicineEffects.recoveredPower(100, fraction));
        assertEquals(1, MedicineEffects.recoveredPower(1, fraction));
        assertEquals(0, MedicineEffects.recoveredPower(0, fraction));
        assertEquals(Long.MAX_VALUE, MedicineEffects.recoveredPower(Long.MAX_VALUE, 1));
        assertEquals(0, MedicineEffects.recoveredPower(100, Double.NaN));
    }

    @Test void stabilitySupportsOnlyAvailableRoomAndRoundsDown() {
        double multiplier = AlchemyRules.get().effects().ningshen().stabilityMultiplier();
        assertEquals(15, MedicineEffects.stabilityGain(10, 100, multiplier));
        assertEquals(7, MedicineEffects.stabilityGain(5, 100, multiplier));
        assertEquals(3, MedicineEffects.stabilityGain(10, 3, multiplier));
        assertEquals(0, MedicineEffects.stabilityGain(10, 0, multiplier));
        assertEquals(0, MedicineEffects.stabilityGain(0, 100, multiplier));
        assertThrows(IllegalArgumentException.class, () -> MedicineEffects.stabilityGain(1, 3, Double.NaN));
    }
    @Test void qualityScalesRecoveryButOnlyTheStabilityBonus() {
        var rules = AlchemyRules.get();
        double base = rules.effects().ningshen().stabilityMultiplier();
        double[] expected = {1.5, 1.625, 1.75, 2};
        long[] recovery = {25, 31, 37, 50};
        for (int q = 1; q <= 4; q++) {
            double factor = rules.qualityMultiplier(q);
            assertEquals(expected[q - 1], MedicineEffects.qualityStabilityMultiplier(base, factor));
            assertEquals(recovery[q - 1], MedicineEffects.recoveredPower(100, rules.effects().huiling().powerFraction() * factor));
            assertEquals(3, MedicineEffects.stabilityGain(10, 3, MedicineEffects.qualityStabilityMultiplier(base, factor)));
            assertEquals(1, MedicineEffects.qualityStabilityMultiplier(1, factor));
        }
        assertThrows(IllegalArgumentException.class, () -> MedicineEffects.qualityStabilityMultiplier(base, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> MedicineEffects.qualityStabilityMultiplier(base, 0));
        assertThrows(IllegalArgumentException.class, () -> MedicineEffects.qualityStabilityMultiplier(.5, 2));
    }

}
