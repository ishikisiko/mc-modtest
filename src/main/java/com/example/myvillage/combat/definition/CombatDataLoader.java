package com.example.myvillage.combat.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Reads the bundled combat data (schema 1): {@code data/myvillage/combat/index.json}, which lists
 * style and weapon ids, and one file per id at {@code data/<ns>/combat/style/<path>.json} or
 * {@code data/<ns>/combat/weapon/<path>.json}. Pure functions over JSON: the caller supplies how a
 * path is opened, so the same code reads the mod jar at runtime and the source tree in tests.
 *
 * <p>Unknown fields, missing files, malformed values and invariant violations throw a
 * {@link CombatDataException} that names the file and the field. There is no partial result.
 */
public final class CombatDataLoader {
    public static final int SCHEMA_VERSION = 1;
    public static final String INDEX_PATH = "data/myvillage/combat/index.json";

    private static final Set<String> INDEX_FIELDS = Set.of("schema", "styles", "weapons");
    private static final Set<String> STYLE_FIELDS = Set.of(
            "schema", "id", "combo_timeout_ticks", "minimum_intent_interval_ticks", "animations", "moves");
    private static final Set<String> ANIMATION_FIELDS = Set.of("ready_idle", "mode_enter");
    private static final Set<String> MOVE_FIELDS = Set.of(
            "id", "display_key", "kind", "total_ticks", "active_ticks", "buffer_start_tick", "chain_tick",
            "damage_multiplier", "maximum_targets", "range", "reaction", "hitbox", "step", "feedback", "camera");
    private static final Set<String> REACTION_FIELDS = Set.of("hitstun_ticks", "slide_distance", "lift", "lateral_bias");
    private static final Set<String> HITBOX_FIELDS = Set.of(
            "shape_family", "horizontal_tolerance", "vertical_tolerance", "samples");
    private static final Set<String> SAMPLE_FIELDS = Set.of(
            "tick", "start", "end", "horizontal_radius", "vertical_radius");
    private static final Set<String> THRUST_FIELDS = Set.of("generator", "first_range", "final_range", "radius");
    private static final Set<String> ARC_FIELDS = Set.of(
            "generator", "range", "start_angle", "end_angle", "height", "radius");
    private static final Set<String> DIAGONAL_FIELDS = Set.of("generator", "descending", "radius");
    private static final Set<String> STEP_FIELDS = Set.of("tick", "maximum_distance", "support_depth");
    private static final Set<String> FEEDBACK_FIELDS = Set.of(
            "swing_sound", "swing_pitch", "hit_sound", "heavy_layer_sound", "heavy_hit", "hit_stop_ticks",
            "camera_trauma", "cut_roll_degrees");
    private static final Set<String> CAMERA_FIELDS = Set.of(
            "hit_pitch_kick", "hit_roll_kick", "hit_fov_punch", "swing_lean_degrees", "step_fov_surge");
    private static final Set<String> WEAPON_FIELDS = Set.of("schema", "item", "style", "first_person_rig", "geometry");

    /** Opens one data path (relative, no leading slash); returns null when it does not exist. */
    @FunctionalInterface
    public interface ResourceOpener {
        InputStream open(String path) throws IOException;
    }

    private CombatDataLoader() {
    }

    /** Loads the index and every listed style and weapon, then checks the cross-file rules. */
    public static CombatStyles load(ResourceOpener opener) {
        JsonObject indexJson = read(opener, INDEX_PATH, INDEX_PATH, "<file>");
        Fields index = new Fields(INDEX_PATH, "", indexJson, INDEX_FIELDS);
        index.schema();
        List<ResourceLocation> styleIds = index.idList("styles", true);
        List<ResourceLocation> weaponIds = index.idList("weapons", false);

        List<CombatStyleDefinition> styles = new ArrayList<>();
        Map<ResourceLocation, String> styleFiles = new HashMap<>();
        Map<ResourceLocation, ResourceLocation> moveOwners = new HashMap<>();
        for (int position = 0; position < styleIds.size(); position++) {
            ResourceLocation styleId = styleIds.get(position);
            String file = stylePath(styleId);
            JsonObject json = read(opener, file, INDEX_PATH, "styles[" + position + "]");
            CombatStyleDefinition style = parseStyle(file, styleId, json);
            for (int moveIndex = 0; moveIndex < style.moves().size(); moveIndex++) {
                ResourceLocation moveId = style.move(moveIndex).id();
                ResourceLocation owner = moveOwners.putIfAbsent(moveId, styleId);
                if (owner != null) {
                    throw new CombatDataException(file, "moves[" + moveIndex + "].id",
                            "move id " + moveId + " is declared by both style " + owner + " ("
                                    + styleFiles.get(owner) + ") and style " + styleId);
                }
            }
            styleFiles.put(styleId, file);
            styles.add(style);
        }

        List<WeaponDefinition> weapons = new ArrayList<>();
        Map<ResourceLocation, String> weaponFilesByItem = new HashMap<>();
        for (int position = 0; position < weaponIds.size(); position++) {
            ResourceLocation weaponId = weaponIds.get(position);
            String file = weaponPath(weaponId);
            JsonObject json = read(opener, file, INDEX_PATH, "weapons[" + position + "]");
            WeaponDefinition weapon = parseWeapon(file, json);
            if (!styleFiles.containsKey(weapon.style())) {
                throw new CombatDataException(file, "style",
                        "style " + weapon.style() + " is not listed in " + INDEX_PATH);
            }
            String previous = weaponFilesByItem.putIfAbsent(weapon.item(), file);
            if (previous != null) {
                throw new CombatDataException(file, "item",
                        "item " + weapon.item() + " already has a weapon entry in " + previous);
            }
            weapons.add(weapon);
        }
        return new CombatStyles(styles, weapons);
    }

    public static String stylePath(ResourceLocation styleId) {
        return "data/" + styleId.getNamespace() + "/combat/style/" + styleId.getPath() + ".json";
    }

    public static String weaponPath(ResourceLocation weaponId) {
        return "data/" + weaponId.getNamespace() + "/combat/weapon/" + weaponId.getPath() + ".json";
    }

    /** Parses one style file; {@code expectedId} is the id the index lists it under. */
    public static CombatStyleDefinition parseStyle(String file, ResourceLocation expectedId, JsonObject json) {
        Fields root = new Fields(file, "", json, STYLE_FIELDS);
        root.schema();
        ResourceLocation id = root.id("id");
        if (!id.equals(expectedId)) {
            throw root.error("id", "is " + id + " but the index lists this file as " + expectedId);
        }
        int comboTimeout = root.positiveInteger("combo_timeout_ticks");
        int minimumInterval = root.positiveInteger("minimum_intent_interval_ticks");
        Fields animations = root.object("animations", ANIMATION_FIELDS);
        ResourceLocation readyIdle = animations.id("ready_idle");
        ResourceLocation modeEnter = animations.id("mode_enter");
        JsonArray movesJson = root.array("moves");
        if (movesJson.isEmpty()) {
            throw root.error("moves", "needs at least one move");
        }
        List<AttackMoveDefinition> moves = new ArrayList<>();
        Set<ResourceLocation> moveIds = new HashSet<>();
        for (int index = 0; index < movesJson.size(); index++) {
            String path = "moves[" + index + "]";
            AttackMoveDefinition move = parseMove(new Fields(file, path, movesJson.get(index), MOVE_FIELDS));
            if (!moveIds.add(move.id())) {
                throw new CombatDataException(file, path + ".id", "duplicate move id " + move.id() + " in this style");
            }
            moves.add(move);
        }
        return build(file, "<style>", () -> new CombatStyleDefinition(
                id, readyIdle, modeEnter, comboTimeout, minimumInterval, moves));
    }

    /** Parses one weapon file. */
    public static WeaponDefinition parseWeapon(String file, JsonObject json) {
        Fields root = new Fields(file, "", json, WEAPON_FIELDS);
        root.schema();
        return new WeaponDefinition(
                root.id("item"),
                root.id("style"),
                root.id("first_person_rig"),
                root.id("geometry"));
    }

    private static AttackMoveDefinition parseMove(Fields move) {
        ResourceLocation id = move.id("id");
        String displayKey = move.nonBlankString("display_key");
        String kindName = move.string("kind");
        MoveKind kind = MoveKind.bySerializedName(kindName)
                .orElseThrow(() -> move.error("kind", "must be thrust or cut, not " + kindName));
        int totalTicks = move.positiveInteger("total_ticks");
        int[] active = move.integerPair("active_ticks");
        int activeStart = active[0];
        int activeEnd = active[1];
        if (activeStart < 0 || activeEnd < activeStart || activeEnd >= totalTicks) {
            throw move.error("active_ticks",
                    "must satisfy 0 <= start <= end < total_ticks (" + totalTicks + "), got [" + activeStart + ", "
                            + activeEnd + "]");
        }
        int bufferStart = move.integer("buffer_start_tick");
        if (bufferStart < activeStart || bufferStart >= totalTicks) {
            throw move.error("buffer_start_tick",
                    "must satisfy active start (" + activeStart + ") <= buffer_start_tick < total_ticks ("
                            + totalTicks + "), got " + bufferStart);
        }
        int chainTick = move.integer("chain_tick");
        if (chainTick <= activeEnd || chainTick > totalTicks || chainTick < bufferStart) {
            throw move.error("chain_tick",
                    "must satisfy active end (" + activeEnd + ") < chain_tick <= total_ticks (" + totalTicks
                            + ") and chain_tick >= buffer_start_tick, got " + chainTick);
        }
        double damageMultiplier = move.positiveNumber("damage_multiplier");
        int maximumTargets = move.positiveInteger("maximum_targets");
        double range = move.positiveNumber("range");

        Fields reactionFields = move.object("reaction", REACTION_FIELDS);
        ReactionDefinition reaction = build(reactionFields, () -> new ReactionDefinition(
                reactionFields.integer("hitstun_ticks"),
                reactionFields.number("slide_distance"),
                reactionFields.number("lift"),
                reactionFields.number("lateral_bias")));

        HitboxDefinition hitbox = parseHitbox(move.object("hitbox", HITBOX_FIELDS), activeStart, activeEnd, totalTicks);

        Optional<StepDefinition> step = Optional.empty();
        Optional<Fields> stepFields = move.optionalObject("step", STEP_FIELDS);
        if (stepFields.isPresent()) {
            Fields fields = stepFields.get();
            int stepTick = fields.integer("tick");
            if (stepTick < 0 || stepTick >= totalTicks) {
                throw fields.error("tick", "must lie inside the move (0 <= tick < " + totalTicks + "), got " + stepTick);
            }
            step = Optional.of(build(fields, () -> new StepDefinition(
                    stepTick, fields.number("maximum_distance"), fields.number("support_depth"))));
        }

        Fields feedbackFields = move.object("feedback", FEEDBACK_FIELDS);
        MoveFeedback feedback = build(feedbackFields, () -> new MoveFeedback(
                feedbackFields.id("swing_sound"),
                feedbackFields.floatNumber("swing_pitch"),
                feedbackFields.id("hit_sound"),
                feedbackFields.optionalId("heavy_layer_sound"),
                feedbackFields.bool("heavy_hit"),
                feedbackFields.floatNumber("hit_stop_ticks"),
                feedbackFields.floatNumber("camera_trauma"),
                feedbackFields.floatNumber("cut_roll_degrees")));

        Fields cameraFields = move.object("camera", CAMERA_FIELDS);
        CameraCues camera = build(cameraFields, () -> new CameraCues(
                cameraFields.floatNumber("hit_pitch_kick"),
                cameraFields.floatNumber("hit_roll_kick"),
                cameraFields.floatNumber("hit_fov_punch"),
                cameraFields.floatNumber("swing_lean_degrees"),
                cameraFields.floatNumber("step_fov_surge")));
        if (camera.stepFovSurge() > 0.0F && step.isEmpty()) {
            throw cameraFields.error("step_fov_surge", "is positive but the move has no step");
        }

        Optional<StepDefinition> finalStep = step;
        return build(move, () -> new AttackMoveDefinition(
                id,
                displayKey,
                kind,
                totalTicks,
                activeStart,
                activeEnd,
                damageMultiplier,
                maximumTargets,
                range,
                bufferStart,
                chainTick,
                reaction,
                new AnimationDefinition(id, totalTicks),
                hitbox,
                finalStep,
                feedback,
                camera));
    }

    private static HitboxDefinition parseHitbox(Fields hitbox, int activeStart, int activeEnd, int totalTicks) {
        String shapeFamily = hitbox.nonBlankString("shape_family");
        double horizontalTolerance = hitbox.number("horizontal_tolerance");
        double verticalTolerance = hitbox.number("vertical_tolerance");
        JsonElement samplesJson = hitbox.require("samples");
        List<HitboxSample> samples;
        if (samplesJson.isJsonArray()) {
            samples = explicitSamples(hitbox, samplesJson.getAsJsonArray(), totalTicks);
        } else if (samplesJson.isJsonObject()) {
            samples = generatedSamples(hitbox, samplesJson.getAsJsonObject(), activeStart, activeEnd);
        } else {
            throw hitbox.error("samples", "must be a list of samples or a generator object");
        }
        return build(hitbox, () -> new HitboxDefinition(
                shapeFamily, samples, horizontalTolerance, verticalTolerance));
    }

    private static List<HitboxSample> explicitSamples(Fields hitbox, JsonArray array, int totalTicks) {
        if (array.isEmpty()) {
            throw hitbox.error("samples", "needs at least one sample");
        }
        List<HitboxSample> samples = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            Fields sample = new Fields(hitbox.file, hitbox.field("samples[" + index + "]"), array.get(index), SAMPLE_FIELDS);
            int tick = sample.integer("tick");
            if (tick < 0 || tick >= totalTicks) {
                throw sample.error("tick", "must lie inside the move (0 <= tick < " + totalTicks + "), got " + tick);
            }
            double[] start = sample.vector("start");
            double[] end = sample.vector("end");
            samples.add(build(sample, () -> new HitboxSample(
                    tick, start[0], start[1], start[2], end[0], end[1], end[2],
                    sample.number("horizontal_radius"), sample.number("vertical_radius"))));
        }
        return samples;
    }

    private static List<HitboxSample> generatedSamples(
            Fields hitbox, JsonObject generatorJson, int activeStart, int activeEnd) {
        String path = hitbox.field("samples");
        JsonElement nameJson = generatorJson.get("generator");
        if (nameJson == null || !nameJson.isJsonPrimitive() || !nameJson.getAsJsonPrimitive().isString()) {
            throw new CombatDataException(hitbox.file, path + ".generator", "must name thrust, arc, or diagonal");
        }
        String generator = nameJson.getAsString();
        return switch (generator) {
            case "thrust" -> {
                Fields fields = new Fields(hitbox.file, path, generatorJson, THRUST_FIELDS);
                fields.require("generator");
                yield build(fields, () -> HitboxGenerators.thrust(
                        activeStart, activeEnd,
                        fields.number("first_range"), fields.number("final_range"), fields.number("radius")));
            }
            case "arc" -> {
                Fields fields = new Fields(hitbox.file, path, generatorJson, ARC_FIELDS);
                fields.require("generator");
                yield build(fields, () -> HitboxGenerators.arc(
                        activeStart, activeEnd,
                        fields.number("range"), fields.number("start_angle"), fields.number("end_angle"),
                        fields.number("height"), fields.number("radius")));
            }
            case "diagonal" -> {
                Fields fields = new Fields(hitbox.file, path, generatorJson, DIAGONAL_FIELDS);
                fields.require("generator");
                yield build(fields, () -> HitboxGenerators.diagonal(
                        activeStart, activeEnd, fields.bool("descending"), fields.number("radius")));
            }
            default -> throw new CombatDataException(
                    hitbox.file, path + ".generator", "must name thrust, arc, or diagonal, not " + generator);
        };
    }

    private static JsonObject read(ResourceOpener opener, String file, String listedIn, String listedAt) {
        InputStream stream;
        try {
            stream = opener.open(file);
        } catch (IOException exception) {
            throw new CombatDataException(listedIn, listedAt, "cannot open " + file, exception);
        }
        if (stream == null) {
            throw new CombatDataException(listedIn, listedAt, "listed file " + file + " is missing");
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new CombatDataException(file, "<root>", "must be a JSON object");
            }
            return element.getAsJsonObject();
        } catch (IOException | JsonParseException exception) {
            throw new CombatDataException(file, "<root>", "is not valid JSON: " + exception.getMessage(), exception);
        }
    }

    private static <T> T build(Fields fields, Supplier<T> factory) {
        return build(fields.file, fields.path.isEmpty() ? "<root>" : fields.path, factory);
    }

    private static <T> T build(String file, String field, Supplier<T> factory) {
        try {
            return factory.get();
        } catch (IllegalArgumentException exception) {
            throw new CombatDataException(file, field, exception.getMessage(), exception);
        }
    }

    /** One JSON object whose keys must all be known; every accessor names the file and field. */
    private static final class Fields {
        private final String file;
        private final String path;
        private final JsonObject json;

        private Fields(String file, String path, JsonElement element, Set<String> allowed) {
            this.file = file;
            this.path = path;
            if (element == null || !element.isJsonObject()) {
                throw new CombatDataException(file, path.isEmpty() ? "<root>" : path, "must be a JSON object");
            }
            this.json = element.getAsJsonObject();
            Set<String> unknown = new LinkedHashSet<>(json.keySet());
            unknown.removeAll(allowed);
            if (!unknown.isEmpty()) {
                throw new CombatDataException(file, field(unknown.iterator().next()), "unknown field");
            }
        }

        String field(String key) {
            return path.isEmpty() ? key : path + "." + key;
        }

        CombatDataException error(String key, String problem) {
            return new CombatDataException(file, field(key), problem);
        }

        JsonElement require(String key) {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                throw error(key, "is required");
            }
            return element;
        }

        void schema() {
            int schema = integer("schema");
            if (schema != SCHEMA_VERSION) {
                throw error("schema", "must be " + SCHEMA_VERSION + ", got " + schema);
            }
        }

        private JsonPrimitive primitive(String key, String expected) {
            JsonElement element = require(key);
            if (!element.isJsonPrimitive()) {
                throw error(key, "must be " + expected);
            }
            return element.getAsJsonPrimitive();
        }

        int integer(String key) {
            JsonPrimitive primitive = primitive(key, "an integer");
            if (!primitive.isNumber()) {
                throw error(key, "must be an integer");
            }
            return integerValue(key, primitive.getAsBigDecimal());
        }

        private int integerValue(String key, BigDecimal value) {
            try {
                return value.intValueExact();
            } catch (ArithmeticException exception) {
                throw error(key, "must be an integer, got " + value);
            }
        }

        int positiveInteger(String key) {
            int value = integer(key);
            if (value <= 0) {
                throw error(key, "must be positive, got " + value);
            }
            return value;
        }

        double number(String key) {
            JsonPrimitive primitive = primitive(key, "a number");
            if (!primitive.isNumber()) {
                throw error(key, "must be a number");
            }
            double value = primitive.getAsDouble();
            if (!Double.isFinite(value)) {
                throw error(key, "must be finite");
            }
            return value;
        }

        double positiveNumber(String key) {
            double value = number(key);
            if (!(value > 0.0)) {
                throw error(key, "must be positive, got " + value);
            }
            return value;
        }

        float floatNumber(String key) {
            return (float) number(key);
        }

        boolean bool(String key) {
            JsonPrimitive primitive = primitive(key, "true or false");
            if (!primitive.isBoolean()) {
                throw error(key, "must be true or false");
            }
            return primitive.getAsBoolean();
        }

        String string(String key) {
            JsonPrimitive primitive = primitive(key, "a string");
            if (!primitive.isString()) {
                throw error(key, "must be a string");
            }
            return primitive.getAsString();
        }

        String nonBlankString(String key) {
            String value = string(key);
            if (value.isBlank()) {
                throw error(key, "must not be blank");
            }
            return value;
        }

        ResourceLocation id(String key) {
            return parseId(key, string(key));
        }

        Optional<ResourceLocation> optionalId(String key) {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                return Optional.empty();
            }
            return Optional.of(id(key));
        }

        private ResourceLocation parseId(String key, String value) {
            int separator = value.indexOf(':');
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (separator <= 0 || separator == value.length() - 1 || id == null) {
                throw error(key, "must be a namespaced id like myvillage:name, got \"" + value + "\"");
            }
            return id;
        }

        List<ResourceLocation> idList(String key, boolean requireOne) {
            JsonArray array = array(key);
            if (requireOne && array.isEmpty()) {
                throw error(key, "needs at least one entry");
            }
            List<ResourceLocation> ids = new ArrayList<>();
            for (int index = 0; index < array.size(); index++) {
                String entryKey = key + "[" + index + "]";
                JsonElement element = array.get(index);
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw error(entryKey, "must be a string id");
                }
                ResourceLocation id = parseId(entryKey, element.getAsString());
                if (ids.contains(id)) {
                    throw error(entryKey, "lists " + id + " twice");
                }
                ids.add(id);
            }
            return ids;
        }

        JsonArray array(String key) {
            JsonElement element = require(key);
            if (!element.isJsonArray()) {
                throw error(key, "must be a list");
            }
            return element.getAsJsonArray();
        }

        int[] integerPair(String key) {
            JsonArray array = array(key);
            if (array.size() != 2) {
                throw error(key, "must be [start, end]");
            }
            int[] values = new int[2];
            for (int index = 0; index < 2; index++) {
                JsonElement element = array.get(index);
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                    throw error(key, "must be [start, end] integers");
                }
                values[index] = integerValue(key, element.getAsBigDecimal());
            }
            return values;
        }

        double[] vector(String key) {
            JsonArray array = array(key);
            if (array.size() != 3) {
                throw error(key, "must be [x, y, z]");
            }
            double[] values = new double[3];
            for (int index = 0; index < 3; index++) {
                JsonElement element = array.get(index);
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
                        || !Double.isFinite(element.getAsDouble())) {
                    throw error(key, "must be [x, y, z] finite numbers");
                }
                values[index] = element.getAsDouble();
            }
            return values;
        }

        Fields object(String key, Set<String> allowed) {
            return new Fields(file, field(key), require(key), allowed);
        }

        Optional<Fields> optionalObject(String key, Set<String> allowed) {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                return Optional.empty();
            }
            return Optional.of(new Fields(file, field(key), element, allowed));
        }
    }
}
