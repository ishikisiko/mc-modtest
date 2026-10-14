package com.example.myvillage.stats;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Numerical contract only. Actual Mixin loading and enchantment execution need game evidence. */
class ArmorPressureTest {
    @Test
    void baselineAndInvalidScalesPreserveOriginalFloatBits() {
        for (float original : new float[]{-0.0F, 0, -17.25F, Float.MAX_VALUE, Float.intBitsToFloat(0x7fc01234)}) {
            for (double scale : new double[]{1, 0, -1, 0.5, Double.NaN, Double.POSITIVE_INFINITY}) {
                assertEquals(Float.floatToRawIntBits(original),
                        Float.floatToRawIntBits(ArmorPressure.normalizeUnclamped(30, original, scale)));
            }
        }
    }

    @Test
    void equallyScaledDamageAndHealthKeepTheSameDamageFraction() {
        for (double scale : new double[]{1.5, 2.1, 3, 3.3, 6, 6.6, 12, 13.2}) {
            for (float baseDamage : new float[]{10, 50, 100, 300}) {
                double baseline = remaining(baseDamage, 30, 12, 1) / 20;
                double scaled = remaining((float) (baseDamage * scale), 30, 12, scale) / (20 * scale);
                assertEquals(baseline, scaled, 2e-6);
            }
        }
    }

    @Test
    void onlyDefenderScaleChangesCrossRealmArmorPressure() {
        double againstMortal = remaining(100, 20, 8, 1);
        double againstFoundation = remaining(100, 20, 8, 3);
        double againstGolden = remaining(100, 20, 8, 6.6);
        assertTrue(againstMortal > againstFoundation);
        assertTrue(againstFoundation > againstGolden);
    }

    @Test
    void thirtyArmorTwelveToughnessRetainsNativeUpperCapAndLargeHitPressure() {
        assertEquals(13.2, remaining(66, 30, 12, 6.6), 1e-5);
        assertEquals(66, remaining(330, 30, 12, 6.6), 1e-5);
        assertTrue(remaining(331, 30, 12, 6.6) > 331 * 0.2);
        assertEquals(396, remaining(660, 30, 12, 6.6), 1e-4);
        // Breach IV adds -0.60 to armor effectiveness after the native 0.80 fraction.
        assertEquals(52.8, 66 * (1 - (fraction(66, 30, 12, 6.6) - 0.60)), 1e-5);
    }

    @Test
    void zeroAndPartialShieldRemainderKeepTheirFullDamageSemantics() {
        assertEquals(0, remaining(0, 30, 12, 6.6));
        float enteringArmorAfterPartialShield = 66 - 16;
        assertEquals(10, remaining(enteringArmorAfterPartialShield, 30, 12, 6.6), 1e-5);
        assertEquals(enteringArmorAfterPartialShield,
                remaining(enteringArmorAfterPartialShield, 0, 0, 6.6));
    }

    @Test
    void largeFiniteInputsDoNotOverflowTheSubtractionOrScaleFullDamage() {
        assertEquals(0, ArmorPressure.normalizeUnclamped(Float.MAX_VALUE, -Float.MAX_VALUE, 2));
        float damage = Float.MAX_VALUE;
        float original = 30 - damage / 2;
        assertTrue(Float.isFinite(ArmorPressure.normalizeUnclamped(30, original, 6.6)));
        assertEquals(damage * 0.76D, remaining(damage, 30, 0, 6.6), damage * 1e-7D);
    }

    private static double remaining(float damage, float armor, float toughness, double scale) {
        return damage * (1 - fraction(damage, armor, toughness, scale));
    }

    private static double fraction(float damage, float armor, float toughness, double scale) {
        // This independent vanilla oracle is test data, not production balance authority.
        float original = armor - damage / (2 + toughness / 4);
        float normalized = ArmorPressure.normalizeUnclamped(armor, original, scale);
        return Math.max(armor * 0.2F, Math.min(20, normalized)) / 25D;
    }
}
