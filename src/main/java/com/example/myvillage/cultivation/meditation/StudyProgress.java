package com.example.myvillage.cultivation.meditation;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * What a study (研读) session shows: the technique being read, the comprehension points on the manual, the
 * points needed to finish, the next gate not yet passed ({@code -1} when none is left) and the stability that
 * each gate costs. Present on a {@link MeditationStatus} only while a study session runs.
 */
public record StudyProgress(
        ResourceLocation techniqueId,
        int points,
        int totalPoints,
        int nextGatePoints,
        int gateStabilityCost) {
    public static final int NO_GATE = -1;

    public StudyProgress {
        Objects.requireNonNull(techniqueId, "techniqueId");
        if (totalPoints <= 0) {
            throw new IllegalArgumentException("Study total points must be positive, got " + totalPoints);
        }
        if (points < 0 || points > totalPoints) {
            throw new IllegalArgumentException("Study points must be in 0.." + totalPoints + ", got " + points);
        }
        if (nextGatePoints != NO_GATE && (nextGatePoints < points || nextGatePoints >= totalPoints)) {
            throw new IllegalArgumentException(
                    "Next study gate must be -1 or in " + points + ".." + (totalPoints - 1) + ", got " + nextGatePoints);
        }
        if (gateStabilityCost < 0) {
            throw new IllegalArgumentException("Gate stability cost must be non-negative, got " + gateStabilityCost);
        }
    }
}
