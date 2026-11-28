package com.example.myvillage.entity.beast;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Shared registration, spawn and encounter parameters for the native field beasts. */
public final class FieldEncounterRules {
    public record Body(float width, float height, float eyeHeight, int experience) {}
    public record Beast(Body body, String realm, int stage, int nearWater, int nearWaterDepth, int maximumLight) {}
    public record Encounter(int checkTicks, int tideHoldTicks, int tideReturnTicks, int steleHoldTicks,
                            int maxTideBlocks, int tideWriteBatch, int bridgeWidth, float weakPointMultiplier,
                            int steleLaunchRange) {}

    private static final class Bundled {
        private static final FieldEncounterRules INSTANCE = load();
    }

    private final Map<String, Beast> beasts;
    private final Encounter encounter;

    private FieldEncounterRules(Map<String, Beast> beasts, Encounter encounter) {
        this.beasts = Map.copyOf(beasts);
        this.encounter = encounter;
    }

    public static FieldEncounterRules bundled() { return Bundled.INSTANCE; }
    public Beast beast(String path) { return Objects.requireNonNull(beasts.get(path), path); }
    public Encounter encounter() { return encounter; }

    private static FieldEncounterRules load() {
        String path = "/data/yuanxian/beast/field_encounters.json";
        try (var input = Objects.requireNonNull(FieldEncounterRules.class.getResourceAsStream(path), path);
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("field encounter schema");
            var definitions = new HashMap<String, Beast>();
            for (String id : new String[]{"tide_dragon", "stele_ape", "tide_mud_crab", "hook_jaw_scorpion"}) {
                var entry = root.getAsJsonObject(id);
                var size = entry.getAsJsonArray("body");
                float width = size.get(0).getAsFloat(), height = size.get(1).getAsFloat(), eye = size.get(2).getAsFloat();
                if (size.size() != 3 || !(width > 0 && width <= 16 && height > 0 && height <= 24 && eye > 0 && eye <= height))
                    throw new IllegalArgumentException("field encounter body " + id);
                var body = new Body(width, height, eye, integer(entry, "experience", 0, 1000));
                String realm = entry.has("realm") ? ResourceLocation.parse(entry.get("realm").getAsString()).toString() : null;
                definitions.put(id, new Beast(body, realm, realm == null ? 0 : integer(entry, "stage", 0, 9),
                        entry.has("near_water") ? integer(entry, "near_water", 1, 8) : 0,
                        entry.has("near_water_depth") ? integer(entry, "near_water_depth", 1, 6) : 0,
                        entry.has("maximum_light") ? integer(entry, "maximum_light", 0, 15) : 15));
            }
            var e = root.getAsJsonObject("encounter");
            var encounter = new Encounter(integer(e, "check_ticks", 1, 60), integer(e, "tide_hold_ticks", 30, 600),
                    integer(e, "tide_return_ticks", 20, 200), integer(e, "stele_hold_ticks", 30, 600),
                    integer(e, "max_tide_blocks", 1, 12000), integer(e, "tide_write_batch", 8, 256),
                    integer(e, "bridge_width", 1, 9), e.get("weak_point_multiplier").getAsFloat(),
                    integer(e, "stele_launch_range", 8, 48));
            if (encounter.bridgeWidth() % 2 != 1 || !(encounter.weakPointMultiplier() > 1 && encounter.weakPointMultiplier() <= 2))
                throw new IllegalArgumentException("field encounter weak point or bridge width");
            return new FieldEncounterRules(definitions, encounter);
        } catch (IOException | RuntimeException malformed) {
            throw new IllegalStateException("Invalid " + path, malformed);
        }
    }

    private static int integer(JsonObject from, String field, int min, int max) {
        int value = from.get(field).getAsInt();
        if (value < min || value > max) throw new IllegalArgumentException(field + "=" + value);
        return value;
    }
}
