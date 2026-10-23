package com.example.myvillage.combat.foundation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HealingMathTest {
    @Test void primaryBasisExcludesAbsorptionOverkillAndBonusDamage() {
        assertEquals(0, HealingMath.primaryLoss(20, 20, 1));
        assertEquals(0, HealingMath.primaryLoss(20, 30, 1));
        assertEquals(3, HealingMath.primaryLoss(3, -50, 1));
        assertEquals(4, HealingMath.primaryLoss(20, 14, 2.0 / 3));
        assertEquals(0, HealingMath.primaryLoss(20, 0, Double.NaN));
        assertEquals(0, HealingMath.primaryLoss(20, 0, 1.1));
        assertEquals(0, HealingMath.primaryLoss(Double.POSITIVE_INFINITY, 0, 1));
        assertEquals(0, HealingMath.primaryLoss(20, Double.NaN, 1));
        assertEquals(0, HealingMath.primaryLoss(0, 0, 1));
    }

    @Test void lifestealUsesActualPrimaryBasisAndRejectsInvalidFractions() {
        assertEquals(.8, HealingMath.lifesteal(10, .08), 1e-12);
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(0, HealingMath.lifesteal(invalid, .08));
            assertEquals(0, HealingMath.lifesteal(10, invalid));
        }
        assertEquals(0, HealingMath.lifesteal(10, 1.01));
        assertEquals(Float.MAX_VALUE, HealingMath.lifesteal(Double.MAX_VALUE, 1));
    }

    @Test void correctionCapsAfterAmplificationAndNeverRevivesOrOverheals() {
        assertEquals(.4, HealingMath.adjusted(.8, .5, 10, 20, 1), 1e-12);
        assertEquals(1, HealingMath.adjusted(.8, 5, 10, 20, 1));
        assertEquals(.5, HealingMath.adjusted(20, 5, 19.5, 20, 10));
        assertEquals(0, HealingMath.adjusted(1, 5, 0, 20, 1));
        assertEquals(0, HealingMath.adjusted(1, 5, -1, 20, 1));
        assertEquals(0, HealingMath.adjusted(1, 0, 10, 20, 1));
        assertEquals(0, HealingMath.adjusted(1, 1, 20, 20, 1));
        assertEquals(0, HealingMath.adjusted(1, 1, 21, 20, 1));
        assertEquals(0, HealingMath.adjusted(1, 1, 10, 20, 0));
        assertEquals(0, HealingMath.adjusted(Double.NaN, 1, 10, 20, 1));
        assertEquals(0, HealingMath.adjusted(1, Double.POSITIVE_INFINITY, 10, 20, 1));
    }

    @Test void floatUpperHealthCannotRoundAboveTheRemainingBudget() {
        float before = 19;
        double cap = .01;
        assertTrue((float) (before + cap) > before + cap);
        float after = HealingMath.floorFloat(before + cap);
        assertTrue(after >= before);
        assertTrue(after - (double) before <= cap);
        assertEquals(before, HealingMath.floorFloat(before));
        assertEquals(0, HealingMath.floorFloat(Double.NaN));
        assertEquals(0, HealingMath.floorFloat(-1));
        assertEquals(0, HealingMath.floorFloat(Double.MIN_VALUE));
    }
}
