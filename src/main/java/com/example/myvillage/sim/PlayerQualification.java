package com.example.myvillage.sim;

import java.util.Objects;

/**
 * What the ledger knows of a player's cultivation when judging admission and promotion. The
 * runtime reads it from the player's cultivation profile; the pure core never touches Minecraft.
 *
 * @param realmId    the player-realm registry path ({@code mortal}, {@code qi_refining},
 *                   {@code foundation_establishment}); {@code mortal} ranks below every ledger realm
 * @param stageIndex 0-based stage within that realm
 * @param awakened   the spiritual root is awakened
 * @param rootPeakBp the highest single-element root affinity in basis points (0..10000)
 */
public record PlayerQualification(String realmId, int stageIndex, boolean awakened, int rootPeakBp) {
    public PlayerQualification {
        Objects.requireNonNull(realmId, "realmId");
        if (realmId.isEmpty()) {
            throw new IllegalArgumentException("realmId must not be empty");
        }
        if (stageIndex < 0) {
            throw new IllegalArgumentException("stageIndex must not be negative, got " + stageIndex);
        }
        if (rootPeakBp < 0) {
            throw new IllegalArgumentException("rootPeakBp must not be negative, got " + rootPeakBp);
        }
    }
}
