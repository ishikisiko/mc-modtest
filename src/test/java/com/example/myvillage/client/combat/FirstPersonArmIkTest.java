package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.CombatTestData;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The first-person arm on the shipped rig: every move, both main arms, every eighth of a tick,
 * with and without the wrist lag. The fist must close around the handle (guard and pommel
 * outside), the wrist must stay anatomical, the arm must never meet the blade, and nothing may pop.
 */
final class FirstPersonArmIkTest {
    private static final Path RIG = CombatTestData.assetPath(CombatTestData.qingfeng().firstPersonRig());
    private static final float EPSILON = 1.0E-4F;
    private static final float STEP = 0.125F;
    /** Handle length (model pixels either side of grip_center) the fist must cover even for slim skins. */
    private static final float COVERED_HANDLE_PIXELS = 1.2F;
    private static final float BLADE_CLEARANCE = 0.03F;

    @Test
    void wristStaysAnatomicalAndBonesKeepTheirLength() throws IOException {
        FirstPersonSwing swing = shipped();
        for (HumanoidArm arm : HumanoidArm.values()) {
            for (FirstPersonSwing.Move move : swing.moves()) {
                for (float tick = 0.0F; tick <= move.totalTicks(); tick += STEP) {
                    FirstPersonSwing.Pose pose = move.sample(tick);
                    FirstPersonArmIk.Solution still = FirstPersonArmIk.solve(arm, 0.0F, swing, pose);
                    assertFalse(still.clamped(), move.id() + " " + arm + " overstretches at " + tick);
                    FirstPersonArmIk.Solution lagged = FirstPersonArmIk.solve(
                            arm, 0.0F, swing, pose, FirstPersonArmLag.offset(swing, move, tick));
                    Vector3f grip = FirstPersonWeaponTransform.gripFrame(arm, 0.0F, swing.rig(), pose)
                            .getTranslation(new Vector3f());
                    for (FirstPersonArmIk.Solution solution : List.of(still, lagged)) {
                        String where = move.id() + " " + arm + " at " + tick;
                        assertTrue(FirstPersonArmIk.withinLimits(solution.flexion(), solution.deviation()),
                                where + " wrist flexion " + solution.flexion() + " deviation " + solution.deviation());
                        assertBones(swing, solution, where);
                        assertEquals(0.0F, solution.grip().distance(grip), EPSILON, where);
                    }
                }
            }
        }
    }

    @Test
    void fistClosesAroundTheHandleWithGuardAndPommelOutside() throws IOException {
        FirstPersonSwing swing = shipped();
        WeaponGeometry sword = swing.weapon();
        float pixel = swing.rig().arm().thickness() / 16.0F;
        Box slimFist = fistBox(pixel, 3.0F);
        Box wideFist = fistBox(pixel, 4.0F);
        for (FirstPersonSwing.Move move : swing.moves()) {
            for (float tick = 0.0F; tick <= move.totalTicks(); tick += STEP) {
                FirstPersonSwing.Pose pose = move.sample(tick);
                FirstPersonArmIk.Solution solution = FirstPersonArmIk.solve(
                        HumanoidArm.RIGHT, 0.0F, swing, pose, FirstPersonArmLag.offset(swing, move, tick));
                String where = move.id() + " at " + tick;
                for (int step = -3; step <= 3; step++) {
                    float along = COVERED_HANDLE_PIXELS * step / 3.0F;
                    Vector3f handle = weaponPoint(swing, pose, axis(sword, sword.gripCenter().y + along));
                    assertTrue(slimFist.contains(solution.wrist(), solution.fistRotation(), handle),
                            where + " handle leaves the fist " + along + " px from the grip");
                }
                float pommel = (sword.buttBottom() + sword.buttTop()) * 0.5F;
                assertFalse(wideFist.contains(solution.wrist(), solution.fistRotation(),
                        weaponPoint(swing, pose, axis(sword, pommel))), where + " pommel buried in the fist");
                for (float x : new float[] {-sword.collarHalfThickness(), sword.collarHalfThickness()}) {
                    for (float z : new float[] {-sword.collarHalfWidth(), sword.collarHalfWidth()}) {
                        for (float y : new float[] {sword.collarBottom(), sword.collarTop()}) {
                            Vector3f corner = weaponPoint(swing, pose, new Vector3f(
                                    sword.gripCenter().x + x, y, sword.gripCenter().z + z));
                            assertTrue(wideFist.distance(solution.wrist(), solution.fistRotation(), corner) > 0.01F,
                                    where + " guard sinks into the fist");
                        }
                    }
                }
            }
        }
    }

    @Test
    void forearmAndUpperArmNeverCrossTheBlade() throws IOException {
        FirstPersonSwing swing = shipped();
        WeaponGeometry sword = swing.weapon();
        FirstPersonSwing.Arm armRig = swing.rig().arm();
        float pixel = armRig.thickness() / 16.0F;
        float half = 2.0F * pixel;
        float forearmHalf = half * FirstPersonArmRenderer.FOREARM_WIDTH;
        Box forearm = new Box(forearmHalf,
                -FirstPersonArmRenderer.ELBOW_OVERLAP_PIXELS * pixel,
                armRig.forearm() + FirstPersonArmRenderer.WRIST_OVERLAP_PIXELS * pixel,
                forearmHalf);
        Box upper = new Box(half, 0.0F, armRig.upperArm() + pixel, half);
        for (FirstPersonSwing.Move move : swing.moves()) {
            for (float tick = 0.0F; tick <= move.totalTicks(); tick += STEP) {
                FirstPersonSwing.Pose pose = move.sample(tick);
                FirstPersonArmIk.Solution solution = FirstPersonArmIk.solve(
                        HumanoidArm.RIGHT, 0.0F, swing, pose, FirstPersonArmLag.offset(swing, move, tick));
                for (int step = 0; step <= 20; step++) {
                    Vector3f point = weaponPoint(swing, pose, sword.headBase().lerp(sword.headTip(), step / 20.0F));
                    String where = move.id() + " at " + tick + " blade point " + step;
                    assertTrue(forearm.distance(solution.elbow(), solution.forearmRotation(), point) > BLADE_CLEARANCE,
                            where + " passes through the forearm");
                    assertTrue(upper.distance(solution.shoulder(), solution.upperArmRotation(), point) > BLADE_CLEARANCE,
                            where + " passes through the upper arm");
                }
            }
        }
    }

    @Test
    void handAndForearmMoveWithoutPopping() throws IOException {
        FirstPersonSwing swing = shipped();
        for (FirstPersonSwing.Move move : swing.moves()) {
            Quaternionf previousHand = null;
            Vector3f previousForearm = null;
            for (float tick = 0.0F; tick <= move.totalTicks(); tick += STEP) {
                FirstPersonSwing.Pose pose = move.sample(tick);
                FirstPersonArmIk.Solution solution = FirstPersonArmIk.solve(
                        HumanoidArm.RIGHT, 0.0F, swing, pose, FirstPersonArmLag.offset(swing, move, tick));
                // The hand's turn on the handle, in the sword's own frame.
                Quaternionf sword = FirstPersonWeaponTransform.gripFrame(HumanoidArm.RIGHT, 0.0F, swing.rig(), pose)
                        .getNormalizedRotation(new Quaternionf());
                Quaternionf hand = new Quaternionf(sword).conjugate().mul(solution.fistRotation());
                Vector3f forearm = new Vector3f(solution.wrist()).sub(solution.elbow()).normalize();
                if (previousHand != null) {
                    float handTurn = angleDegrees(previousHand, hand);
                    float forearmTurn = (float) Math.toDegrees(Math.acos(Math.min(1.0F, forearm.dot(previousForearm))));
                    assertTrue(handTurn <= 12.0F, move.id() + " hand spins " + handTurn + " deg on the handle at " + tick);
                    assertTrue(forearmTurn <= 25.0F, move.id() + " forearm snaps " + forearmTurn + " deg at " + tick);
                }
                previousHand = hand;
                previousForearm = forearm;
            }
        }
    }

    @Test
    void cutsTrailTheArmThenFollowThrough() throws IOException {
        FirstPersonSwing swing = shipped();
        for (int index = 1; index <= 3; index++) {
            FirstPersonSwing.Move move = swing.move(index);
            float contact = move.contactTick();
            Vector3f direction = grip(swing, move.sample(contact + 0.25F))
                    .sub(grip(swing, move.sample(contact - 0.25F)))
                    .normalize();
            float atContact = FirstPersonArmLag.offset(swing, move, contact).dot(direction);
            assertTrue(atContact < -0.02F, move.id() + " arm does not trail the strike: " + atContact);
            float followThrough = 0.0F;
            for (float tick = move.strikeEndTick() - 1.0F; tick <= move.strikeEndTick() + 2.5F; tick += 0.25F) {
                followThrough = Math.max(followThrough, FirstPersonArmLag.offset(swing, move, tick).dot(direction));
            }
            assertTrue(followThrough > 0.02F, move.id() + " no follow-through past the grip: " + followThrough);
        }
        for (FirstPersonSwing.Move move : swing.moves()) {
            assertEquals(0.0F, FirstPersonArmLag.offset(swing, move, 0.0F).length(), EPSILON, move.id() + " start");
            assertEquals(0.0F, FirstPersonArmLag.offset(swing, move, move.totalTicks()).length(), EPSILON,
                    move.id() + " end");
            for (float tick = 0.0F; tick <= move.totalTicks(); tick += 0.5F) {
                assertTrue(FirstPersonArmLag.offset(swing, move, tick).length() <= FirstPersonArmLag.CAP + EPSILON);
            }
        }
    }

    @Test
    void leftArmMirrorsTheRightArm() throws IOException {
        FirstPersonSwing swing = shipped();
        FirstPersonSwing.Move move = swing.move(3);
        FirstPersonSwing.Pose pose = move.sample(7.0F);
        Vector3f lag = FirstPersonArmLag.offset(swing, move, 7.0F);
        FirstPersonArmIk.Solution right =
                FirstPersonArmIk.solve(HumanoidArm.RIGHT, 0.0F, swing, pose, lag);
        FirstPersonArmIk.Solution left =
                FirstPersonArmIk.solve(HumanoidArm.LEFT, 0.0F, swing, pose, lag);
        for (Vector3f[] pair : new Vector3f[][] {
                {right.shoulder(), left.shoulder()}, {right.elbow(), left.elbow()},
                {right.wrist(), left.wrist()}, {right.grip(), left.grip()}}) {
            assertEquals(pair[0].x, -pair[1].x, EPSILON);
            assertEquals(pair[0].y, pair[1].y, EPSILON);
            assertEquals(pair[0].z, pair[1].z, EPSILON);
        }
        assertEquals(right.flexion(), left.flexion(), EPSILON);
        assertEquals(right.deviation(), left.deviation(), EPSILON);
        Vector3f leftGrip = FirstPersonWeaponTransform.gripFrame(HumanoidArm.LEFT, 0.0F, swing.rig(), pose)
                .getTranslation(new Vector3f());
        assertEquals(0.0F, left.grip().distance(leftGrip), EPSILON);
        // Mirrored frames stay proper rotations, so face winding (and culling) is intact.
        for (Quaternionf rotation : List.of(left.upperArmRotation(), left.forearmRotation(), left.fistRotation())) {
            Vector3f x = rotation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
            Vector3f y = rotation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
            Vector3f z = rotation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
            assertEquals(0.0F, new Vector3f(x).cross(y).distance(z), EPSILON);
        }
    }

    @Test
    void neutralHoldKeepsTheArmLowAndRight() throws IOException {
        FirstPersonSwing swing = shipped();
        FirstPersonArmIk.Solution solution =
                FirstPersonArmIk.solve(HumanoidArm.RIGHT, 0.0F, swing, swing.neutral());
        assertTrue(solution.wrist().x > 0.1F && solution.wrist().y < -0.2F, "wrist " + solution.wrist());
        assertTrue(solution.elbow().x > solution.wrist().x && solution.elbow().y < solution.wrist().y,
                "elbow " + solution.elbow());
        assertTrue(Math.abs(solution.deviation()) <= 20.0F && Math.abs(solution.flexion()) <= 20.0F,
                "neutral wrist " + solution.flexion() + " / " + solution.deviation());
    }

    /** Writes each move's key-frame wrist angles (static and with lag) as build evidence. */
    @Test
    void recordsKeyFrameWristAngles() throws IOException {
        FirstPersonSwing swing = shipped();
        List<String> lines = new ArrayList<>();
        lines.add("move\ttick\tflexion\tdeviation\tflexion_lag\tdeviation_lag\tgrip_roll\telbow\tlag_scale");
        for (FirstPersonSwing.Move move : swing.moves()) {
            for (FirstPersonSwing.Key key : move.keys()) {
                FirstPersonSwing.Pose pose = move.sample(key.tick());
                FirstPersonArmIk.Solution still =
                        FirstPersonArmIk.solve(HumanoidArm.RIGHT, 0.0F, swing, pose);
                FirstPersonArmIk.Solution lagged = FirstPersonArmIk.solve(
                        HumanoidArm.RIGHT, 0.0F, swing, pose, FirstPersonArmLag.offset(swing, move, key.tick()));
                lines.add(String.format(Locale.ROOT, "%s\t%.1f\t%.1f\t%.1f\t%.1f\t%.1f\t%.0f\t%.0f\t%.2f",
                        move.id().getPath(), key.tick(), still.flexion(), still.deviation(),
                        lagged.flexion(), lagged.deviation(), pose.gripRoll(), pose.elbow(), lagged.lagScale()));
            }
        }
        Path out = Path.of("build", "reports", "qingfeng_wrist_angles.tsv");
        Files.createDirectories(out.getParent());
        Files.write(out, lines);
        assertTrue(lines.size() > swing.moves().size());
    }

    private static void assertBones(FirstPersonSwing swing, FirstPersonArmIk.Solution solution, String where) {
        FirstPersonSwing.Arm arm = swing.rig().arm();
        assertEquals(arm.upperArm(), solution.elbow().distance(solution.shoulder()), EPSILON, where + " upper arm");
        assertEquals(arm.forearm(), solution.wrist().distance(solution.elbow()), EPSILON, where + " forearm");
        Vector3f palm = new Vector3f(solution.grip()).sub(solution.wrist());
        assertEquals(FirstPersonArmIk.wristToGrip(arm),
                solution.fistRotation().transform(new Vector3f(0.0F, 1.0F, 0.0F)).dot(palm), EPSILON, where + " palm");
    }

    private static Box fistBox(float pixel, float widthPixels) {
        return new Box(
                widthPixels * 0.5F * pixel * FirstPersonArmRenderer.FIST_WIDTH,
                -FirstPersonArmIk.FIST_OVERLAP_PIXELS * pixel,
                (FirstPersonArmIk.FIST_LENGTH_PIXELS - FirstPersonArmIk.FIST_OVERLAP_PIXELS) * pixel,
                FirstPersonArmIk.DEPTH_PIXELS * 0.5F * pixel * FirstPersonArmRenderer.FIST_WIDTH);
    }

    private static Vector3f axis(WeaponGeometry sword, float y) {
        return new Vector3f(sword.gripCenter().x, y, sword.gripCenter().z);
    }

    private static Vector3f weaponPoint(FirstPersonSwing swing, FirstPersonSwing.Pose pose, Vector3f modelPixels) {
        return FirstPersonWeaponTransform.weaponPoint(HumanoidArm.RIGHT, 0.0F, swing, pose, modelPixels);
    }

    private static Vector3f grip(FirstPersonSwing swing, FirstPersonSwing.Pose pose) {
        Matrix4f frame = FirstPersonWeaponTransform.gripFrame(HumanoidArm.RIGHT, 0.0F, swing.rig(), pose);
        return frame.getTranslation(new Vector3f());
    }

    private static float angleDegrees(Quaternionf from, Quaternionf to) {
        float dot = Math.abs(from.dot(to));
        return (float) Math.toDegrees(2.0 * Math.acos(Math.min(1.0F, dot)));
    }

    private static FirstPersonSwing shipped() throws IOException {
        return FirstPersonSwing.parse(
                JsonParser.parseString(Files.readString(RIG)).getAsJsonObject(),
                CombatTestData.basicSword(),
                FirstPersonSwingTest.geometry());
    }

    /** A segment box in its bone frame: |x| <= halfWidth, yMin <= y <= yMax, |z| <= halfDepth. */
    private record Box(float halfWidth, float yMin, float yMax, float halfDepth) {
        private Vector3f local(Vector3f origin, Quaternionf rotation, Vector3f point) {
            return new Quaternionf(rotation).conjugate().transform(new Vector3f(point).sub(origin));
        }

        boolean contains(Vector3f origin, Quaternionf rotation, Vector3f point) {
            Vector3f local = local(origin, rotation, point);
            return Math.abs(local.x) <= halfWidth && local.y >= yMin && local.y <= yMax
                    && Math.abs(local.z) <= halfDepth;
        }

        float distance(Vector3f origin, Quaternionf rotation, Vector3f point) {
            Vector3f local = local(origin, rotation, point);
            float dx = Math.max(0.0F, Math.abs(local.x) - halfWidth);
            float dy = Math.max(0.0F, Math.max(yMin - local.y, local.y - yMax));
            float dz = Math.max(0.0F, Math.abs(local.z) - halfDepth);
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }
}
