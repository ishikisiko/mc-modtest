package com.example.myvillage.combat.runtime;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StrongestTimedEffectTest {
    @Test void weakerHitsDoNotExtendStrongerArmorBreak() {
        var effects=new StrongestTimedEffect();
        assertTrue(effects.apply(.2,80,0,32));
        assertTrue(effects.apply(.1,80,60,32));
        assertEquals(.2,effects.strongest(79));
        assertEquals(.1,effects.strongest(80));
        assertEquals(.1,effects.strongest(139));
        assertEquals(0,effects.strongest(140));
    }
    @Test void strongerShorterSourcePreservesLongerWeakSource() {
        var effects=new StrongestTimedEffect();effects.apply(.1,80,0,32);effects.apply(.3,20,10,32);
        assertEquals(.3,effects.strongest(29));assertEquals(.1,effects.strongest(30));
        assertEquals(0,effects.strongest(80));
    }
    @Test void sameStrengthRefreshesAndCapacityRefusalPreservesExistingValues() {
        var effects=new StrongestTimedEffect();assertTrue(effects.apply(.1,80,0,1));
        assertTrue(effects.apply(.1,80,60,1));assertFalse(effects.apply(.2,80,60,1));
        assertEquals(.1,effects.strongest(139));assertEquals(0,effects.strongest(140));
        assertTrue(effects.apply(.2,80,140,1));assertEquals(.2,effects.strongest(140));
    }
    @Test void invalidAndOverflowingEntriesCannotProducePermanentModifiers() {
        var effects=new StrongestTimedEffect();
        assertFalse(effects.apply(Double.NaN,80,0,32));assertFalse(effects.apply(1.1,80,0,32));
        assertFalse(effects.apply(.1,80,Long.MAX_VALUE-1,32));
        assertFalse(effects.apply(.1,0,0,32));assertFalse(effects.apply(.1,80,-1,32));
        assertEquals(0,effects.strongest(0));
    }
}
