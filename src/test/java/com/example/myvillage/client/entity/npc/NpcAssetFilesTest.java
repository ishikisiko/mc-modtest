package com.example.myvillage.client.entity.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.client.entity.beast.BeastAnimationFile;
import com.example.myvillage.client.entity.beast.BeastGait;
import com.example.myvillage.client.entity.beast.BeastModelFile;
import com.example.myvillage.entity.beast.BeastDataException;
import com.example.myvillage.entity.npc.CultivatorEntity;
import com.example.myvillage.entity.npc.NpcEntity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The generated cultivator files of every look against the rules the NPC renderer applies when it loads them. */
final class NpcAssetFilesTest {
    // Not CultivatorEntity.ID: initialising an entity class needs the game's registries.
    private static final ResourceLocation CULTIVATOR = ResourceLocation.fromNamespaceAndPath("myvillage", "cultivator");
    // CultivatorEntity.LOOKS, written out for the same reason; tools/validate_custom_entities.py keeps
    // that list, the contract's looks and the npcgen definitions equal.
    static final List<String> LOOKS = List.of("default", "f_novice", "f_adept");
    /** The humanoid body every look keeps (a look may add bones, never drop these). */
    private static final Set<String> BODY_BONES = Set.of("root", "body", "head", "arm_right", "arm_left",
            "forearm_right", "forearm_left", "leg_right", "leg_left", "hair_back");

    static List<String> looks() {
        return LOOKS;
    }

    @ParameterizedTest
    @MethodSource("looks")
    void generatedFilesLoad(String look) {
        BeastModelFile model = model(look);
        BeastAnimationFile animations = animations(look, json -> { });
        NpcRenderer.check(animationPath(look), model, animations);
        assertEquals(0.5F, model.scale(), 1.0E-6F, "authored in units of 1/32 block");
        assertEquals("head", model.look().bone());
        ModelPart meshRoot = model.toLayerDefinition().bakeRoot();
        assertEquals(model.bones().size(), model.bonesOf(meshRoot).size());
        assertTrue(model.bonesOf(meshRoot).keySet().containsAll(BODY_BONES),
                () -> look + " lacks body bones: " + BODY_BONES.stream().filter(b -> !model.hasBone(b)).toList());
        assertTrue(animations.toAnimationDefinitions().keySet().containsAll(
                List.of(NpcEntity.IDLE_CLIP, NpcEntity.WALK_CLIP)));
    }

    @ParameterizedTest
    @MethodSource("looks")
    void walkRateFollowsThePlantedFoot(String look) {
        BeastModelFile model = model(look);
        BeastAnimationFile.Clip walk = animations(look, json -> { }).clip(NpcEntity.WALK_CLIP).orElseThrow();
        double foot = BeastGait.plantedFootSpeed(model, walk);
        // A stride of two leg swings per clip second: between one and two blocks at the default leg
        // length; the female looks take shorter steps (a smaller swing), so they may go down to half.
        double least = NpcEntity.LOOK_DEFAULT.equals(look) ? 1.0 : 0.5;
        assertTrue(foot > least && foot < 2.0, look + ": planted foot speed in blocks per clip second: " + foot);
        assertEquals(5.0 / foot, NpcRenderer.walkRate(foot), 1.0E-4);
    }

    @Test
    void aClipWithoutAPlantedFootPlaysAtThePlainRate() {
        assertEquals(1.0F, NpcRenderer.walkRate(0.0));
    }

    @Test
    void lookFilesFollowTheEntityAndLookNames() {
        assertEquals("myvillage:npc/cultivator_model.json", NpcRenderer.modelFile(CULTIVATOR).toString());
        assertEquals(NpcRenderer.modelFile(CULTIVATOR), NpcRenderer.modelFile(CULTIVATOR, "default"));
        assertEquals("myvillage:npc/cultivator_f_novice_model.json", NpcRenderer.modelFile(CULTIVATOR, "f_novice").toString());
        assertEquals("myvillage:npc/cultivator_f_adept_animations.json",
                NpcRenderer.animationFile(CULTIVATOR, "f_adept").toString());
        assertEquals("myvillage:textures/entity/cultivator/cultivator.png", NpcRenderer.texture(CULTIVATOR).toString());
        assertEquals("myvillage:textures/entity/cultivator/cultivator_f_novice.png",
                NpcRenderer.texture(CULTIVATOR, "f_novice").toString());
        assertEquals("main", NpcRenderer.layer(CULTIVATOR).getLayer(), "the contract's model_layer myvillage:cultivator#main");
        assertEquals("f_adept", NpcRenderer.layer(CULTIVATOR, "f_adept").getLayer());
        assertEquals(List.of("default", "f_novice"), NpcRenderer.withDefault(List.of("f_novice", "default")));
    }

    @Test
    void idleAndWalkMustExistAndLoopOnModelBones() {
        BeastModelFile model = model(NpcEntity.LOOK_DEFAULT);
        assertFailure(model, json -> json.getAsJsonObject("clips").remove("walk"), "clips.walk");
        assertFailure(model, json -> json.getAsJsonObject("clips").getAsJsonObject("idle").addProperty("loop", false),
                "clips.idle.loop");
        assertFailure(model, json -> json.getAsJsonObject("clips").getAsJsonObject("walk").getAsJsonArray("channels")
                .get(0).getAsJsonObject().addProperty("bone", "tail"), "clips.walk.channels[0].bone");
    }

    @Test
    void cultivatorIsABodyWithoutADisposition() {
        assertTrue(NpcEntity.class.isAssignableFrom(CultivatorEntity.class));
        assertFalse(net.minecraft.world.entity.monster.Enemy.class.isAssignableFrom(CultivatorEntity.class));
    }

    private static void assertFailure(BeastModelFile model, Consumer<JsonObject> mutation, String field) {
        String look = NpcEntity.LOOK_DEFAULT;
        BeastAnimationFile animations = animations(look, mutation);
        BeastDataException failure = assertThrows(BeastDataException.class,
                () -> NpcRenderer.check(animationPath(look), model, animations));
        assertTrue(failure.getMessage().contains(field), failure.getMessage());
    }

    private static String modelPath(String look) {
        return "src/main/resources/" + NpcRenderer.assetPath(NpcRenderer.modelFile(CULTIVATOR, look));
    }

    private static String animationPath(String look) {
        return "src/main/resources/" + NpcRenderer.assetPath(NpcRenderer.animationFile(CULTIVATOR, look));
    }

    /** A look whose files are not generated yet is skipped (they land with their npcgen definition). */
    private static void assumeGenerated(String look) {
        for (String path : List.of(modelPath(look), animationPath(look))) {
            Assumptions.assumeTrue(Files.isRegularFile(Path.of(path)), () -> path + " is not generated yet");
        }
    }

    private static BeastModelFile model(String look) {
        assumeGenerated(look);
        String path = modelPath(look);
        return BeastModelFile.parse(path, CULTIVATOR, read(path));
    }

    private static BeastAnimationFile animations(String look, Consumer<JsonObject> mutation) {
        assumeGenerated(look);
        String path = animationPath(look);
        JsonObject json = read(path);
        mutation.accept(json);
        return BeastAnimationFile.parse(path, CULTIVATOR, json);
    }

    private static JsonObject read(String path) {
        try {
            return JsonParser.parseString(Files.readString(Path.of(path), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
