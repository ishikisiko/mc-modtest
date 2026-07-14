package com.example.myvillage.combat.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record AnimationDefinition(ResourceLocation animationId, int lengthTicks) {
    public AnimationDefinition {
        Objects.requireNonNull(animationId, "animationId");
        if (lengthTicks <= 0) {
            throw new IllegalArgumentException("Animation length must be positive");
        }
    }
}
