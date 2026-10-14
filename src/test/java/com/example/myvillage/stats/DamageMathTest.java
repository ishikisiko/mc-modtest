package com.example.myvillage.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DamageMathTest {
    private final StatsRules rules = StatsRules.bundled();

    @Test void neutralCompositionExactlyPreservesExistingMoveDamage() {
        for (double base : new double[]{0, 1, 4.3, 16, 52.8}) {
            for (double move : new double[]{0, 0.7, 1, 1.75, 3}) {
                DamageMath.Trace trace = DamageMath.calculate(base, 0, move, List.of(), 1, rules);
                assertEquals(base * move, trace.requestedDamage());
                assertEquals(trace.requestedDamage(), DamageMath.mitigate(trace.requestedDamage(), 0, 0, rules).damage());
            }
        }
    }

    @Test void bonusTermsAddAndTraceKeepsCompositionOrder() {
        DamageMath.Trace trace = DamageMath.calculate(10, 2, 1.5, List.of(0.2, 0.3), 2, rules);
        assertEquals(12, trace.afterFlat());
        assertEquals(18, trace.afterMove());
        assertEquals(0.5, trace.bonusSum());
        assertEquals(27, trace.afterBonuses());
        assertEquals(54, trace.requestedDamage());
    }

    @Test void resistanceConsumesPostVanillaAmountAndPenetrationCannotCreateAmplification() {
        DamageMath.Mitigation resist = DamageMath.mitigate(20, 0.8, 0.3, rules);
        assertEquals(20, resist.postVanilla());
        assertEquals(0.5, resist.effectiveResistance(), 1e-12);
        assertEquals(10, resist.damage(), 1e-12);
        DamageMath.Mitigation penetrate = DamageMath.mitigate(20, 0.1, 0.8, rules);
        assertEquals(0, penetrate.effectiveResistance());
        assertEquals(20, penetrate.damage());
        assertEquals(4, DamageMath.mitigate(20, 0.8, 0, rules).damage(), 1e-12);
    }

    @Test void externalDamageCanExceedTheOutgoingBudgetWithoutBreakingMitigation() {
        double externalDamage = 2_000_000_000;
        assertTrue(externalDamage > rules.limits().maxDamage());
        assertEquals(externalDamage, DamageMath.mitigate(externalDamage, 0, 0, rules).damage());
        DamageMath.Mitigation resisted = DamageMath.mitigate(externalDamage, 0.5, 0, rules);
        assertEquals(externalDamage, resisted.postVanilla());
        assertEquals(1_000_000_000, resisted.damage());
        assertThrows(IllegalArgumentException.class,
                () -> DamageMath.calculate(externalDamage, 0, 1, List.of(), 1, rules));
        assertThrows(IllegalArgumentException.class,
                () -> DamageMath.mitigate(Double.POSITIVE_INFINITY, 0, 0, rules));
        assertThrows(IllegalArgumentException.class,
                () -> DamageMath.mitigate(Double.NaN, 0, 0, rules));
    }

    @Test void nonfiniteNegativeAndUnsupportedInputsFailAtTheNamedStage() {
        assertThrows(IllegalArgumentException.class, () -> DamageMath.calculate(Double.NaN, 0, 1, List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> DamageMath.calculate(1, -1, 1, List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> DamageMath.calculate(1, 0, -1, List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> DamageMath.calculate(1, 0, 1, List.of(-0.1), 1));
        assertThrows(IllegalArgumentException.class, () -> DamageMath.calculate(1, 0, 1, List.of(), Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> DamageMath.calculate(Double.MAX_VALUE, Double.MAX_VALUE, 0, List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> DamageMath.calculate(1, 0, 101, List.of(), 1, rules));
        assertThrows(IllegalArgumentException.class, () -> DamageMath.calculate(1, 0, 1, List.of(10.1), 1, rules));
        assertThrows(IllegalArgumentException.class, () -> DamageMath.mitigate(20, 0.81, 0, rules));
        assertThrows(IllegalArgumentException.class, () -> DamageMath.mitigate(20, 0, 0.81, rules));
        assertThrows(IllegalArgumentException.class, () -> DamageMath.mitigate(20, Double.NaN, 0, rules));
        assertThrows(IllegalArgumentException.class, () -> DamageMath.mitigate(-1, 0, 0, rules));
    }
}
