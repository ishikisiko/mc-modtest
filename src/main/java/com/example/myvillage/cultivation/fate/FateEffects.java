package com.example.myvillage.cultivation.fate;

/** Pure boundary arithmetic, shared by actual hooks and tests. */
public final class FateEffects {
    private FateEffects() { }
    public static boolean low(float health, float maximum, double threshold) {
        return health > 0 && maximum > 0 && threshold > 0 && health < maximum * threshold;
    }
    public static double damageFactor(double general, double fire, double hunter) {
        return Math.max(0, 1 + general + fire + hunter);
    }
    public static FateState beginRecovery(FateState state, long now, float health, float maximum,
            double threshold, double fraction, int ticks, int cooldown) {
        if (!state.confirmed() || now < state.recoveryReadyAt() || state.healingTicks() > 0
                || !low(health, maximum, threshold) || ticks <= 0 || cooldown <= 0 || fraction <= 0) return state;
        return state.timers(Math.addExact(now, cooldown), ticks, maximum * fraction / ticks, state.hunterReadyAt());
    }
    public static long recoveredPower(long cap, double fraction) {
        return Math.max(0, (long) Math.floor(cap * fraction));
    }
}
