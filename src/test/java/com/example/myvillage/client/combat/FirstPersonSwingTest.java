package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatTestData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class FirstPersonSwingTest {
    private static final Path RIG = CombatTestData.assetPath(CombatTestData.qingfeng().firstPersonRig());
    private static final Path GEOMETRY = CombatTestData.assetPath(CombatTestData.qingfeng().geometry());

    @Test
    void shippedRigCoversEveryServerMove() throws IOException {
        FirstPersonSwing swing = shipped();
        assertEquals(CombatTestData.basicSword().moves().size(), swing.moves().size());
        for (int index = 0; index < swing.moves().size(); index++) {
            AttackMoveDefinition definition = CombatTestData.basicSword().move(index);
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
            AttackMoveDefinition definition = CombatTestData.basicSword().move(index);
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
            assertEquals(1.0F, ease.apply(1.0F), 1.0E-5F);
            if (ease.overshoots()) {
                continue;
            }
            float previous = 0.0F;
            for (int step = 1; step <= 20; step++) {
                float value = ease.apply(step / 20.0F);
                assertTrue(value >= previous, ease + " is not monotonic");
                previous = value;
            }
        }
        assertTrue(FirstPersonSwing.Ease.IN.apply(0.5F) < 0.5F);
        assertTrue(FirstPersonSwing.Ease.OUT.apply(0.5F) > 0.5F);
        assertTrue(FirstPersonSwing.Ease.IN_CUBIC.apply(0.5F) < FirstPersonSwing.Ease.IN.apply(0.5F));
        assertTrue(FirstPersonSwing.Ease.OUT_CUBIC.apply(0.5F) > FirstPersonSwing.Ease.OUT.apply(0.5F));
        assertEquals(0.5F, FirstPersonSwing.Ease.IN_OUT_CUBIC.apply(0.5F), 1.0E-6F);
    }

    @Test
    void outBackOvershootsByTenToFifteenPercentThenSettles() {
        float peak = 0.0F;
        for (int step = 0; step <= 200; step++) {
            peak = Math.max(peak, FirstPersonSwing.Ease.OUT_BACK.apply(step / 200.0F));
        }
        assertTrue(peak > 1.10F && peak < 1.15F, "overshoot peak " + peak);
        assertTrue(FirstPersonSwing.Ease.OUT_BACK.overshoots());
        assertFalse(FirstPersonSwing.Ease.IN_CUBIC.overshoots());
    }

    @Test
    void contactTickIsParsedAndDefaultsToTheStrikeStart() throws IOException {
        FirstPersonSwing swing = shipped();
        for (FirstPersonSwing.Move move : swing.moves()) {
            assertTrue(move.contactTick() >= move.strikeStartTick()
                    && move.contactTick() <= move.strikeEndTick(), move.id().toString());
        }
        JsonObject withoutContact = rigJson();
        firstMove(withoutContact).remove("contact");
        FirstPersonSwing.Move move = parse(withoutContact).move(0);
        assertEquals(move.strikeStartTick(), move.contactTick());
    }

    @Test
    void neutralHoldShowsTheGuardInTheLowerRight() throws IOException {
        FirstPersonSwing swing = shipped();
        Blade blade = blade(swing, swing.neutral());
        assertTrue(blade.grip[0] >= 0.45F * WIDTH && blade.grip[0] <= 0.92F * WIDTH, "grip x " + blade.grip[0]);
        assertTrue(blade.grip[1] >= 0.55F * HEIGHT && blade.grip[1] <= 0.95F * HEIGHT, "grip y " + blade.grip[1]);
        assertTrue(onScreen(blade.guard), "guard hidden");
        assertTrue(blade.tipAboveGrip(), "neutral blade hangs down");
    }

    @Test
    void tipStaysAboveTheGripWheneverTheBladeIsVisible() throws IOException {
        FirstPersonSwing swing = shipped();
        for (FirstPersonSwing.Move move : swing.moves()) {
            int onScreenTips = 0;
            int samples = move.totalTicks() * 4;
            for (int sample = 0; sample <= samples; sample++) {
                float tick = sample / 4.0F;
                Blade blade = blade(swing, move.sample(tick));
                assertTrue(blade.tipAboveGrip() || blade.visibleFraction == 0.0F,
                        move.id() + " blade hangs tip-down on screen at tick " + tick);
                if (onScreen(blade.tip)) {
                    onScreenTips++;
                }
                if (tick <= move.strikeStartTick()) {
                    assertTrue(blade.visibleFraction >= 0.5F, move.id() + " wind-up off screen at " + tick);
                }
            }
            assertTrue(onScreenTips >= 0.8F * (samples + 1), move.id() + " tip on screen " + onScreenTips);
        }
    }

    @Test
    void strikesSweepAcrossTheViewWithoutFlipping() throws IOException {
        FirstPersonSwing swing = shipped();
        for (FirstPersonSwing.Move move : swing.moves()) {
            Blade previous = null;
            for (float tick = move.strikeStartTick(); tick <= move.strikeEndTick(); tick += 0.25F) {
                Blade blade = blade(swing, move.sample(tick));
                assertTrue(blade.projectedLength() >= 0.25F * HEIGHT,
                        move.id() + " blade collapses to a stub at tick " + tick);
                if (previous != null) {
                    float turn = Math.abs(wrapDegrees(blade.screenAngle() - previous.screenAngle())) * 2.0F;
                    assertTrue(turn <= 48.0F, move.id() + " blade spins " + turn + " deg per half tick at " + tick);
                }
                previous = blade;
            }
        }
    }

    @Test
    void cutsWhipTheTipAroundTheWrist() throws IOException {
        FirstPersonSwing swing = shipped();
        for (int index = 1; index <= 3; index++) {
            FirstPersonSwing.Move move = swing.move(index);
            float gripTravel = 0.0F;
            float tipTravel = 0.0F;
            Blade previous = null;
            for (float tick = move.strikeStartTick(); tick <= move.strikeEndTick(); tick += 0.25F) {
                FirstPersonSwing.Pose pose = move.sample(tick);
                assertTrue(pose.reach() >= 0.55F - 1.0E-4F && pose.reach() <= 0.65F + 1.0E-4F,
                        move.id() + " strike reach " + pose.reach());
                Blade blade = blade(swing, pose);
                if (previous != null) {
                    gripTravel += distance(previous.grip, blade.grip);
                    tipTravel += distance(previous.tip, blade.tip);
                }
                previous = blade;
            }
            assertTrue(tipTravel >= 1.8F * gripTravel, move.id() + " tip " + tipTravel + " vs grip " + gripTravel);
            float contactLift = move.sample(move.contactTick()).lift();
            assertTrue(contactLift >= -65.0F && contactLift <= -50.0F, move.id() + " contact lift " + contactLift);
        }
    }

    @Test
    void followThroughOvershootsHoldsThenReturnsQuickly() throws IOException {
        FirstPersonSwing swing = shipped();
        for (FirstPersonSwing.Move move : swing.moves()) {
            List<FirstPersonSwing.Key> keys = move.keys();
            int followThrough = -1;
            for (int index = 0; index < keys.size(); index++) {
                if (keys.get(index).ease() == FirstPersonSwing.Ease.OUT_BACK) {
                    assertEquals(-1, followThrough, move.id() + " has one follow-through");
                    followThrough = index;
                }
            }
            assertTrue(followThrough > 0, move.id() + " has no overshooting follow-through");
            assertTrue(keys.get(followThrough - 1).tick() >= move.contactTick() - 1.0E-4F,
                    move.id() + " follow-through starts before contact");
            float returnStart = keys.get(keys.size() - 2).tick();
            float hold = returnStart - keys.get(followThrough).tick();
            float recovery = move.totalTicks() - returnStart;
            assertTrue(hold >= 2.0F, move.id() + " hold " + hold);
            assertTrue(recovery >= 3.0F && recovery <= 4.0F + 1.0E-4F, move.id() + " return " + recovery);
            // Strikes accelerate into contact.
            float early = tipSpeed(swing, move, move.strikeStartTick() + 0.125F);
            float atContact = tipSpeed(swing, move, move.contactTick());
            assertTrue(atContact >= 1.5F * early, move.id() + " contact speed " + atContact + " vs " + early);
        }
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

        JsonObject lateContact = rigJson();
        firstMove(lateContact).addProperty("contact", 9.0F);
        assertThrows(IllegalArgumentException.class, () -> parse(lateContact));
    }

    @Test
    void swordGripLandsOnTheGripFrameWhateverTheDisplayTransform() throws IOException {
        FirstPersonSwing swing = shipped();
        SwordGeometry sword = swing.sword();
        float scale = swing.rig().swordScale();
        FirstPersonSwing.Pose pose = swing.move(1).sample(5.0F);
        Matrix4f[] displays = {
                new Matrix4f(),
                // Vanilla item/handheld first person, and the 3D sword's equivalent display.
                new Matrix4f().translate(1.13F / 16.0F, 3.2F / 16.0F, 1.13F / 16.0F)
                        .rotateXYZ(0.0F, radians(-90.0F), radians(25.0F)).scale(0.68F),
                new Matrix4f().translate(1.13F / 16.0F, -0.024F / 16.0F, -0.073F / 16.0F)
                        .rotateXYZ(radians(19.19F), radians(180.0F), 0.0F).scale(0.572F),
                new Matrix4f().translate(0.1F, -0.2F, 0.05F).rotateXYZ(0.3F, -1.1F, 0.7F).scale(0.5F, 0.8F, 0.6F)
        };
        Vector3f[] pixels = {
                sword.gripCenter(),
                sword.bladeBase(),
                sword.bladeTip(),
                new Vector3f(sword.gripCenter().x, sword.pommelBottom(), sword.gripCenter().z),
                new Vector3f(sword.gripCenter().x + sword.guardHalfThickness(), sword.guardTop(),
                        sword.gripCenter().z + sword.guardHalfWidth())
        };
        for (HumanoidArm arm : HumanoidArm.values()) {
            Matrix4f gripFrame = FirstPersonSwordTransform.gripFrame(arm, 0.0F, swing.rig(), pose);
            for (Matrix4f display : displays) {
                PoseStack poseStack = new PoseStack();
                FirstPersonSwordTransform.apply(poseStack, arm, 0.0F, swing, pose, display);
                // What the item pass then does: the model's display transform and translate(-0.5).
                Matrix4f item = new Matrix4f(poseStack.last().pose()).mul(display).translate(-0.5F, -0.5F, -0.5F);
                for (Vector3f pixel : pixels) {
                    Vector3f drawn = item.transformPosition(new Vector3f(pixel).div(16.0F));
                    Vector3f expected = gripFrame.transformPosition(sword.toGrip(pixel, scale));
                    assertEquals(0.0F, drawn.distance(expected), 1.0E-4F, arm + " " + pixel + " via " + display);
                }
                Vector3f origin = item.transformPosition(sword.gripCenter().div(16.0F));
                assertEquals(0.0F, origin.distance(gripFrame.getTranslation(new Vector3f())), 1.0E-4F);
            }
        }
        // About the 0.27.0 on-screen length (0.76 from grip to tip) or a little shorter.
        float tip = sword.toGrip(sword.bladeTip(), scale).length();
        assertTrue(tip >= 0.66F && tip <= 0.78F, "grip-to-tip " + tip);
    }

    @Test
    void armTuningHasDefaultsAndRejectsNonsense() throws IOException {
        JsonObject bare = rigJson();
        bare.getAsJsonObject("rig").remove("arm");
        bare.getAsJsonObject("rig").remove("sword_scale");
        FirstPersonSwing swing = parse(bare);
        assertEquals(FirstPersonSwing.Arm.DEFAULT, swing.rig().arm());
        assertEquals(FirstPersonSwing.DEFAULT_SWORD_SCALE, swing.rig().swordScale());

        JsonObject thick = rigJson();
        thick.getAsJsonObject("rig").getAsJsonObject("arm").addProperty("thickness", 3.0F);
        assertThrows(IllegalArgumentException.class, () -> parse(thick));
        JsonObject tiny = rigJson();
        tiny.getAsJsonObject("rig").addProperty("sword_scale", 0.05F);
        assertThrows(IllegalArgumentException.class, () -> parse(tiny));
        JsonObject straight = rigJson();
        straight.getAsJsonObject("rig").getAsJsonObject("arm").addProperty("grip_diagonal", 80.0F);
        assertThrows(IllegalArgumentException.class, () -> parse(straight));
    }

    @Test
    void gripRollAndElbowAreKeyedAndInherited() throws IOException {
        FirstPersonSwing swing = shipped();
        FirstPersonSwing.Move rising = swing.move(2);
        FirstPersonSwing.Key finish = rising.keys().get(4);
        assertEquals(7.6F, finish.tick(), 1.0E-6F);
        assertTrue(finish.pose().elbow() > swing.neutral().elbow(), "high finish raises the elbow");
        // A key that omits the fields keeps the previous key's values.
        JsonObject inherited = rigJson();
        JsonObject key = firstMove(inherited).getAsJsonArray("keys").get(2).getAsJsonObject();
        key.remove("grip_roll");
        key.remove("elbow");
        FirstPersonSwing.Move move = parse(inherited).move(0);
        assertEquals(move.keys().get(1).pose().gripRoll(), move.keys().get(2).pose().gripRoll());
        assertEquals(move.keys().get(1).pose().elbow(), move.keys().get(2).pose().elbow());
        // Interpolated like every other pose value.
        FirstPersonSwing.Pose halfway = FirstPersonSwing.Pose.interpolate(
                swing.neutral(), finish.pose(), 0.5F);
        assertEquals((swing.neutral().elbow() + finish.pose().elbow()) * 0.5F, halfway.elbow(), 1.0E-4F);
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static float tipSpeed(FirstPersonSwing swing, FirstPersonSwing.Move move, float tick) {
        Vector3f before = point(swing, move.sample(tick - 0.125F), TIP);
        Vector3f after = point(swing, move.sample(tick + 0.125F), TIP);
        return after.distance(before) / 0.25F;
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
        FirstPersonSwordTransform.applyGripFrame(poseStack, arm, 0.0F, swing.rig(), pose);
        return poseStack.last().pose().getTranslation(new Vector3f());
    }

    // Sword points in the grip frame (+Y along the blade), from the geometry contract at the rig's
    // sword scale; the view uses the fixed 70 degree hand FOV at 16:9.
    private static final Vector3f HANDLE;
    private static final Vector3f GUARD;
    private static final Vector3f BLADE_BASE;
    private static final Vector3f TIP;

    static {
        try {
            FirstPersonSwing swing = shipped();
            SwordGeometry sword = swing.sword();
            float scale = swing.rig().swordScale();
            HANDLE = sword.axisPoint(sword.gripCenter().y, scale);
            GUARD = sword.axisPoint((sword.guardBottom() + sword.guardTop()) * 0.5F, scale);
            BLADE_BASE = sword.toGrip(sword.bladeBase(), scale);
            TIP = sword.toGrip(sword.bladeTip(), scale);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
    private static final float WIDTH = 960.0F;
    private static final float HEIGHT = 540.0F;
    private static final float TAN_HALF_FOV = (float) Math.tan(Math.toRadians(35.0));

    private static Vector3f point(FirstPersonSwing swing, FirstPersonSwing.Pose pose, Vector3f local) {
        Matrix4f frame = FirstPersonSwordTransform.gripFrame(HumanoidArm.RIGHT, 0.0F, swing.rig(), pose);
        return frame.transformPosition(local, new Vector3f());
    }

    /** Screen pixels (y down) at 960x540, or null behind the eye. */
    private static float[] screen(Vector3f view) {
        if (view.z >= -1.0E-3F) {
            return null;
        }
        float depth = -view.z;
        return new float[] {
                WIDTH * 0.5F * (1.0F + view.x / (depth * TAN_HALF_FOV * WIDTH / HEIGHT)),
                HEIGHT * 0.5F * (1.0F - view.y / (depth * TAN_HALF_FOV))
        };
    }

    private static boolean onScreen(float[] point) {
        return point != null && point[0] >= 0.0F && point[0] <= WIDTH && point[1] >= 0.0F && point[1] <= HEIGHT;
    }

    private static float distance(float[] from, float[] to) {
        return (float) Math.hypot(to[0] - from[0], to[1] - from[1]);
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private static Blade blade(FirstPersonSwing swing, FirstPersonSwing.Pose pose) {
        float[] grip = screen(point(swing, pose, HANDLE));
        float[] guard = screen(point(swing, pose, GUARD));
        float[] tip = screen(point(swing, pose, TIP));
        int visible = 0;
        for (int step = 0; step <= 10; step++) {
            Vector3f local = new Vector3f(BLADE_BASE).lerp(TIP, step / 10.0F);
            if (onScreen(screen(point(swing, pose, local)))) {
                visible++;
            }
        }
        return new Blade(grip, guard, tip, visible / 11.0F);
    }

    private record Blade(float[] grip, float[] guard, float[] tip, float visibleFraction) {
        boolean tipAboveGrip() {
            return grip != null && tip != null && tip[1] < grip[1];
        }

        float projectedLength() {
            return grip == null || tip == null ? 0.0F : distance(grip, tip);
        }

        float screenAngle() {
            return (float) Math.toDegrees(Math.atan2(-(tip[1] - grip[1]), tip[0] - grip[0]));
        }
    }

    private static FirstPersonSwing shipped() throws IOException {
        return parse(rigJson());
    }

    private static FirstPersonSwing parse(JsonObject json) throws IOException {
        return FirstPersonSwing.parse(json, CombatTestData.basicSword(), geometry());
    }

    static SwordGeometry geometry() throws IOException {
        return SwordGeometry.parse(JsonParser.parseString(Files.readString(GEOMETRY)).getAsJsonObject());
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
