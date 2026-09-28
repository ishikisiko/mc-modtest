package com.example.myvillage.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;

/**
 * Places the held sword for one {@link FirstPersonSwing.Pose}. The left hand mirrors the right
 * across the camera's vertical plane.
 */
final class FirstPersonSwordTransform {
    private static final float EQUIP_DROP = 0.60F;
    // The vanilla handheld first-person display transform leaves the Qingfeng handle at this
    // offset and leans the blade 19.3 degrees toward the camera. Undo both so the rig's grip
    // point is the handle and lift 0 means the blade points straight up.
    private static final float GRIP_ALIGN_PITCH = -19.3F;
    private static final float GRIP_X = 0.0706F;
    private static final float GRIP_Y = -0.101F;
    private static final float GRIP_Z = -0.047F;

    private FirstPersonSwordTransform() {
    }

    static void apply(
            PoseStack poseStack,
            HumanoidArm arm,
            float equipProgress,
            FirstPersonSwing.Rig rig,
            FirstPersonSwing.Pose pose) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.translate(
                side * (rig.shoulderX() + pose.x()),
                rig.shoulderY() + pose.y() - equipProgress * EQUIP_DROP,
                rig.shoulderZ() + pose.z());
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * pose.plane()));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * pose.sweep()));
        poseStack.translate(0.0F, 0.0F, -pose.reach());
        poseStack.mulPose(Axis.YP.rotationDegrees(side * pose.lead()));
        poseStack.mulPose(Axis.XP.rotationDegrees(pose.lift()));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * pose.twist()));
        poseStack.mulPose(Axis.XP.rotationDegrees(GRIP_ALIGN_PITCH));
        poseStack.translate(-side * GRIP_X, -GRIP_Y, -GRIP_Z);
    }

    /** The handle position in the item display frame, which {@link #apply} places at the rig's grip. */
    static Vector3f gripInItemFrame(HumanoidArm arm) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        return new Vector3f(side * GRIP_X, GRIP_Y, GRIP_Z);
    }
}
