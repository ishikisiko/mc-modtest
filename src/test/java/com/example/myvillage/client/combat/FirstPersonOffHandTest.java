package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.CombatTestData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The optional off arm ({@code rig.off_hand}): parsing and defaults, interpolation, the hand
 * locked onto the shaft, the mirror for a left main arm, the reach slide, letting go, and that a
 * rig without the block is untouched. The rig is the shipped Qingfeng rig on the two-handed spear
 * contract with a spear-like neutral hold (its sword keys stress the reach slide), so the tests do
 * not depend on a spear rig being authored yet; the shipped spear rig is checked once it has the block.
 */
final class FirstPersonOffHandTest {
    private static final Path SWORD_RIG = CombatTestData.assetPath(CombatTestData.qingfeng().firstPersonRig());
    private static final Path SWORD_GEOMETRY = CombatTestData.assetPath(CombatTestData.qingfeng().geometry());
    private static final Path SPEAR_GEOMETRY = CombatTestData.assetPath(CombatTestData.lingxiao().geometry());
    private static final float EPSILON = 1.0E-4F;
    private static final float STEP = 0.25F;

    @Test
    void rigWithoutTheBlockHasNoOffHandAndDefaultOffHandPoseValues() throws IOException {
        FirstPersonSwing sword = parse(rigJson(), swordGeometry());
        assertNull(sword.rig().offHand());
        for (FirstPersonSwing.Move move : sword.moves()) {
            for (float tick = 0.0F; tick <= move.totalTicks(); tick += STEP) {
                FirstPersonSwing.Pose pose = move.sample(tick);
                assertEquals(0.0F, pose.offHandSlide());
                assertEquals(0.0F, pose.offHandRoll());
                assertEquals(0.0F, pose.offHandElbow());
                assertEquals(1.0F, pose.offHandHold());
                assertTrue(FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, sword, pose).isEmpty());
            }
        }
        // The same rig on the spear contract, without the block, still draws one arm.
        assertTrue(FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, parse(rigJson(), spearGeometry()),
                parse(rigJson(), spearGeometry()).neutral()).isEmpty());
    }

    @Test
    void theOffHandNeverChangesTheMainArm() throws IOException {
        JsonObject withoutBlock = twoHandedJson();
        withoutBlock.getAsJsonObject("rig").remove("off_hand");
        FirstPersonSwing one = parse(withoutBlock, spearGeometry());
        FirstPersonSwing two = twoHanded();
        assertNull(one.rig().offHand());
        for (HumanoidArm arm : HumanoidArm.values()) {
            for (int index = 0; index < one.moves().size(); index++) {
                FirstPersonSwing.Move move = one.move(index);
                for (float tick = 0.0F; tick <= move.totalTicks(); tick += STEP) {
                    Vector3f lag = FirstPersonArmLag.offset(one, move, tick);
                    FirstPersonArmIk.Solution a = FirstPersonArmIk.solve(arm, 0.0F, one, move.sample(tick), lag);
                    FirstPersonArmIk.Solution b = FirstPersonArmIk.solve(
                            arm, 0.0F, two, two.move(index).sample(tick), FirstPersonArmLag.offset(two, two.move(index), tick));
                    assertEquals(a, b, move.id() + " " + arm + " at " + tick);
                }
            }
        }
    }

    @Test
    void offHandOnAOneHandedWeaponIsARigError() throws IOException {
        JsonObject json = twoHandedJson();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parse(json, swordGeometry()));
        assertTrue(error.getMessage().contains("off_hand_grip_center"), error.getMessage());
    }

    @Test
    void blockFieldsDefaultToTheMainArmAndAreChecked() throws IOException {
        JsonObject empty = rigJson();
        empty.getAsJsonObject("rig").add("off_hand", new JsonObject());
        FirstPersonSwing swing = parse(empty, spearGeometry());
        FirstPersonSwing.Arm arm = swing.rig().arm();
        FirstPersonSwing.OffHand offHand = swing.rig().offHand();
        assertEquals(arm.shoulderOffsetX(), offHand.shoulderOffsetX());
        assertEquals(arm.shoulderOffsetY(), offHand.shoulderOffsetY());
        assertEquals(arm.shoulderOffsetZ(), offHand.shoulderOffsetZ());
        assertEquals(arm.gripDiagonal(), offHand.gripDiagonal());

        JsonObject tuned = twoHandedJson();
        JsonObject block = tuned.getAsJsonObject("rig").getAsJsonObject("off_hand");
        block.add("shoulder_offset", vector(0.05F, -0.03F, 0.02F));
        block.addProperty("grip_diagonal", 20.0F);
        block.addProperty("unknown_field", 3.0F); // ignored, like unknown fields elsewhere in the rig
        FirstPersonSwing.OffHand parsed = parse(tuned, spearGeometry()).rig().offHand();
        assertEquals(new FirstPersonSwing.OffHand(0.05F, -0.03F, 0.02F, 20.0F), parsed);

        JsonObject steep = twoHandedJson();
        steep.getAsJsonObject("rig").getAsJsonObject("off_hand").addProperty("grip_diagonal", 70.0F);
        assertThrows(IllegalArgumentException.class, () -> parse(steep, spearGeometry()));
        JsonObject shortOffset = twoHandedJson();
        shortOffset.getAsJsonObject("rig").getAsJsonObject("off_hand").add("shoulder_offset", new JsonArray());
        assertThrows(IllegalArgumentException.class, () -> parse(shortOffset, spearGeometry()));
    }

    @Test
    void keyFieldsAreParsedInheritedInterpolatedAndChecked() throws IOException {
        JsonObject json = twoHandedJson();
        JsonArray keys = firstMove(json).getAsJsonArray("keys");
        JsonObject first = keys.get(1).getAsJsonObject();
        first.addProperty("off_hand_slide", -4.0F);
        first.addProperty("off_hand_roll", 25.0F);
        first.addProperty("off_hand_elbow", 15.0F);
        first.addProperty("off_hand_hold", 0.5F);
        keys.get(2).getAsJsonObject().remove("off_hand_slide");
        FirstPersonSwing.Move move = parse(json, spearGeometry()).move(0);
        FirstPersonSwing.Pose keyed = move.keys().get(1).pose();
        assertEquals(-4.0F, keyed.offHandSlide());
        assertEquals(25.0F, keyed.offHandRoll());
        assertEquals(15.0F, keyed.offHandElbow());
        assertEquals(0.5F, keyed.offHandHold());
        FirstPersonSwing.Pose inherited = move.keys().get(2).pose();
        assertEquals(keyed.offHandSlide(), inherited.offHandSlide());
        assertEquals(keyed.offHandHold(), inherited.offHandHold());
        FirstPersonSwing.Pose start = move.keys().get(0).pose();
        assertEquals(start, parse(json, spearGeometry()).neutral());
        FirstPersonSwing.Pose halfway = FirstPersonSwing.Pose.interpolate(start, keyed, 0.5F);
        assertEquals((NEUTRAL_SLIDE - 4.0F) * 0.5F, halfway.offHandSlide(), EPSILON);
        assertEquals(12.5F, halfway.offHandRoll(), EPSILON);
        assertEquals(7.5F, halfway.offHandElbow(), EPSILON);
        assertEquals(0.75F, halfway.offHandHold(), EPSILON);

        JsonObject tight = twoHandedJson();
        firstMove(tight).getAsJsonArray("keys").get(1).getAsJsonObject().addProperty("off_hand_hold", 1.5F);
        assertThrows(IllegalArgumentException.class, () -> parse(tight, spearGeometry()));
        JsonObject offTheHandle = twoHandedJson();
        firstMove(offTheHandle).getAsJsonArray("keys").get(1).getAsJsonObject().addProperty("off_hand_slide", 9.0F);
        assertThrows(IllegalArgumentException.class, () -> parse(offTheHandle, spearGeometry()));
    }

    @Test
    void offFistStaysClosedOnTheShaftAndTheArmKeepsItsBones() throws IOException {
        FirstPersonSwing swing = twoHanded();
        SwordGeometry spear = swing.sword();
        for (HumanoidArm arm : HumanoidArm.values()) {
            for (FirstPersonSwing.Move move : swing.moves()) {
                for (float tick = 0.0F; tick <= move.totalTicks(); tick += STEP) {
                    FirstPersonSwing.Pose pose = move.sample(tick);
                    String where = move.id() + " " + arm + " at " + tick;
                    FirstPersonArmIk.OffHandSolution off =
                            FirstPersonArmIk.solveOffHand(arm, 0.0F, swing, pose).orElseThrow();
                    FirstPersonArmIk.Solution solution = off.arm();
                    Vector3f onShaft = FirstPersonSwordTransform.swordPoint(arm, 0.0F, swing, pose,
                            new Vector3f(spear.gripCenter().x, off.gripY(), spear.gripCenter().z));
                    assertEquals(0.0F, solution.grip().distance(onShaft), EPSILON, where + " off grip leaves the shaft");
                    assertTrue(off.gripY() > spear.gripCenter().y && off.gripY() < spear.handleTop(), where);
                    // The off fist's thumb side points up the shaft (toward the tip). A mirrored
                    // (left) arm's +X is the reflected thumb side, as for a left main arm.
                    Vector3f tipward = FirstPersonSwordTransform.swordPoint(arm, 0.0F, swing, pose, spear.bladeTip())
                            .sub(onShaft).normalize();
                    float thumbSide = arm == HumanoidArm.RIGHT ? -1.0F : 1.0F;
                    assertTrue(thumbSide * solution.fistRotation().transform(new Vector3f(1.0F, 0.0F, 0.0F))
                            .dot(tipward) > 0.5F, where + " thumb");
                    assertBones(swing, solution, where);
                }
            }
        }
    }

    @Test
    void leftMainArmGetsTheMirroredSolution() throws IOException {
        FirstPersonSwing swing = twoHanded();
        for (FirstPersonSwing.Move move : swing.moves()) {
            for (float tick = 0.0F; tick <= move.totalTicks(); tick += 1.0F) {
                FirstPersonSwing.Pose pose = move.sample(tick);
                FirstPersonArmIk.OffHandSolution right =
                        FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.2F, swing, pose).orElseThrow();
                FirstPersonArmIk.OffHandSolution left =
                        FirstPersonArmIk.solveOffHand(HumanoidArm.LEFT, 0.2F, swing, pose).orElseThrow();
                assertEquals(right.gripY(), left.gripY(), EPSILON);
                FirstPersonArmIk.Solution a = right.arm();
                FirstPersonArmIk.Solution b = left.arm();
                for (Vector3f[] pair : new Vector3f[][] {
                        {a.shoulder(), b.shoulder()}, {a.elbow(), b.elbow()},
                        {a.wrist(), b.wrist()}, {a.grip(), b.grip()}}) {
                    assertEquals(pair[0].x, -pair[1].x, EPSILON);
                    assertEquals(pair[0].y, pair[1].y, EPSILON);
                    assertEquals(pair[0].z, pair[1].z, EPSILON);
                }
                assertEquals(a.flexion(), b.flexion(), EPSILON);
                assertEquals(a.deviation(), b.deviation(), EPSILON);
                // Left main hand: the off grip is on the left-hand weapon's shaft.
                Vector3f leftShaft = FirstPersonSwordTransform.swordPoint(HumanoidArm.LEFT, 0.2F, swing, pose,
                        new Vector3f(swing.sword().gripCenter().x, left.gripY(), swing.sword().gripCenter().z));
                assertEquals(0.0F, b.grip().distance(leftShaft), EPSILON);
                for (Quaternionf rotation : List.of(b.upperArmRotation(), b.forearmRotation(), b.fistRotation(),
                        a.upperArmRotation(), a.forearmRotation(), a.fistRotation())) {
                    Vector3f x = rotation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
                    Vector3f y = rotation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
                    Vector3f z = rotation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
                    assertEquals(0.0F, new Vector3f(x).cross(y).distance(z), EPSILON);
                }
            }
        }
    }

    @Test
    void outOfReachTheHandSlidesAlongTheShaftInsteadOfLeavingIt() throws IOException {
        JsonObject json = twoHandedJson();
        // The off shoulder far out to the side and back: the default point is out of reach.
        json.getAsJsonObject("rig").getAsJsonObject("off_hand").add("shoulder_offset", vector(0.25F, -0.05F, 0.25F));
        FirstPersonSwing swing = parse(json, spearGeometry());
        FirstPersonSwing.Arm arm = swing.rig().arm();
        float reach = FirstPersonArmIk.REACH_FRACTION * (arm.upperArm() + arm.forearm());
        SwordGeometry spear = swing.sword();
        int slid = 0;
        for (FirstPersonSwing.Move move : swing.moves()) {
            float previous = Float.NaN;
            for (float tick = 0.0F; tick <= move.totalTicks(); tick += 0.125F) {
                FirstPersonSwing.Pose pose = move.sample(tick);
                String where = move.id() + " at " + tick;
                FirstPersonArmIk.OffHandSolution off =
                        FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, swing, pose).orElseThrow();
                Vector3f onShaft = FirstPersonSwordTransform.swordPoint(HumanoidArm.RIGHT, 0.0F, swing, pose,
                        new Vector3f(spear.gripCenter().x, off.gripY(), spear.gripCenter().z));
                assertEquals(0.0F, off.arm().grip().distance(onShaft), EPSILON, where);
                assertTrue(off.gripY() > spear.gripCenter().y && off.gripY() < spear.handleTop(), where);
                if (off.slid()) {
                    slid++;
                    // Slid only as far as needed: onto the reach boundary, unless no shaft point is in reach.
                    if (!off.arm().clamped()) {
                        float distance = off.arm().wrist().distance(off.arm().shoulder());
                        assertEquals(reach, distance, 2.0E-3F, where + " slid past the nearest reachable point");
                    }
                }
                if (!Float.isNaN(previous)) {
                    assertTrue(Math.abs(off.gripY() - previous) <= 3.0F, where + " hand jumps along the shaft");
                }
                previous = off.gripY();
            }
        }
        assertTrue(slid > 0, "the test rig never needed the reach slide");
    }

    @Test
    void lettingGoBlendsTheHandToItsRestAndBack() throws IOException {
        FirstPersonSwing swing = twoHanded();
        FirstPersonSwing.Pose base = swing.neutral();
        FirstPersonArmIk.OffHandSolution held = solveWithHold(swing, base, 1.0F).orElseThrow();
        assertEquals(1.0F, held.hold());
        assertTrue(solveWithHold(swing, base, 0.0F).isEmpty(), "a released hand is not drawn");
        Vector3f previous = held.arm().wrist();
        float previousY = previous.y;
        for (float hold = 0.9F; hold > 0.0F; hold -= 0.1F) {
            FirstPersonArmIk.Solution solution = solveWithHold(swing, base, hold).orElseThrow().arm();
            assertBones(swing, solution, "hold " + hold);
            assertTrue(solution.wrist().y < previousY + EPSILON, "the hand drops as it lets go: " + hold);
            assertTrue(solution.wrist().distance(previous) < 0.12F, "the hand jumps at hold " + hold);
            previous = solution.wrist();
            previousY = previous.y;
        }
        FirstPersonArmIk.Solution almost = solveWithHold(swing, base, 0.999F).orElseThrow().arm();
        assertEquals(0.0F, almost.wrist().distance(held.arm().wrist()), 2.0E-3F);
    }

    @Test
    void slideMovesTheHandAlongTheShaft() throws IOException {
        FirstPersonSwing swing = twoHanded();
        FirstPersonSwing.Pose base = swing.neutral();
        float center = swing.sword().offHandGripCenter().orElseThrow().y;
        FirstPersonArmIk.OffHandSolution still = FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, swing, base)
                .orElseThrow();
        assertEquals(center + NEUTRAL_SLIDE, still.wantedGripY(), EPSILON);
        assertFalse(still.slid());
        FirstPersonSwing.Pose slid = withOffHand(base, NEUTRAL_SLIDE - 3.0F, 0.0F, 0.0F, 1.0F);
        FirstPersonArmIk.OffHandSolution down = FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, swing, slid)
                .orElseThrow();
        assertEquals(center + NEUTRAL_SLIDE - 3.0F, down.gripY(), EPSILON);
        assertNotEquals(still.arm().grip(), down.arm().grip());
        float travelled = down.arm().grip().distance(still.arm().grip());
        assertEquals(3.0F * swing.rig().swordScale() / 16.0F, travelled, EPSILON, "slide is in model pixels");
        // Never into the main fist: the range stops a hand gap ahead of the main grip.
        FirstPersonSwing.Pose crowded = withOffHand(base, -12.0F, 0.0F, 0.0F, 1.0F);
        FirstPersonArmIk.OffHandSolution low = FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, swing, crowded)
                .orElseThrow();
        float gap = FirstPersonArmIk.OFF_HAND_GAP_PIXELS * swing.rig().arm().thickness() / swing.rig().swordScale();
        assertTrue(low.gripY() >= swing.sword().gripCenter().y + gap - EPSILON, "off grip " + low.gripY());
        // Roll turns the hand about the shaft; elbow swivels the elbow; neither moves the grip.
        FirstPersonArmIk.OffHandSolution rolled = FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, swing,
                withOffHand(base, NEUTRAL_SLIDE, 30.0F, 0.0F, 1.0F)).orElseThrow();
        FirstPersonArmIk.OffHandSolution raised = FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, swing,
                withOffHand(base, NEUTRAL_SLIDE, 0.0F, 30.0F, 1.0F)).orElseThrow();
        assertEquals(0.0F, rolled.arm().grip().distance(still.arm().grip()), EPSILON);
        assertEquals(0.0F, raised.arm().grip().distance(still.arm().grip()), EPSILON);
        assertTrue(rolled.arm().wrist().distance(still.arm().wrist()) > 1.0E-3F);
        assertTrue(raised.arm().elbow().y > still.arm().elbow().y, "positive off_hand_elbow raises the elbow");
        assertEquals(0.0F, raised.arm().wrist().distance(still.arm().wrist()), EPSILON);
    }

    @Test
    void neutralHoldPutsTheOffArmLowLeftWithBothFistsApart() throws IOException {
        FirstPersonSwing swing = twoHanded();
        FirstPersonArmIk.Solution main = FirstPersonArmIk.solve(HumanoidArm.RIGHT, 0.0F, swing, swing.neutral());
        FirstPersonArmIk.OffHandSolution off =
                FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, swing, swing.neutral()).orElseThrow();
        assertFalse(off.slid(), "the neutral off grip is out of reach");
        assertTrue(off.arm().elbow().x < off.arm().wrist().x, "off elbow " + off.arm().elbow());
        assertTrue(off.arm().shoulder().x < 0.0F && off.arm().elbow().y < off.arm().wrist().y,
                "off arm " + off.arm().shoulder() + " " + off.arm().elbow());
        assertTrue(off.arm().grip().distance(main.grip()) > 0.2F, "fists too close");
    }

    /** The shipped spear rig draws the off arm, and its hand holds the shaft at every tick. */
    @Test
    void shippedSpearRigKeepsTheOffHandOnTheShaft() throws IOException {
        Path rigPath = CombatTestData.assetPath(CombatTestData.lingxiao().firstPersonRig());
        assertTrue(Files.exists(rigPath), "the shipped spear rig is missing: " + rigPath);
        JsonObject json = JsonParser.parseString(Files.readString(rigPath)).getAsJsonObject();
        assertTrue(json.getAsJsonObject("rig").has("off_hand"), "the shipped spear rig lost its rig.off_hand block");
        FirstPersonSwing swing = FirstPersonSwing.parse(json, CombatTestData.basicSpear(), spearGeometry());
        SwordGeometry spear = swing.sword();
        FirstPersonArmIk.OffHandSolution neutral =
                FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, swing, swing.neutral()).orElseThrow();
        assertFalse(neutral.arm().clamped(), "the neutral off hand cannot reach the shaft");
        for (FirstPersonSwing.Move move : swing.moves()) {
            float previous = Float.NaN;
            for (float tick = 0.0F; tick <= move.totalTicks(); tick += 0.125F) {
                FirstPersonSwing.Pose pose = move.sample(tick);
                Optional<FirstPersonArmIk.OffHandSolution> off =
                        FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, swing, pose);
                if (off.isEmpty()) {
                    previous = Float.NaN;
                    continue;
                }
                String where = move.id() + " at " + tick;
                if (off.get().hold() >= 1.0F) {
                    Vector3f onShaft = FirstPersonSwordTransform.swordPoint(HumanoidArm.RIGHT, 0.0F, swing, pose,
                            new Vector3f(spear.gripCenter().x, off.get().gripY(), spear.gripCenter().z));
                    assertEquals(0.0F, off.get().arm().grip().distance(onShaft), EPSILON, where);
                }
                assertBones(swing, off.get().arm(), where);
                if (!Float.isNaN(previous)) {
                    assertTrue(Math.abs(off.get().gripY() - previous) <= 3.0F, where + " hand jumps along the shaft");
                }
                previous = off.get().gripY();
            }
        }
    }

    private static Optional<FirstPersonArmIk.OffHandSolution> solveWithHold(
            FirstPersonSwing swing, FirstPersonSwing.Pose pose, float hold) {
        return FirstPersonArmIk.solveOffHand(HumanoidArm.RIGHT, 0.0F, swing,
                withOffHand(pose, pose.offHandSlide(), pose.offHandRoll(), pose.offHandElbow(), hold));
    }

    private static FirstPersonSwing.Pose withOffHand(
            FirstPersonSwing.Pose pose, float slide, float roll, float elbow, float hold) {
        return new FirstPersonSwing.Pose(pose.plane(), pose.sweep(), pose.reach(), pose.lead(), pose.lift(),
                pose.twist(), pose.x(), pose.y(), pose.z(), pose.gripRoll(), pose.elbow(), slide, roll, elbow, hold);
    }

    private static void assertBones(FirstPersonSwing swing, FirstPersonArmIk.Solution solution, String where) {
        FirstPersonSwing.Arm arm = swing.rig().arm();
        assertEquals(arm.upperArm(), solution.elbow().distance(solution.shoulder()), EPSILON, where + " upper arm");
        assertEquals(arm.forearm(), solution.wrist().distance(solution.elbow()), EPSILON, where + " forearm");
        Vector3f palm = new Vector3f(solution.grip()).sub(solution.wrist());
        assertEquals(FirstPersonArmIk.wristToGrip(arm),
                solution.fistRotation().transform(new Vector3f(0.0F, 1.0F, 0.0F)).dot(palm), EPSILON, where + " palm");
    }

    static FirstPersonSwing twoHanded() throws IOException {
        return parse(twoHandedJson(), spearGeometry());
    }

    /** The spear-like neutral's off-hand slide (model pixels) and the off shoulder's forward lead. */
    private static final float NEUTRAL_SLIDE = -3.0F;

    /**
     * The Qingfeng rig with {@code rig.off_hand} (the left shoulder led forward, as in a spear
     * stance) and a spear-like neutral hold: the shaft across the body, both hands on it.
     */
    static JsonObject twoHandedJson() throws IOException {
        JsonObject json = rigJson();
        JsonObject block = new JsonObject();
        block.add("shoulder_offset", vector(0.03F, -0.02F, -0.12F));
        json.getAsJsonObject("rig").add("off_hand", block);
        JsonObject neutral = new JsonObject();
        neutral.addProperty("plane", 0.0F);
        neutral.addProperty("sweep", 20.0F);
        neutral.addProperty("reach", 0.52F);
        neutral.addProperty("lead", 35.0F);
        neutral.addProperty("lift", -50.0F);
        neutral.addProperty("twist", 55.0F);
        neutral.add("offset", vector(0.0F, 0.06F, 0.0F));
        neutral.addProperty("grip_roll", 120.0F);
        neutral.addProperty("elbow", 30.0F);
        neutral.addProperty("off_hand_slide", NEUTRAL_SLIDE);
        json.add("neutral", neutral);
        return json;
    }

    private static FirstPersonSwing parse(JsonObject json, SwordGeometry geometry) {
        return FirstPersonSwing.parse(json, CombatTestData.basicSword(), geometry);
    }

    private static JsonObject rigJson() throws IOException {
        return JsonParser.parseString(Files.readString(SWORD_RIG)).getAsJsonObject();
    }

    static SwordGeometry spearGeometry() throws IOException {
        return SwordGeometry.parse(JsonParser.parseString(Files.readString(SPEAR_GEOMETRY)).getAsJsonObject());
    }

    private static SwordGeometry swordGeometry() throws IOException {
        return SwordGeometry.parse(JsonParser.parseString(Files.readString(SWORD_GEOMETRY)).getAsJsonObject());
    }

    private static JsonObject firstMove(JsonObject rig) {
        return rig.getAsJsonObject("moves").getAsJsonObject("myvillage:basic_sword_01_thrust");
    }

    private static JsonArray vector(float x, float y, float z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }
}
