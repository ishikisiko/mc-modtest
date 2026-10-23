package com.example.myvillage.combat.foundation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Pure identity/receipt checks; actual NeoForge event order still needs remote PvP validation. */
class HealingPrimaryReceiptTest {
    private final Object target = new Object();
    private final Object source = new Object();
    private final Object container = new Object();

    @Test void totemRecoveryCannotEraseTheOneHealthLostAtPost() {
        var receipt = begin(1);
        assertTrue(receipt.freeze(target, source, container, 0, 8, 1));
        // Vanilla restores the target to 1 HP before hurt returns: the old net-difference path is zero.
        double healthAfterHurt = 1;
        assertEquals(0, HealingMath.primaryLoss(1, healthAfterHurt, 1));
        assertEquals(.08, HealingMath.lifesteal(receipt.consume(), .08), 1e-12);
        assertEquals(0, receipt.consume());
    }

    @Test void fullAbsorptionWithoutPostOrWithZeroDamageCannotProduceAReceipt() {
        assertEquals(0, begin(10).consume());
        var receipt = begin(10);
        assertTrue(receipt.freeze(target, source, container, 10, 0, 1));
        assertEquals(0, receipt.consume());
    }

    @Test void overkillIsCappedByHealthAndTemperedBonesBonusIsExcluded() {
        var receipt = begin(3);
        assertTrue(receipt.freeze(target, source, container, 0, 100, .75));
        assertEquals(2.25, receipt.consume());
    }

    @Test void postDamageCapsUnrelatedHealthLossAndExcludesAbsorbedDamage() {
        var receipt = begin(20);
        // The primary's final post-absorption damage is 2, even if another callback removed more HP.
        assertTrue(receipt.freeze(target, source, container, 10, 2, 1));
        assertEquals(2, receipt.consume());
    }

    @Test void nestedSameSourceContainerCannotBindAtTheWrongDepthOrOverwriteThePrimary() {
        var receipt = new HealingRuntime.PrimaryReceipt(target, source, 1);
        Object nestedContainer = new Object();
        assertFalse(receipt.begin(target, source, nestedContainer, 2, 20));
        assertTrue(receipt.begin(target, source, container, 1, 10));
        assertFalse(receipt.begin(target, source, nestedContainer, 2, 10));
        assertFalse(receipt.freeze(target, source, nestedContainer, 0, 100, .1));
        assertTrue(receipt.freeze(target, source, container, 8, 2, 1));
        assertEquals(2, receipt.consume());
    }

    @Test void nestedCustomCallsKeepSeparateReceipts() {
        var outer = begin(10);
        Object nestedContainer = new Object();
        var nested = new HealingRuntime.PrimaryReceipt(target, source, 2);
        assertTrue(nested.begin(target, source, nestedContainer, 2, 8));
        assertTrue(nested.freeze(target, source, nestedContainer, 5, 3, .5));
        assertEquals(1.5, nested.consume());
        assertFalse(outer.freeze(target, source, nestedContainer, 5, 3, .5));
        assertTrue(outer.freeze(target, source, container, 5, 2, 1));
        assertEquals(2, outer.consume());
        assertEquals(0, nested.consume());
    }

    @Test void equalButDistinctSourcesTargetsAndContainersCannotMatch() {
        Object identityTarget = new String("target");
        Object identitySource = new String("source");
        Object identityContainer = new String("container");
        var receipt = new HealingRuntime.PrimaryReceipt(identityTarget, identitySource, 1);
        assertFalse(receipt.begin(new String("target"), identitySource, identityContainer, 1, 10));
        assertFalse(receipt.begin(identityTarget, new String("source"), identityContainer, 1, 10));
        assertTrue(receipt.begin(identityTarget, identitySource, identityContainer, 1, 10));
        assertFalse(receipt.freeze(identityTarget, identitySource, new String("container"), 0, 10, 1));
        assertFalse(receipt.freeze(identityTarget, new String("source"), identityContainer, 0, 10, 1));
        assertFalse(receipt.freeze(new String("target"), identitySource, identityContainer, 0, 10, 1));
        assertTrue(receipt.freeze(identityTarget, identitySource, identityContainer, 8, 2, 1));
        assertEquals(2, receipt.consume());
    }

    @Test void repeatedPostAndConsumeCannotRewriteOrReplayTheFrozenLoss() {
        var receipt = begin(10);
        assertTrue(receipt.freeze(target, source, container, 8, 2, 1));
        assertTrue(receipt.freeze(target, source, container, 0, 10, 1));
        assertEquals(2, receipt.consume());
        assertTrue(receipt.freeze(target, source, container, 0, 10, 1));
        assertFalse(receipt.begin(target, source, new Object(), 1, 10));
        assertEquals(0, receipt.consume());
    }

    @Test void latePostAfterEmptyConsumptionAndNonFiniteDamageCannotCreateCredit() {
        var receipt = begin(10);
        assertEquals(0, receipt.consume());
        assertTrue(receipt.freeze(target, source, container, 0, 10, 1));
        assertEquals(0, receipt.consume());
        for (double damage : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            var invalid = begin(10);
            assertTrue(invalid.freeze(target, source, container, 0, damage, 1));
            assertEquals(0, invalid.consume());
        }
    }

    private HealingRuntime.PrimaryReceipt begin(double health) {
        var receipt = new HealingRuntime.PrimaryReceipt(target, source, 1);
        assertTrue(receipt.begin(target, source, container, 1, health));
        return receipt;
    }
}
