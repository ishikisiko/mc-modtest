package com.example.myvillage.combat.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

public record AttackMoveDefinition(
        ResourceLocation id,
        String displayKey,
        int totalTicks,
        int activeStartTick,
        int activeEndTick,
        double damageMultiplier,
        int maximumTargets,
        double range,
        int bufferStartTick,
        double knockback,
        AnimationDefinition animation,
        HitboxDefinition hitbox,
        Optional<StepDefinition> step) {
    public AttackMoveDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayKey, "displayKey");
        Objects.requireNonNull(animation, "animation");
        Objects.requireNonNull(hitbox, "hitbox");
        step = Objects.requireNonNull(step, "step");
        if (displayKey.isBlank()) {
            throw new IllegalArgumentException("Display key must not be blank");
        }
        if (totalTicks <= 0 || activeStartTick < 0 || activeEndTick < activeStartTick
                || activeEndTick >= totalTicks) {
            throw new IllegalArgumentException("Active ticks must lie inside the move duration");
        }
        if (bufferStartTick <= activeEndTick || bufferStartTick >= totalTicks) {
            throw new IllegalArgumentException("Buffer window must be late recovery");
        }
        if (!(damageMultiplier > 0.0) || maximumTargets <= 0 || !(range > 0.0)
                || knockback < 0.0) {
            throw new IllegalArgumentException("Move damage, targets, range, and knockback are invalid");
        }
        if (!animation.animationId().equals(id) || animation.lengthTicks() != totalTicks) {
            throw new IllegalArgumentException("Move and animation ids/durations must match");
        }
        if (step.isPresent() && step.orElseThrow().actionTick() >= totalTicks) {
            throw new IllegalArgumentException("Step tick must lie inside the move duration");
        }
    }

    public boolean isActiveTick(int actionTick) {
        return actionTick >= activeStartTick && actionTick <= activeEndTick;
    }

    public boolean acceptsBuffer(int actionTick) {
        return actionTick >= bufferStartTick && actionTick < totalTicks;
    }
}
