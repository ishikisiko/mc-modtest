package com.example.myvillage.combat.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CombatStepServiceTest {
    @Test
    void choosesLongestCollisionAndSupportSafeDistance() {
        double chosen = CombatStepService.chooseSafeDistance(
                0.8,
                0.1,
                distance -> distance <= 0.5);

        assertEquals(0.5, chosen, 1.0E-9);
    }

    @Test
    void suppressesStepWhenEveryCandidateIsUnsafe() {
        assertEquals(
                0.0,
                CombatStepService.chooseSafeDistance(0.8, 0.1, ignored -> false),
                1.0E-9);
    }
}
