package com.example.myvillage.alchemy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicineEffectsTest {
    @Test void basicPillsUseFixedAmountsByTierAndQuality() {
        var effects = AlchemyRules.get().effects();
        assertEquals(18, effects.amount("huiling", 1));
        assertEquals(90, effects.amount("huiling_2", 4));
        assertEquals(105, effects.amount("huiling_3", 1));
        assertEquals(6.5, effects.amount("yangyuan_2", 4));
        assertEquals(22, effects.amount("yangyuan_3", 4));
        assertEquals(80, effects.amount("ningshen_3", 1));
        assertEquals(180, effects.amount("ningshen_3", 4));
    }

    @Test void stabilitySupportsOnlyAvailableRoomAndRoundsDown() {
        double multiplier = 1.5;
        assertEquals(15, MedicineEffects.stabilityGain(10, 100, multiplier));
        assertEquals(7, MedicineEffects.stabilityGain(5, 100, multiplier));
        assertEquals(3, MedicineEffects.stabilityGain(10, 3, multiplier));
        assertEquals(0, MedicineEffects.stabilityGain(10, 0, multiplier));
        assertEquals(0, MedicineEffects.stabilityGain(0, 100, multiplier));
        assertThrows(IllegalArgumentException.class, () -> MedicineEffects.stabilityGain(1, 3, Double.NaN));
    }
}
