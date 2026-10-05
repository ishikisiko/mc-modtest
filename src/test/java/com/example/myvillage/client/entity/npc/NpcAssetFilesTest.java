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
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The generated cultivator files against the rules the NPC renderer applies when it loads them. */
final class NpcAssetFilesTest {
    // Not CultivatorEntity.ID: initialising an entity class needs the game's registries.
    private static final ResourceLocation CULTIVATOR = ResourceLocation.fromNamespaceAndPath("myvillage", "cultivator");
    private static final String MODEL = "src/main/resources/assets/myvillage/npc/cultivator_model.json";
    private static final String ANIMATIONS = "src/main/resources/assets/myvillage/npc/cultivator_animations.json";

    @Test
    void generatedCultivatorFilesLoad() {
        BeastModelFile model = model();
        BeastAnimationFile animations = animations(json -> { });
        NpcRenderer.check(ANIMATIONS, model, animations);
        assertEquals(0.5F, model.scale(), 1.0E-6F, "authored in units of 1/32 block");
        assertEquals("head", model.look().bone());
        ModelPart meshRoot = model.toLayerDefinition().bakeRoot();
        assertEquals(model.bones().size(), model.bonesOf(meshRoot).size());
        assertTrue(animations.toAnimationDefinitions().keySet().containsAll(
                java.util.List.of(NpcEntity.IDLE_CLIP, NpcEntity.WALK_CLIP)));
    }

    @Test
    void walkRateFollowsThePlantedFoot() {
        BeastModelFile model = model();
        BeastAnimationFile.Clip walk = animations(json -> { }).clip(NpcEntity.WALK_CLIP).orElseThrow();
        double foot = BeastGait.plantedFootSpeed(model, walk);
        // A stride of two leg swings per clip second: between one and two blocks at this leg length.
        assertTrue(foot > 1.0 && foot < 2.0, "planted foot speed in blocks per clip second: " + foot);
        assertEquals(5.0 / foot, NpcRenderer.walkRate(foot), 1.0E-4);
        assertEquals(1.0F, NpcRenderer.walkRate(0.0), "a clip with no planted foot plays at the plain rate");
    }

    @Test
    void idleAndWalkMustExistAndLoopOnModelBones() {
        BeastModelFile model = model();
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
        BeastAnimationFile animations = animations(mutation);
        BeastDataException failure = assertThrows(BeastDataException.class,
                () -> NpcRenderer.check(ANIMATIONS, model, animations));
        assertTrue(failure.getMessage().contains(field), failure.getMessage());
    }

    private static BeastModelFile model() {
        return BeastModelFile.parse(MODEL, CULTIVATOR, read(MODEL));
    }

    private static BeastAnimationFile animations(Consumer<JsonObject> mutation) {
        JsonObject json = read(ANIMATIONS);
        mutation.accept(json);
        return BeastAnimationFile.parse(ANIMATIONS, CULTIVATOR, json);
    }

    private static JsonObject read(String path) {
        try {
            return JsonParser.parseString(Files.readString(Path.of(path), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
