package com.example.myvillage.client.combat;

import com.zigythebird.playeranimcore.bones.PlayerAnimBone;

/**
 * Hands the legs back to vanilla walking while the third-person ready guard plays. Presentation
 * only. The ready idle keys a planted stance (fixed leg angles, a 15 degree body turn and a hip
 * drop), so without this the feet stay frozen and the player glides when walking with the sword
 * drawn.
 *
 * <p>PAL passes each bone in with the vanilla pose already copied onto it, so blending toward the
 * input restores vanilla limb swing. The legs blend fully. The body root blends its position and
 * yaw/roll back to neutral so the walk faces the travel direction without sinking the feet, and
 * keeps its forward lean. Arms, torso, head and the held item keep the guard.
 */
final class LocomotionBlend {
    /** Vanilla limb-swing amount below which the stance stays fully planted. */
    static final float START_SPEED = 0.05F;
    /** Vanilla limb-swing amount at which the legs are fully vanilla (walking is about 0.8). */
    static final float FULL_SPEED = 0.3F;
    /** Largest change of the blend per client tick, in and out (full blend in four ticks). */
    static final float MAX_STEP_PER_TICK = 0.25F;

    enum Part {
        LEG,
        BODY
    }

    private LocomotionBlend() {
    }

    /** Which blend a PAL bone takes, or null for bones that keep the guard. */
    static Part part(String boneName) {
        return switch (boneName) {
            case "right_leg", "left_leg" -> Part.LEG;
            case "body" -> Part.BODY;
            default -> null;
        };
    }

    /** Target blend for the current vanilla limb-swing amount. */
    static float target(float limbSwingAmount) {
        if (!Float.isFinite(limbSwingAmount)) {
            return 0.0F;
        }
        float t = (limbSwingAmount - START_SPEED) / (FULL_SPEED - START_SPEED);
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    /** Moves the blend toward its target by at most {@link #MAX_STEP_PER_TICK}. */
    static float step(float current, float target) {
        float delta = Math.max(-MAX_STEP_PER_TICK, Math.min(MAX_STEP_PER_TICK, target - current));
        return Math.max(0.0F, Math.min(1.0F, current + delta));
    }

    static void copyPose(PlayerAnimBone from, PlayerAnimBone to) {
        to.positionX = from.positionX;
        to.positionY = from.positionY;
        to.positionZ = from.positionZ;
        to.rotX = from.rotX;
        to.rotY = from.rotY;
        to.rotZ = from.rotZ;
        to.bend = from.bend;
    }

    /** Blends {@code animated} toward {@code vanilla} in place by {@code weight}. */
    static void apply(Part part, PlayerAnimBone vanilla, PlayerAnimBone animated, float weight) {
        animated.positionX = lerp(animated.positionX, vanilla.positionX, weight);
        animated.positionY = lerp(animated.positionY, vanilla.positionY, weight);
        animated.positionZ = lerp(animated.positionZ, vanilla.positionZ, weight);
        animated.rotY = lerp(animated.rotY, vanilla.rotY, weight);
        animated.rotZ = lerp(animated.rotZ, vanilla.rotZ, weight);
        if (part == Part.LEG) {
            animated.rotX = lerp(animated.rotX, vanilla.rotX, weight);
            animated.bend = lerp(animated.bend, vanilla.bend, weight);
        }
    }

    static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
