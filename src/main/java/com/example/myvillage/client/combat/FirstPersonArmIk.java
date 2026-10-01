package com.example.myvillage.client.combat;

import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shoulder, elbow, wrist and fist for the first-person sword arm, in hand-render pose-stack space.
 *
 * <p>The fist is locked to the sword: the handle passes through it across the palm, leaning
 * {@code grip_diagonal} degrees toward the knuckles (as a real sword grip runs from the index
 * knuckle to the heel of the hand), so the guard sits above the thumb side and the pommel below
 * the little finger. The wrist is one palm behind the handle along the hand. A two-bone solve then
 * reaches from an off-screen shoulder to that wrist, so the angle between forearm and fist is a
 * real wrist bend.
 *
 * <p>Two pose fields keep that bend anatomical, both authored per key in the rig and interpolated
 * with the rest of the pose (so they are continuous and need no frame history): {@code grip_roll},
 * how the hand turns about the handle in the sword's frame (a loose grip lets the rig show the
 * blade's flat while the hand stays lined up with the forearm), and {@code elbow}, the elbow's
 * swivel about the shoulder-wrist line. A presentation-only lag vector (see
 * {@link FirstPersonArmLag}) then drags the shoulder and elbow behind the grip, scaled down if it
 * would push the wrist past {@link #FLEX_LIMIT}, {@link #RADIAL_LIMIT} or {@link #ULNAR_LIMIT}.
 * Everything is solved for the right arm and mirrored.
 */
final class FirstPersonArmIk {
    /** Anatomical wrist limits in degrees: flexion/extension, radial and ulnar deviation. */
    static final float FLEX_LIMIT = 45.0F;
    static final float RADIAL_LIMIT = 30.0F;
    static final float ULNAR_LIMIT = 45.0F;
    static final float REACH_FRACTION = 0.97F;
    static final float MINIMUM_REACH_FRACTION = 0.30F;
    /** Fist box in skin pixels: four long, starting three quarters of a pixel behind the wrist joint. */
    static final float FIST_LENGTH_PIXELS = 4.0F;
    static final float FIST_OVERLAP_PIXELS = 0.75F;
    /** The handle crosses the middle of the fist box. */
    static final float GRIP_ALONG_PIXELS = FIST_LENGTH_PIXELS * 0.5F - FIST_OVERLAP_PIXELS;
    static final float DEPTH_PIXELS = 4.0F;
    /** Share of the lag vector that moves the (off-screen) shoulder; the rest swivels the elbow. */
    static final float LAG_SHOULDER_SHARE = 0.6F;
    static final float LAG_SWIVEL_LIMIT = 30.0F;

    private static final Vector3f RIGHT_POLE = new Vector3f(0.5F, -1.0F, 0.3F).normalize();
    private static final int LAG_BISECTIONS = 5;
    private static final float DEGENERATE = 1.0E-4F;

    private FirstPersonArmIk() {
    }

    /**
     * Solves the arm for one rig pose, from the same grip frame the sword item is drawn in.
     *
     * @param lag right-arm lag offset from {@link FirstPersonArmLag}, or null for none
     */
    static Solution solve(
            HumanoidArm arm,
            float equipProgress,
            FirstPersonSwing swing,
            FirstPersonSwing.Pose pose,
            Vector3f lag) {
        Solution right = solveRight(equipProgress, swing, pose, lag);
        return arm == HumanoidArm.RIGHT ? right : right.mirrored();
    }

    static Solution solve(HumanoidArm arm, float equipProgress, FirstPersonSwing swing, FirstPersonSwing.Pose pose) {
        return solve(arm, equipProgress, swing, pose, null);
    }

    /** Distance from the wrist joint to the handle axis along the hand, in blocks. */
    static float wristToGrip(FirstPersonSwing.Arm arm) {
        return GRIP_ALONG_PIXELS * arm.thickness() / 16.0F;
    }

    private static Solution solveRight(
            float equipProgress,
            FirstPersonSwing swing,
            FirstPersonSwing.Pose pose,
            Vector3f lag) {
        FirstPersonSwing.Rig rig = swing.rig();
        FirstPersonSwing.Arm armRig = rig.arm();
        Matrix4f gripFrame = FirstPersonSwordTransform.gripFrame(HumanoidArm.RIGHT, equipProgress, rig, pose);
        Frame frame = new Frame(
                gripFrame.getTranslation(new Vector3f()),
                gripFrame.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize(),
                FirstPersonSwordTransform.pivot(HumanoidArm.RIGHT, equipProgress, rig, pose)
                        .add(armRig.shoulderOffsetX(), armRig.shoulderOffsetY(), armRig.shoulderOffsetZ()),
                armRig);
        Vector3f handRoll = handRoll(gripFrame, pose.gripRoll());

        Candidate still = candidate(frame, handRoll, pose.elbow(), null);
        Candidate solved = still;
        float lagScale = 0.0F;
        if (lag != null && lag.lengthSquared() > 1.0E-10F) {
            Candidate full = candidate(frame, handRoll, pose.elbow(), lag);
            if (withinLimits(full) || !withinLimits(still)) {
                solved = full;
                lagScale = 1.0F;
            } else {
                // Keep as much lag as the wrist allows (continuous in the lag, unlike a hard cut).
                float low = 0.0F;
                float high = 1.0F;
                for (int step = 0; step < LAG_BISECTIONS; step++) {
                    float middle = (low + high) * 0.5F;
                    if (withinLimits(candidate(frame, handRoll, pose.elbow(), new Vector3f(lag).mul(middle)))) {
                        low = middle;
                    } else {
                        high = middle;
                    }
                }
                if (low > 0.0F) {
                    solved = candidate(frame, handRoll, pose.elbow(), new Vector3f(lag).mul(low));
                    lagScale = low;
                }
            }
        }
        return solved.toSolution(frame, pose.gripRoll(), pose.elbow(), lagScale);
    }

    /**
     * The hand's knuckle direction projected across the handle: {@code gripRoll} degrees from the
     * sword's +Z edge toward its +X flat normal, in the grip frame.
     */
    static Vector3f handRoll(Matrix4f gripFrame, float gripRoll) {
        float radians = (float) Math.toRadians(gripRoll);
        Vector3f edge = gripFrame.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
        Vector3f flat = gripFrame.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
        return edge.mul((float) Math.cos(radians)).add(flat.mul((float) Math.sin(radians)));
    }

    private static Candidate candidate(Frame frame, Vector3f handRoll, float swivel, Vector3f lag) {
        float diagonal = (float) Math.toRadians(frame.arm.gripDiagonal());
        float cos = (float) Math.cos(diagonal);
        float sin = (float) Math.sin(diagonal);
        Vector3f thumb = new Vector3f(frame.blade).mul(cos).sub(new Vector3f(handRoll).mul(sin));
        Vector3f hand = new Vector3f(frame.blade).mul(sin).add(new Vector3f(handRoll).mul(cos));
        Vector3f palm = new Vector3f(thumb).cross(hand);
        Vector3f wrist = new Vector3f(frame.grip).sub(new Vector3f(hand).mul(wristToGrip(frame.arm)));
        Vector3f shoulder = new Vector3f(frame.shoulder);
        if (lag != null) {
            shoulder.add(new Vector3f(lag).mul(LAG_SHOULDER_SHARE));
        }

        float upperArm = frame.arm.upperArm();
        float forearm = frame.arm.forearm();
        Vector3f toWrist = new Vector3f(wrist).sub(shoulder);
        float distance = toWrist.length();
        Vector3f axis = distance > DEGENERATE
                ? new Vector3f(toWrist).div(distance)
                : new Vector3f(0.0F, 0.0F, -1.0F);
        float reachLimit = REACH_FRACTION * (upperArm + forearm);
        float minimumReach = MINIMUM_REACH_FRACTION * (upperArm + forearm);
        float clamped = Math.max(minimumReach, Math.min(reachLimit, distance));
        Vector3f solvedShoulder = new Vector3f(wrist).sub(new Vector3f(axis).mul(clamped));
        float along = (upperArm * upperArm - forearm * forearm + clamped * clamped) / (2.0F * clamped);
        float out = (float) Math.sqrt(Math.max(0.0F, upperArm * upperArm - along * along));

        Vector3f bend = perpendicular(RIGHT_POLE, axis);
        if (bend == null) {
            bend = perpendicular(new Vector3f(0.0F, -1.0F, 0.0F), axis);
        }
        if (bend == null) {
            bend = perpendicular(new Vector3f(1.0F, 0.0F, 0.0F), axis);
        }
        // Positive swivel turns the elbow up and out (right-handed about the backward axis).
        bend.rotateAxis((float) Math.toRadians(-swivel), axis.x, axis.y, axis.z);
        if (lag != null) {
            Vector3f lagAcross = perpendicularRaw(lag, axis);
            Vector3f dragged = perpendicular(new Vector3f(bend).mul(out).add(lagAcross), axis);
            if (dragged != null) {
                float turn = (float) Math.toDegrees(Math.atan2(
                        new Vector3f(bend).cross(dragged).dot(axis), bend.dot(dragged)));
                if (Math.abs(turn) > LAG_SWIVEL_LIMIT) {
                    dragged = new Vector3f(bend).rotateAxis(
                            (float) Math.toRadians(Math.copySign(LAG_SWIVEL_LIMIT, turn)), axis.x, axis.y, axis.z);
                }
                bend = dragged;
            }
        }
        Vector3f elbow = new Vector3f(solvedShoulder).add(new Vector3f(axis).mul(along)).add(new Vector3f(bend).mul(out));
        Vector3f forearmAxis = new Vector3f(wrist).sub(elbow).normalize();

        float alongHand = forearmAxis.dot(hand);
        float flexion = (float) -Math.toDegrees(Math.atan2(forearmAxis.dot(palm), alongHand));
        float deviation = (float) -Math.toDegrees(Math.atan2(forearmAxis.dot(thumb), alongHand));
        return new Candidate(solvedShoulder, elbow, wrist, forearmAxis, thumb, hand, palm, flexion, deviation,
                distance > reachLimit || distance < minimumReach);
    }

    static boolean withinLimits(float flexion, float deviation) {
        return Math.abs(flexion) <= FLEX_LIMIT && deviation <= RADIAL_LIMIT && deviation >= -ULNAR_LIMIT;
    }

    private static boolean withinLimits(Candidate candidate) {
        return withinLimits(candidate.flexion, candidate.deviation);
    }

    /** {@code vector} minus its component along unit {@code axis}, normalised; null when parallel. */
    static Vector3f perpendicular(Vector3f vector, Vector3f axis) {
        Vector3f result = perpendicularRaw(vector, axis);
        float length = result.length();
        return length < 1.0E-3F ? null : result.div(length);
    }

    private static Vector3f perpendicularRaw(Vector3f vector, Vector3f axis) {
        return new Vector3f(vector).sub(new Vector3f(axis).mul(vector.dot(axis)));
    }

    /** Right-handed rotation taking model +X to {@code xHint} (made orthogonal) and model +Y to {@code yAxis}. */
    private static Quaternionf basis(Vector3f xHint, Vector3f yAxis) {
        Vector3f x = perpendicular(xHint, yAxis);
        if (x == null) {
            x = perpendicular(new Vector3f(1.0F, 0.0F, 0.0F), yAxis);
        }
        if (x == null) {
            x = perpendicular(new Vector3f(0.0F, 0.0F, 1.0F), yAxis);
        }
        Vector3f z = new Vector3f(x).cross(yAxis).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(x, yAxis, z));
    }

    private record Frame(Vector3f grip, Vector3f blade, Vector3f shoulder, FirstPersonSwing.Arm arm) {
    }

    private record Candidate(
            Vector3f shoulder,
            Vector3f elbow,
            Vector3f wrist,
            Vector3f forearmAxis,
            Vector3f thumb,
            Vector3f hand,
            Vector3f palm,
            float flexion,
            float deviation,
            boolean clamped) {
        Solution toSolution(Frame frame, float roll, float swivel, float lagScale) {
            Vector3f upperAxis = new Vector3f(elbow).sub(shoulder).normalize();
            Quaternionf forearmRotation = basis(thumb, forearmAxis);
            Vector3f forearmX = forearmRotation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
            return new Solution(
                    new Vector3f(shoulder),
                    new Vector3f(elbow),
                    new Vector3f(wrist),
                    new Vector3f(frame.grip),
                    basis(forearmX, upperAxis),
                    forearmRotation,
                    basis(thumb, hand),
                    flexion,
                    deviation,
                    roll,
                    swivel,
                    lagScale,
                    clamped);
        }
    }

    /**
     * @param shoulder solved shoulder (moved toward the wrist when the wrist was out of reach)
     * @param wrist wrist joint: the forearm ends and the fist begins here
     * @param grip grip point on the handle axis (inside the fist)
     * @param upperArmRotation model-to-view rotation of the upper arm (+Y shoulder to elbow)
     * @param forearmRotation model-to-view rotation of the forearm (+Y elbow to wrist)
     * @param fistRotation model-to-view rotation of the fist: +Y wrist to knuckles, +X thumb side
     *     (toward the guard), +Z palm
     * @param flexion wrist flexion (+, toward the palm) or extension (-), degrees
     * @param deviation radial (+, toward the thumb) or ulnar (-) deviation, degrees
     * @param roll hand roll about the handle (the pose's {@code grip_roll}), degrees
     * @param swivel elbow swivel (+ raises the elbow; the pose's {@code elbow}), degrees
     * @param lagScale share of the requested lag the wrist limits allowed
     * @param clamped true when the requested shoulder had to move
     */
    record Solution(
            Vector3f shoulder,
            Vector3f elbow,
            Vector3f wrist,
            Vector3f grip,
            Quaternionf upperArmRotation,
            Quaternionf forearmRotation,
            Quaternionf fistRotation,
            float flexion,
            float deviation,
            float roll,
            float swivel,
            float lagScale,
            boolean clamped) {
        /** The same arm reflected across the view's vertical plane (x to -x), for a left main hand. */
        Solution mirrored() {
            return new Solution(
                    mirror(shoulder), mirror(elbow), mirror(wrist), mirror(grip),
                    mirror(upperArmRotation), mirror(forearmRotation), mirror(fistRotation),
                    flexion, deviation, roll, swivel, lagScale, clamped);
        }

        private static Vector3f mirror(Vector3f vector) {
            return new Vector3f(-vector.x, vector.y, vector.z);
        }

        /** Conjugating a rotation by the x reflection keeps it proper: (x, y, z, w) to (x, -y, -z, w). */
        private static Quaternionf mirror(Quaternionf rotation) {
            return new Quaternionf(rotation.x, -rotation.y, -rotation.z, rotation.w);
        }
    }
}
