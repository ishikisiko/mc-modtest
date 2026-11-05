package com.example.myvillage.compat.waystones;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Bounded integer quotes; the currency ledger remains independent BigInteger storage. */
public record WaystonesPricingRules(int baseCost, int blocksPerStep, int minimumDistanceCoefficient,
        int maximumDistanceCoefficient, int interdimensionalCoefficient, int unknownRealmMultiplier,
        Map<ResourceLocation, Integer> realmMultipliers) {
    // Waystones' public variable interface transports floats. Every integer through this limit is exact.
    private static final int MAX_EXACT_VARIABLE_INTEGER = 1 << 24;

    public WaystonesPricingRules {
        if (baseCost <= 0 || blocksPerStep <= 0 || minimumDistanceCoefficient <= 0
                || maximumDistanceCoefficient < minimumDistanceCoefficient
                || interdimensionalCoefficient < minimumDistanceCoefficient
                || interdimensionalCoefficient > maximumDistanceCoefficient || unknownRealmMultiplier <= 0)
            throw new IllegalArgumentException("Invalid Waystones price bounds");
        realmMultipliers = Map.copyOf(realmMultipliers);
        int largestMultiplier = unknownRealmMultiplier;
        for (int multiplier : realmMultipliers.values()) {
            if (multiplier <= 0) throw new IllegalArgumentException("Realm price multipliers must be positive");
            largestMultiplier = Math.max(largestMultiplier, multiplier);
        }
        try {
            long maximum = Math.multiplyExact(Math.multiplyExact((long) baseCost, maximumDistanceCoefficient), largestMultiplier);
            if (maximum > MAX_EXACT_VARIABLE_INTEGER)
                throw new IllegalArgumentException("Waystones price exceeds its exact integer variable range");
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Waystones price overflows", exception);
        }
    }

    public int distanceCoefficient(double horizontalDistance, boolean interdimensional) {
        if (interdimensional) return interdimensionalCoefficient;
        if (!Double.isFinite(horizontalDistance) || horizontalDistance < 0)
            throw new IllegalArgumentException("Distance must be finite and nonnegative");
        return (int) Math.clamp(Math.ceil(horizontalDistance / blocksPerStep),
                minimumDistanceCoefficient, maximumDistanceCoefficient);
    }

    public int realmMultiplier(ResourceLocation realm) {
        return realm == null ? unknownRealmMultiplier : realmMultipliers.getOrDefault(realm, unknownRealmMultiplier);
    }

    public int cost(ResourceLocation realm, double horizontalDistance, boolean interdimensional) {
        return Math.multiplyExact(Math.multiplyExact(baseCost, realmMultiplier(realm)),
                distanceCoefficient(horizontalDistance, interdimensional));
    }
}
