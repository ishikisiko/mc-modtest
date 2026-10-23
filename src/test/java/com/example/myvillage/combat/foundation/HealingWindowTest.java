package com.example.myvillage.combat.foundation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HealingWindowTest {
    private final int ticks = HealingRules.get().lifestealWindowTicks();

    @Test void actualPostModifierHealingCannotExceedFivePercentAcrossMultipleHits() {
        var window = new HealingWindow();
        var first = window.reserve(0, 20, .05, ticks);
        assertEquals(1, first.amount());
        double amplified = HealingMath.adjusted(HealingMath.lifesteal(10, .08), 5, 10, 20, first.amount());
        assertEquals(1, amplified);
        assertTrue(window.settle(first, amplified));
        assertNull(window.reserve(0, 20, .05, ticks));
        assertNull(window.reserve(ticks - 1, 20, .05, ticks));
        assertFalse(window.expired(ticks - 1, ticks));
        assertTrue(window.expired(ticks, ticks));
        assertEquals(1, window.reserve(ticks, 20, .05, ticks).amount());
    }

    @Test void unusedHealingIsRefundedAndRepeatedSettlementCannotReplayIt() {
        var window = new HealingWindow();
        var first = window.reserve(10, 100, .05, ticks);
        assertTrue(window.settle(first, 2));
        assertFalse(window.settle(first, 0));
        assertEquals(2, window.spent());
        var second = window.reserve(11, 100, .05, ticks);
        assertEquals(3, second.amount());
        assertTrue(window.settle(second, 0));
        assertEquals(3, window.reserve(12, 100, .05, ticks).amount());
    }

    @Test void reservationsBlockReentrantLifestealEvenAtAWindowBoundary() {
        var window = new HealingWindow();
        var reservation = window.reserve(ticks - 1, 20, .05, ticks);
        assertNotNull(reservation);
        assertNull(window.reserve(ticks - 1, 20, .05, ticks));
        assertNull(window.reserve(ticks, 20, .05, ticks));
        assertFalse(window.settle(new HealingWindow.Reservation(reservation.amount()), 0));
        assertTrue(window.settle(reservation, 1));
        assertEquals(1, window.reserve(ticks, 20, .05, ticks).amount());
    }

    @Test void maxHealthAndFractionChangesCannotRefillAnExistingWindow() {
        var window = new HealingWindow();
        var reservation = window.reserve(0, 100, .05, ticks);
        window.settle(reservation, 2);
        var expanded = window.reserve(1, 1000, .1, ticks);
        assertEquals(3, expanded.amount());
        window.settle(expanded, 0);
        var reduced = window.reserve(2, 50, .05, ticks);
        assertEquals(.5, reduced.amount());
        window.settle(reduced, .5);
        assertNull(window.reserve(3, 100, .05, ticks));
        assertEquals(5, window.reserve(ticks, 100, .05, ticks).amount());
    }

    @Test void invalidInputsAndClockRollbackDoNotCreateBudget() {
        var window = new HealingWindow();
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertNull(window.reserve(0, invalid, .05, ticks));
            assertNull(window.reserve(0, 20, invalid, ticks));
        }
        assertNull(window.reserve(0, 20, 1.1, ticks));
        assertNull(window.reserve(-1, 20, .05, ticks));
        assertNull(window.reserve(0, 20, .05, 0));
        var reservation = window.reserve(ticks, 20, .05, ticks);
        window.settle(reservation, 1);
        assertNull(window.reserve(0, 20, .05, ticks));
        assertNull(window.reserve(ticks + 1, 20, .05, ticks));
        assertEquals(1, window.reserve(ticks * 2L, 20, .05, ticks).amount());
    }

    @Test void roundingAfterEveryHitKeepsTheRealGainWithinBudget() {
        var window = new HealingWindow();
        float health = 10;
        double gained = 0;
        for (int i = 0; i < 100; i++) {
            var reservation = window.reserve(0, 20, .05, ticks);
            if (reservation == null) break;
            float next = HealingMath.floorFloat(health + Math.min(.03, reservation.amount()));
            double actual = next - (double) health;
            window.settle(reservation, actual);
            health = next;
            gained += actual;
        }
        assertTrue(gained <= 1);
        assertEquals(gained, window.spent());
    }
}
