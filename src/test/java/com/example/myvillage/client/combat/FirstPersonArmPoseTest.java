package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class FirstPersonArmPoseTest {
    private static final float[] WINDUP_PROGRESS = {0.12F, 0.13F, 0.14F, 0.15F, 0.16F};
    private static final float[] STRIKE_PROGRESS = {0.56F, 0.57F, 0.58F, 0.59F, 0.60F};

    @Test
    void armUsesCalibratedPartialRotationFollow() {
        assertEquals(0.10F, FirstPersonArmPose.PITCH_FOLLOW);
        assertEquals(0.35F, FirstPersonArmPose.YAW_FOLLOW);
        assertEquals(0.20F, FirstPersonArmPose.ROLL_FOLLOW);
    }

    @Test
    void armAndSwordShareTheCorrectedActionFrameAtEverySample() {
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            for (int sample = 0; sample <= 100; sample++) {
                float progress = sample / 100.0F;
                FirstPersonSwordPose.Pose swordPose = FirstPersonSwordPose.sample(
                        moveIndex, progress);
                FirstPersonArmPose.Pose pose = FirstPersonArmPose.sample(
                        moveIndex, progress, swordPose);
                assertEquals(swordPose.x(), pose.x());
                assertEquals(swordPose.y(), pose.y());
                assertEquals(swordPose.z(), pose.z());
                assertEquals(swordPose.pitch(), pose.pitch());
                assertEquals(swordPose.yaw(), pose.yaw());
                assertEquals(swordPose.roll(), pose.roll());
                assertEquals(swordPose.scale(), pose.scale());
                assertEquals(
                        swordPose.pitch() * FirstPersonArmPose.PITCH_FOLLOW,
                        swordPose.pitch() + pose.counterPitch(),
                        0.0001F);
                assertEquals(
                        swordPose.yaw() * FirstPersonArmPose.YAW_FOLLOW,
                        swordPose.yaw() + pose.counterYaw(),
                        0.0001F);
                assertEquals(
                        swordPose.roll() * FirstPersonArmPose.ROLL_FOLLOW,
                        swordPose.roll() + pose.counterRoll(),
                        0.0001F);
            }
        }
    }

    @Test
    void fiveMovesAuthorDistinctShoulderElbowAndWristPoses() {
        Set<FirstPersonArmPose.JointPose> strikePoses = new HashSet<>();
        FirstPersonArmPose.JointPose neutral = armPose(0, 0.0F).joints();
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            strikePoses.add(armPose(moveIndex, STRIKE_PROGRESS[moveIndex]).joints());
            assertEquals(neutral, armPose(moveIndex, 0.0F).joints());
            assertEquals(neutral, armPose(moveIndex, 1.0F).joints());
        }
        assertEquals(FirstPersonSwordPose.MOVE_COUNT, strikePoses.size());
    }

    @Test
    void thrustsExtendTheElbowAtTheirStrikeKeyframes() {
        FirstPersonArmPose.JointPose thrustWindup = armPose(0, WINDUP_PROGRESS[0]).joints();
        FirstPersonArmPose.JointPose thrustStrike = armPose(0, STRIKE_PROGRESS[0]).joints();
        FirstPersonArmPose.JointPose lungeWindup = armPose(4, WINDUP_PROGRESS[4]).joints();
        FirstPersonArmPose.JointPose lungeStrike = armPose(4, STRIKE_PROGRESS[4]).joints();

        assertTrue(thrustWindup.forearmRoll() - thrustStrike.forearmRoll() >= 28.0F);
        assertTrue(lungeWindup.forearmRoll() - lungeStrike.forearmRoll() >= 36.0F);
    }

    @Test
    void jointDepthAxesStayInsideTheNearPlaneEnvelope() {
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            for (int sample = 0; sample <= 100; sample++) {
                FirstPersonArmPose.JointPose pose = armPose(
                        moveIndex, sample / 100.0F).joints();
                assertTrue(Math.abs(pose.upperPitch()) <= 5.0F);
                assertTrue(Math.abs(pose.upperYaw()) <= 5.0F);
                assertTrue(Math.abs(pose.forearmPitch()) <= 5.0F);
                assertTrue(Math.abs(pose.forearmYaw()) <= 5.0F);
                assertTrue(Math.abs(pose.handPitch()) <= 5.0F);
                assertTrue(Math.abs(pose.handYaw()) <= 5.0F);
            }
        }
    }

    @Test
    void gripCorrectionAnchorsTheArticulatedHandForBothHandsAtEverySample() {
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            for (int sample = 0; sample <= 100; sample++) {
                FirstPersonArmPose.Pose pose = armPose(moveIndex, sample / 100.0F);
                for (HumanoidArm arm : HumanoidArm.values()) {
                    Vector3f endpoint = FirstPersonArmPose.correctedHandEndpoint(pose, arm);
                    assertEquals(0.0F, endpoint.x, 0.0001F);
                    assertEquals(FirstPersonArmPose.GRIP_ENDPOINT_Y, endpoint.y, 0.0001F);
                    assertEquals(0.0F, endpoint.z, 0.0001F);
                }
            }
        }
    }

    @Test
    void leftAndRightGripCorrectionsMirrorWithoutChangingDepthOrHeight() {
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            for (int sample = 0; sample <= 100; sample++) {
                FirstPersonArmPose.Pose pose = armPose(moveIndex, sample / 100.0F);
                Vector3f right = FirstPersonArmPose.gripCorrection(pose, HumanoidArm.RIGHT);
                Vector3f left = FirstPersonArmPose.gripCorrection(pose, HumanoidArm.LEFT);
                assertEquals(-right.x, left.x, 0.0001F);
                assertEquals(right.y, left.y, 0.0001F);
                assertEquals(right.z, left.z, 0.0001F);
            }
        }
    }

    @Test
    void scaledElbowConnectorTargetMirrorsForBothHandsAtEverySample() {
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            for (int sample = 0; sample <= 100; sample++) {
                FirstPersonArmPose.Pose pose = armPose(moveIndex, sample / 100.0F);
                Vector3f right = FirstPersonArmPose.scaledCorrectedElbow(
                        pose, HumanoidArm.RIGHT);
                Vector3f left = FirstPersonArmPose.scaledCorrectedElbow(
                        pose, HumanoidArm.LEFT);
                assertEquals(-right.x, left.x, 0.0001F);
                assertEquals(right.y, left.y, 0.0001F);
                assertEquals(right.z, left.z, 0.0001F);
                assertTrue(Float.isFinite(right.x));
                assertTrue(Float.isFinite(right.y));
                assertTrue(Float.isFinite(right.z));
            }
        }
    }

    @Test
    void jointCurvesStayContinuousAndWithinTheViewmodelEnvelope() {
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            FirstPersonArmPose.JointPose previous = null;
            for (int sample = 0; sample <= 100; sample++) {
                FirstPersonArmPose.JointPose pose = armPose(
                        moveIndex, sample / 100.0F).joints();
                for (float angle : angles(pose)) {
                    assertTrue(Math.abs(angle) <= 60.0F);
                }
                if (previous != null) {
                    float[] before = angles(previous);
                    float[] after = angles(pose);
                    for (int index = 0; index < before.length; index++) {
                        assertTrue(Math.abs(after[index] - before[index]) <= 5.0F);
                    }
                }
                previous = pose;
            }
        }
    }

    @Test
    void viewmodelScaleProtectsOnlyTheMiddleAndReturnsToFullSize() {
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            assertEquals(1.0F, armPose(moveIndex, 0.0F).viewmodelScale(), 0.0001F);
            assertEquals(1.0F, armPose(moveIndex, 1.0F).viewmodelScale(), 0.0001F);
            assertEquals(
                    FirstPersonArmPose.MIN_VIEWMODEL_SCALE,
                    armPose(moveIndex, 0.5F).viewmodelScale(),
                    0.0001F);
            for (int sample = 0; sample <= 100; sample++) {
                float progress = sample / 100.0F;
                float scale = armPose(moveIndex, progress).viewmodelScale();
                assertTrue(scale >= FirstPersonArmPose.MIN_VIEWMODEL_SCALE);
                assertTrue(scale <= 1.0F);
                assertEquals(
                        scale,
                        armPose(moveIndex, 1.0F - progress).viewmodelScale(),
                        0.0001F);
            }
        }
    }

    @Test
    void counterRotationCannotMoveTheGripPivot() {
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            for (int sample = 0; sample <= 100; sample++) {
                FirstPersonArmPose.Pose pose = armPose(moveIndex, sample / 100.0F);
                for (float side : new float[]{-1.0F, 1.0F}) {
                    Vector3f pivot = new Vector3f(
                            side * FirstPersonArmPose.GRIP_PIVOT_X,
                            FirstPersonArmPose.GRIP_PIVOT_Y,
                            FirstPersonArmPose.GRIP_PIVOT_Z);
                    Vector3f transformed = new Matrix4f()
                            .translate(pivot)
                            .rotateZ((float) Math.toRadians(side * pose.counterRoll()))
                            .rotateY((float) Math.toRadians(side * pose.counterYaw()))
                            .rotateX((float) Math.toRadians(pose.counterPitch()))
                            .translate(-pivot.x, -pivot.y, -pivot.z)
                            .transformPosition(new Vector3f(pivot));
                    assertEquals(pivot.x, transformed.x, 0.0001F);
                    assertEquals(pivot.y, transformed.y, 0.0001F);
                    assertEquals(pivot.z, transformed.z, 0.0001F);
                }
            }
        }
    }

    private static FirstPersonArmPose.Pose armPose(int moveIndex, float progress) {
        return FirstPersonArmPose.sample(
                moveIndex,
                progress,
                FirstPersonSwordPose.sample(moveIndex, progress));
    }

    private static float[] angles(FirstPersonArmPose.JointPose pose) {
        return new float[]{
                pose.upperPitch(), pose.upperYaw(), pose.upperRoll(),
                pose.forearmPitch(), pose.forearmYaw(), pose.forearmRoll(),
                pose.handPitch(), pose.handYaw(), pose.handRoll(),
        };
    }
}
