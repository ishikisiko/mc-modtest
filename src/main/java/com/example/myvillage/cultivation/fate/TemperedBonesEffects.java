package com.example.myvillage.cultivation.fate;

/** Pure charge/damage rules. All tuning is supplied by the fate definition. */
public final class TemperedBonesEffects {
    private TemperedBonesEffects() { }

    public static TemperedBonesState charge(TemperedBonesState state, double actualHealthLost,
                                            double maximumHealth, double threshold, boolean enemyAttack) {
        if (!enemyAttack || state.ready() || !positive(actualHealthLost)
                || !positive(maximumHealth) || !positive(threshold)) return state;
        double required = maximumHealth * threshold;
        if (!positive(required)) return state;
        double damage = Math.min(required, state.damage() + actualHealthLost);
        return new TemperedBonesState(damage, damage >= required);
    }

    /** Base attribute damage is independent of move multiplier, sweep damage and charge scale. */
    public static double bonusDamage(double weaponDamage, double bonus, boolean ready, boolean melee) {
        if (!ready || !melee || !positive(weaponDamage) || !positive(bonus)) return 0;
        double result = weaponDamage * bonus;
        return positive(result) ? result : 0;
    }

    /** Attribute actual lost health to the primary hit; the added retaliation never recharges bones. */
    public static double primaryHealthLoss(double healthBefore, double healthAfter, double primaryFraction) {
        if (!Double.isFinite(healthBefore) || !Double.isFinite(healthAfter)
                || !Double.isFinite(primaryFraction) || primaryFraction <= 0 || primaryFraction > 1) return 0;
        return Math.max(0, healthBefore - Math.max(0, healthAfter)) * primaryFraction;
    }

    /** A final positive residual or a committed shield receipt proves an accepted hit. */
    public static boolean acceptedDamage(double remainingDamage, double shieldBlocked) {
        return positive(remainingDamage) || positive(shieldBlocked);
    }

    public static TemperedBonesState consume(TemperedBonesState state, boolean acceptedMeleeHit) {
        return state.ready() && acceptedMeleeHit ? TemperedBonesState.EMPTY : state;
    }

    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
}
