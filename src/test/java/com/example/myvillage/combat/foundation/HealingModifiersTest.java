package com.example.myvillage.combat.foundation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HealingModifiersTest {
    private final HealingRules rules = HealingRules.get();

    @Test void sameSourceReplacesRatherThanStacksAndRefreshesItsDeadline() {
        var modifiers = new HealingModifiers();
        assertTrue(modifiers.modifier("myvillage:medicine", .5, 10, 0, rules));
        assertTrue(modifiers.modifier("myvillage:medicine", .25, 10, 5, rules));
        assertEquals(1, modifiers.size());
        assertEquals(1.25, modifiers.multiplier(10, rules));
        assertEquals(1.25, modifiers.multiplier(14, rules));
        assertEquals(1, modifiers.multiplier(15, rules));
        assertTrue(modifiers.empty());
    }

    @Test void differentSourcesAddDeterministicallyAndClearIndependently() {
        var first = new HealingModifiers();
        var second = new HealingModifiers();
        first.modifier("myvillage:medicine", .5, 100, 0, rules);
        first.modifier("myvillage:wound", -.75, 100, 0, rules);
        second.modifier("myvillage:wound", -.75, 100, 0, rules);
        second.modifier("myvillage:medicine", .5, 100, 0, rules);
        assertEquals(.75, first.multiplier(1, rules));
        assertEquals(first.multiplier(1, rules), second.multiplier(1, rules));
        first.clear("myvillage:wound");
        assertEquals(1.5, first.multiplier(1, rules));
        assertEquals(.75, second.multiplier(1, rules));
    }

    @Test void amplifiedAndSuppressedHealingAreBothBounded() {
        var modifiers = new HealingModifiers();
        modifiers.modifier("myvillage:a", rules.maximumBonusFraction(), 100, 0, rules);
        modifiers.modifier("myvillage:b", rules.maximumBonusFraction(), 100, 0, rules);
        assertEquals(rules.maximumHealingMultiplier(), modifiers.multiplier(1, rules));
        modifiers.clear("myvillage:a"); modifiers.clear("myvillage:b");
        modifiers.modifier("myvillage:a", -.75, 100, 0, rules);
        modifiers.modifier("myvillage:b", -.75, 100, 0, rules);
        assertEquals(0, modifiers.multiplier(1, rules));
    }

    @Test void fullSourceLedgerRejectsNewSourceButAllowsReplacementAndExpiredSlots() {
        var modifiers = new HealingModifiers();
        for (int i = 0; i < rules.maximumSourcesPerEntity(); i++)
            assertTrue(modifiers.modifier("myvillage:source_" + i, .1, 10, 0, rules));
        assertFalse(modifiers.modifier("myvillage:overflow", .1, 10, 0, rules));
        assertTrue(modifiers.modifier("myvillage:source_0", -.5, 20, 0, rules));
        assertEquals(rules.maximumSourcesPerEntity(), modifiers.size());
        assertTrue(modifiers.modifier("myvillage:overflow", .1, 10, 10, rules));
        assertEquals(2, modifiers.size());
    }

    @Test void invalidInputNeverMutatesAnExistingSource() {
        var modifiers = new HealingModifiers();
        assertTrue(modifiers.modifier("myvillage:a", .25, 20, 0, rules));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY,
                rules.maximumBonusFraction() + 1, -rules.maximumReductionFraction() - 1})
            assertFalse(modifiers.modifier("myvillage:a", invalid, 20, 1, rules));
        assertFalse(modifiers.modifier("myvillage:a", 0, 0, 1, rules));
        assertFalse(modifiers.modifier("myvillage:a", 0, rules.maximumDurationTicks() + 1, 1, rules));
        assertFalse(modifiers.modifier("myvillage:a", 0, 10, -1, rules));
        assertFalse(modifiers.modifier("myvillage:a", 0, 10, Long.MAX_VALUE, rules));
        assertFalse(modifiers.modifier(null, 0, 10, 1, rules));
        assertFalse(modifiers.modifier(" ", 0, 10, 1, rules));
        assertEquals(1.25, modifiers.multiplier(2, rules));
        assertEquals(1, modifiers.size());
    }
}
