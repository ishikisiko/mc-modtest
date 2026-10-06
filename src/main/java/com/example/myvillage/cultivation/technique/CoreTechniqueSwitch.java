package com.example.myvillage.cultivation.technique;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Pure rules for changing the running core technique (换运转心法). Only one core technique runs at
 * a time; switching disperses part of the cultivation progress (散功) unless the two techniques
 * belong to the same heritage chain, judged by following {@code lineage.previous} in either
 * direction. {@code CultivationService.switchCoreTechnique} is the only caller that commits a plan.
 */
public final class CoreTechniqueSwitch {
    public static final int BASIS_POINTS = 10_000;
    /** Bound on a lineage walk, so a cyclic or very long chain cannot stall the server. */
    public static final int MAX_LINEAGE_STEPS = 64;

    private CoreTechniqueSwitch() {
    }

    /**
     * Plans a switch of {@code current}'s running core technique to {@code target}.
     *
     * @param lookup                 current technique definitions by id (null when unregistered)
     * @param progressLossBasisPoints the progress fraction lost outside a heritage chain, 0..10000
     */
    public static Plan plan(
            CultivationProfile current,
            ResourceLocation target,
            Function<ResourceLocation, TechniqueDefinition> lookup,
            int progressLossBasisPoints) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(lookup, "lookup");
        if (progressLossBasisPoints < 0 || progressLossBasisPoints > BASIS_POINTS) {
            throw new IllegalArgumentException(
                    "Progress loss must be in 0..10000 basis points, got " + progressLossBasisPoints);
        }
        if (target == null) {
            return Plan.rejected(Status.UNKNOWN_TECHNIQUE, current);
        }
        TechniqueDefinition definition = lookup.apply(target);
        if (definition == null) {
            return Plan.rejected(Status.UNKNOWN_TECHNIQUE, current);
        }
        if (!current.learnedTechniques().containsKey(target)) {
            return Plan.rejected(Status.NOT_LEARNED, current);
        }
        if (!definition.isCore()) {
            return Plan.rejected(Status.NOT_CORE, current);
        }
        Optional<ResourceLocation> active = current.activeCoreTechnique();
        if (active.equals(Optional.of(target))) {
            return new Plan(Status.ALREADY_ACTIVE, current, 0, false);
        }

        boolean sameLineage = active.isPresent() && sameLineage(active.get(), target, lookup);
        long lost = active.isEmpty() || sameLineage
                ? 0
                : lostProgress(current.cultivationProgress(), progressLossBasisPoints);
        CultivationProfile replacement = current
                .withCultivationProgress(current.cultivationProgress() - lost)
                .withActiveCoreTechnique(target);
        return new Plan(Status.SWITCHED, replacement, lost, sameLineage);
    }

    /**
     * True when {@code a} and {@code b} lie on one heritage chain: one is reachable from the other
     * by following {@code lineage.previous}. The walk stops at an unregistered id, a repeated id,
     * or after {@link #MAX_LINEAGE_STEPS}.
     */
    public static boolean sameLineage(
            ResourceLocation a,
            ResourceLocation b,
            Function<ResourceLocation, TechniqueDefinition> lookup) {
        Objects.requireNonNull(lookup, "lookup");
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return precedes(b, a, lookup) || precedes(a, b, lookup);
    }

    /** True when {@code ancestor} appears in {@code technique}'s {@code lineage.previous} chain. */
    public static boolean precedes(
            ResourceLocation ancestor,
            ResourceLocation technique,
            Function<ResourceLocation, TechniqueDefinition> lookup) {
        Set<ResourceLocation> seen = new HashSet<>();
        ResourceLocation cursor = technique;
        for (int step = 0; step < MAX_LINEAGE_STEPS && cursor != null && seen.add(cursor); step++) {
            TechniqueDefinition definition = lookup.apply(cursor);
            if (definition == null) {
                return false;
            }
            ResourceLocation previous = definition.previous().orElse(null);
            if (ancestor.equals(previous)) {
                return true;
            }
            cursor = previous;
        }
        return false;
    }

    /** Progress lost to 散功: {@code floor(progress × loss / 10000)}, so rounding favours the player. */
    public static long lostProgress(long progress, int progressLossBasisPoints) {
        if (progress <= 0 || progressLossBasisPoints <= 0) {
            return 0;
        }
        long whole = progress / BASIS_POINTS * progressLossBasisPoints;
        long part = progress % BASIS_POINTS * progressLossBasisPoints / BASIS_POINTS;
        return Math.min(progress, whole + part);
    }

    /** A data fraction (0..1) as integer basis points. */
    public static int basisPoints(double fraction) {
        if (!Double.isFinite(fraction) || fraction < 0.0D || fraction > 1.0D) {
            throw new IllegalArgumentException("Fraction must be in 0..1, got " + fraction);
        }
        return (int) Math.round(fraction * BASIS_POINTS);
    }

    public enum Status {
        SWITCHED,
        ALREADY_ACTIVE,
        UNKNOWN_TECHNIQUE,
        NOT_LEARNED,
        NOT_CORE;

        public boolean success() {
            return this == SWITCHED || this == ALREADY_ACTIVE;
        }
    }

    /**
     * @param replacement  the profile after the switch (the unchanged profile unless {@code SWITCHED})
     * @param progressLost progress removed by 散功
     * @param sameLineage  the switch stayed on one heritage chain, so nothing was lost
     */
    public record Plan(Status status, CultivationProfile replacement, long progressLost, boolean sameLineage) {
        public Plan {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(replacement, "replacement");
            if (progressLost < 0) {
                throw new IllegalArgumentException("Progress lost must be non-negative");
            }
        }

        private static Plan rejected(Status status, CultivationProfile current) {
            return new Plan(status, current, 0, false);
        }

        public boolean success() {
            return status.success();
        }

        public boolean changed() {
            return status == Status.SWITCHED;
        }
    }
}
