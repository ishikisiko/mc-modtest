package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.CombatTestData;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * A paired weapon's second on the free off hand (0.39.1, {@link FirstPersonWeaponTransform#pairedItem}),
 * on the shipped Xuantie gauntlet rig and contract: the gauntlet's grip on the off fist's grip, its
 * axes on the fist's (palm, wrist to knuckles, away from the thumb), scaled to the off fist, and
 * mirrored exactly when the off hand is the left one.
 */
final class FirstPersonPairedHandTest {
    private static final Path RIG = CombatTestData.assetPath(CombatTestData.xuantie().firstPersonRig());
    private static final Path GEOMETRY = CombatTestData.assetPath(CombatTestData.xuantie().geometry());
    private static final float EPSILON = 1.0E-4F;

    @Test
    void theGauntletIsAPairWithAFreeOffHand() throws IOException {
        assertTrue(CombatTestData.xuantie().paired());
        assertFalse(CombatTestData.qingfeng().paired());
        assertFalse(CombatTestData.lingxiao().paired());
        assertTrue(swing().rig().offHand().free());
    }

    @Test
    void theSecondSitsOnTheOffFistMirroredOnTheLeftHand() throws IOException {
        FirstPersonSwing swing = swing();
        FirstPersonSwing.Rig rig = swing.rig();
        float scale = rig.weaponScale() * rig.offArm().thickness() / rig.arm().thickness();
        Vector3f grip = swing.weapon().gripCenter().div(16.0F);
        for (FirstPersonSwing.Move move : swing.moves()) {
            for (float tick = 0.0F; tick <= move.totalTicks(); tick += 0.5F) {
                FirstPersonSwing.Pose pose = move.sample(tick);
                for (HumanoidArm mainArm : HumanoidArm.values()) {
                    String where = move.id() + " at " + tick + " with a " + mainArm + " main arm";
                    FirstPersonArmIk.Solution off =
                            FirstPersonArmIk.solveOffHand(mainArm, 0.0F, swing, pose).orElseThrow().arm();
                    Matrix4f placement = FirstPersonWeaponTransform.pairedItem(mainArm, swing, off);
                    // The contract's grip centre lands on the off fist's grip point.
                    assertTrue(placement.transformPosition(new Vector3f(grip)).distance(off.grip()) < EPSILON, where);
                    // A left off hand wears the mirror image (determinant < 0), a right one the model as is.
                    boolean left = mainArm == HumanoidArm.RIGHT;
                    assertEquals(left, placement.determinant() < 0.0F, where);
                    Vector3f thumb = off.fistRotation().transform(new Vector3f(1.0F, 0.0F, 0.0F));
                    Vector3f hand = off.fistRotation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
                    Vector3f palm = off.fistRotation().transform(new Vector3f(0.0F, 0.0F, 1.0F));
                    Vector3f modelX = placement.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F));
                    Vector3f modelY = placement.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
                    Vector3f modelZ = placement.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F));
                    for (Vector3f axis : new Vector3f[] {modelX, modelY, modelZ}) {
                        assertEquals(scale, axis.length(), EPSILON, where + " scale");
                    }
                    // Palm on the palm, knuckles along the hand; +Z toward the little finger, which
                    // the reflected (left) fist frame carries as its +X.
                    assertTrue(new Vector3f(modelX).normalize().distance(palm) < EPSILON, where + " palm");
                    assertTrue(new Vector3f(modelY).normalize().distance(hand) < EPSILON, where + " hand");
                    Vector3f little = left ? thumb : new Vector3f(thumb).negate();
                    assertTrue(new Vector3f(modelZ).normalize().distance(little) < EPSILON, where + " little finger");
                }
            }
        }
    }

    @Test
    void leftAndRightMainArmsGiveMirrorImagePlacements() throws IOException {
        FirstPersonSwing swing = swing();
        FirstPersonSwing.Pose pose = swing.move(4).sample(swing.move(4).contactTick());
        Matrix4f right = FirstPersonWeaponTransform.pairedItem(HumanoidArm.RIGHT, swing,
                FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, swing, pose).orElseThrow().arm());
        Matrix4f left = FirstPersonWeaponTransform.pairedItem(HumanoidArm.LEFT, swing,
                FirstPersonArmIk.solveOffHand(HumanoidArm.LEFT, 0.0F, swing, pose).orElseThrow().arm());
        Matrix4f reflected = new Matrix4f().scale(-1.0F, 1.0F, 1.0F).mul(left);
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 3; row++) {
                assertEquals(right.get(column, row), reflected.get(column, row), EPSILON, column + "," + row);
            }
        }
    }

    private static FirstPersonSwing swing() throws IOException {
        WeaponGeometry geometry = WeaponGeometry.parse(
                JsonParser.parseString(Files.readString(GEOMETRY)).getAsJsonObject());
        return FirstPersonSwing.parse(
                JsonParser.parseString(Files.readString(RIG)).getAsJsonObject(), CombatTestData.basicFist(), geometry);
    }
}
