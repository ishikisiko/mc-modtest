package com.example.myvillage.entity.beast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class BeastDataLoaderTest {
    private static final String WOLF_FILE = "data/myvillage/beast/demon_wolf.json";

    @Test
    void loadsTheBundledDemonWolf() {
        BeastDefinition wolf = BeastTestData.demonWolf();
        assertEquals(BeastTestData.DEMON_WOLF, wolf.entity());
        assertEquals(2, wolf.moves().size());
        assertEquals("stagger", wolf.staggerAnimation());
        assertEquals(70.0, wolf.attributes().maxHealth());
        assertEquals(4.0, wolf.attributes().attackDamage());

        BeastMoveDefinition bite = wolf.move(0);
        assertEquals(BeastTestData.BITE, bite.id());
        assertEquals("bite", bite.animation());
        assertEquals(new BeastTickRange(10, 12), bite.activeTicks());
        BeastMoveDefinition pounce = wolf.move(1);
        assertEquals(BeastTestData.POUNCE, pounce.id());
        assertEquals("pounce", pounce.animation());
        assertEquals(13, pounce.turnLockTick());
        assertEquals(18, pounce.lunge().tick());
        assertEquals(0.38, pounce.lunge().up());
        assertEquals(1, wolf.moveIndex(BeastTestData.POUNCE).orElseThrow());
    }

    @Test
    void classpathLoadEqualsTheSourceTree() {
        assertEquals(BeastTestData.load(), BeastDefinitions.bundled().all());
    }

    @Test
    void unknownFieldsAreNamed() {
        BeastDataException error = failure(json -> move(json, 1).addProperty("speed", 3));
        assertEquals(WOLF_FILE, error.file());
        assertEquals("moves[1].speed", error.field());

        error = failure(json -> json.getAsJsonObject("chase").addProperty("leap", true));
        assertEquals("chase.leap", error.field());
    }

    @Test
    void missingFieldsAreNamed() {
        BeastDataException error = failure(json -> move(json, 0).remove("turn_lock_tick"));
        assertEquals("moves[0].turn_lock_tick", error.field());
        assertTrue(error.getMessage().contains("is required"), error.getMessage());

        error = failure(json -> move(json, 0).getAsJsonObject("lunge").remove("up"));
        assertEquals("moves[0].lunge.up", error.field());

        error = failure(json -> json.getAsJsonObject("attributes").remove("armor"));
        assertEquals("attributes.armor", error.field());
    }

    @Test
    void inconsistentTickRangesFail() {
        assertEquals("moves[0].active_ticks",
                failure(json -> move(json, 0).add("active_ticks", pair(10, 28))).field(), "last active == total");
        assertEquals("moves[0].active_ticks",
                failure(json -> move(json, 0).add("active_ticks", pair(12, 10))).field());
        assertEquals("moves[1].windup_ticks",
                failure(json -> move(json, 1).addProperty("windup_ticks", 20)).field(), "wind-up past first active tick");
        assertEquals("moves[1].turn_lock_tick",
                failure(json -> move(json, 1).addProperty("turn_lock_tick", 19)).field(), "aim locks after the wind-up");
        assertEquals("moves[1].immune_ticks",
                failure(json -> move(json, 1).add("immune_ticks", pair(10, 50))).field());
        assertEquals("moves[1].lunge.tick",
                failure(json -> move(json, 1).getAsJsonObject("lunge").addProperty("tick", 12)).field(),
                "lunge before the aim lock");
        assertEquals("moves[0].lunge.tick",
                failure(json -> move(json, 0).getAsJsonObject("lunge").addProperty("tick", 13)).field(),
                "lunge after the last active tick");
    }

    @Test
    void badValuesFail() {
        assertEquals("moves[1].lunge.forward_max",
                failure(json -> move(json, 1).getAsJsonObject("lunge").addProperty("forward_max", 0.5)).field());
        assertEquals("moves[0].hit.forward",
                failure(json -> move(json, 0).getAsJsonObject("hit").add("forward", pair(2.0, 1.0))).field());
        assertEquals("moves[0].use_range",
                failure(json -> move(json, 0).add("use_range", pair(3.0, 1.0))).field());
        assertEquals("moves[0].weight", failure(json -> move(json, 0).addProperty("weight", 0)).field());
        assertEquals("moves[0].total_ticks", failure(json -> move(json, 0).addProperty("total_ticks", 12.5)).field());
        assertEquals("attributes.knockback_resistance",
                failure(json -> json.getAsJsonObject("attributes").addProperty("knockback_resistance", 1.5)).field());
        assertEquals("schema", failure(json -> json.addProperty("schema", 2)).field());
        assertEquals("moves[0].animation", failure(json -> move(json, 0).addProperty("animation", "Bite")).field());
    }

    @Test
    void clipNamesMustBeDistinct() {
        assertEquals("moves[1].animation", failure(json -> move(json, 1).addProperty("animation", "bite")).field());
        assertEquals("moves[0].animation", failure(json -> move(json, 0).addProperty("animation", "run")).field());
        assertEquals("moves[0].animation", failure(json -> move(json, 0).addProperty("animation", "stagger")).field());
        assertEquals("stagger.animation",
                failure(json -> json.getAsJsonObject("stagger").addProperty("animation", "idle")).field());
        assertEquals("moves[1].id",
                failure(json -> move(json, 1).addProperty("id", "myvillage:demon_wolf_bite")).field());
    }

    @Test
    void entityMustMatchTheIndex() {
        BeastDataException error = failure(json -> json.addProperty("entity", "myvillage:other_wolf"));
        assertEquals("entity", error.field());
    }

    @Test
    void indexProblemsNameTheIndex() {
        BeastDataException missing = assertThrows(BeastDataException.class, () -> BeastDataLoader.load(path ->
                path.equals(BeastDataLoader.INDEX_PATH)
                        ? stream("{\"schema\": 1, \"beasts\": [\"myvillage:no_such_beast\"]}")
                        : BeastTestData.open(path)));
        assertEquals(BeastDataLoader.INDEX_PATH, missing.file());
        assertEquals("beasts[0]", missing.field());

        BeastDataException duplicateKey = assertThrows(BeastDataException.class, () -> BeastDataLoader.load(path ->
                path.equals(BeastDataLoader.INDEX_PATH)
                        ? stream("{\"schema\": 1, \"schema\": 1, \"beasts\": [\"myvillage:demon_wolf\"]}")
                        : BeastTestData.open(path)));
        assertEquals("schema", duplicateKey.field());
        assertFalse(duplicateKey.getMessage().isBlank());
    }

    private static JsonObject move(JsonObject json, int index) {
        return json.getAsJsonArray("moves").get(index).getAsJsonObject();
    }

    private static JsonArray pair(Number first, Number second) {
        JsonArray array = new JsonArray();
        array.add(first);
        array.add(second);
        return array;
    }

    private static BeastDataException failure(Consumer<JsonObject> mutation) {
        JsonObject json;
        try {
            json = JsonParser.parseString(Files.readString(BeastTestData.RESOURCES.resolve(WOLF_FILE))).getAsJsonObject();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        mutation.accept(json);
        String text = json.toString();
        return assertThrows(BeastDataException.class, () -> BeastDataLoader.load(path ->
                path.equals(WOLF_FILE) ? stream(text) : BeastTestData.open(path)));
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
