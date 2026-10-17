package com.example.myvillage.cultivation.fate;

/** Pure transitions used by the actual dodge and accepted-hit runtime. */
public final class SwordHeartEffects {
    private SwordHeartEffects() { }

    /** The dodge clock and persistent clock are deliberately separate (dimension versus overworld). */
    public static SwordHeartState dodged(SwordHeartState state, long persistentNow, long dodgeNow,
            long dodgeStartedAt, int windowTicks, int openingTicks, int cooldownTicks,
            boolean actualEnemyDirectAttack, double amount) {
        if (!actualEnemyDirectAttack || !Double.isFinite(amount) || amount <= 0
                || persistentNow < 0 || dodgeStartedAt < 0 || dodgeNow < dodgeStartedAt
                || windowTicks <= 0 || dodgeNow - dodgeStartedAt >= windowTicks
                || openingTicks <= 0 || cooldownTicks <= 0 || persistentNow < state.cooldownReadyAt())
            return state;
        return new SwordHeartState(deadline(persistentNow, openingTicks), deadline(persistentNow, cooldownTicks));
    }

    public static double criticalMultiplier(SwordHeartState state, long now, double bonus, boolean swordMelee) {
        return swordMelee && state.opening(now) && Double.isFinite(bonus) && bonus > 0 && bonus <= 1
                ? 1 + bonus : 1;
    }

    /** A failed target does not spend anything; only the first accepted target can claim the token. */
    public static SwordHeartState consume(SwordHeartState state, long now, long expectedOpening,
            boolean accepted) {
        return accepted && expectedOpening == state.openingExpiresAt() && state.opening(now)
                ? state.clearOpening() : state;
    }

    public static boolean acceptedDamage(double residual, double shieldBlocked) {
        return Double.isFinite(residual) && residual > 0 || Double.isFinite(shieldBlocked) && shieldBlocked > 0;
    }

    private static long deadline(long now, int ticks) {
        return now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks;
    }
}
