package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.data.AdvancementDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record AdvancementContext(
        ResourceLocation sourceRealm,
        ResourceLocation sourceStage,
        long cultivationCap,
        AdvancementDefinition definition) {
    public AdvancementContext {
        Objects.requireNonNull(sourceRealm, "sourceRealm");
        Objects.requireNonNull(sourceStage, "sourceStage");
        Objects.requireNonNull(definition, "definition");
        if (cultivationCap <= 0) {
            throw new IllegalArgumentException("Advancement cultivation cap must be positive");
        }
    }
}
