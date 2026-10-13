package com.example.myvillage.cultivation.qi;

import com.example.myvillage.cultivation.technique.CoreTechniqueFactor;

import java.math.BigInteger;

/** Transient server-tick counter with exact fractional recovery. No elapsed/offline time is used. */
public final class QiRegeneration {
    private static final BigInteger UNIT = BigInteger.valueOf(CoreTechniqueFactor.UNIT_BASIS_POINTS);
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private int ticksRemaining;
    private long fraction;

    public QiRegeneration(QiRules rules) {
        spent(rules);
    }

    /** Every fresh lifecycle starts with the full delay, so relogging cannot shorten a spend delay. */
    public void spent(QiRules rules) {
        ticksRemaining = Math.max(1, rules.spendDelayTicks());
        fraction = 0;
    }

    public long tick(QiRules rules, int coreFactorBasisPoints, boolean meditating, boolean full) {
        if (coreFactorBasisPoints < 0) throw new IllegalArgumentException("Core factor must be non-negative");
        if (full) fraction = 0;
        if (--ticksRemaining > 0) return 0;
        ticksRemaining = rules.regenIntervalTicks();
        if (full) return 0;
        // BigInteger is used only on recovery intervals and prevents overflow even for extreme datapacks.
        BigInteger scaled = BigInteger.valueOf(rules.regenAmount())
                .multiply(BigInteger.valueOf(coreFactorBasisPoints))
                .multiply(BigInteger.valueOf(meditating ? rules.meditationMultiplier() : 1))
                .add(BigInteger.valueOf(fraction));
        BigInteger[] result = scaled.divideAndRemainder(UNIT);
        fraction = result[1].longValueExact();
        return result[0].min(MAX_LONG).longValueExact();
    }

    public int ticksRemaining() { return ticksRemaining; }
}
