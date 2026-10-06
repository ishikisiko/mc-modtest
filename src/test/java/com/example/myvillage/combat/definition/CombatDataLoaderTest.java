package com.example.myvillage.combat.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.runtime.CombatGeometry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class CombatDataLoaderTest {
    private static final String INDEX = CombatDataLoader.INDEX_PATH;
    private static final String STYLE = "data/myvillage/combat/style/basic_sword.json";
    private static final String WEAPON = "data/myvillage/combat/weapon/qingfeng_sword.json";
    private static final ResourceLocation QINGFENG_FILE = ResourceLocation.fromNamespaceAndPath("myvillage", "qingfeng_sword");

    @Test
    void loadsTheBundledIndexStyleAndWeapon() {
        CombatStyles styles = CombatDataLoader.load(CombatTestData::open);
        // Every listed style and weapon loads, in the order the index lists them.
        assertEquals(indexIds("styles"), styles.styles().stream().map(CombatStyleDefinition::id).toList());
        List<ResourceLocation> weaponFiles = indexIds("weapons");
        assertEquals(weaponFiles.size(), styles.weapons().size());
        CombatStyleDefinition style = styles.style(CombatTestData.BASIC_SWORD).orElseThrow();
        assertEquals(5, style.moves().size());
        // The weapon file id is the index entry; the item id is a field inside the file.
        WeaponDefinition weapon = styles.weapons().get(weaponFiles.indexOf(QINGFENG_FILE));
        assertEquals(CombatTestData.QINGFENG_SWORD, weapon.item());
        assertEquals(weapon, styles.weapon(CombatTestData.QINGFENG_SWORD).orElseThrow());
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
        HitboxDefinition hitbox = files.load().style(CombatTestData.BASIC_SWORD).orElseThrow().move(0).hitbox();
        assertEquals(List.of(new HitboxSample(3, 0.0, 1.0, 0.5, 0.25, 1.25, 2.5, 0.2, 0.3)), hitbox.samples());
    }

    @Test
    void explicitSamplesMustBeListedInTickOrder() {
        // Several samples on one tick are fine; a tick lower than the one before is a load error.
        Files files = Files.bundled();
        files.edit(STYLE, style -> move(style, 0).getAsJsonObject("hitbox").add("samples", samplesAt(3, 4, 4, 5)));
        assertEquals(List.of(3, 4, 4, 5), files.load().style(CombatTestData.BASIC_SWORD).orElseThrow().move(0)
                .hitbox().samples().stream().map(HitboxSample::actionTick).toList());
        assertRejected(STYLE, "moves[0].hitbox.samples[2].tick", "non-decreasing tick order",
                rejected -> rejected.edit(STYLE, style -> move(style, 0).getAsJsonObject("hitbox")
                        .add("samples", samplesAt(5, 6, 5))));
    }

    private static JsonArray samplesAt(int... ticks) {
        JsonArray samples = new JsonArray();
        for (int tick : ticks) {
            JsonObject sample = new JsonObject();
            sample.addProperty("tick", tick);
            sample.add("start", vector(0.0, 1.0, 0.5));
            sample.add("end", vector(0.25, 1.25, 2.5));
            sample.addProperty("horizontal_radius", 0.2);
            sample.addProperty("vertical_radius", 0.3);
            samples.add(sample);
        }
        return samples;
    }

    @Test
    void withoutATrailBlockTheWorldTrailFollowsTheHitSamples() {
        for (CombatStyleDefinition style : CombatTestData.styles().styles()) {
            for (AttackMoveDefinition move : style.moves()) {
                assertTrue(move.trail().isEmpty(), move.id() + ": no shipped move draws a separate trail");
                assertEquals(move.hitbox().samples(), move.worldTrailSamples(), move.id().toString());
            }
        }
    }

    @Test
    void trailSamplesRedirectOnlyTheWorldTrail() {
        AttackMoveDefinition original = CombatTestData.basicSword().move(1);
        // The hit samples mirrored left to right, three per active tick: a path no hit sample takes.
        JsonArray path = new JsonArray();
        for (HitboxSample hit : original.hitbox().samples()) {
            for (int repeat = 0; repeat < 3; repeat++) {
                JsonObject sample = new JsonObject();
                sample.addProperty("tick", hit.actionTick());
                sample.add("start", vector(-hit.startX(), hit.startY(), hit.startZ()));
                sample.add("end", vector(-hit.endX() - 0.1 * repeat, hit.endY(), hit.endZ()));
                sample.addProperty("horizontal_radius", hit.horizontalRadius());
                sample.addProperty("vertical_radius", hit.verticalRadius());
                path.add(sample);
            }
        }
        Files files = Files.bundled();
        files.edit(STYLE, style -> {
            JsonObject trail = new JsonObject();
            trail.add("samples", path);
            move(style, 1).add("trail", trail);
        });
        CombatStyleDefinition loaded = files.load().style(CombatTestData.BASIC_SWORD).orElseThrow();
        AttackMoveDefinition moved = loaded.move(1);

        // The world trail reads the new path ...
        List<HitboxSample> drawn = moved.worldTrailSamples();
        assertEquals(path.size(), drawn.size());
        assertEquals(drawn, moved.trail().orElseThrow().samples());
        assertEquals(-original.hitbox().samples().getFirst().endX(), drawn.getFirst().endX(), 1.0E-12);
        // ... and nothing else changes: the hit samples, every other field and every other move load
        // as before, so the server, which reads hitbox() and never trail(), resolves the same hits.
        assertEquals(original.hitbox(), moved.hitbox());
        assertEquals(original, withoutTrail(moved));
        for (int index = 0; index < loaded.moves().size(); index++) {
            if (index != 1) {
                assertEquals(CombatTestData.basicSword().move(index), loaded.move(index));
            }
        }
        // The server's active samples (CombatHitResolver: hitbox().samplesAt, turned to the world)
        // and their contacts with targets on the hit path and on the trail path are the same.
        Vec3 origin = new Vec3(10.0, 64.0, -3.0);
        List<AABB> targets = List.of(
                box(origin, original.hitbox().samples().getLast()), box(origin, drawn.getFirst()));
        for (int tick = moved.activeStartTick(); tick <= moved.activeEndTick(); tick++) {
            for (float yaw : new float[] {0.0F, 73.0F}) {
                List<CombatGeometry.WorldSample> before = world(original, tick, origin, yaw);
                List<CombatGeometry.WorldSample> after = world(moved, tick, origin, yaw);
                assertEquals(before, after, "tick " + tick);
                for (AABB target : targets) {
                    for (int index = 0; index < before.size(); index++) {
                        assertEquals(
                                CombatGeometry.firstContact(before.get(index), target, 0.1, 0.1),
                                CombatGeometry.firstContact(after.get(index), target, 0.1, 0.1),
                                "tick " + tick + " sample " + index);
                    }
                }
            }
        }
    }

    @Test
    void malformedTrailSamplesAreRejected() {
        assertRejected(STYLE, "moves[1].trail.samples[2].tick", "non-decreasing tick order",
                files -> files.edit(STYLE, style -> move(style, 1).add("trail", trail(samplesAt(5, 6, 5)))));
        assertRejected(STYLE, "moves[1].trail.samples", "needs at least one sample",
                files -> files.edit(STYLE, style -> move(style, 1).add("trail", trail(new JsonArray()))));
        assertRejected(STYLE, "moves[1].trail.samples", "is required",
                files -> files.edit(STYLE, style -> move(style, 1).add("trail", new JsonObject())));
        assertRejected(STYLE, "moves[1].trail.path", "unknown field", files -> files.edit(STYLE, style -> {
            JsonObject trail = trail(samplesAt(4, 5, 6));
            trail.add("path", samplesAt(4));
            move(style, 1).add("trail", trail);
        }));
        assertRejected(STYLE, "moves[1].trail", "must be a JSON object",
                files -> files.edit(STYLE, style -> move(style, 1).add("trail", samplesAt(4, 5, 6))));
        assertRejected(STYLE, "moves[1].trail.samples[0].tick", "must lie inside the move",
                files -> files.edit(STYLE, style -> move(style, 1).add("trail", trail(samplesAt(40)))));
        assertRejected(STYLE, "moves[1].trail.samples[0].end", "must be [x, y, z]", files -> files.edit(STYLE, style -> {
            JsonArray samples = samplesAt(4);
            samples.get(0).getAsJsonObject().add("end", new JsonArray());
            move(style, 1).add("trail", trail(samples));
        }));
        assertRejected(STYLE, "moves[1].trail.samples[0]", "radii must be positive", files -> files.edit(STYLE, style -> {
            JsonArray samples = samplesAt(4);
            samples.get(0).getAsJsonObject().addProperty("vertical_radius", 0.0);
            move(style, 1).add("trail", trail(samples));
        }));
        assertRejected(STYLE, "moves[1].trail.samples.generator", "thrust, arc, or diagonal", files -> files.edit(STYLE,
                style -> {
                    JsonObject generator = new JsonObject();
                    generator.addProperty("generator", "spiral");
                    move(style, 1).add("trail", trail(generator));
                }));
    }

    @Test
    void onlyTheWorldTrailReadsTheTrailSamples() throws IOException {
        // Presentation only: outside the definitions, the one reader is the client's world trail.
        Path sources = Path.of("src/main/java/com/example/myvillage");
        List<String> readers;
        try (Stream<Path> files = java.nio.file.Files.walk(sources)) {
            readers = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(sources.resolve("combat/definition")))
                    .filter(path -> {
                        try {
                            String source = java.nio.file.Files.readString(path);
                            return source.contains("worldTrailSamples(") || source.contains(".trail()")
                                    || source.contains("TrailDefinition");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(path -> sources.relativize(path).toString().replace('\\', '/'))
                    .toList();
        }
        assertEquals(List.of("client/combat/CombatWorldTrails.java"), readers);
    }

    private static JsonObject trail(JsonElement samples) {
        JsonObject trail = new JsonObject();
        trail.add("samples", samples);
        return trail;
    }

    private static AttackMoveDefinition withoutTrail(AttackMoveDefinition move) {
        return new AttackMoveDefinition(move.id(), move.displayKey(), move.kind(), move.totalTicks(),
                move.activeStartTick(), move.activeEndTick(), move.damageMultiplier(), move.maximumTargets(),
                move.range(), move.bufferStartTick(), move.chainTick(), move.reaction(), move.animation(),
                move.hitbox(), move.step(), move.feedback(), move.camera());
    }

    private static List<CombatGeometry.WorldSample> world(AttackMoveDefinition move, int tick, Vec3 origin, float yaw) {
        return move.hitbox().samplesAt(tick).stream()
                .map(sample -> CombatGeometry.transform(sample, origin, yaw))
                .toList();
    }

    /** A target box around a sample's far end, in front of an attacker facing yaw 0 at {@code origin}. */
    private static AABB box(Vec3 origin, HitboxSample sample) {
        Vec3 end = CombatGeometry.transform(sample, origin, 0.0F).end();
        return new AABB(end.x - 0.3, end.y - 0.9, end.z - 0.3, end.x + 0.3, end.y + 0.9, end.z + 0.3);
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
        // The field is the missing entry's position in the index: appended after the bundled styles.
        assertRejected(INDEX, "styles[" + indexIds("styles").size() + "]",
                "data/myvillage/combat/style/missing_sword.json is missing",
                files -> files.edit(INDEX, index -> index.getAsJsonArray("styles").add("myvillage:missing_sword")));
        assertRejected(INDEX, "weapons[" + indexIds("weapons").indexOf(QINGFENG_FILE) + "]",
                "data/myvillage/combat/weapon/qingfeng_sword.json is missing",
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
    void pairedIsAnOptionalBoolean() {
        Files files = Files.bundled();
        CombatStyles styles = files.load();
        assertFalse(styles.weapon(CombatTestData.QINGFENG_SWORD).orElseThrow().paired(), "defaults to false");
        assertTrue(styles.weapon(CombatTestData.XUANTIE_GAUNTLET).orElseThrow().paired());
        files.edit(WEAPON, weapon -> weapon.addProperty("paired", true));
        assertTrue(files.load().weapon(CombatTestData.QINGFENG_SWORD).orElseThrow().paired());
        assertRejected(WEAPON, "paired", "true or false",
                bad -> bad.edit(WEAPON, weapon -> weapon.addProperty("paired", "yes")));
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
        assertEquals(indexIds("weapons").size() + 1, styles.weapons().size());
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

    /** The style or weapon ids the bundled index lists under {@code key}, in order. */
    private static List<ResourceLocation> indexIds(String key) {
        return Files.bundled().json(INDEX).getAsJsonArray(key).asList().stream()
                .map(element -> ResourceLocation.parse(element.getAsString()))
                .toList();
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

    /** An editable in-memory copy of the bundled data files: the index and every file it lists. */
    private static final class Files {
        private final Map<String, String> contents = new HashMap<>();

        static Files bundled() {
            Files files = new Files();
            files.copy(INDEX);
            JsonObject index = files.json(INDEX);
            for (JsonElement style : index.getAsJsonArray("styles")) {
                files.copy(CombatDataLoader.stylePath(ResourceLocation.parse(style.getAsString())));
            }
            for (JsonElement weapon : index.getAsJsonArray("weapons")) {
                files.copy(CombatDataLoader.weaponPath(ResourceLocation.parse(weapon.getAsString())));
            }
            for (String path : List.of(STYLE, WEAPON)) {
                if (!files.contents.containsKey(path)) {
                    throw new IllegalStateException("The bundled index no longer lists " + path);
                }
            }
            return files;
        }

        private void copy(String path) {
            try (InputStream stream = CombatTestData.open(path)) {
                if (stream == null) {
                    throw new IllegalStateException(path + " is missing from the source tree");
                }
                contents.put(path, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
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
