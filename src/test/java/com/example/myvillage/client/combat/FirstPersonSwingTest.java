package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class FirstPersonSwingTest {
    private static final Path RIG = Path.of(
            "src/main/resources/assets/myvillage", FirstPersonSwing.RESOURCE_PATH);

    @Test
    void shippedRigCoversEveryServerMove() throws IOException {
        FirstPersonSwing swing = shipped();
        assertEquals(BasicSwordStyle.DEFINITION.moves().size(), swing.moves().size());
        for (int index = 0; index < swing.moves().size(); index++) {
            AttackMoveDefinition definition = BasicSwordStyle.DEFINITION.move(index);
            FirstPersonSwing.Move move = swing.move(index);
            assertEquals(definition.id(), move.id());
            assertEquals(definition.totalTicks(), move.totalTicks());
            assertEquals(swing.neutral(), move.sample(0.0F));
            assertEquals(swing.neutral(), move.sample(definition.totalTicks()));
        }
    }

    @Test
    void visibleStrikeCoversTheServerActiveWindow() throws IOException {
        FirstPersonSwing swing = shipped();
        for (int index = 0; index < swing.moves().size(); index++) {
            AttackMoveDefinition definition = BasicSwordStyle.DEFINITION.move(index);
            FirstPersonSwing.Move move = swing.move(index);
            assertTrue(move.strikeStartTick() <= definition.activeStartTick(), move.id().toString());
            assertTrue(move.strikeEndTick() >= definition.activeEndTick(), move.id().toString());
            // The strike itself is a fast whip, not most of the move.
            assertTrue(move.strikeEndTick() - move.strikeStartTick() <= 3.0F, move.id().toString());
            assertTrue(move.strikeStartTick() >= 2.0F, "wind-up precedes the strike: " + move.id());
        }
    }

    @Test
    void bladeTravelsMostDuringTheStrike() throws IOException {
        FirstPersonSwing swing = shipped();
        for (int index = 0; index < swing.moves().size(); index++) {
            FirstPersonSwing.Move move = swing.move(index);
            float strikeSpeed = gripTravel(swing, move, move.strikeStartTick(), move.strikeEndTick())
                    / (move.strikeEndTick() - move.strikeStartTick());
            float recoverySpeed = gripTravel(swing, move, move.strikeEndTick() + 1.5F, move.totalTicks())
                    / (move.totalTicks() - move.strikeEndTick() - 1.5F);
            assertTrue(
                    strikeSpeed > recoverySpeed * 2.0F,
                    move.id() + " strike " + strikeSpeed + " vs recovery " + recoverySpeed);
        }
    }

    @Test
    void strikeSilhouettesAreDistinct() throws IOException {
        FirstPersonSwing swing = shipped();
        Set<FirstPersonSwing.Pose> poses = new HashSet<>();
        for (FirstPersonSwing.Move move : swing.moves()) {
            poses.add(move.sample((move.strikeStartTick() + move.strikeEndTick()) * 0.5F));
        }
        assertEquals(swing.moves().size(), poses.size());
    }

    @Test
    void gripStaysInFrontOfTheCameraForBothHands() throws IOException {
        FirstPersonSwing swing = shipped();
        for (HumanoidArm arm : HumanoidArm.values()) {
            for (FirstPersonSwing.Move move : swing.moves()) {
                for (int sample = 0; sample <= 200; sample++) {
                    float tick = move.totalTicks() * sample / 200.0F;
                    Vector3f grip = grip(swing, arm, move.sample(tick));
                    assertTrue(grip.z < -0.25F, move.id() + " grip too close at tick " + tick + ": " + grip);
                    assertTrue(grip.y > -1.2F && grip.y < 0.6F, move.id() + " grip height at " + tick);
                }
            }
        }
    }

    @Test
    void leftHandMirrorsTheRightHand() throws IOException {
        FirstPersonSwing swing = shipped();
        FirstPersonSwing.Move move = swing.move(1);
        FirstPersonSwing.Pose pose = move.sample(5.0F);
        Vector3f right = grip(swing, HumanoidArm.RIGHT, pose);
        Vector3f left = grip(swing, HumanoidArm.LEFT, pose);
        assertEquals(right.x, -left.x, 1.0E-4F);
        assertEquals(right.y, left.y, 1.0E-4F);
        assertEquals(right.z, left.z, 1.0E-4F);
    }

    @Test
    void easingKeepsSegmentEndpoints() {
        for (FirstPersonSwing.Ease ease : FirstPersonSwing.Ease.values()) {
            assertEquals(0.0F, ease.apply(0.0F), 1.0E-6F);
            assertEquals(1.0F, ease.apply(1.0F), 1.0E-6F);
            float previous = 0.0F;
            for (int step = 1; step <= 20; step++) {
                float value = ease.apply(step / 20.0F);
                assertTrue(value >= previous, ease + " is not monotonic");
                previous = value;
            }
        }
        assertTrue(FirstPersonSwing.Ease.IN.apply(0.5F) < 0.5F);
        assertTrue(FirstPersonSwing.Ease.OUT.apply(0.5F) > 0.5F);
    }

    @Test
    void invalidRigsAreRejected() throws IOException {
        JsonObject missingMove = rigJson();
        missingMove.getAsJsonObject("moves").remove("myvillage:basic_sword_05_lunge_thrust");
        assertThrows(IllegalArgumentException.class, () -> parse(missingMove));

        JsonObject lateStrike = rigJson();
        firstMove(lateStrike).add("strike", pair(3.5F, 4.2F));
        assertThrows(IllegalArgumentException.class, () -> parse(lateStrike));

        JsonObject unordered = rigJson();
        JsonArray keys = firstMove(unordered).getAsJsonArray("keys");
        keys.get(2).getAsJsonObject().addProperty("tick", 1.0F);
        assertThrows(IllegalArgumentException.class, () -> parse(unordered));

        JsonObject notNeutral = rigJson();
        JsonArray lastKeys = firstMove(notNeutral).getAsJsonArray("keys");
        JsonObject last = lastKeys.get(lastKeys.size() - 1).getAsJsonObject();
        last.remove("pose");
        last.addProperty("sweep", 12.0F);
        assertThrows(IllegalArgumentException.class, () -> parse(notNeutral));

        JsonObject badEase = rigJson();
        firstMove(badEase).getAsJsonArray("keys").get(1).getAsJsonObject().addProperty("ease", "bounce");
        assertThrows(IllegalArgumentException.class, () -> parse(badEase));
    }

    private static float gripTravel(FirstPersonSwing swing, FirstPersonSwing.Move move, float from, float to) {
        float travel = 0.0F;
        Vector3f previous = grip(swing, HumanoidArm.RIGHT, move.sample(from));
        for (int step = 1; step <= 40; step++) {
            Vector3f next = grip(swing, HumanoidArm.RIGHT, move.sample(from + (to - from) * step / 40.0F));
            travel += next.distance(previous);
            previous = next;
        }
        return travel;
    }

    private static Vector3f grip(FirstPersonSwing swing, HumanoidArm arm, FirstPersonSwing.Pose pose) {
        PoseStack poseStack = new PoseStack();
        FirstPersonSwordTransform.apply(poseStack, arm, 0.0F, swing.rig(), pose);
        // The alignment translation leaves the handle at the model point the rig treats as the grip.
        return poseStack.last().pose().transformPosition(
                FirstPersonSwordTransform.gripInItemFrame(arm), new Vector3f());
    }

    private static FirstPersonSwing shipped() throws IOException {
        return parse(rigJson());
    }

    private static FirstPersonSwing parse(JsonObject json) {
        return FirstPersonSwing.parse(json, BasicSwordStyle.DEFINITION);
    }

    private static JsonObject rigJson() throws IOException {
        return JsonParser.parseString(Files.readString(RIG)).getAsJsonObject();
    }

    private static JsonObject firstMove(JsonObject rig) {
        return rig.getAsJsonObject("moves").getAsJsonObject("myvillage:basic_sword_01_thrust");
    }

    private static JsonArray pair(float first, float second) {
        JsonArray array = new JsonArray();
        array.add(first);
        array.add(second);
        return array;
    }
}
