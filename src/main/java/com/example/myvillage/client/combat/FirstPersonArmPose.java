package com.example.myvillage.client.combat;

import java.util.List;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class FirstPersonArmPose {
    static final float PITCH_FOLLOW = 0.10F;
    static final float YAW_FOLLOW = 0.35F;
    static final float ROLL_FOLLOW = 0.20F;
    static final float MIN_VIEWMODEL_SCALE = 0.45F;
    static final float VIEWMODEL_SCALE_FADE_PROGRESS = 0.12F;
    static final float SHOULDER_TO_ELBOW_Y = 3.0F;
    static final float FOREARM_LENGTH = 5.0F;
    static final float HAND_LENGTH = 2.0F;
    static final float GRIP_ENDPOINT_Y = 10.0F;
    static final float GRIP_PIVOT_X = 0.055F;
    static final float GRIP_PIVOT_Y = -0.061F;
    static final float GRIP_PIVOT_Z = -0.024F;
    static final float GRIP_OFFSET_X = -0.040F;
    static final float GRIP_OFFSET_Y = -0.182F;
    static final float GRIP_OFFSET_Z = 0.394F;

    private static final JointPose NEUTRAL_JOINTS = joints(
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F);
    private static final List<Keyframe> THRUST = List.of(
            frame(0.00F, NEUTRAL_JOINTS),
            frame(0.12F, joints(
                    4.0F, -2.0F, -10.0F,
                    -4.0F, 3.0F, 38.0F,
                    2.0F, -2.0F, -18.0F)),
            frame(0.56F, joints(
                    1.0F, 2.0F, -3.0F,
                    -2.0F, -1.0F, 10.0F,
                    1.0F, 0.0F, -6.0F)),
            frame(0.84F, joints(
                    2.0F, 1.0F, -7.0F,
                    0.0F, 0.0F, 20.0F,
                    -2.0F, 0.0F, -8.0F)),
            frame(1.00F, NEUTRAL_JOINTS));
    private static final List<Keyframe> HORIZONTAL_CUT = List.of(
            frame(0.00F, NEUTRAL_JOINTS),
            frame(0.13F, joints(
                    3.0F, -4.0F, -18.0F,
                    -4.0F, 5.0F, 40.0F,
                    2.0F, -3.0F, -16.0F)),
            frame(0.57F, joints(
                    -3.0F, 5.0F, 6.0F,
                    2.0F, -5.0F, 24.0F,
                    -1.0F, 3.0F, -14.0F)),
            frame(0.85F, joints(
                    0.0F, 2.0F, -8.0F,
                    1.0F, -2.0F, 20.0F,
                    -1.0F, 1.0F, -10.0F)),
            frame(1.00F, NEUTRAL_JOINTS));
    private static final List<Keyframe> RISING_CUT = List.of(
            frame(0.00F, NEUTRAL_JOINTS),
            frame(0.14F, joints(
                    4.0F, -3.0F, -16.0F,
                    -3.0F, 4.0F, 42.0F,
                    2.0F, -2.0F, -18.0F)),
            frame(0.58F, joints(
                    -5.0F, 5.0F, 8.0F,
                    3.0F, -4.0F, 20.0F,
                    2.0F, 2.0F, -12.0F)),
            frame(0.86F, joints(
                    -2.0F, 2.0F, -6.0F,
                    2.0F, -2.0F, 22.0F,
                    0.0F, 1.0F, -12.0F)),
            frame(1.00F, NEUTRAL_JOINTS));
    private static final List<Keyframe> DIAGONAL_CUT = List.of(
            frame(0.00F, NEUTRAL_JOINTS),
            frame(0.15F, joints(
                    -4.0F, -4.0F, -10.0F,
                    3.0F, 5.0F, 30.0F,
                    -2.0F, -3.0F, -12.0F)),
            frame(0.59F, joints(
                    4.0F, 5.0F, -16.0F,
                    -3.0F, -5.0F, 30.0F,
                    2.0F, 3.0F, -16.0F)),
            frame(0.87F, joints(
                    1.0F, 2.0F, -8.0F,
                    0.0F, -2.0F, 22.0F,
                    -1.0F, 1.0F, -12.0F)),
            frame(1.00F, NEUTRAL_JOINTS));
    private static final List<Keyframe> LUNGE_THRUST = List.of(
            frame(0.00F, NEUTRAL_JOINTS),
            frame(0.16F, joints(
                    3.0F, -3.0F, -12.0F,
                    -4.0F, 4.0F, 44.0F,
                    2.0F, -2.0F, -20.0F)),
            frame(0.60F, joints(
                    0.0F, 2.0F, -2.0F,
                    0.0F, -2.0F, 8.0F,
                    0.0F, 0.0F, -6.0F)),
            frame(0.88F, joints(
                    1.0F, 1.0F, -6.0F,
                    0.0F, -1.0F, 16.0F,
                    -1.0F, 0.0F, -8.0F)),
            frame(1.00F, NEUTRAL_JOINTS));

    private static final List<List<Keyframe>> MOVES =
            List.of(THRUST, HORIZONTAL_CUT, RISING_CUT, DIAGONAL_CUT, LUNGE_THRUST);

    private FirstPersonArmPose() {
    }

    static Pose neutral(FirstPersonSwordPose.Pose swordPose) {
        return create(swordPose, NEUTRAL_JOINTS, 1.0F);
    }

    static Pose sample(
            int moveIndex,
            float progress,
            FirstPersonSwordPose.Pose swordPose) {
        if (moveIndex < 0 || moveIndex >= MOVES.size()) {
            throw new IllegalArgumentException("Unknown first-person arm move index: " + moveIndex);
        }
        float boundedProgress = Math.max(0.0F, Math.min(1.0F, progress));
        return create(
                swordPose,
                sampleJoints(MOVES.get(moveIndex), boundedProgress),
                viewmodelScale(boundedProgress));
    }

    static Vector3f gripCorrection(Pose pose, HumanoidArm arm) {
        Vector3f endpoint = handEndpoint(pose.joints(), arm);
        return new Vector3f(
                -endpoint.x,
                GRIP_ENDPOINT_Y - endpoint.y,
                -endpoint.z);
    }

    static Vector3f correctedHandEndpoint(Pose pose, HumanoidArm arm) {
        return handEndpoint(pose.joints(), arm).add(gripCorrection(pose, arm));
    }

    static Vector3f scaledCorrectedElbow(Pose pose, HumanoidArm arm) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        Vector3f elbow = rotate(
                new Vector3f(0.0F, SHOULDER_TO_ELBOW_Y, 0.0F),
                pose.joints().upperPitch(),
                side * pose.joints().upperYaw(),
                side * pose.joints().upperRoll());
        elbow.add(gripCorrection(pose, arm));
        Vector3f grip = new Vector3f(0.0F, GRIP_ENDPOINT_Y, 0.0F);
        return elbow.sub(grip).mul(pose.viewmodelScale()).add(grip);
    }

    private static Pose create(
            FirstPersonSwordPose.Pose swordPose,
            JointPose joints,
            float viewmodelScale) {
        return new Pose(
                swordPose.x(),
                swordPose.y(),
                swordPose.z(),
                swordPose.pitch(),
                swordPose.yaw(),
                swordPose.roll(),
                swordPose.scale(),
                -swordPose.pitch() * (1.0F - PITCH_FOLLOW),
                -swordPose.yaw() * (1.0F - YAW_FOLLOW),
                -swordPose.roll() * (1.0F - ROLL_FOLLOW),
                joints,
                viewmodelScale);
    }

    private static JointPose sampleJoints(List<Keyframe> keyframes, float progress) {
        for (int index = 1; index < keyframes.size(); index++) {
            Keyframe end = keyframes.get(index);
            if (progress <= end.progress()) {
                Keyframe start = keyframes.get(index - 1);
                float span = end.progress() - start.progress();
                float localProgress = span == 0.0F
                        ? 1.0F
                        : (progress - start.progress()) / span;
                return JointPose.interpolate(
                        start.pose(), end.pose(), smoothstep(localProgress));
            }
        }
        return keyframes.getLast().pose();
    }

    private static float viewmodelScale(float progress) {
        float edgeDistance = Math.min(progress, 1.0F - progress);
        float fade = Math.min(1.0F, edgeDistance / VIEWMODEL_SCALE_FADE_PROGRESS);
        float actionWeight = smoothstep(fade);
        return 1.0F - (1.0F - MIN_VIEWMODEL_SCALE) * actionWeight;
    }

    private static Vector3f handEndpoint(JointPose pose, HumanoidArm arm) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        Vector3f handEnd = rotate(
                new Vector3f(0.0F, HAND_LENGTH, 0.0F),
                pose.handPitch(),
                side * pose.handYaw(),
                side * pose.handRoll());
        Vector3f wristToHand = rotate(
                new Vector3f(0.0F, FOREARM_LENGTH, 0.0F).add(handEnd),
                pose.forearmPitch(),
                side * pose.forearmYaw(),
                side * pose.forearmRoll());
        return rotate(
                new Vector3f(0.0F, SHOULDER_TO_ELBOW_Y, 0.0F).add(wristToHand),
                pose.upperPitch(),
                side * pose.upperYaw(),
                side * pose.upperRoll());
    }

    private static Vector3f rotate(
            Vector3f vector,
            float pitch,
            float yaw,
            float roll) {
        return new Quaternionf()
                .rotationZYX(radians(roll), radians(yaw), radians(pitch))
                .transform(vector);
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static float smoothstep(float value) {
        float bounded = Math.max(0.0F, Math.min(1.0F, value));
        return bounded * bounded * (3.0F - 2.0F * bounded);
    }

    private static Keyframe frame(float progress, JointPose pose) {
        return new Keyframe(progress, pose);
    }

    private static JointPose joints(
            float upperPitch,
            float upperYaw,
            float upperRoll,
            float forearmPitch,
            float forearmYaw,
            float forearmRoll,
            float handPitch,
            float handYaw,
            float handRoll) {
        return new JointPose(
                upperPitch,
                upperYaw,
                upperRoll,
                forearmPitch,
                forearmYaw,
                forearmRoll,
                handPitch,
                handYaw,
                handRoll);
    }

    record Pose(
            float x,
            float y,
            float z,
            float pitch,
            float yaw,
            float roll,
            float scale,
            float counterPitch,
            float counterYaw,
            float counterRoll,
            JointPose joints,
            float viewmodelScale) {
    }

    record JointPose(
            float upperPitch,
            float upperYaw,
            float upperRoll,
            float forearmPitch,
            float forearmYaw,
            float forearmRoll,
            float handPitch,
            float handYaw,
            float handRoll) {
        private static JointPose interpolate(JointPose start, JointPose end, float progress) {
            return new JointPose(
                    lerp(start.upperPitch, end.upperPitch, progress),
                    lerp(start.upperYaw, end.upperYaw, progress),
                    lerp(start.upperRoll, end.upperRoll, progress),
                    lerp(start.forearmPitch, end.forearmPitch, progress),
                    lerp(start.forearmYaw, end.forearmYaw, progress),
                    lerp(start.forearmRoll, end.forearmRoll, progress),
                    lerp(start.handPitch, end.handPitch, progress),
                    lerp(start.handYaw, end.handYaw, progress),
                    lerp(start.handRoll, end.handRoll, progress));
        }

        private static float lerp(float start, float end, float progress) {
            return start + (end - start) * progress;
        }
    }

    private record Keyframe(float progress, JointPose pose) {
    }
}
