package com.example.myvillage.combat.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One server-authoritative move, loaded from a style file.
 *
 * <p>Input buffer: a click at {@code bufferStartTick <= actionTick < totalTicks} is held in the
 * session's single slot (clicks during anticipation are rejected). Chain: a held click starts the
 * next move at {@code chainTick}, cancelling the rest of this move's recovery; without a held click
 * the move still plays to {@code totalTicks}.
 *
 * <p>{@code kind}, {@code feedback} and {@code camera} are presentation: the server reads only the
 * feedback's hit-stop length to size the target freeze. The optional {@code trail} is presentation
 * too and never read by the server: it only redirects the world trail (see
 * {@link #worldTrailSamples()}).
 */
public record AttackMoveDefinition(
        ResourceLocation id,
        String displayKey,
        MoveKind kind,
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
        Optional<StepDefinition> step,
        MoveFeedback feedback,
        CameraCues camera,
        Optional<TrailDefinition> trail) {
    public AttackMoveDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayKey, "displayKey");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reaction, "reaction");
        Objects.requireNonNull(animation, "animation");
        Objects.requireNonNull(hitbox, "hitbox");
        step = Objects.requireNonNull(step, "step");
        Objects.requireNonNull(feedback, "feedback");
        Objects.requireNonNull(camera, "camera");
        trail = Objects.requireNonNull(trail, "trail");
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
        if (camera.stepFovSurge() > 0.0F && step.isEmpty()) {
            throw new IllegalArgumentException("A step FOV surge needs a step");
        }
    }

    /** A move without a separate world-trail path: the trail follows the hit samples. */
    public AttackMoveDefinition(
            ResourceLocation id,
            String displayKey,
            MoveKind kind,
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
            Optional<StepDefinition> step,
            MoveFeedback feedback,
            CameraCues camera) {
        this(id, displayKey, kind, totalTicks, activeStartTick, activeEndTick, damageMultiplier, maximumTargets,
                range, bufferStartTick, chainTick, reaction, animation, hitbox, step, feedback, camera,
                Optional.empty());
    }

    /**
     * The samples the world trail follows: the move's {@code trail.samples} when it has them, else
     * its hit samples. Presentation only; hit detection reads {@link #hitbox()}.
     */
    public List<HitboxSample> worldTrailSamples() {
        return trail.map(TrailDefinition::samples).orElse(hitbox.samples());
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
