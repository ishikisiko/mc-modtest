package com.example.myvillage.combat.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatTimingPolicyTest {
    @Test
    void intentRateUsesServerTicksOnly() {
        assertTrue(CombatTimingPolicy.allowsIntent(null, 100, 2));
        assertFalse(CombatTimingPolicy.allowsIntent(100L, 101, 2));
        assertTrue(CombatTimingPolicy.allowsIntent(100L, 102, 2));
    }

    @Test
    void recoveryCannotBeCanceledByModeOrItemSwap() {
        long originalActionEnd = 111;
        assertFalse(CombatTimingPolicy.recoveryComplete(105, originalActionEnd));
        assertFalse(CombatTimingPolicy.recoveryComplete(110, originalActionEnd));
        assertTrue(CombatTimingPolicy.recoveryComplete(111, originalActionEnd));
    }
}
