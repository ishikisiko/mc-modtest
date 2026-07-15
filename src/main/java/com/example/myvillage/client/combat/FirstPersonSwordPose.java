package com.example.myvillage.client.combat;

import java.util.List;

final class FirstPersonSwordPose {
    static final int MOVE_COUNT = 5;

    private static final Pose NEUTRAL = new Pose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
    private static final List<Keyframe> THRUST = List.of(
            frame(0.00F, NEUTRAL),
            frame(0.12F, pose(0.32F, -0.08F, -0.12F, 24.0F, -34.0F, 30.0F, 0.96F)),
            frame(0.56F, pose(-0.72F, 0.22F, 0.22F, -82.0F, 16.0F, -8.0F, 1.10F)),
            frame(0.84F, pose(-0.42F, 0.12F, 0.14F, -58.0F, 11.0F, -4.0F, 1.05F)),
            frame(1.00F, NEUTRAL));
    private static final List<Keyframe> HORIZONTAL_CUT = List.of(
            frame(0.00F, NEUTRAL),
            frame(0.13F, pose(0.42F, 0.16F, 0.02F, -16.0F, -66.0F, 42.0F, 0.96F)),
            frame(0.57F, pose(-1.05F, 0.22F, 0.14F, -42.0F, 78.0F, -44.0F, 1.09F)),
            frame(0.85F, pose(-0.58F, 0.10F, 0.08F, -25.0F, 42.0F, -25.0F, 1.04F)),
            frame(1.00F, NEUTRAL));
    private static final List<Keyframe> RISING_CUT = List.of(
            frame(0.00F, NEUTRAL),
            frame(0.14F, pose(0.12F, -0.26F, 0.02F, 32.0F, -32.0F, 34.0F, 0.96F)),
            frame(0.58F, pose(-0.48F, 0.70F, 0.12F, -92.0F, 46.0F, -44.0F, 1.06F)),
            frame(0.86F, pose(-0.28F, 0.40F, 0.09F, -60.0F, 28.0F, -25.0F, 1.03F)),
            frame(1.00F, NEUTRAL));
    private static final List<Keyframe> DIAGONAL_CUT = List.of(
            frame(0.00F, NEUTRAL),
            frame(0.15F, pose(0.42F, 0.62F, 0.04F, -90.0F, -44.0F, 50.0F, 0.94F)),
            frame(0.59F, pose(-1.00F, -0.10F, 0.14F, 22.0F, 62.0F, -52.0F, 1.08F)),
            frame(0.87F, pose(-0.58F, -0.08F, 0.10F, -14.0F, 34.0F, -28.0F, 1.04F)),
            frame(1.00F, NEUTRAL));
    private static final List<Keyframe> LUNGE_THRUST = List.of(
            frame(0.00F, NEUTRAL),
            frame(0.16F, pose(0.36F, 0.16F, -0.12F, 30.0F, -48.0F, 35.0F, 0.96F)),
            frame(0.60F, pose(-0.82F, 0.34F, 0.26F, -94.0F, 6.0F, -5.0F, 1.16F)),
            frame(0.88F, pose(-0.50F, 0.20F, 0.18F, -84.0F, 5.0F, -4.0F, 1.09F)),
            frame(1.00F, NEUTRAL));

    private static final List<List<Keyframe>> MOVES =
            List.of(THRUST, HORIZONTAL_CUT, RISING_CUT, DIAGONAL_CUT, LUNGE_THRUST);

    private FirstPersonSwordPose() {
    }

    static Pose neutral() {
        return NEUTRAL;
    }

    static Pose sample(int moveIndex, float progress) {
        if (moveIndex < 0 || moveIndex >= MOVES.size()) {
            throw new IllegalArgumentException("Unknown first-person sword move index: " + moveIndex);
        }
        float boundedProgress = Math.max(0.0F, Math.min(1.0F, progress));
        List<Keyframe> keyframes = MOVES.get(moveIndex);
        for (int index = 1; index < keyframes.size(); index++) {
            Keyframe end = keyframes.get(index);
            if (boundedProgress <= end.progress()) {
                Keyframe start = keyframes.get(index - 1);
                float span = end.progress() - start.progress();
                float localProgress = span == 0.0F
                        ? 1.0F
                        : (boundedProgress - start.progress()) / span;
                float easedProgress = smoothstep(localProgress);
                return Pose.interpolate(start.pose(), end.pose(), easedProgress);
            }
        }
        return keyframes.getLast().pose();
    }

    private static float smoothstep(float value) {
        float bounded = Math.max(0.0F, Math.min(1.0F, value));
        return bounded * bounded * (3.0F - 2.0F * bounded);
    }

    private static Keyframe frame(float progress, Pose pose) {
        return new Keyframe(progress, pose);
    }

    private static Pose pose(
            float x,
            float y,
            float z,
            float pitch,
            float yaw,
            float roll,
            float scale) {
        return new Pose(x, y, z, pitch, yaw, roll, scale);
    }

    record Pose(float x, float y, float z, float pitch, float yaw, float roll, float scale) {
        private static Pose interpolate(Pose start, Pose end, float progress) {
            return new Pose(
                    lerp(start.x, end.x, progress),
                    lerp(start.y, end.y, progress),
                    lerp(start.z, end.z, progress),
                    lerp(start.pitch, end.pitch, progress),
                    lerp(start.yaw, end.yaw, progress),
                    lerp(start.roll, end.roll, progress),
                    lerp(start.scale, end.scale, progress));
        }

        private static float lerp(float start, float end, float progress) {
            return start + (end - start) * progress;
        }
    }

    private record Keyframe(float progress, Pose pose) {
    }
}
