package com.example.myvillage.alchemy;

/** Pure bounded medicine calculations. Authored strengths are supplied by AlchemyRules. */
public final class MedicineEffects {
    private MedicineEffects() { }

    /** Serenity improves only the bonus above one, preserving the ordinary gain. */
    public static double qualityStabilityMultiplier(double base, double qualityMultiplier) {
        if (!Double.isFinite(base) || base < 1 || !Double.isFinite(qualityMultiplier) || qualityMultiplier < 1)
            throw new IllegalArgumentException("Medicine strength must be finite and at least one");
        return 1 + (base - 1) * qualityMultiplier;
    }

    public static long recoveredPower(long cap, double fraction) {
        if (cap <= 0 || !Double.isFinite(fraction) || fraction <= 0) return 0;
        return Math.min(cap, Math.max(1L, (long) Math.floor(cap * fraction)));
    }

    public static long stabilityGain(long ordinary, long remaining, double multiplier) {
        if (ordinary <= 0 || remaining <= 0) return 0;
        if (!Double.isFinite(multiplier) || multiplier < 1)
            throw new IllegalArgumentException("Stability multiplier must be finite and at least one");
        return Math.min(remaining, (long) Math.floor(ordinary * multiplier));
    }
}
