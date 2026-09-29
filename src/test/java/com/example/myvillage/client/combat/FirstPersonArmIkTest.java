package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class FirstPersonArmIkTest {
    private static final Path RIG = Path.of(
            "src/main/resources/assets/myvillage", FirstPersonSwing.RESOURCE_PATH);
    private static final float EPSILON = 1.0E-4F;
    private static final Vector3f SHOULDER = new Vector3f(0.30F, -0.34F, -0.10F);
    private static final Vector3f BLADE_UP_FORWARD = new Vector3f(0.0F, 0.5F, -0.866F).normalize();

    @Test
    void handLandsOnTheGripAndBonesKeepTheirLength() {
        Vector3f[] grips = {
                new Vector3f(0.10F, -0.30F, -0.55F),
                new Vector3f(-0.15F, -0.35F, -0.45F),
                new Vector3f(0.20F, -0.20F, -0.40F),
        };
        for (Vector3f grip : grips) {
            QingfengFirstPersonArmIk.Solution solution =
                    QingfengFirstPersonArmIk.solve(SHOULDER, grip, BLADE_UP_FORWARD, HumanoidArm.RIGHT);
            assertFalse(solution.clamped(), "grip " + grip + " is within reach");
            assertEquals(0.0F, solution.hand().distance(grip), EPSILON);
            assertEquals(0.0F, solution.shoulder().distance(SHOULDER), EPSILON);
            assertBones(solution);
        }
    }

    @Test
    void outOfReachGripMovesTheOffscreenShoulderNotTheHand() {
        Vector3f far = new Vector3f(0.0F, -0.30F, -0.95F);
        QingfengFirstPersonArmIk.Solution solution =
                QingfengFirstPersonArmIk.solve(SHOULDER, far, BLADE_UP_FORWARD, HumanoidArm.RIGHT);
        assertTrue(solution.clamped());
        assertEquals(0.0F, solution.hand().distance(far), EPSILON);
        assertEquals(QingfengFirstPersonArmIk.REACH_LIMIT, solution.shoulder().distance(far), EPSILON);
        assertBones(solution);

        Vector3f near = new Vector3f(0.30F, -0.34F, -0.15F);
        QingfengFirstPersonArmIk.Solution folded =
                QingfengFirstPersonArmIk.solve(SHOULDER, near, BLADE_UP_FORWARD, HumanoidArm.RIGHT);
        assertTrue(folded.clamped());
        assertEquals(0.0F, folded.hand().distance(near), EPSILON);
        assertEquals(QingfengFirstPersonArmIk.MINIMUM_REACH, folded.shoulder().distance(near), EPSILON);
        assertBones(folded);
    }

    @Test
    void elbowBendsDownAndOutwardForEitherMainArm() {
        Vector3f grip = new Vector3f(0.10F, -0.30F, -0.55F);
        QingfengFirstPersonArmIk.Solution right =
                QingfengFirstPersonArmIk.solve(SHOULDER, grip, BLADE_UP_FORWARD, HumanoidArm.RIGHT);
        Vector3f middle = new Vector3f(SHOULDER).add(grip).mul(0.5F);
        assertTrue(right.elbow().y < middle.y, "elbow should drop below the shoulder-grip line");
        assertTrue(right.elbow().x > middle.x, "right elbow should swing outward");

        Vector3f leftShoulder = new Vector3f(-SHOULDER.x, SHOULDER.y, SHOULDER.z);
        Vector3f leftGrip = new Vector3f(-grip.x, grip.y, grip.z);
        QingfengFirstPersonArmIk.Solution left =
                QingfengFirstPersonArmIk.solve(leftShoulder, leftGrip, BLADE_UP_FORWARD, HumanoidArm.LEFT);
        assertEquals(-right.elbow().x, left.elbow().x, EPSILON);
        assertEquals(right.elbow().y, left.elbow().y, EPSILON);
        assertEquals(right.elbow().z, left.elbow().z, EPSILON);
    }

    @Test
    void segmentFramesFollowTheBonesAndFaceTheBlade() {
        Vector3f grip = new Vector3f(0.05F, -0.32F, -0.50F);
        QingfengFirstPersonArmIk.Solution solution =
                QingfengFirstPersonArmIk.solve(SHOULDER, grip, BLADE_UP_FORWARD, HumanoidArm.RIGHT);
        Vector3f upperDirection = new Vector3f(solution.elbow()).sub(solution.shoulder()).normalize();
        Vector3f forearmDirection = new Vector3f(solution.hand()).sub(solution.elbow()).normalize();
        assertEquals(0.0F, solution.upperArmRotation().transform(new Vector3f(0.0F, 1.0F, 0.0F))
                .distance(upperDirection), EPSILON);
        assertEquals(0.0F, solution.forearmRotation().transform(new Vector3f(0.0F, 1.0F, 0.0F))
                .distance(forearmDirection), EPSILON);
        // The arm's front face (model -Z) turns toward the blade, so the handle crosses the fist.
        Vector3f front = solution.forearmRotation().transform(new Vector3f(0.0F, 0.0F, -1.0F));
        assertTrue(front.dot(BLADE_UP_FORWARD) > 0.0F);
        // Right-handed frames keep face winding (and culling) intact.
        Vector3f x = solution.forearmRotation().transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f y = solution.forearmRotation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f z = solution.forearmRotation().transform(new Vector3f(0.0F, 0.0F, 1.0F));
        assertEquals(0.0F, new Vector3f(x).cross(y).distance(z), EPSILON);
    }

    @Test
    void bladeAlongTheForearmStillGivesAFrame() {
        Vector3f grip = new Vector3f(0.10F, -0.30F, -0.55F);
        QingfengFirstPersonArmIk.Solution probe =
                QingfengFirstPersonArmIk.solve(SHOULDER, grip, BLADE_UP_FORWARD, HumanoidArm.RIGHT);
        Vector3f alongForearm = new Vector3f(probe.hand()).sub(probe.elbow()).normalize();
        QingfengFirstPersonArmIk.Solution solution =
                QingfengFirstPersonArmIk.solve(SHOULDER, grip, alongForearm, HumanoidArm.RIGHT);
        Vector3f y = solution.forearmRotation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        assertTrue(Float.isFinite(y.x) && Float.isFinite(y.y) && Float.isFinite(y.z));
        assertEquals(1.0F, y.length(), EPSILON);
    }

    @Test
    void shippedRigNeverOverstretchesTheArmOrBendsTheWristBackward() throws IOException {
        FirstPersonSwing swing = FirstPersonSwing.parse(
                JsonParser.parseString(Files.readString(RIG)).getAsJsonObject(), BasicSwordStyle.DEFINITION);
        float wristLimit = (float) Math.sin(Math.toRadians(-35.0));
        for (HumanoidArm arm : HumanoidArm.values()) {
            for (FirstPersonSwing.Move move : swing.moves()) {
                for (int sample = 0; sample <= move.totalTicks() * 4; sample++) {
                    float tick = sample / 4.0F;
                    FirstPersonSwing.Pose pose = move.sample(tick);
                    QingfengFirstPersonArmIk.Solution solution =
                            QingfengFirstPersonArmIk.solve(arm, 0.0F, swing.rig(), pose);
                    assertFalse(solution.clamped(), move.id() + " " + arm + " overstretches at " + tick);
                    Vector3f grip = FirstPersonSwordTransform.gripFrame(arm, 0.0F, swing.rig(), pose)
                            .getTranslation(new Vector3f());
                    assertEquals(0.0F, solution.hand().distance(grip), EPSILON);
                    assertBones(solution);
                    Vector3f middle = new Vector3f(solution.shoulder()).add(solution.hand()).mul(0.5F);
                    assertTrue(solution.elbow().y < middle.y, move.id() + " elbow rises at " + tick);
                    Vector3f blade = FirstPersonSwordTransform.gripFrame(arm, 0.0F, swing.rig(), pose)
                            .transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
                    Vector3f forearm = new Vector3f(solution.hand()).sub(solution.elbow()).normalize();
                    assertTrue(blade.dot(forearm) >= wristLimit,
                            move.id() + " blade points back along the forearm at " + tick);
                }
            }
        }
    }

    private static void assertBones(QingfengFirstPersonArmIk.Solution solution) {
        assertEquals(QingfengFirstPersonArmIk.UPPER_ARM_LENGTH,
                solution.elbow().distance(solution.shoulder()), EPSILON);
        assertEquals(QingfengFirstPersonArmIk.FOREARM_LENGTH,
                solution.hand().distance(solution.elbow()), EPSILON);
        assertTrue(solution.hand().distance(solution.shoulder()) <= QingfengFirstPersonArmIk.REACH_LIMIT + EPSILON);
    }
}
