package com.example.myvillage.combat.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class CombatDataLoaderTest {
    private static final String INDEX = CombatDataLoader.INDEX_PATH;
    private static final String STYLE = "data/myvillage/combat/style/basic_sword.json";
    private static final String WEAPON = "data/myvillage/combat/weapon/qingfeng_sword.json";

    @Test
    void loadsTheBundledIndexStyleAndWeapon() {
        CombatStyles styles = CombatDataLoader.load(CombatTestData::open);
        assertEquals(1, styles.styles().size());
        assertEquals(1, styles.weapons().size());
        CombatStyleDefinition style = styles.styles().getFirst();
        assertEquals(CombatTestData.BASIC_SWORD, style.id());
        assertEquals(5, style.moves().size());
        WeaponDefinition weapon = styles.weapons().getFirst();
        assertEquals(CombatTestData.QINGFENG_SWORD, weapon.item());
        assertEquals(CombatTestData.BASIC_SWORD, weapon.style());
        assertEquals(id("combat/qingfeng_first_person.json"), weapon.firstPersonRig());
        assertEquals(id("combat/qingfeng_sword_geometry.json"), weapon.geometry());
    }

    @Test
    void classpathLoadEqualsTheSourceTree() {
        CombatStyles bundled = CombatStyles.bundled();
        CombatStyles source = CombatTestData.styles();
        assertEquals(source.styles(), bundled.styles());
        assertEquals(source.weapons(), bundled.weapons());
    }

    @Test
    void explicitSamplesLoadAsWritten() {
        Files files = Files.bundled();
        files.edit(STYLE, style -> {
            JsonObject hitbox = move(style, 0).getAsJsonObject("hitbox");
            JsonArray samples = new JsonArray();
            JsonObject sample = new JsonObject();
            sample.addProperty("tick", 3);
            sample.add("start", vector(0.0, 1.0, 0.5));
            sample.add("end", vector(0.25, 1.25, 2.5));
            sample.addProperty("horizontal_radius", 0.2);
            sample.addProperty("vertical_radius", 0.3);
            samples.add(sample);
            hitbox.add("samples", samples);
        });
        HitboxDefinition hitbox = files.load().styles().getFirst().move(0).hitbox();
        assertEquals(List.of(new HitboxSample(3, 0.0, 1.0, 0.5, 0.25, 1.25, 2.5, 0.2, 0.3)), hitbox.samples());
    }

    @Test
    void unknownFieldsAreRejectedWithFileAndField() {
        assertRejected(STYLE, "moves[0].chain_tik", "unknown field",
                files -> files.edit(STYLE, style -> move(style, 0).addProperty("chain_tik", 7)));
        assertRejected(STYLE, "moves[3].camera.lean", "unknown field",
                files -> files.edit(STYLE, style -> move(style, 3).getAsJsonObject("camera").addProperty("lean", 1.0)));
        assertRejected(STYLE, "moves[1].hitbox.samples.range", "unknown field",
                files -> files.edit(STYLE, style -> move(style, 1).getAsJsonObject("hitbox")
                        .getAsJsonObject("samples").addProperty("generator", "thrust")));
        assertRejected(WEAPON, "colour", "unknown field",
                files -> files.edit(WEAPON, weapon -> weapon.addProperty("colour", "green")));
        assertRejected(INDEX, "extra", "unknown field",
                files -> files.edit(INDEX, index -> index.addProperty("extra", true)));
    }

    @Test
    void timingInvariantsAreRejectedWithTheField() {
        assertRejected(STYLE, "moves[0].chain_tick", "active end",
                files -> files.edit(STYLE, style -> move(style, 0).addProperty("chain_tick", 4)));
        assertRejected(STYLE, "moves[0].chain_tick", "total_ticks",
                files -> files.edit(STYLE, style -> move(style, 0).addProperty("chain_tick", 12)));
        assertRejected(STYLE, "moves[2].buffer_start_tick", "active start",
                files -> files.edit(STYLE, style -> move(style, 2).addProperty("buffer_start_tick", 4)));
        assertRejected(STYLE, "moves[1].active_ticks", "total_ticks",
                files -> files.edit(STYLE, style -> {
                    JsonArray active = new JsonArray();
                    active.add(4);
                    active.add(13);
                    move(style, 1).add("active_ticks", active);
                }));
        assertRejected(STYLE, "moves[4].step.tick", "inside the move",
                files -> files.edit(STYLE, style -> move(style, 4).getAsJsonObject("step").addProperty("tick", 20)));
        assertRejected(STYLE, "moves[4].step", "1.6",
                files -> files.edit(STYLE, style -> move(style, 4).getAsJsonObject("step")
                        .addProperty("maximum_distance", 1.7)));
        assertRejected(STYLE, "moves[4].camera.step_fov_surge", "no step",
                files -> files.edit(STYLE, style -> move(style, 4).remove("step")));
        assertRejected(STYLE, "combo_timeout_ticks", "positive",
                files -> files.edit(STYLE, style -> style.addProperty("combo_timeout_ticks", 0)));
        assertRejected(STYLE, "moves[0].total_ticks", "integer",
                files -> files.edit(STYLE, style -> move(style, 0).addProperty("total_ticks", 11.5)));
    }

    @Test
    void malformedValuesAreRejected() {
        assertRejected(STYLE, "moves[0].feedback.swing_sound", "namespaced id",
                files -> files.edit(STYLE, style -> move(style, 0).getAsJsonObject("feedback")
                        .addProperty("swing_sound", "Combat Sword Thrust")));
        assertRejected(STYLE, "moves[0].feedback.hit_sound", "namespaced id",
                files -> files.edit(STYLE, style -> move(style, 0).getAsJsonObject("feedback")
                        .addProperty("hit_sound", "combat.sword.hit")));
        assertRejected(STYLE, "moves[3].feedback.heavy_layer_sound", "namespaced id",
                files -> files.edit(STYLE, style -> move(style, 3).getAsJsonObject("feedback")
                        .addProperty("heavy_layer_sound", "myvillage:")));
        assertRejected(STYLE, "moves[0].kind", "thrust or cut",
                files -> files.edit(STYLE, style -> move(style, 0).addProperty("kind", "stab")));
        assertRejected(STYLE, "moves[0].hitbox.samples.generator", "thrust, arc, or diagonal",
                files -> files.edit(STYLE, style -> move(style, 0).getAsJsonObject("hitbox")
                        .getAsJsonObject("samples").addProperty("generator", "spiral")));
        assertRejected(STYLE, "moves[0].feedback", "Hit-stop",
                files -> files.edit(STYLE, style -> move(style, 0).getAsJsonObject("feedback")
                        .addProperty("hit_stop_ticks", 9.0)));
        assertRejected(STYLE, "moves[0].display_key", "required",
                files -> files.edit(STYLE, style -> move(style, 0).remove("display_key")));
        assertRejected(STYLE, "schema", "must be 1",
                files -> files.edit(STYLE, style -> style.addProperty("schema", 2)));
        assertRejected(STYLE, "id", "index lists",
                files -> files.edit(STYLE, style -> style.addProperty("id", "myvillage:other_sword")));
        assertRejected(STYLE, "<root>", "JSON",
                files -> files.put(STYLE, "{\"schema\": 1,"));
    }

    @Test
    void lenientJsonIsRejected() {
        String style = Files.bundled().contents(STYLE);
        // A duplicate key: a lenient parser would silently keep the second chain tick.
        assertRejected(STYLE, "moves[0].chain_tick", "duplicate key",
                files -> files.put(STYLE, style.replaceFirst("\"chain_tick\": 7,", "\"chain_tick\": 7, \"chain_tick\": 8,")));
        assertRejected(STYLE, "id", "duplicate key",
                files -> files.put(STYLE, style.replaceFirst("\"id\": \"myvillage:basic_sword\",",
                        "\"id\": \"myvillage:basic_sword\", \"id\": \"myvillage:basic_sword\",")));
        assertRejected(WEAPON, "<root>", "not valid JSON",
                files -> files.put(WEAPON, "// comment\n" + files.contents(WEAPON)));
        assertRejected(WEAPON, "<root>", "not valid JSON",
                files -> files.put(WEAPON, files.contents(WEAPON).replace("\"item\"", "item")));
        assertRejected(WEAPON, "<root>", "not valid JSON",
                files -> files.put(WEAPON, files.contents(WEAPON).replace("\"myvillage:basic_sword\"", "'myvillage:basic_sword'")));
        assertRejected(WEAPON, "<root>", "not valid JSON",
                files -> files.put(WEAPON, files.contents(WEAPON) + "{}"));
        assertRejected(STYLE, "<root>", "not valid JSON",
                files -> files.put(STYLE, style.replaceFirst("\"range\": 3.0", "\"range\": NaN")));
        assertRejected(INDEX, "<root>", "must be a JSON object", files -> files.put(INDEX, "[]"));
    }

    @Test
    void missingListedFilesAreRejected() {
        assertRejected(INDEX, "styles[1]", "data/myvillage/combat/style/missing_sword.json is missing",
                files -> files.edit(INDEX, index -> index.getAsJsonArray("styles").add("myvillage:missing_sword")));
        assertRejected(INDEX, "weapons[0]", "data/myvillage/combat/weapon/qingfeng_sword.json is missing",
                files -> files.remove(WEAPON));
        assertRejected(INDEX, "<file>", "data/myvillage/combat/index.json is missing",
                files -> files.remove(INDEX));
    }

    @Test
    void duplicateMoveIdsAcrossStylesNameBothStyles() {
        String other = "data/myvillage/combat/style/other_sword.json";
        CombatDataException failure = assertRejected(other, "moves[0].id", "myvillage:basic_sword", files -> {
            JsonObject copy = files.json(STYLE).deepCopy();
            copy.addProperty("id", "myvillage:other_sword");
            files.put(other, copy.toString());
            files.edit(INDEX, index -> index.getAsJsonArray("styles").add("myvillage:other_sword"));
        });
        assertTrue(failure.getMessage().contains("myvillage:other_sword"), failure.getMessage());
    }

    @Test
    void weaponCrossReferencesAreChecked() {
        assertRejected(WEAPON, "style", "not listed",
                files -> files.edit(WEAPON, weapon -> weapon.addProperty("style", "myvillage:spear")));
        String twin = "data/myvillage/combat/weapon/twin.json";
        assertRejected(twin, "item", "already has a weapon entry", files -> {
            files.put(twin, files.json(WEAPON).toString());
            files.edit(INDEX, index -> index.getAsJsonArray("weapons").add("myvillage:twin"));
        });
    }

    @Test
    void twoWeaponsMayShareAStyle() {
        Files files = Files.bundled();
        String twin = "data/myvillage/combat/weapon/twin.json";
        JsonObject weapon = files.json(WEAPON).deepCopy();
        weapon.addProperty("item", "myvillage:twin_sword");
        weapon.addProperty("geometry", "myvillage:combat/twin_geometry.json");
        files.put(twin, weapon.toString());
        files.edit(INDEX, index -> index.getAsJsonArray("weapons").add("myvillage:twin"));
        CombatStyles styles = files.load();
        assertEquals(2, styles.weapons().size());
        assertEquals(styles.styleForItem(CombatTestData.QINGFENG_SWORD), styles.styleForItem(id("twin_sword")));
        assertFalse(styles.styleForItem(id("stick")).isPresent());
    }

    private static CombatDataException assertRejected(
            String file, String field, String messagePart, Consumer<Files> mutation) {
        Files files = Files.bundled();
        mutation.accept(files);
        CombatDataException failure = assertThrows(CombatDataException.class, files::load);
        assertEquals(file, failure.file(), failure.getMessage());
        assertEquals(field, failure.field(), failure.getMessage());
        assertTrue(failure.getMessage().contains(messagePart), failure.getMessage());
        assertTrue(failure.getMessage().contains(file), failure.getMessage());
        return failure;
    }

    private static JsonObject move(JsonObject style, int index) {
        return style.getAsJsonArray("moves").get(index).getAsJsonObject();
    }

    private static JsonArray vector(double x, double y, double z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }

    /** An editable in-memory copy of the bundled data files. */
    private static final class Files {
        private final Map<String, String> contents = new HashMap<>();

        static Files bundled() {
            Files files = new Files();
            for (String path : List.of(INDEX, STYLE, WEAPON)) {
                try (InputStream stream = CombatTestData.open(path)) {
                    files.contents.put(path, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }
            return files;
        }

        String contents(String path) {
            return contents.get(path);
        }

        JsonObject json(String path) {
            JsonElement element = JsonParser.parseString(contents.get(path));
            return element.getAsJsonObject();
        }

        void edit(String path, Consumer<JsonObject> edit) {
            JsonObject json = json(path);
            edit.accept(json);
            contents.put(path, json.toString());
        }

        void put(String path, String content) {
            contents.put(path, content);
        }

        void remove(String path) {
            contents.remove(path);
        }

        CombatStyles load() {
            return CombatDataLoader.load(path -> {
                String content = contents.get(path);
                return content == null ? null : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            });
        }
    }
}
