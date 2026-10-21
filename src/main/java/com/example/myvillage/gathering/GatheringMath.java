package com.example.myvillage.gathering;

import com.example.myvillage.cultivation.meditation.BasicBreathingSettlement;

/** Pure arithmetic: one charge buys one successful, positive progress bonus. */
public final class GatheringMath {
    private GatheringMath() { }
    public static long bonus(long baseGain, long remaining, int basisPoints) {
        if (baseGain < 0 || remaining < 0 || basisPoints < 0 || basisPoints > 10000)
            throw new IllegalArgumentException("Invalid gathering gain");
        return Math.min(remaining, baseGain / 10000 * basisPoints + baseGain % 10000 * basisPoints / 10000);
    }
    public static BasicBreathingSettlement.Plan boost(BasicBreathingSettlement.Plan base, long cap, int basisPoints) {
        long remaining = Math.max(0, cap - base.replacement().cultivationProgress());
        long extra = bonus(base.progressApplied(), remaining, basisPoints);
        return new BasicBreathingSettlement.Plan(base.replacement().withCultivationProgress(
                base.replacement().cultivationProgress() + extra), base.consumeSpiritStones(),
                base.downgradeToNormal(), base.progressApplied() + extra, base.stabilityApplied());
    }
    public record Debit(int stones, int charges) { }
    public static Debit debit(int stones, int charges, int perStone) {
        if (stones < 0 || charges < 0 || charges > Short.MAX_VALUE || perStone < 1 || perStone > Short.MAX_VALUE)
            throw new IllegalArgumentException("Invalid gathering energy");
        if (charges > 0) return new Debit(stones, charges - 1);
        return stones > 0 ? new Debit(stones - 1, perStone - 1) : null;
    }
}
