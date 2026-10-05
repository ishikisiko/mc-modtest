package com.example.myvillage.entity.beast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Move phases by tick and move selection, on the bundled demon wolf data. */
final class BeastMoveLogicTest {
    @Test
    void biteTimeline() {
        BeastMoveDefinition bite = BeastTestData.bite();
        List<BeastMovePhase> phases = new ArrayList<>();
        for (int tick = 0; tick <= bite.totalTicks(); tick++) {
            phases.add(bite.phase(tick));
        }
        // Wind-up 0..9, strike 10..12, recovery 13..27, done at 28.
        for (int tick = 0; tick < 10; tick++) {
            assertEquals(BeastMovePhase.WINDUP, phases.get(tick), "tick " + tick);
        }
        for (int tick = 10; tick <= 12; tick++) {
            assertEquals(BeastMovePhase.ACTIVE, phases.get(tick), "tick " + tick);
        }
        for (int tick = 13; tick < 28; tick++) {
            assertEquals(BeastMovePhase.RECOVERY, phases.get(tick), "tick " + tick);
        }
        assertEquals(BeastMovePhase.FINISHED, phases.get(28));
        assertFalse(phases.contains(BeastMovePhase.RELEASE), "the bite strikes right after its wind-up");

        assertTrue(bite.turnsAt(0));
        assertTrue(bite.turnsAt(3));
        assertFalse(bite.turnsAt(4), "the yaw is locked from turn_lock_tick on");
        assertTrue(bite.locksAt(4));
        // Poise covers the whole wind-up and strike: a hit as the bite starts cannot throw it away.
        assertTrue(bite.immuneAt(0));
        assertTrue(bite.immuneAt(4));
        assertTrue(bite.immuneAt(12));
        assertFalse(bite.immuneAt(13), "recovery is the punish window");
        assertTrue(bite.lungesAt(9));
    }

    @Test
    void pounceTimeline() {
        BeastMoveDefinition pounce = BeastTestData.pounce();
        assertEquals(BeastMovePhase.WINDUP, pounce.phase(17));
        assertEquals(BeastMovePhase.RELEASE, pounce.phase(18), "the leap tick sits between wind-up and strike");
        assertTrue(pounce.lungesAt(18));
        assertEquals(BeastMovePhase.ACTIVE, pounce.phase(19));
        assertEquals(BeastMovePhase.ACTIVE, pounce.phase(30));
        assertEquals(BeastMovePhase.RECOVERY, pounce.phase(31));
        assertEquals(BeastMovePhase.RECOVERY, pounce.phase(49));
        assertEquals(BeastMovePhase.FINISHED, pounce.phase(50));
        assertTrue(pounce.turnsAt(12));
        assertTrue(pounce.locksAt(13));
        assertFalse(pounce.turnsAt(13));
        assertFalse(pounce.immuneAt(9));
        assertTrue(pounce.immuneAt(10));
        assertTrue(pounce.immuneAt(30));
        assertFalse(pounce.immuneAt(31));
    }

    @Test
    void selectionNeedsRangeAndCooldown() {
        List<BeastMoveDefinition> moves = BeastTestData.demonWolf().moves();
        long[] ready = {0L, 0L};
        // Close: only the bite is in range; mid: only the pounce; between them nothing.
        assertEquals(0, BeastMoveSelector.select(moves, 1.5, 100, ready, 0, 10, bound -> 0));
        // The bite may start from just beyond the sword's reach (about 3.25 centre to centre).
        assertEquals(0, BeastMoveSelector.select(moves, 3.4, 100, ready, 0, 10, bound -> bound - 1), "range is inclusive");
        assertEquals(BeastMoveSelector.NONE, BeastMoveSelector.select(moves, 3.6, 100, ready, 0, 10, bound -> 0));
        assertEquals(1, BeastMoveSelector.select(moves, 6.0, 100, ready, 0, 10, bound -> 0));
        assertEquals(BeastMoveSelector.NONE, BeastMoveSelector.select(moves, 12.0, 100, ready, 0, 10, bound -> 0));

        long[] pounceCooling = {0L, 150L};
        assertEquals(BeastMoveSelector.NONE, BeastMoveSelector.select(moves, 6.0, 149, pounceCooling, 0, 10, bound -> 0));
        assertEquals(1, BeastMoveSelector.select(moves, 6.0, 150, pounceCooling, 0, 10, bound -> 0), "ready on its tick");
    }

    @Test
    void selectionHonoursTheGapBetweenMoves() {
        List<BeastMoveDefinition> moves = BeastTestData.demonWolf().moves();
        long[] ready = {0L, 0L};
        assertEquals(BeastMoveSelector.NONE, BeastMoveSelector.select(moves, 1.0, 109, ready, 100, 10, bound -> 0));
        assertEquals(0, BeastMoveSelector.select(moves, 1.0, 110, ready, 100, 10, bound -> 0));
    }

    @Test
    void weightedPickUsesTheInjectedRandom() {
        BeastMoveDefinition bite = BeastTestData.bite();
        BeastMoveDefinition pounce = BeastTestData.pounce();
        // Widen both ranges so both are candidates at the same distance: weights 3 and 2.
        BeastMoveDefinition wideBite = withRange(bite, 0.0, 10.0);
        List<BeastMoveDefinition> moves = List.of(wideBite, pounce);
        long[] ready = {0L, 0L};
        List<Integer> bounds = new ArrayList<>();
        for (int roll = 0; roll < 5; roll++) {
            int fixed = roll;
            int picked = BeastMoveSelector.select(moves, 5.0, 0, ready, -100, 10, bound -> {
                bounds.add(bound);
                return fixed;
            });
            assertEquals(roll < 3 ? 0 : 1, picked, "roll " + roll);
        }
        assertEquals(List.of(5, 5, 5, 5, 5), bounds, "total weight 3 + 2");
    }

    private static BeastMoveDefinition withRange(BeastMoveDefinition move, double minimum, double maximum) {
        return new BeastMoveDefinition(
                move.id(), move.animation(), move.totalTicks(), move.windupTicks(), move.turnLockTick(),
                move.activeTicks(), move.immuneTicks(), move.damageMultiplier(), move.maximumTargets(),
                new BeastMoveDefinition.UseRange(minimum, maximum), move.cooldownTicks(), move.weight(),
                move.lunge(), move.hit(), move.knockback());
    }
}
