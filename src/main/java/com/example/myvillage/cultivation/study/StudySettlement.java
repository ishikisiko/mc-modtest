package com.example.myvillage.cultivation.study;

import java.util.Objects;

/**
 * One ten-tick study (研读) batch, pure integer arithmetic with nothing left to chance.
 *
 * <p>Gain is {@code affinity × (10000 + elementBonusBp) / 10000}, rounded down; the grade multiplier is
 * deliberately not applied. {@code gates} gates sit at {@code total × k / (gates + 1)} for k = 1..gates
 * (rounded down). A gate at {@code g} is passed once the points exceed {@code g}; an accrual that would take
 * the points past an unpassed gate pays {@code gateCost} stability once and goes on, or, when the stability
 * is short, stops exactly on the gate ({@link Outcome#GATE_BLOCKED}). Reaching {@code total} completes.
 */
public final class StudySettlement {
    public static final int UNIT_BASIS_POINTS = 10_000;

    private StudySettlement() {
    }

    public enum Outcome {
        CONTINUE,
        GATE_BLOCKED,
        COMPLETE
    }

    /**
     * @param points        the new comprehension points (clamped to {@code total}, or to the blocking gate)
     * @param stabilityCost stability to deduct for the gates passed in this batch
     * @param outcome       what the batch ended in
     * @param blockingGate  the gate the points stopped on, or -1
     * @param shortfall     stability still missing at the blocking gate, or 0
     */
    public record Plan(int points, int stabilityCost, Outcome outcome, int blockingGate, int shortfall) {
        public Plan {
            Objects.requireNonNull(outcome, "outcome");
            if (points < 0 || stabilityCost < 0 || shortfall < 0) {
                throw new IllegalArgumentException("Study plan values must be non-negative");
            }
            if ((outcome == Outcome.GATE_BLOCKED) != (blockingGate >= 0)) {
                throw new IllegalArgumentException("Only a blocked plan names its gate");
            }
        }
    }

    public static Plan plan(
            int points,
            int total,
            int gates,
            int gateCost,
            int stability,
            int affinity,
            int elementBonusBp) {
        requireRules(total, gates, gateCost);
        if (points < 0 || stability < 0 || affinity < 0 || elementBonusBp < 0) {
            throw new IllegalArgumentException("Study points, stability, affinity and bonus must be non-negative");
        }
        int current = Math.min(points, total);
        if (current >= total) {
            return new Plan(total, 0, Outcome.COMPLETE, -1, 0);
        }
        long gain = gain(affinity, elementBonusBp);
        long target = Math.min((long) total, current + gain);
        int cost = 0;
        int previousGate = -1;
        for (int k = 1; k <= gates; k++) {
            int gate = gatePoints(total, gates, k);
            if (gate == previousGate) {
                continue;
            }
            previousGate = gate;
            if (gate < current || target <= gate) {
                continue;
            }
            if (stability - cost >= gateCost) {
                cost += gateCost;
            } else {
                return new Plan(gate, cost, Outcome.GATE_BLOCKED, gate, gateCost - (stability - cost));
            }
        }
        int next = (int) target;
        return new Plan(next, cost, next >= total ? Outcome.COMPLETE : Outcome.CONTINUE, -1, 0);
    }

    /** {@code affinity × (10000 + elementBonusBp) / 10000}, rounded down. */
    public static long gain(int affinity, int elementBonusBp) {
        if (affinity < 0 || elementBonusBp < 0) {
            throw new IllegalArgumentException("Affinity and bonus must be non-negative");
        }
        return Math.multiplyExact((long) affinity, UNIT_BASIS_POINTS + (long) elementBonusBp) / UNIT_BASIS_POINTS;
    }

    /** The k-th of {@code gates} gates (1-based): {@code total × k / (gates + 1)}, rounded down. */
    public static int gatePoints(int total, int gates, int k) {
        requireRules(total, gates, 0);
        if (k < 1 || k > gates) {
            throw new IllegalArgumentException("Gate index must be in 1.." + gates + ", got " + k);
        }
        return (int) ((long) total * k / (gates + 1L));
    }

    /** The first gate not yet passed (points not above it), or -1 when every gate is behind. */
    public static int nextGate(int points, int total, int gates) {
        requireRules(total, gates, 0);
        for (int k = 1; k <= gates; k++) {
            int gate = gatePoints(total, gates, k);
            if (points <= gate) {
                return gate;
            }
        }
        return -1;
    }

    private static void requireRules(int total, int gates, int gateCost) {
        if (total <= 0 || gates < 0 || gateCost < 0) {
            throw new IllegalArgumentException(
                    "Study needs total > 0, gates >= 0, gate cost >= 0; got " + total + ", " + gates + ", " + gateCost);
        }
    }
}
