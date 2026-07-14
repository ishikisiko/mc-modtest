package com.example.myvillage.combat.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CombatStyleDefinition(
        ResourceLocation id,
        Set<ResourceLocation> supportedItems,
        int comboTimeoutTicks,
        int minimumIntentIntervalTicks,
        List<AttackMoveDefinition> moves) {
    public CombatStyleDefinition {
        Objects.requireNonNull(id, "id");
        supportedItems = Set.copyOf(Objects.requireNonNull(supportedItems, "supportedItems"));
        moves = List.copyOf(Objects.requireNonNull(moves, "moves"));
        if (supportedItems.isEmpty() || moves.isEmpty()) {
            throw new IllegalArgumentException("A style needs a supported item and moves");
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
