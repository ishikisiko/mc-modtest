package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.CombatTestData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The free off hand ({@code rig.off_hand.free}, the gauntlet's bare guard hand), the keyed rest
 * ({@code off_hand_rest}, {@code off_hand_reach}) and {@code grip_diagonal} 90 (the worn weapon
 * along the hand), on the shipped Xuantie gauntlet rig and contract.
 */
final class FirstPersonFreeOffHandTest {
    private static final Path RIG = CombatTestData.assetPath(CombatTestData.xuantie().firstPersonRig());
    private static final Path GEOMETRY = CombatTestData.assetPath(CombatTestData.xuantie().geometry());
    private static final float EPSILON = 1.0E-4F;

    @Test
    void freeOffHandNeedsNoOffHandGripAndIsAlwaysDrawnAtItsKeyedRest() throws IOException {
        FirstPersonSwing swing = parse(rigJson());
        WeaponGeometry geometry = geometry();
        assertTrue(geometry.offHandGripCenter().isEmpty(), "the gauntlet contract has no off-hand grip");
        assertTrue(swing.rig().offHand().free());
        for (FirstPersonSwing.Move move : swing.moves()) {
            for (float tick = 0.0F; tick <= move.totalTicks(); tick += 0.5F) {
                FirstPersonSwing.Pose pose = move.sample(tick);
                for (HumanoidArm arm : HumanoidArm.values()) {
                    FirstPersonArmIk.OffHandSolution off =
                            FirstPersonArmIk.solveOffHand(arm, 0.0F, swing, pose).orElseThrow();
                    assertTrue(off.free());
                    assertFalse(off.slid());
                    FirstPersonArmIk.Solution solution = off.arm();
                    // The wrist sits at the keyed rest from the off shoulder.
                    FirstPersonSwing.Arm offArm = swing.rig().offArm();
                    float length = offArm.upperArm() + offArm.forearm();
                    assertEquals(pose.offHandReach() * length, solution.wrist().distance(solution.shoulder()),
                            1.0E-3F, move.id() + " at " + tick);
                    // The off arm is on the other side of the view from the main arm.
                    FirstPersonArmIk.Solution main = FirstPersonArmIk.solve(arm, 0.0F, swing, pose);
                    assertTrue(Math.signum(solution.shoulder().x) != Math.signum(main.shoulder().x));
                }
            }
        }
    }

    @Test
    void keyedRestMovesTheHandAndInterpolates() throws IOException {
        FirstPersonSwing swing = parse(rigJson());
        FirstPersonSwing.Move punch = swing.move(0);
        FirstPersonSwing.Pose guard = punch.sample(0.0F);
        FirstPersonSwing.Pose pulled = punch.sample(punch.contactTick());
        assertNotEquals(guard.offHandRest(), pulled.offHandRest(), "the punch pulls the guard hand back");
        Vector3f guardWrist = solve(swing, guard).arm().wrist();
        Vector3f pulledWrist = solve(swing, pulled).arm().wrist();
        assertTrue(pulledWrist.y < guardWrist.y, "the pulled-back hand drops below the guard");
        // Between keys the rest interpolates component-wise.
        FirstPersonSwing.Pose halfway = FirstPersonSwing.Pose.interpolate(guard, pulled, 0.5F);
        assertEquals((guard.offHandReach() + pulled.offHandReach()) * 0.5F, halfway.offHandReach(), EPSILON);
    }

    @Test
    void neutralRestDefaultsToTheBlockAndRestFieldsAreChecked() throws IOException {
        JsonObject json = rigJson();
        FirstPersonSwing swing = parse(json);
        assertEquals(swing.rig().offHand().restDirection(), swing.neutral().offHandRest());
        assertEquals(swing.rig().offHand().restReach(), swing.neutral().offHandReach(), EPSILON);

        JsonObject zero = rigJson();
        JsonArray none = new JsonArray();
        none.add(0.0F);
        none.add(0.0F);
        none.add(0.0F);
        zero.getAsJsonObject("neutral").add("off_hand_rest", none);
        assertThrows(IllegalArgumentException.class, () -> parse(zero));
        JsonObject far = rigJson();
        far.getAsJsonObject("neutral").addProperty("off_hand_reach", 1.2F);
        assertThrows(IllegalArgumentException.class, () -> parse(far));
    }

    @Test
    void anOffHandOnTheShaftStillNeedsTheContractPoint() throws IOException {
        JsonObject json = rigJson();
        json.getAsJsonObject("rig").getAsJsonObject("off_hand").addProperty("free", false);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parse(json));
        assertTrue(error.getMessage().contains("off_hand_grip_center"), error.getMessage());
    }

    @Test
    void gripDiagonalNinetyLaysTheHandAlongTheWeapon() throws IOException {
        FirstPersonSwing swing = parse(rigJson());
        assertEquals(90.0F, swing.rig().arm().gripDiagonal());
        FirstPersonSwing.Pose pose = swing.move(0).sample(swing.move(0).contactTick());
        FirstPersonArmIk.Solution main = FirstPersonArmIk.solve(HumanoidArm.RIGHT, 0.0F, swing, pose);
        Vector3f blade = FirstPersonWeaponTransform.gripFrame(HumanoidArm.RIGHT, 0.0F, swing.rig(), pose)
                .transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
        Vector3f hand = main.fistRotation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        assertEquals(1.0F, hand.dot(blade), 1.0E-3F, "wrist to knuckles runs along the gauntlet's punch axis");
        assertEquals(FirstPersonArmIk.wristToGrip(swing.rig().arm()), main.grip().distance(main.wrist()), EPSILON);
        JsonObject tooFar = rigJson();
        tooFar.getAsJsonObject("rig").getAsJsonObject("arm").addProperty("grip_diagonal", 95.0F);
        assertThrows(IllegalArgumentException.class, () -> parse(tooFar));
    }

    private static FirstPersonArmIk.OffHandSolution solve(FirstPersonSwing swing, FirstPersonSwing.Pose pose) {
        return FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, swing, pose).orElseThrow();
    }

    private static FirstPersonSwing parse(JsonObject json) throws IOException {
        return FirstPersonSwing.parse(json, CombatTestData.basicFist(), geometry());
    }

    private static WeaponGeometry geometry() throws IOException {
        return WeaponGeometry.parse(JsonParser.parseString(Files.readString(GEOMETRY)).getAsJsonObject());
    }

    private static JsonObject rigJson() throws IOException {
        return JsonParser.parseString(Files.readString(RIG)).getAsJsonObject();
    }
}
