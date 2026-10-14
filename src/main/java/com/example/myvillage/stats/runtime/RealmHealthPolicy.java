package com.example.myvillage.stats.runtime;

/** Health refresh cannot heal; a true death respawn is an explicit separate initialization. */
public final class RealmHealthPolicy {
    private RealmHealthPolicy() {}

    public static float refreshed(float health, float oldMaximum, float newMaximum) {
        return newMaximum < oldMaximum ? Math.min(health, newMaximum) : health;
    }

    public static float respawned(float health, float oldMaximum, float newMaximum, boolean endReturn) {
        return endReturn ? refreshed(health, oldMaximum, newMaximum) : newMaximum;
    }
}
