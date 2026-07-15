package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class FirstPersonSwordTransformTest {
    private static final float BASE_X = 0.56F;
    private static final float BASE_Y = -0.52F;
    private static final float BASE_Z = -0.72F;
    private static final float EQUIP_DROP = 0.60F;
    private static final float EPSILON = 0.00001F;

    @Test
    void sharedParentTransformMirrorsHandsAndPreservesMatrixOrder() {
        FirstPersonSwordPose.Pose pose = FirstPersonSwordPose.sample(3, 0.59F);
        float equipProgress = 0.35F;

        Matrix4f right = actualTransform(HumanoidArm.RIGHT, equipProgress, pose);
        Matrix4f left = actualTransform(HumanoidArm.LEFT, equipProgress, pose);

        assertMatrixEquals(expectedTransform(1.0F, equipProgress, pose), right);
        assertMatrixEquals(expectedTransform(-1.0F, equipProgress, pose), left);
        assertEquals(-right.m30(), left.m30(), EPSILON);
        assertEquals(right.m31(), left.m31(), EPSILON);
        assertEquals(right.m32(), left.m32(), EPSILON);
    }

    private static Matrix4f actualTransform(
            HumanoidArm arm,
            float equipProgress,
            FirstPersonSwordPose.Pose pose) {
        PoseStack poseStack = new PoseStack();
        FirstPersonSwordTransform.apply(poseStack, arm, equipProgress, pose);
        return new Matrix4f(poseStack.last().pose());
    }

    private static Matrix4f expectedTransform(
            float side,
            float equipProgress,
            FirstPersonSwordPose.Pose pose) {
        return new Matrix4f()
                .translate(
                        side * (BASE_X + pose.x()),
                        BASE_Y - equipProgress * EQUIP_DROP + pose.y(),
                        BASE_Z + pose.z())
                .rotateX((float) Math.toRadians(pose.pitch()))
                .rotateY((float) Math.toRadians(side * pose.yaw()))
                .rotateZ((float) Math.toRadians(side * pose.roll()))
                .scale(pose.scale());
    }

    private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual) {
        assertArrayEquals(
                expected.get(new float[16]),
                actual.get(new float[16]),
                EPSILON);
    }
}
