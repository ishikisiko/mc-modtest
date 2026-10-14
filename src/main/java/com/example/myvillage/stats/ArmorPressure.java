package com.example.myvillage.stats;

/** Changes only the unclamped armor-pressure expression; vanilla still owns all its constants. */
public final class ArmorPressure {
    private ArmorPressure() {}

    public static float normalizeUnclamped(float armor, float original, double defenderScale) {
        // Every supported realm is at least the mortal baseline. Preserve its exact float bits.
        if (defenderScale == 1 || !Double.isFinite(defenderScale) || defenderScale < 1
                || !Float.isFinite(armor) || !Float.isFinite(original)) {
            return original;
        }
        // Subtract in double: the difference between two finite floats can exceed float range.
        return (float) (armor - ((double) armor - original) / defenderScale);
    }
}
