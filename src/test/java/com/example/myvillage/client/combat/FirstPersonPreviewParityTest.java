package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.CombatStyleDefinition;
import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.combat.definition.WeaponDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Pins the first-person solver to a golden fixture that the offline preview's Python port
 * ({@code tools/combat_preview/fp_rig.py}) is checked against too
 * ({@code tools/tests/test_combat_preview_parity.py}). For every shipped weapon, every move, the
 * rig's key ticks, strike window and contact plus a regular step, and both main arms, it records the
 * sampled pose, the arm lag, the grip frame, the main arm solved with that lag (as the arm renderer
 * draws it) and the off arm where the rig has one.
 *
 * <p>A deliberate change to the solver, a rig, or a geometry contract rewrites the golden:
 * {@code ./gradlew test --tests com.example.myvillage.client.combat.FirstPersonPreviewParityTest
 * -PupdatePreviewParity}; then the Python port must pass the Python test against it.
 */
final class FirstPersonPreviewParityTest {
    static final Path GOLDEN = Path.of("src/test/resources/first_person_preview_parity.json");
    private static final String UPDATE_PROPERTY = "myvillage.updatePreviewParity";
    private static final String UPDATE_COMMAND = "./gradlew test --tests "
            + FirstPersonPreviewParityTest.class.getName() + " -PupdatePreviewParity";
    /**
     * Regular tick step on top of each move's key ticks. The key ticks alone are about 140 samples;
     * 2.5 adds about 20 and keeps the fixture near 200 KB.
     */
    private static final float TICK_STEP = 2.5F;
    /** Decimals kept in the golden; quaternion components keep one more (a rounded component tilts an axis twice as far). */
    private static final int DECIMALS = 4;
    /** Absolute tolerances, written into the golden so the Python test reads the same ones. */
    private static final Map<String, Float> TOLERANCE = Map.of(
            "pose", 1.0E-3F,
            "position", 2.0E-4F,
            "rotation", 2.0E-4F,
            "degrees", 1.0E-2F,
            "scale", 1.0E-3F,
            "model_px", 1.0E-3F);

    @Test
    void solverMatchesTheGolden() throws IOException {
        JsonObject actual = snapshot();
        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            Files.writeString(GOLDEN, format(actual, 0, "") + "\n", StandardCharsets.UTF_8);
            System.out.println("wrote " + GOLDEN.toAbsolutePath() + " (" + Files.size(GOLDEN) + " bytes)");
            return;
        }
        assertTrue(Files.exists(GOLDEN), "missing " + GOLDEN + "; write it with " + UPDATE_COMMAND);
        JsonObject golden = JsonParser.parseString(Files.readString(GOLDEN)).getAsJsonObject();
        JsonObject tolerance = golden.getAsJsonObject("tolerance");
        JsonArray expectedWeapons = golden.getAsJsonArray("weapons");
        JsonArray actualWeapons = actual.getAsJsonArray("weapons");
        assertEquals(ids(actualWeapons, "weapon"), ids(expectedWeapons, "weapon"),
                "the golden's weapons are not the shipped weapons; rewrite it with " + UPDATE_COMMAND);
        int compared = 0;
        int offHands = 0;
        for (int w = 0; w < actualWeapons.size(); w++) {
            JsonObject expectedWeapon = expectedWeapons.get(w).getAsJsonObject();
            JsonArray expectedMoves = expectedWeapon.getAsJsonArray("moves");
            JsonArray actualMoves = actualWeapons.get(w).getAsJsonObject().getAsJsonArray("moves");
            String weapon = expectedWeapon.get("weapon").getAsString();
            assertEquals(ids(actualMoves, "id"), ids(expectedMoves, "id"), weapon + " moves; " + UPDATE_COMMAND);
            for (int m = 0; m < actualMoves.size(); m++) {
                JsonArray expectedSamples = expectedMoves.get(m).getAsJsonObject().getAsJsonArray("samples");
                JsonArray actualSamples = actualMoves.get(m).getAsJsonObject().getAsJsonArray("samples");
                String move = actualMoves.get(m).getAsJsonObject().get("id").getAsString();
                assertEquals(ticks(actualSamples), ticks(expectedSamples), move + " ticks; " + UPDATE_COMMAND);
                for (int s = 0; s < actualSamples.size(); s++) {
                    JsonObject expected = expectedSamples.get(s).getAsJsonObject();
                    JsonObject sample = actualSamples.get(s).getAsJsonObject();
                    String where = move + " at tick " + sample.get("tick").getAsString();
                    near(expected, sample, "pose", tolerance.get("pose").getAsFloat(), where);
                    near(expected, sample, "lag", tolerance.get("position").getAsFloat(), where);
                    for (String arm : new String[] {"right", "left"}) {
                        JsonObject e = expected.getAsJsonObject(arm);
                        JsonObject a = sample.getAsJsonObject(arm);
                        String at = where + " " + arm;
                        near(e, a, "grip_frame", tolerance.get("position").getAsFloat(), at);
                        assertArm(e.getAsJsonObject("main"), a.getAsJsonObject("main"), tolerance, at + " main arm");
                        assertEquals(e.get("off").isJsonNull(), a.get("off").isJsonNull(), at + " off arm drawn");
                        if (!a.get("off").isJsonNull()) {
                            assertArm(e.getAsJsonObject("off"), a.getAsJsonObject("off"), tolerance, at + " off arm");
                            offHands++;
                        }
                        compared++;
                    }
                }
            }
        }
        assertTrue(compared > 0 && offHands > 0, "nothing compared (" + compared + " arms, " + offHands + " off arms)");
    }

    private static void assertArm(JsonObject expected, JsonObject actual, JsonObject tolerance, String where) {
        float position = tolerance.get("position").getAsFloat();
        for (String joint : new String[] {"shoulder", "elbow", "wrist", "grip"}) {
            near(expected, actual, joint, position, where);
        }
        for (String bone : new String[] {"upper_arm", "forearm", "fist"}) {
            Quaternionf e = quaternion(expected.getAsJsonArray(bone));
            Quaternionf a = quaternion(actual.getAsJsonArray(bone));
            for (Vector3f axis : new Vector3f[] {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)}) {
                float off = e.transform(new Vector3f(axis)).distance(a.transform(new Vector3f(axis)));
                assertTrue(off <= tolerance.get("rotation").getAsFloat(), where + " " + bone + " rotation off by " + off);
            }
        }
        near(expected, actual, "flexion", tolerance.get("degrees").getAsFloat(), where);
        near(expected, actual, "deviation", tolerance.get("degrees").getAsFloat(), where);
        assertEquals(expected.get("clamped").getAsBoolean(), actual.get("clamped").getAsBoolean(), where + " clamped");
        for (String scalar : new String[] {"lag_scale", "hold"}) {
            if (actual.has(scalar)) {
                near(expected, actual, scalar, tolerance.get("scale").getAsFloat(), where);
            }
        }
        for (String height : new String[] {"grip_y", "wanted_grip_y"}) {
            if (actual.has(height)) {
                near(expected, actual, height, tolerance.get("model_px").getAsFloat(), where);
            }
        }
    }

    private static void near(JsonObject expected, JsonObject actual, String key, float tolerance, String where) {
        float[] e = numbers(expected.get(key));
        float[] a = numbers(actual.get(key));
        assertEquals(e.length, a.length, where + " " + key);
        for (int i = 0; i < e.length; i++) {
            assertEquals(e[i], a[i], tolerance, where + " " + key + "[" + i + "]");
        }
    }

    private static float[] numbers(JsonElement element) {
        if (!element.isJsonArray()) {
            return new float[] {element.getAsFloat()};
        }
        JsonArray array = element.getAsJsonArray();
        float[] values = new float[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    private static List<String> ids(JsonArray objects, String key) {
        List<String> ids = new ArrayList<>();
        objects.forEach(element -> ids.add(element.getAsJsonObject().get(key).getAsString()));
        return ids;
    }

    private static List<Float> ticks(JsonArray samples) {
        List<Float> ticks = new ArrayList<>();
        samples.forEach(element -> ticks.add(element.getAsJsonObject().get("tick").getAsFloat()));
        return ticks;
    }

    // ------------------------------------------------------------------------------- snapshot

    static JsonObject snapshot() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("about", "First-person solver golden: FirstPersonPreviewParityTest (Java) writes and "
                + "checks it; tools/tests/test_combat_preview_parity.py checks the Python port against it. "
                + "Rotations are quaternions (x, y, z, w); grip_frame is the 3x4 affine, row-major.");
        root.addProperty("regenerate", UPDATE_COMMAND);
        root.add("tick_step", number(TICK_STEP));
        JsonObject tolerance = new JsonObject();
        TOLERANCE.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> tolerance.addProperty(entry.getKey(), entry.getValue()));
        root.add("tolerance", tolerance);
        JsonArray weapons = new JsonArray();
        for (WeaponDefinition weapon : CombatTestData.styles().weapons()) {
            weapons.add(weapon(weapon));
        }
        root.add("weapons", weapons);
        return root;
    }

    private static JsonObject weapon(WeaponDefinition weapon) throws IOException {
        CombatStyleDefinition style = CombatTestData.styles().style(weapon.style()).orElseThrow();
        SwordGeometry geometry = SwordGeometry.parse(json(CombatTestData.assetPath(weapon.geometry())));
        FirstPersonSwing swing = FirstPersonSwing.parse(
                json(CombatTestData.assetPath(weapon.firstPersonRig())), style, geometry);
        JsonObject out = new JsonObject();
        out.addProperty("weapon", weapon.item().toString());
        out.addProperty("rig", weapon.firstPersonRig().toString());
        out.addProperty("geometry", weapon.geometry().toString());
        JsonArray moves = new JsonArray();
        for (FirstPersonSwing.Move move : swing.moves()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", move.id().toString());
            JsonArray samples = new JsonArray();
            for (float tick : ticks(move)) {
                samples.add(sample(swing, move, tick));
            }
            entry.add("samples", samples);
            moves.add(entry);
        }
        out.add("moves", moves);
        return out;
    }

    /** The rig's key ticks, the strike window and contact, and every {@link #TICK_STEP}. */
    static List<Float> ticks(FirstPersonSwing.Move move) {
        TreeSet<Float> ticks = new TreeSet<>();
        move.keys().forEach(key -> ticks.add(key.tick()));
        ticks.add(move.strikeStartTick());
        ticks.add(move.strikeEndTick());
        ticks.add(move.contactTick());
        for (int step = 0; step * TICK_STEP <= move.totalTicks(); step++) {
            ticks.add(step * TICK_STEP);
        }
        return List.copyOf(ticks);
    }

    private static JsonObject sample(FirstPersonSwing swing, FirstPersonSwing.Move move, float tick) {
        FirstPersonSwing.Pose pose = move.sample(tick);
        Vector3f lag = FirstPersonArmLag.offset(swing, move, tick);
        JsonObject out = new JsonObject();
        out.add("tick", new JsonPrimitive(new BigDecimal(Float.toString(tick))));
        out.add("pose", numbers(pose.plane(), pose.sweep(), pose.reach(), pose.lead(), pose.lift(), pose.twist(),
                pose.x(), pose.y(), pose.z(), pose.gripRoll(), pose.elbow(),
                pose.offHandSlide(), pose.offHandRoll(), pose.offHandElbow(), pose.offHandHold()));
        out.add("lag", vector(lag));
        for (HumanoidArm arm : new HumanoidArm[] {HumanoidArm.RIGHT, HumanoidArm.LEFT}) {
            JsonObject side = new JsonObject();
            side.add("grip_frame", gripFrame(FirstPersonSwordTransform.gripFrame(arm, 0.0F, swing.rig(), pose)));
            FirstPersonArmIk.Solution main = FirstPersonArmIk.solve(arm, 0.0F, swing, pose, lag);
            JsonObject mainJson = solution(main);
            mainJson.add("lag_scale", number(main.lagScale()));
            side.add("main", mainJson);
            Optional<FirstPersonArmIk.OffHandSolution> off = FirstPersonArmIk.solveOffHand(arm, 0.0F, swing, pose);
            if (off.isPresent()) {
                JsonObject offJson = solution(off.get().arm());
                offJson.add("grip_y", number(off.get().gripY()));
                offJson.add("wanted_grip_y", number(off.get().wantedGripY()));
                offJson.add("hold", number(off.get().hold()));
                side.add("off", offJson);
            } else {
                side.add("off", JsonNull.INSTANCE);
            }
            out.add(arm == HumanoidArm.RIGHT ? "right" : "left", side);
        }
        return out;
    }

    private static JsonObject solution(FirstPersonArmIk.Solution solution) {
        JsonObject out = new JsonObject();
        out.add("shoulder", vector(solution.shoulder()));
        out.add("elbow", vector(solution.elbow()));
        out.add("wrist", vector(solution.wrist()));
        out.add("grip", vector(solution.grip()));
        out.add("upper_arm", quaternion(solution.upperArmRotation()));
        out.add("forearm", quaternion(solution.forearmRotation()));
        out.add("fist", quaternion(solution.fistRotation()));
        out.add("flexion", number(solution.flexion()));
        out.add("deviation", number(solution.deviation()));
        out.addProperty("clamped", solution.clamped());
        return out;
    }

    private static JsonArray gripFrame(Matrix4f frame) {
        JsonArray out = new JsonArray();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                out.add(number(frame.get(column, row)));
            }
        }
        return out;
    }

    private static JsonArray vector(Vector3f vector) {
        return numbers(vector.x, vector.y, vector.z);
    }

    private static JsonArray quaternion(Quaternionf rotation) {
        JsonArray out = new JsonArray();
        for (float value : new float[] {rotation.x, rotation.y, rotation.z, rotation.w}) {
            out.add(number(value, DECIMALS + 1));
        }
        return out;
    }

    private static Quaternionf quaternion(JsonArray values) {
        return new Quaternionf(values.get(0).getAsFloat(), values.get(1).getAsFloat(),
                values.get(2).getAsFloat(), values.get(3).getAsFloat()).normalize();
    }

    private static JsonArray numbers(float... values) {
        JsonArray out = new JsonArray();
        for (float value : values) {
            out.add(number(value));
        }
        return out;
    }

    private static JsonPrimitive number(float value) {
        return number(value, DECIMALS);
    }

    private static JsonPrimitive number(float value, int decimals) {
        BigDecimal rounded = new BigDecimal(Float.toString(value)).setScale(decimals, RoundingMode.HALF_EVEN)
                .stripTrailingZeros();
        return new JsonPrimitive(rounded.scale() < 0 ? rounded.setScale(0) : rounded);
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    // ------------------------------------------------------------------------------- output

    /** Objects and arrays above the sample level one entry per line; each sample on one line. */
    private static final int SAMPLE_DEPTH = 6;
    private static final Gson COMPACT = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    private static String format(JsonElement element, int depth, String indent) {
        if (depth >= SAMPLE_DEPTH || element.isJsonPrimitive() || element.isJsonNull()) {
            return COMPACT.toJson(element);
        }
        String inner = indent + "  ";
        List<String> items = new ArrayList<>();
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                items.add(inner + COMPACT.toJson(entry.getKey()) + ": " + format(entry.getValue(), depth + 1, inner));
            }
            return items.isEmpty() ? "{}" : "{\n" + String.join(",\n", items) + "\n" + indent + "}";
        }
        for (JsonElement item : element.getAsJsonArray()) {
            items.add(inner + format(item, depth + 1, inner));
        }
        return items.isEmpty() ? "[]" : "[\n" + String.join(",\n", items) + "\n" + indent + "]";
    }
}
