package com.example.myvillage.client.combat;

import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Shoulder, elbow, wrist and fist for the first-person weapon arm, in hand-render pose-stack space.
 *
 * <p>The fist is locked to the weapon: the handle passes through it across the palm, leaning
 * {@code grip_diagonal} degrees toward the knuckles (as a real sword grip runs from the index
 * knuckle to the heel of the hand), so the collar sits above the thumb side and the butt below
 * the little finger. The wrist is one palm behind the handle along the hand. A two-bone solve then
 * reaches from an off-screen shoulder to that wrist, so the angle between forearm and fist is a
 * real wrist bend.
 *
 * <p>Two pose fields keep that bend anatomical, both authored per key in the rig and interpolated
 * with the rest of the pose (so they are continuous and need no frame history): {@code grip_roll},
 * how the hand turns about the handle in the weapon's frame (a loose grip lets the rig show the
 * head's flat while the hand stays lined up with the forearm), and {@code elbow}, the elbow's
 * swivel about the shoulder-wrist line. A presentation-only lag vector (see
 * {@link FirstPersonArmLag}) then drags the shoulder and elbow behind the grip, scaled down if it
 * would push the wrist past {@link #FLEX_LIMIT}, {@link #RADIAL_LIMIT} or {@link #ULNAR_LIMIT}.
 * Everything is solved for the right arm and mirrored.
 *
 * <p>With {@code rig.off_hand} the off arm is solved too ({@link #solveOffHand}): the mirror image
 * of the main arm (its shoulder is the main shoulder reflected across the view's vertical plane,
 * the body offset shared, the cross-section and bone lengths its own), whose fist closes on the
 * shaft at the contract's {@code off_hand_grip_center} plus the pose's {@code off_hand_slide}, in
 * the same grip frame the weapon and the main hand use. Its thumb points toward the tip; by default the hand lines up with
 * the reach from its shoulder (knuckles away from the shoulder), and {@code off_hand_roll} turns it
 * about the shaft from there. A point out of reach slides the hand along the shaft to the nearest
 * reachable point, never off the shaft or into the main fist. The off arm takes no lag: the lag
 * never moves the weapon, so the off hand stays locked to the shaft. {@code off_hand_hold} below 1
 * moves the hand from the shaft toward its released rest ({@code rig.off_hand.rest_direction} and
 * {@code rest_reach}; by default beside the body, below the view at 0).
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

    /**
     * Closest the two grip points come along the shaft, and closest the off grip comes to the
     * handle's top end, in skin pixels at the arm's thickness (about two and one half-fists across
     * a diagonal grip), so the fists never overlap each other or the collar.
     */
    static final float OFF_HAND_GAP_PIXELS = 6.0F;
    static final float OFF_HAND_END_PIXELS = 3.0F;
    static final int OFF_HAND_REACH_SAMPLES = 48;
    static final int OFF_HAND_REACH_BISECTIONS = 10;

    private static final Vector3f RIGHT_POLE = new Vector3f(0.5F, -1.0F, 0.3F).normalize();
    private static final int LAG_BISECTIONS = 5;
    private static final float DEGENERATE = 1.0E-4F;

    private FirstPersonArmIk() {
    }

    /**
     * Solves the arm for one rig pose, from the same grip frame the weapon item is drawn in.
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
        Matrix4f gripFrame = FirstPersonWeaponTransform.gripFrame(HumanoidArm.RIGHT, equipProgress, rig, pose);
        Frame frame = new Frame(
                gripFrame.getTranslation(new Vector3f()),
                gripFrame.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize(),
                FirstPersonWeaponTransform.pivot(HumanoidArm.RIGHT, equipProgress, rig, pose)
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
     * weapon's +Z edge toward its +X flat normal, in the grip frame.
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
        return bones(frame.shoulder, frame.arm, wrist, thumb, hand, palm, swivel, lag);
    }

    /** Two-bone solve from {@code requestedShoulder} to a wrist whose fist axes are given. */
    private static Candidate bones(
            Vector3f requestedShoulder,
            FirstPersonSwing.Arm arm,
            Vector3f wrist,
            Vector3f thumb,
            Vector3f hand,
            Vector3f palm,
            float swivel,
            Vector3f lag) {
        Vector3f shoulder = new Vector3f(requestedShoulder);
        if (lag != null) {
            shoulder.add(new Vector3f(lag).mul(LAG_SHOULDER_SHARE));
        }

        float upperArm = arm.upperArm();
        float forearm = arm.forearm();
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

    /**
     * Solves the off arm for one rig pose; empty when the rig has no {@code off_hand} block or the
     * pose lets go of the shaft ({@code off_hand_hold} 0). Solved as a right arm in the mirror image
     * of the right-handed scene and reflected back, so a left main arm gets the exact mirror.
     */
    static Optional<OffHandSolution> solveOffHand(
            HumanoidArm mainArm,
            float equipProgress,
            FirstPersonSwing swing,
            FirstPersonSwing.Pose pose) {
        FirstPersonSwing.Rig rig = swing.rig();
        FirstPersonSwing.OffHand offHand = rig.offHand();
        Optional<Vector3f> center = swing.weapon().offHandGripCenter();
        float hold = Math.max(0.0F, Math.min(1.0F, pose.offHandHold()));
        if (offHand == null || center.isEmpty() || hold <= 0.0F) {
            return Optional.empty();
        }
        FirstPersonSwing.Arm armRig = rig.offArm();
        Matrix4f gripFrame = FirstPersonWeaponTransform.gripFrame(HumanoidArm.RIGHT, equipProgress, rig, pose);
        Shaft shaft = new Shaft(
                gripFrame,
                swing.weapon(),
                rig.weaponScale(),
                mirror(gripFrame.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize()),
                mirror(gripFrame.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)).normalize()),
                new Vector3f(
                        rig.shoulderX() + offHand.shoulderOffsetX() - pose.x(),
                        rig.shoulderY() + pose.y() - equipProgress * FirstPersonWeaponTransform.EQUIP_DROP
                                + offHand.shoulderOffsetY(),
                        rig.shoulderZ() + pose.z() + offHand.shoulderOffsetZ()),
                armRig,
                (float) Math.toRadians(pose.offHandRoll()));

        // The off hand's range on the shaft: a hand gap ahead of the main grip, short of the top end.
        float pixelsPerSkinPixel = armRig.thickness() / swing.rig().weaponScale();
        WeaponGeometry weapon = swing.weapon();
        float low = weapon.gripCenter().y + OFF_HAND_GAP_PIXELS * pixelsPerSkinPixel;
        float high = weapon.handleTop() - OFF_HAND_END_PIXELS * pixelsPerSkinPixel;
        if (low > high) {
            low = high = Math.max(weapon.handleBottom(), Math.min(weapon.handleTop(), center.get().y));
        }
        float wanted = Math.max(low, Math.min(high, center.get().y + pose.offHandSlide()));
        float gripY = reachable(shaft, wanted, low, high);

        Grasp grasp = shaft.grasp(gripY);
        Vector3f wrist = grasp.wrist;
        Vector3f thumb = grasp.thumb;
        Vector3f hand = grasp.hand;
        Vector3f palm = grasp.palm;
        if (hold < 1.0F) {
            // Let go: the wrist travels toward the rig's rest (by default beside the body) and the
            // fist turns with it.
            Vector3f restHand = offHand.restDirection();
            Vector3f restWrist = new Vector3f(shaft.shoulder)
                    .add(new Vector3f(restHand).mul(offHand.restReach() * (armRig.upperArm() + armRig.forearm())));
            Vector3f restThumb = perpendicular(new Vector3f(0.0F, 0.0F, -1.0F), restHand);
            Quaternionf rotation = basis(restThumb, restHand).slerp(basis(thumb, hand), hold);
            wrist = new Vector3f(restWrist).lerp(wrist, hold);
            thumb = rotation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
            hand = rotation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
            palm = rotation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        }
        Candidate solved = bones(shaft.shoulder, armRig, wrist, thumb, hand, palm, pose.offHandElbow(), null);
        Vector3f grip = new Vector3f(wrist).add(new Vector3f(hand).mul(wristToGrip(armRig)));
        Solution mirrored = solved.toSolution(
                new Frame(grip, shaft.blade, shaft.shoulder, armRig), pose.offHandRoll(), pose.offHandElbow(), 0.0F);
        Solution solution = mainArm == HumanoidArm.RIGHT ? mirrored.mirrored() : mirrored;
        return Optional.of(new OffHandSolution(solution, gripY, wanted, hold));
    }

    /**
     * The shaft point nearest {@code wanted} (model pixels up the handle) whose wrist the off arm
     * can reach without moving its shoulder; when none can be reached, the point that needs the
     * least shoulder movement.
     */
    private static float reachable(Shaft shaft, float wanted, float low, float high) {
        if (shaft.overreach(wanted) <= 0.0F || high <= low) {
            return wanted;
        }
        float step = (high - low) / OFF_HAND_REACH_SAMPLES;
        float best = Float.NaN;
        float leastOverreach = Float.POSITIVE_INFINITY;
        float leastAt = wanted;
        for (int index = 0; index <= OFF_HAND_REACH_SAMPLES; index++) {
            float y = low + step * index;
            float overreach = shaft.overreach(y);
            if (overreach <= 0.0F && (Float.isNaN(best) || Math.abs(y - wanted) < Math.abs(best - wanted))) {
                best = y;
            }
            if (overreach < leastOverreach) {
                leastOverreach = overreach;
                leastAt = y;
            }
        }
        if (Float.isNaN(best)) {
            return leastAt;
        }
        // Refine toward the wanted point between the reachable sample and its unreachable neighbour.
        float reached = best;
        float missed = best + Math.copySign(Math.min(step, Math.abs(wanted - best)), wanted - best);
        for (int bisection = 0; bisection < OFF_HAND_REACH_BISECTIONS; bisection++) {
            float middle = (reached + missed) * 0.5F;
            if (shaft.overreach(middle) <= 0.0F) {
                reached = middle;
            } else {
                missed = middle;
            }
        }
        return reached;
    }

    private static Vector3f mirror(Vector3f vector) {
        return new Vector3f(-vector.x, vector.y, vector.z);
    }

    /**
     * The weapon's shaft seen from the off arm, in the mirror image of the right-handed scene
     * (where the off arm is a right arm): {@code blade} and {@code edge} are the mirrored grip-frame
     * axes and {@code shoulder} the off shoulder.
     */
    private record Shaft(
            Matrix4f gripFrame,
            WeaponGeometry weapon,
            float weaponScale,
            Vector3f blade,
            Vector3f edge,
            Vector3f shoulder,
            FirstPersonSwing.Arm arm,
            float roll) {
        Vector3f point(float y) {
            return mirror(gripFrame.transformPosition(weapon.axisPoint(y, weaponScale)));
        }

        /** The off fist closed on the shaft at model height {@code y}, thumb toward the tip. */
        Grasp grasp(float y) {
            Vector3f grip = point(y);
            Vector3f reach = perpendicular(new Vector3f(grip).sub(shoulder), blade);
            if (reach == null) {
                reach = perpendicular(edge, blade);
            }
            Vector3f handRoll = reach.rotateAxis(roll, blade.x, blade.y, blade.z);
            float diagonal = (float) Math.toRadians(arm.gripDiagonal());
            float cos = (float) Math.cos(diagonal);
            float sin = (float) Math.sin(diagonal);
            Vector3f thumb = new Vector3f(blade).mul(cos).sub(new Vector3f(handRoll).mul(sin));
            Vector3f hand = new Vector3f(blade).mul(sin).add(new Vector3f(handRoll).mul(cos));
            Vector3f palm = new Vector3f(thumb).cross(hand);
            Vector3f wrist = new Vector3f(grip).sub(new Vector3f(hand).mul(wristToGrip(arm)));
            return new Grasp(wrist, thumb, hand, palm);
        }

        /** How far (blocks) the wrist at {@code y} lies outside the arm's reach; 0 or less when inside. */
        float overreach(float y) {
            float distance = grasp(y).wrist.distance(shoulder);
            float total = arm.upperArm() + arm.forearm();
            return Math.max(distance - REACH_FRACTION * total, MINIMUM_REACH_FRACTION * total - distance);
        }
    }

    private record Grasp(Vector3f wrist, Vector3f thumb, Vector3f hand, Vector3f palm) {
    }

    /**
     * @param arm the off arm, in the same hand-render space as the main arm
     * @param gripY model height on the shaft where the off hand holds (after the reach slide)
     * @param wantedGripY model height the pose asked for ({@code off_hand_grip_center} plus slide,
     *     within the hand's range)
     * @param hold how firmly the hand is on the shaft: 1 holds, below 1 it is moving to its rest
     */
    record OffHandSolution(Solution arm, float gripY, float wantedGripY, float hold) {
        boolean slid() {
            return Math.abs(gripY - wantedGripY) > 1.0E-3F;
        }
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
     *     (toward the collar), +Z palm
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
