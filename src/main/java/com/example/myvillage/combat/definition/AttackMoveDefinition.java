package com.example.myvillage.combat.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/**
 * One server-authoritative move.
 *
 * <p>Input buffer: a click at {@code bufferStartTick <= actionTick < totalTicks} is held in the
 * session's single slot (clicks during anticipation are rejected). Chain: a held click starts the
 * next move at {@code chainTick}, cancelling the rest of this move's recovery; without a held click
 * the move still plays to {@code totalTicks}.
 */
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
        int chainTick,
        ReactionDefinition reaction,
        AnimationDefinition animation,
        HitboxDefinition hitbox,
        Optional<StepDefinition> step) {
    public AttackMoveDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayKey, "displayKey");
        Objects.requireNonNull(reaction, "reaction");
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
        if (bufferStartTick < activeStartTick || bufferStartTick >= totalTicks) {
            throw new IllegalArgumentException("Buffer window must open at or after the active window");
        }
        if (chainTick <= activeEndTick || chainTick > totalTicks || chainTick < bufferStartTick) {
            throw new IllegalArgumentException("Chain tick must follow the active window and buffer start");
        }
        if (!(damageMultiplier > 0.0) || maximumTargets <= 0 || !(range > 0.0)) {
            throw new IllegalArgumentException("Move damage, targets, and range are invalid");
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

    public boolean chainsAt(int actionTick) {
        return actionTick >= chainTick;
    }
}
