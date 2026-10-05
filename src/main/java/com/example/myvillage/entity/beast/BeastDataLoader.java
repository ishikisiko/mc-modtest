package com.example.myvillage.entity.beast;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Reads the bundled beast data (schema 1): {@code data/myvillage/beast/index.json}, which lists
 * beast entity ids, and one file per id at {@code data/<ns>/beast/<path>.json}. Pure functions over
 * JSON: the caller supplies how a path is opened, so the same code reads the mod jar at runtime and
 * the source tree in tests.
 *
 * <p>Unknown or missing fields, malformed values and inconsistent tick ranges throw a
 * {@link BeastDataException} that names the file and the field. There is no partial result.
 */
public final class BeastDataLoader {
    public static final int SCHEMA_VERSION = BeastJson.SCHEMA_VERSION;
    public static final String INDEX_PATH = "data/myvillage/beast/index.json";

    private static final Set<String> INDEX_FIELDS = Set.of("schema", "beasts");
    private static final Set<String> BEAST_FIELDS = Set.of("schema", "entity", "attributes", "chase", "stagger", "moves");
    private static final Set<String> ATTRIBUTE_FIELDS = Set.of(
            "max_health", "attack_damage", "movement_speed", "follow_range", "armor", "knockback_resistance",
            "step_height");
    private static final Set<String> CHASE_FIELDS = Set.of("speed_modifier", "move_gap_ticks", "cancelled_cooldown_ticks");
    private static final Set<String> STAGGER_FIELDS = Set.of("animation");
    private static final Set<String> MOVE_FIELDS = Set.of(
            "id", "animation", "total_ticks", "windup_ticks", "turn_lock_tick", "active_ticks", "immune_ticks",
            "damage_multiplier", "maximum_targets", "use_range", "cooldown_ticks", "weight", "lunge", "hit",
            "knockback");
    private static final Set<String> LUNGE_FIELDS = Set.of("tick", "forward_min", "forward_max", "up");
    private static final Set<String> HIT_FIELDS = Set.of("forward", "half_width", "height");
    private static final Set<String> KNOCKBACK_FIELDS = Set.of("strength", "lift");

    /** Opens one data path (relative, no leading slash); returns null when it does not exist. */
    @FunctionalInterface
    public interface ResourceOpener {
        InputStream open(String path) throws IOException;
    }

    private BeastDataLoader() {
    }

    /** Loads the index and every listed beast file. */
    public static List<BeastDefinition> load(ResourceOpener opener) {
        JsonObject indexJson = read(opener, INDEX_PATH, INDEX_PATH, "<file>");
        BeastJson.Fields index = new BeastJson.Fields(INDEX_PATH, "", indexJson, INDEX_FIELDS);
        index.schema();
        List<ResourceLocation> ids = index.idList("beasts");
        List<BeastDefinition> beasts = new ArrayList<>();
        Set<ResourceLocation> moveIds = new HashSet<>();
        for (int position = 0; position < ids.size(); position++) {
            ResourceLocation id = ids.get(position);
            String file = beastPath(id);
            BeastDefinition beast = parseBeast(file, id, read(opener, file, INDEX_PATH, "beasts[" + position + "]"));
            for (int moveIndex = 0; moveIndex < beast.moves().size(); moveIndex++) {
                ResourceLocation moveId = beast.move(moveIndex).id();
                if (!moveIds.add(moveId)) {
                    throw new BeastDataException(file, "moves[" + moveIndex + "].id",
                            "move id " + moveId + " is already declared by another beast");
                }
            }
            beasts.add(beast);
        }
        return List.copyOf(beasts);
    }

    public static String beastPath(ResourceLocation entityId) {
        return "data/" + entityId.getNamespace() + "/beast/" + entityId.getPath() + ".json";
    }

    /** Parses one beast file; {@code expectedId} is the entity id the index lists it under. */
    public static BeastDefinition parseBeast(String file, ResourceLocation expectedId, JsonObject json) {
        BeastJson.Fields root = new BeastJson.Fields(file, "", json, BEAST_FIELDS);
        root.schema();
        ResourceLocation entity = root.id("entity");
        if (!entity.equals(expectedId)) {
            throw root.error("entity", "is " + entity + " but the index lists this file as " + expectedId);
        }
        BeastJson.Fields attributes = root.object("attributes", ATTRIBUTE_FIELDS);
        BeastDefinition.Attributes attributeValues = BeastJson.build(attributes, () -> new BeastDefinition.Attributes(
                attributes.positiveNumber("max_health"),
                attributes.nonNegativeNumber("attack_damage"),
                attributes.positiveNumber("movement_speed"),
                attributes.positiveNumber("follow_range"),
                attributes.nonNegativeNumber("armor"),
                attributes.fraction("knockback_resistance"),
                attributes.nonNegativeNumber("step_height")));
        BeastJson.Fields chase = root.object("chase", CHASE_FIELDS);
        BeastDefinition.Chase chaseValues = BeastJson.build(chase, () -> new BeastDefinition.Chase(
                chase.positiveNumber("speed_modifier"),
                chase.nonNegativeInteger("move_gap_ticks"),
                chase.nonNegativeInteger("cancelled_cooldown_ticks")));
        String staggerClip = root.object("stagger", STAGGER_FIELDS).clipName("animation");
        if (BeastDefinition.RESERVED_CLIPS.contains(staggerClip)) {
            throw root.error("stagger.animation", "must not be one of the reserved clips " + BeastDefinition.RESERVED_CLIPS);
        }

        JsonArray movesJson = root.array("moves");
        if (movesJson.isEmpty()) {
            throw root.error("moves", "needs at least one move");
        }
        List<BeastMoveDefinition> moves = new ArrayList<>();
        Set<ResourceLocation> ids = new HashSet<>();
        Set<String> clips = new HashSet<>(BeastDefinition.RESERVED_CLIPS);
        clips.add(staggerClip);
        for (int index = 0; index < movesJson.size(); index++) {
            String path = "moves[" + index + "]";
            BeastMoveDefinition move = parseMove(new BeastJson.Fields(file, path, movesJson.get(index), MOVE_FIELDS));
            if (!ids.add(move.id())) {
                throw new BeastDataException(file, path + ".id", "duplicate move id " + move.id());
            }
            if (!clips.add(move.animation())) {
                throw new BeastDataException(file, path + ".animation",
                        "clip " + move.animation() + " is reserved or already used by the stagger or another move");
            }
            moves.add(move);
        }
        return BeastJson.build(file, "<root>", () -> new BeastDefinition(entity, attributeValues, chaseValues, staggerClip, moves));
    }

    private static BeastMoveDefinition parseMove(BeastJson.Fields move) {
        ResourceLocation id = move.id("id");
        String animation = move.clipName("animation");
        int totalTicks = move.positiveInteger("total_ticks");
        int[] active = move.integerPair("active_ticks");
        if (active[0] < 0 || active[1] < active[0] || active[1] >= totalTicks) {
            throw move.error("active_ticks", "must satisfy 0 <= first <= last < total_ticks (" + totalTicks
                    + "), got [" + active[0] + ", " + active[1] + "]");
        }
        int windup = move.nonNegativeInteger("windup_ticks");
        if (windup > active[0]) {
            throw move.error("windup_ticks", "must be at most the first active tick (" + active[0] + "), got " + windup);
        }
        int turnLock = move.nonNegativeInteger("turn_lock_tick");
        if (turnLock > windup) {
            throw move.error("turn_lock_tick", "must lock the aim within the wind-up (<= windup_ticks " + windup
                    + "), got " + turnLock);
        }
        int[] immune = move.integerPair("immune_ticks");
        if (immune[0] < 0 || immune[1] < immune[0] || immune[1] >= totalTicks) {
            throw move.error("immune_ticks", "must satisfy 0 <= first <= last < total_ticks (" + totalTicks
                    + "), got [" + immune[0] + ", " + immune[1] + "]");
        }
        double damageMultiplier = move.positiveNumber("damage_multiplier");
        int maximumTargets = move.positiveInteger("maximum_targets");
        double[] useRange = move.numberPair("use_range");
        if (useRange[0] < 0.0 || useRange[1] < useRange[0]) {
            throw move.error("use_range", "must satisfy 0 <= min <= max, got [" + useRange[0] + ", " + useRange[1] + "]");
        }
        int cooldown = move.nonNegativeInteger("cooldown_ticks");
        int weight = move.positiveInteger("weight");

        BeastJson.Fields lunge = move.object("lunge", LUNGE_FIELDS);
        int lungeTick = lunge.nonNegativeInteger("tick");
        if (lungeTick < turnLock || lungeTick > active[1]) {
            throw lunge.error("tick", "must satisfy turn_lock_tick (" + turnLock + ") <= tick <= last active tick ("
                    + active[1] + "), got " + lungeTick);
        }
        double forwardMin = lunge.nonNegativeNumber("forward_min");
        double forwardMax = lunge.nonNegativeNumber("forward_max");
        if (forwardMax < forwardMin) {
            throw lunge.error("forward_max", "must be at least forward_min (" + forwardMin + "), got " + forwardMax);
        }
        BeastMoveDefinition.Lunge lungeValue = BeastJson.build(lunge, () -> new BeastMoveDefinition.Lunge(
                lungeTick, forwardMin, forwardMax, lunge.nonNegativeNumber("up")));

        BeastJson.Fields hit = move.object("hit", HIT_FIELDS);
        double[] forward = hit.numberPair("forward");
        if (forward[1] <= forward[0]) {
            throw hit.error("forward", "must be [near, far] with near < far, got [" + forward[0] + ", " + forward[1] + "]");
        }
        double[] height = hit.numberPair("height");
        if (height[1] <= height[0]) {
            throw hit.error("height", "must be [low, high] with low < high, got [" + height[0] + ", " + height[1] + "]");
        }
        BeastMoveDefinition.HitBox hitValue = BeastJson.build(hit, () -> new BeastMoveDefinition.HitBox(
                forward[0], forward[1], hit.positiveNumber("half_width"), height[0], height[1]));

        BeastJson.Fields knockback = move.object("knockback", KNOCKBACK_FIELDS);
        BeastMoveDefinition.Knockback knockbackValue = BeastJson.build(knockback, () -> new BeastMoveDefinition.Knockback(
                knockback.nonNegativeNumber("strength"), knockback.nonNegativeNumber("lift")));

        return BeastJson.build(move, () -> new BeastMoveDefinition(
                id, animation, totalTicks, windup, turnLock,
                new BeastTickRange(active[0], active[1]),
                new BeastTickRange(immune[0], immune[1]),
                damageMultiplier, maximumTargets,
                new BeastMoveDefinition.UseRange(useRange[0], useRange[1]),
                cooldown, weight, lungeValue, hitValue, knockbackValue));
    }

    private static JsonObject read(ResourceOpener opener, String file, String listedIn, String listedAt) {
        InputStream stream;
        try {
            stream = opener.open(file);
        } catch (IOException exception) {
            throw new BeastDataException(listedIn, listedAt, "cannot open " + file, exception);
        }
        if (stream == null) {
            throw new BeastDataException(listedIn, listedAt, "listed file " + file + " is missing");
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return BeastJson.parseStrict(file, reader);
        } catch (IOException exception) {
            throw new BeastDataException(file, "<root>", "cannot read: " + exception.getMessage(), exception);
        }
    }

}
