package com.example.myvillage.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;

final class FirstPersonSwordTransform {
    private static final float BASE_X = 0.56F;
    private static final float BASE_Y = -0.52F;
    private static final float BASE_Z = -0.72F;
    private static final float EQUIP_DROP = 0.60F;

    private FirstPersonSwordTransform() {
    }

    static void apply(
            PoseStack poseStack,
            HumanoidArm arm,
            float equipProgress,
            FirstPersonSwordPose.Pose pose) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.translate(
                side * (BASE_X + pose.x()),
                BASE_Y - equipProgress * EQUIP_DROP + pose.y(),
                BASE_Z + pose.z());
        poseStack.mulPose(Axis.XP.rotationDegrees(pose.pitch()));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * pose.yaw()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * pose.roll()));
        poseStack.scale(pose.scale(), pose.scale(), pose.scale());
    }
}
