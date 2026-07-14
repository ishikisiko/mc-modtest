package com.example.myvillage.combat.session;

public final class CombatTimingPolicy {
    private CombatTimingPolicy() {
    }

    public static boolean allowsIntent(Long previousTick, long currentTick, int minimumIntervalTicks) {
        if (minimumIntervalTicks <= 0) {
            throw new IllegalArgumentException("Minimum interval must be positive");
        }
        return previousTick == null || currentTick - previousTick >= minimumIntervalTicks;
    }

    public static boolean recoveryComplete(long currentTick, long blockedUntilTick) {
        return currentTick >= blockedUntilTick;
    }
}
