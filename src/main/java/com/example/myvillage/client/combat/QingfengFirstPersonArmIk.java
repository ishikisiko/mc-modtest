package com.example.myvillage.client.combat;

import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Analytic two-bone solve for the first-person sword arm, in the hand-render pose-stack space.
 *
 * <p>The shoulder sits just in front of the rig pivot, well outside the view; the hand is locked
 * to the sword's grip point. When the grip is farther than the arm can reach, the shoulder slides
 * toward it instead of the hand leaving the handle (the shoulder is never on screen). The elbow
 * bends down, outward and back. Each segment's frame maps model +Y (joint toward the hand) onto
 * the bone and model -Z (the arm's front face) toward the blade, so the handle crosses the fist
 * and the forearm roll follows the sword's grip frame.
 */
final class QingfengFirstPersonArmIk {
    static final float UPPER_ARM_LENGTH = 5.0F / 16.0F;
    static final float FOREARM_LENGTH = 5.0F / 16.0F;
    static final float REACH_LIMIT = 0.97F * (UPPER_ARM_LENGTH + FOREARM_LENGTH);
    static final float MINIMUM_REACH = 0.30F * (UPPER_ARM_LENGTH + FOREARM_LENGTH);
    /** Shoulder offset from the rig pivot along the view axis. */
    static final float SHOULDER_FORWARD = -0.05F;
    private static final Vector3f RIGHT_POLE = new Vector3f(0.5F, -1.0F, 0.3F).normalize();
    private static final float DEGENERATE = 1.0E-4F;

    private QingfengFirstPersonArmIk() {
    }

    /** Solves the arm for one rig pose, from the same grip frame the sword item is drawn in. */
    static Solution solve(
            HumanoidArm arm,
            float equipProgress,
            FirstPersonSwing.Rig rig,
            FirstPersonSwing.Pose pose) {
        Matrix4f gripFrame = FirstPersonSwordTransform.gripFrame(arm, equipProgress, rig, pose);
        Vector3f grip = gripFrame.getTranslation(new Vector3f());
        Vector3f blade = gripFrame.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
        Vector3f shoulder = shoulderForPivot(FirstPersonSwordTransform.pivot(arm, equipProgress, rig, pose));
        return solve(shoulder, grip, blade, arm);
    }

    /** The unclamped shoulder for a rig pivot (see {@link FirstPersonSwordTransform#pivot}). */
    static Vector3f shoulderForPivot(Vector3f pivot) {
        return new Vector3f(pivot).add(0.0F, 0.0F, SHOULDER_FORWARD);
    }

    static Vector3f pole(HumanoidArm arm) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        return new Vector3f(side * RIGHT_POLE.x, RIGHT_POLE.y, RIGHT_POLE.z);
    }

    /**
     * Solves the arm so the hand lands exactly on {@code grip}.
     *
     * @param shoulder desired shoulder position
     * @param grip grip point of the held sword
     * @param bladeAxis direction from the grip toward the blade tip
     * @param arm which arm holds the sword (mirrors the elbow pole)
     */
    static Solution solve(Vector3f shoulder, Vector3f grip, Vector3f bladeAxis, HumanoidArm arm) {
        Vector3f toGrip = new Vector3f(grip).sub(shoulder);
        float distance = toGrip.length();
        Vector3f axis = distance > DEGENERATE
                ? new Vector3f(toGrip).div(distance)
                : new Vector3f(0.0F, 0.0F, -1.0F);
        float clamped = Math.max(MINIMUM_REACH, Math.min(REACH_LIMIT, distance));
        Vector3f solvedShoulder = new Vector3f(grip).sub(new Vector3f(axis).mul(clamped));

        float along = (UPPER_ARM_LENGTH * UPPER_ARM_LENGTH
                - FOREARM_LENGTH * FOREARM_LENGTH
                + clamped * clamped) / (2.0F * clamped);
        float out = (float) Math.sqrt(Math.max(0.0F, UPPER_ARM_LENGTH * UPPER_ARM_LENGTH - along * along));
        Vector3f bend = perpendicular(pole(arm), axis);
        if (bend == null) {
            bend = perpendicular(new Vector3f(0.0F, -1.0F, 0.0F), axis);
        }
        if (bend == null) {
            bend = perpendicular(new Vector3f(1.0F, 0.0F, 0.0F), axis);
        }
        Vector3f elbow = new Vector3f(solvedShoulder)
                .add(new Vector3f(axis).mul(along))
                .add(new Vector3f(bend).mul(out));

        Vector3f forearmAxis = new Vector3f(grip).sub(elbow).normalize();
        Vector3f upperAxis = new Vector3f(elbow).sub(solvedShoulder).normalize();
        // Model -Z (front) faces the blade: +Z is the part of "away from the blade" that is
        // perpendicular to the bone. Fall back to the elbow bend when the blade runs along it.
        Vector3f awayFromBlade = new Vector3f(bladeAxis).negate();
        Vector3f forearmBack = perpendicular(awayFromBlade, forearmAxis);
        if (forearmBack == null) {
            forearmBack = perpendicular(new Vector3f(bend).negate(), forearmAxis);
        }
        if (forearmBack == null) {
            forearmBack = perpendicular(new Vector3f(1.0F, 0.0F, 0.0F), forearmAxis);
        }
        Vector3f upperBack = perpendicular(forearmBack, upperAxis);
        if (upperBack == null) {
            upperBack = perpendicular(new Vector3f(bend).negate(), upperAxis);
        }
        if (upperBack == null) {
            upperBack = perpendicular(new Vector3f(1.0F, 0.0F, 0.0F), upperAxis);
        }
        return new Solution(
                solvedShoulder,
                elbow,
                new Vector3f(grip),
                basis(upperAxis, upperBack),
                basis(forearmAxis, forearmBack),
                distance > REACH_LIMIT || distance < MINIMUM_REACH);
    }

    /** {@code vector} minus its component along unit {@code axis}, normalised; null when parallel. */
    static Vector3f perpendicular(Vector3f vector, Vector3f axis) {
        Vector3f result = new Vector3f(vector).sub(new Vector3f(axis).mul(vector.dot(axis)));
        float length = result.length();
        return length < 1.0E-3F ? null : result.div(length);
    }

    /** Right-handed rotation taking model +Y to {@code yAxis} and model +Z to {@code zAxis}. */
    private static Quaternionf basis(Vector3f yAxis, Vector3f zAxis) {
        Vector3f xAxis = new Vector3f(yAxis).cross(zAxis).normalize();
        Vector3f z = new Vector3f(xAxis).cross(yAxis).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(xAxis, yAxis, z));
    }

    /**
     * @param shoulder solved shoulder (moved toward the grip when the grip was out of reach)
     * @param upperArmRotation model-to-view rotation of the upper arm
     * @param forearmRotation model-to-view rotation of the forearm and fist
     * @param clamped true when the requested shoulder had to move
     */
    record Solution(
            Vector3f shoulder,
            Vector3f elbow,
            Vector3f hand,
            Quaternionf upperArmRotation,
            Quaternionf forearmRotation,
            boolean clamped) {
    }
}
