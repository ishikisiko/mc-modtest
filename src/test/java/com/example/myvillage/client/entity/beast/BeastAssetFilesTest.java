package com.example.myvillage.client.entity.beast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.entity.beast.BeastDataException;
import com.example.myvillage.entity.beast.BeastDataLoader;
import com.example.myvillage.entity.beast.BeastDefinition;
import com.example.myvillage.entity.beast.BeastDefinitions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Schema-1 model and animation files: strict parsing, vanilla mapping, and the cross-file rules. */
final class BeastAssetFilesTest {
    private static final ResourceLocation FIXTURE = ResourceLocation.fromNamespaceAndPath("myvillage", "fixture_beast");
    private static final ResourceLocation WOLF = ResourceLocation.fromNamespaceAndPath("myvillage", "demon_wolf");
    private static final String MODEL = "beast_fixture/fixture_model.json";
    private static final String ANIMATIONS = "beast_fixture/fixture_animations.json";
    private static final float EPSILON = 1.0E-6F;

    @Test
    void modelMapsOneToOneOntoVanillaParts() {
        BeastModelFile model = model(json -> { });
        assertEquals(64, model.textureWidth());
        assertEquals(0.7F, model.shadowRadius(), EPSILON);
        assertEquals("head", model.look().bone());
        assertNull(model.bones().getFirst().parent());

        ModelPart meshRoot = model.toLayerDefinition().bakeRoot();
        Map<String, ModelPart> bones = model.bonesOf(meshRoot);
        assertEquals(java.util.List.of("root", "body", "head", "leg_left"), java.util.List.copyOf(bones.keySet()));
        assertSame(meshRoot.getChild("root"), bones.get("root"));
        assertSame(bones.get("body").getChild("head"), bones.get("head"));
        ModelPart head = bones.get("head");
        assertEquals(-2.0F, head.y, EPSILON);
        assertEquals(-8.0F, head.z, EPSILON);
        assertEquals((float) Math.toRadians(10.0), head.xRot, EPSILON);
        assertEquals(24.0F, bones.get("root").y, EPSILON);
        // Cube bounds are origin and origin + size (vanilla keeps the un-inflated box here).
        // Each fixture bone has one cube, so the "random" cube is that cube.
        ModelPart.Cube cube = head.getRandomCube(net.minecraft.util.RandomSource.create(0L));
        assertEquals(-3.0F, cube.minX, EPSILON);
        assertEquals(3.0F, cube.maxX, EPSILON);
        assertEquals(-6.0F, cube.minZ, EPSILON);
        assertTrue(bones.get("leg_left").getRandomCube(net.minecraft.util.RandomSource.create(0L)).maxY > 7.9F);
    }

    @Test
    void modelErrorsNameFileAndField() {
        assertEquals("bones[1].parent", modelFailure(json -> bone(json, 1).addProperty("parent", "leg_left")).field(),
                "parents come first");
        assertEquals("bones[2].name", modelFailure(json -> bone(json, 2).addProperty("name", "body")).field());
        assertEquals("bones[2].name", modelFailure(json -> bone(json, 2).addProperty("name", "Head")).field());
        assertEquals("bones[2].cubes[0].uv", modelFailure(json -> cube(json, 2).add("uv", pair(48, 0))).field(),
                "box unwrap past the texture's right edge");
        assertEquals("bones[2].cubes[0].size", modelFailure(json -> cube(json, 2).add("size", triple(6, -1, 6))).field());
        assertEquals("bones[2].cubes[0].glow", modelFailure(json -> cube(json, 2).addProperty("glow", true)).field());
        assertEquals("bones[0].parent", modelFailure(json -> bone(json, 0).remove("parent")).field(),
                "parent must be present, null for a top bone");
        assertEquals("look.bone", modelFailure(json -> json.getAsJsonObject("look").addProperty("bone", "neck")).field());
        assertEquals("look.max_yaw", modelFailure(json -> json.getAsJsonObject("look").addProperty("max_yaw", 200)).field());
        assertEquals("id", modelFailure(json -> json.addProperty("id", "myvillage:other")).field());
        assertEquals("schema", modelFailure(json -> json.addProperty("schema", 2)).field());
        BeastDataException error = modelFailure(json -> bone(json, 1).add("pivot", pair(0, 1)));
        assertEquals(MODEL, error.file());
        assertEquals("bones[1].pivot", error.field());
    }

    @Test
    void keyframesMapOntoVanillaVectors() {
        BeastAnimationFile animations = animations(json -> { });
        Map<String, AnimationDefinition> clips = animations.toAnimationDefinitions();
        assertEquals(6, clips.size());
        assertTrue(clips.get("walk").looping());
        assertFalse(clips.get("bite").looping());
        assertEquals(1.4F, clips.get("bite").lengthInSeconds(), EPSILON);

        Keyframe bite = clips.get("bite").boneAnimations().get("head").getFirst().keyframes()[1];
        assertEquals(0.5F, bite.timestamp(), EPSILON);
        assertEquals((float) Math.toRadians(-20.0), bite.target().x(), EPSILON);
        assertSame(AnimationChannel.Interpolations.LINEAR, bite.interpolation());

        // Position: vanilla posVec negates y, so +y in the file moves the bone up the screen.
        AnimationChannel pounce = clips.get("pounce").boneAnimations().get("root").getFirst();
        assertSame(AnimationChannel.Targets.POSITION, pounce.target());
        assertEquals(new Vector3f(0.0F, 3.0F, 0.0F), pounce.keyframes()[1].target());
        assertEquals(KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), pounce.keyframes()[1].target());

        // Scale: scaleVec stores the offset from 1.
        AnimationChannel breathe = clips.get("idle").boneAnimations().get("body").getFirst();
        assertEquals(0.04F, breathe.keyframes()[1].target().y(), 1.0E-5F);
        assertEquals(0.0F, breathe.keyframes()[1].target().x(), EPSILON);
        assertSame(AnimationChannel.Interpolations.CATMULLROM, breathe.keyframes()[1].interpolation());
    }

    @Test
    void animationErrorsNameFileAndField() {
        assertEquals("clips.bite.channels[0].keyframes[1].time",
                animationFailure(json -> keyframe(json, "bite", 1).addProperty("time", 1.6)).field(), "past the length");
        assertEquals("clips.bite.channels[0].keyframes[2].time",
                animationFailure(json -> keyframe(json, "bite", 2).addProperty("time", 0.2)).field(), "out of order");
        assertEquals("clips.bite.channels[0].target",
                animationFailure(json -> channel(json, "bite").addProperty("target", "twist")).field());
        assertEquals("clips.bite.channels[0].keyframes[0].interp",
                animationFailure(json -> keyframe(json, "bite", 0).addProperty("interp", "step")).field());
        assertEquals("clips.Bite", animationFailure(json -> {
            JsonObject clips = json.getAsJsonObject("clips");
            clips.add("Bite", clips.remove("bite"));
        }).field());
        assertEquals("clips.walk.speed", animationFailure(json -> clip(json, "walk").addProperty("speed", 2)).field());
    }

    @Test
    void crossFileRulesFollowTheServerData() {
        BeastModelFile model = model(json -> { });
        BeastDefinition wolf = new BeastDefinitions(BeastDataLoader.load(BeastAssetFilesTest::openMain)).require(WOLF);
        animations(json -> { }).check(ANIMATIONS, model, wolf);

        assertEquals("clips.bite.length", checkFailure(json -> {
            clip(json, "bite").addProperty("length", 1.5);
            keyframe(json, "bite", 2).addProperty("time", 1.5);
        }, model, wolf).field(), "a move clip lasts total_ticks / 20");
        assertEquals("clips.run", checkFailure(json -> json.getAsJsonObject("clips").remove("run"), model, wolf).field());
        assertEquals("clips.stagger.loop",
                checkFailure(json -> clip(json, "stagger").addProperty("loop", true), model, wolf).field());
        assertEquals("clips.idle.loop", checkFailure(json -> clip(json, "idle").addProperty("loop", false), model, wolf).field());
        assertEquals("clips.bite.channels[0].bone",
                checkFailure(json -> channel(json, "bite").addProperty("bone", "jaw"), model, wolf).field());
    }

    @Test
    void namedRootBoneIsNotTheMeshRoot() {
        BeastModelFile model = model(json -> { });
        ModelPart meshRoot = model.toLayerDefinition().bakeRoot();
        BeastModel<?> beastModel = new BeastModel<>(meshRoot, model, animations(json -> { }).toAnimationDefinitions(),
                new BeastGait.Rates(1.0F, 1.0F, 0.0, 0.0, 0.0, 0.0));
        assertSame(meshRoot.getChild("root"), beastModel.getAnyDescendantWithName("root").orElseThrow());
        assertSame(meshRoot, beastModel.root());

        // Sampling the pounce at 0.9 s moves the bone named root by file y -3: down, so model y grows.
        AnimationDefinition pounce = animations(json -> { }).toAnimationDefinitions().get("pounce");
        KeyframeAnimations.animate(beastModel, pounce, 900L, 1.0F, new Vector3f());
        assertEquals(27.0F, meshRoot.getChild("root").y, 1.0E-4F);
        assertEquals(0.0F, meshRoot.y, EPSILON);
    }

    /** The generated demon wolf files, once tools/beastgen has written them, pass every rule here too. */
    @Test
    void generatedDemonWolfFilesLoadWhenPresent() throws IOException {
        Path modelPath = Path.of("src/main/resources/assets/myvillage/beast/demon_wolf_model.json");
        Path animationPath = Path.of("src/main/resources/assets/myvillage/beast/demon_wolf_animations.json");
        Assumptions.assumeTrue(Files.exists(modelPath) && Files.exists(animationPath), "art not generated yet");
        BeastModelFile model;
        try (Reader reader = Files.newBufferedReader(modelPath)) {
            model = BeastModelFile.parse(modelPath.toString(), WOLF, reader);
        }
        BeastAnimationFile animations;
        try (Reader reader = Files.newBufferedReader(animationPath)) {
            animations = BeastAnimationFile.parse(animationPath.toString(), WOLF, reader);
        }
        BeastDefinition wolf = new BeastDefinitions(BeastDataLoader.load(BeastAssetFilesTest::openMain)).require(WOLF);
        animations.check(animationPath.toString(), model, wolf);
        ModelPart meshRoot = model.toLayerDefinition().bakeRoot();
        assertEquals(model.bones().size(), model.bonesOf(meshRoot).size());
        assertFalse(animations.toAnimationDefinitions().isEmpty());
        // Planted paws sweep backwards while they touch the ground, faster in the run.
        BeastGait.Rates gait = BeastGait.rates(model, animations, wolf, "walk", "run");
        assertTrue(gait.walkFootSpeed() > 0.2 && gait.runFootSpeed() > gait.walkFootSpeed(), gait.toString());
        assertTrue(gait.walk() > 0.0F && gait.run() > 0.0F, gait.toString());
    }

    @Test
    void gaitRateKeepsPlantedFeetStill() {
        // The fixture walk swings one leg +-30 degrees about a pivot 8 units above its foot.
        BeastModelFile model = model(json -> { });
        BeastAnimationFile animations = animations(json -> { });
        double foot = BeastGait.plantedFootSpeed(model, animations.clip("walk").orElseThrow());
        assertTrue(foot > 0.0, "the leg's foot moves backwards while down: " + foot);
        // Below a quarter block per tick the rate is 5 / v, above it 20 d / v.
        assertEquals(5.0 / 0.8, BeastGait.rateFor(0.8, 0.13), 1.0E-5);
        assertEquals(20.0 * 0.33 / 2.75, BeastGait.rateFor(2.75, 0.33), 1.0E-5);
        // A mob at movement 0.3 chasing with modifier 1.3 covers (0.39^2) / 0.454 blocks per tick.
        assertEquals(0.39 * 0.39 / (1.0 - 0.546), BeastGait.groundSpeed(0.3, 1.3), 1.0E-9);
    }

    private static InputStream openMain(String path) throws IOException {
        Path file = Path.of("src/main/resources").resolve(path);
        return Files.exists(file) ? Files.newInputStream(file) : null;
    }

    private static JsonObject fixture(String name) {
        try (InputStream stream = BeastAssetFilesTest.class.getClassLoader().getResourceAsStream(name)) {
            return JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static BeastModelFile model(Consumer<JsonObject> mutation) {
        JsonObject json = fixture(MODEL);
        mutation.accept(json);
        return BeastModelFile.parse(MODEL, FIXTURE, json);
    }

    private static BeastAnimationFile animations(Consumer<JsonObject> mutation) {
        JsonObject json = fixture(ANIMATIONS);
        mutation.accept(json);
        return BeastAnimationFile.parse(ANIMATIONS, FIXTURE, json);
    }

    private static BeastDataException modelFailure(Consumer<JsonObject> mutation) {
        return assertThrows(BeastDataException.class, () -> model(mutation));
    }

    private static BeastDataException animationFailure(Consumer<JsonObject> mutation) {
        return assertThrows(BeastDataException.class, () -> animations(mutation));
    }

    private static BeastDataException checkFailure(Consumer<JsonObject> mutation, BeastModelFile model, BeastDefinition wolf) {
        BeastAnimationFile animations = animations(mutation);
        return assertThrows(BeastDataException.class, () -> animations.check(ANIMATIONS, model, wolf));
    }

    private static JsonObject bone(JsonObject json, int index) {
        return json.getAsJsonArray("bones").get(index).getAsJsonObject();
    }

    private static JsonObject cube(JsonObject json, int boneIndex) {
        return bone(json, boneIndex).getAsJsonArray("cubes").get(0).getAsJsonObject();
    }

    private static JsonObject clip(JsonObject json, String name) {
        return json.getAsJsonObject("clips").getAsJsonObject(name);
    }

    private static JsonObject channel(JsonObject json, String clip) {
        return clip(json, clip).getAsJsonArray("channels").get(0).getAsJsonObject();
    }

    private static JsonObject keyframe(JsonObject json, String clip, int index) {
        return channel(json, clip).getAsJsonArray("keyframes").get(index).getAsJsonObject();
    }

    private static JsonArray pair(Number a, Number b) {
        JsonArray array = new JsonArray();
        array.add(a);
        array.add(b);
        return array;
    }

    private static JsonArray triple(Number a, Number b, Number c) {
        JsonArray array = pair(a, b);
        array.add(c);
        return array;
    }
}
