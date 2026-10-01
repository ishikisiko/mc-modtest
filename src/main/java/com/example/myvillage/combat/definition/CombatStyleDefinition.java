package com.example.myvillage.combat.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * One move set: the ordered combo, its timing policy, and the ready-idle and mode-entry
 * animations. Which items run it is decided by weapon entries, not by the style.
 */
public record CombatStyleDefinition(
        ResourceLocation id,
        ResourceLocation readyIdleAnimation,
        ResourceLocation modeEnterAnimation,
        int comboTimeoutTicks,
        int minimumIntentIntervalTicks,
        List<AttackMoveDefinition> moves) {
    public CombatStyleDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(readyIdleAnimation, "readyIdleAnimation");
        Objects.requireNonNull(modeEnterAnimation, "modeEnterAnimation");
        moves = List.copyOf(Objects.requireNonNull(moves, "moves"));
        if (moves.isEmpty()) {
            throw new IllegalArgumentException("A style needs moves");
        }
        if (comboTimeoutTicks <= 0 || minimumIntentIntervalTicks <= 0) {
            throw new IllegalArgumentException("Style timing must be positive");
        }
        if (moves.stream().map(AttackMoveDefinition::id).distinct().count() != moves.size()) {
            throw new IllegalArgumentException("Move ids must be unique");
        }
    }

    public AttackMoveDefinition move(int index) {
        return moves.get(index);
    }

    public int indexOf(ResourceLocation moveId) {
        for (int index = 0; index < moves.size(); index++) {
            if (moves.get(index).id().equals(moveId)) {
                return index;
            }
        }
        return -1;
    }
}
