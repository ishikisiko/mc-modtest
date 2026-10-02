package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CombatImpactFxTest {
    @Test
    void theAttackerStopRunsOncePerActionHoweverManyTargetsAreHit() {
        java.util.Map<Integer, double[]> stopped = new java.util.HashMap<>();
        assertTrue(CombatImpactFx.firstStopOfAction(stopped, 5, 11, 100.0));
        assertFalse(CombatImpactFx.firstStopOfAction(stopped, 5, 11, 100.0), "second target, same tick");
        assertFalse(CombatImpactFx.firstStopOfAction(stopped, 5, 11, 101.0), "third target, next tick");
        assertTrue(CombatImpactFx.firstStopOfAction(stopped, 6, 11, 101.0), "another attacker");
        assertTrue(CombatImpactFx.firstStopOfAction(stopped, 5, 12, 110.0), "the next action");
        assertFalse(CombatImpactFx.firstStopOfAction(stopped, 5, 12, 112.0));
        assertTrue(CombatImpactFx.firstStopOfAction(stopped, 5, 12, 200.0), "a restarted revision long after");
    }

    @Test
    void attackerAnimationFreezesThenCreepsThroughTheStop() {
        assertEquals(1.0F, CombatImpactFx.hitStopRate(-0.1F, 4.0F), 1.0E-6F);
        assertEquals(0.0F, CombatImpactFx.hitStopRate(0.0F, 4.0F), 1.0E-6F);
        assertEquals(0.0F, CombatImpactFx.hitStopRate(2.3F, 4.0F), 1.0E-6F);
        assertEquals(0.15F, CombatImpactFx.hitStopRate(2.5F, 4.0F), 1.0E-6F);
        assertEquals(1.0F, CombatImpactFx.hitStopRate(4.0F, 4.0F), 1.0E-6F);
    }

    @Test
    void lostTimeIsTheIntegralOfTheStoppedRate() {
        assertEquals(0.0F, CombatImpactFx.hitStopLostTicks(0.0F, 2.0F), 1.0E-6F);
        assertEquals(1.0F, CombatImpactFx.hitStopLostTicks(1.0F, 2.0F), 1.0E-6F);
        float total = 1.2F + 0.8F * 0.85F;
        assertEquals(total, CombatImpactFx.hitStopLostTicks(2.0F, 2.0F), 1.0E-5F);
        assertEquals(total, CombatImpactFx.hitStopLostTicks(9.0F, 2.0F), 1.0E-5F);
    }

    @Test
    void targetShudderStaysWithinItsAmplitudeAndEndsAtRest() {
        for (float elapsed = 0.0F; elapsed < 3.0F; elapsed += 0.05F) {
            assertTrue(Math.abs(CombatImpactFx.jitterOffset(elapsed, 3.0F, 0.06F)) <= 0.06F);
            assertTrue(Math.abs(CombatImpactFx.jitterCross(elapsed, 3.0F, 0.06F)) <= 0.06F);
        }
        assertEquals(0.0F, CombatImpactFx.jitterOffset(3.0F, 3.0F, 0.06F), 1.0E-6F);
        assertEquals(0.0F, CombatImpactFx.jitterCross(-1.0F, 3.0F, 0.06F), 1.0E-6F);
    }
}
