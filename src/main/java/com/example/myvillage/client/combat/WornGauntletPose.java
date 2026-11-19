package com.example.myvillage.client.combat;

import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Common fit for the approved two-model-pixels-per-player-pixel meshes. */
final class WornGauntletPose {
    private static final float PIXEL = 1.0F / 16.0F;
    // PlayerModel's arm runs from -2 to 10 in its own coordinates. The glove
    // grips the center of the final four pixels.
    private static final float HAND_Y = 8.0F * PIXEL;

    private WornGauntletPose() {
    }

    private static float side(HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
    }

    private static float centerX(HumanoidArm arm, boolean slim) {
        return side(arm) * (slim ? -0.5F : -1.0F) * PIXEL;
    }

    /** Baked item quads (0..1) to the original PlayerModel arm's local space. */
    static Matrix4f itemOnArm(HumanoidArm arm, boolean slim, Vector3f gripPixels) {
        float slimInset = slim ? side(arm) * 0.5F * PIXEL : 0.0F;
        return new Matrix4f()
                .translation(centerX(arm, slim) + slimInset, HAND_Y, 0.0F)
                .scale(side(arm) * 0.5F, 0.5F, 0.5F)
                .translate(-gripPixels.x * PIXEL, -gripPixels.y * PIXEL, -gripPixels.z * PIXEL);
    }

    /** Whole vanilla arm, uniformly sized to the cultivation viewmodel's hand. */
    static Matrix4f armAtGrip(Matrix4f grip, float weaponScale, HumanoidArm arm, boolean slim) {
        return new Matrix4f(grip)
                .scale(weaponScale * 2.0F)
                .translate(-centerX(arm, slim), -HAND_Y, 0.0F);
    }

    /** Recover the off-hand arm frame from its existing mirrored glove frame. */
    static Matrix4f armAtOffHand(Matrix4f item, Vector3f gripPixels, HumanoidArm arm, boolean slim) {
        return new Matrix4f(item)
                .translate(gripPixels.x * PIXEL, gripPixels.y * PIXEL, gripPixels.z * PIXEL)
                .scale(side(arm) * 2.0F, 2.0F, 2.0F)
                .translate(-centerX(arm, slim), -HAND_Y, 0.0F);
    }
}
