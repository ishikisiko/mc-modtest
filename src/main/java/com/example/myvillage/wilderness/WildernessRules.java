package com.example.myvillage.wilderness;

import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Strict bundled rules. Unknown regions have no encounters; stages are zero-based. */
public final class WildernessRules {
    public static final String PATH = "data/myvillage/wilderness/rules.json";
    public record RealmRange(RealmKey min, RealmKey max, List<RealmKey> values) {
        public RealmRange {
            values = List.copyOf(values);
            if (values.isEmpty() || !values.getFirst().equals(min) || !values.getLast().equals(max)
                    || new HashSet<>(values).size() != values.size()) {
                throw new IllegalArgumentException("A range must contain its endpoints exactly once");
            }
            Set<String> completedRealms = new HashSet<>();
            for (int i = 1; i < values.size(); i++) {
                RealmKey before = values.get(i - 1), after = values.get(i);
                if (before.realmId().equals(after.realmId())) {
                    if (after.stage() != before.stage() + 1) throw new IllegalArgumentException("Out-of-order range stages");
                } else {
                    completedRealms.add(before.realmId());
                    if (after.stage() != 0 || completedRealms.contains(after.realmId())) {
                        throw new IllegalArgumentException("Out-of-order range realms");
                    }
                }
            }
        }
        public boolean contains(RealmKey key) { return values.contains(key); }
        public RealmKey sample(long seed, String region, long input) {
            long h = mix(seed ^ mix(region.hashCode()) ^ mix(input));
            return values.get((int) Math.floorMod(h, (long) values.size()));
        }
    }
    public record Region(RealmRange beast, RealmRange rogue) { }
    public record Rogues(int intervalTicks, int spawnRadius, int withdrawRadius, int minDistance,
                         int maxPerRegion, int maxTotal, int attemptsPerPass, int probesPerPerson) { }
    private static volatile WildernessRules bundled;
    private final Map<String, Region> regions;
    private final Rogues rogues;
    private final String hash;
    private WildernessRules(Map<String, Region> regions, Rogues rogues, String hash) {
        this.regions = Map.copyOf(regions); this.rogues = rogues; this.hash = hash;
    }
    public Optional<Region> region(String id) { return Optional.ofNullable(regions.get(id)); }
    public Map<String, Region> regions() { return regions; }
    public Rogues rogues() { return rogues; }
    public String hash() { return hash; }
    public static WildernessRules bundled() {
        WildernessRules result = bundled;
        if (result == null) synchronized (WildernessRules.class) {
            result = bundled;
            if (result == null) try (InputStream in = WildernessRules.class.getResourceAsStream("/" + PATH)) {
                if (in == null) throw new IllegalArgumentException("Missing " + PATH);
                result = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), StatsRules.bundled());
                bundled = result;
            } catch (Exception e) { throw new IllegalStateException("Cannot load " + PATH, e); }
        }
        return result;
    }
    public static WildernessRules parse(String json, StatsRules stats) {
        JsonObject root;
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            root = readJson(reader, "root").getAsJsonObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing wilderness JSON");
        } catch (IOException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid wilderness JSON", e);
        }
        fields(root, "root", "schema", "rogues", "regions");
        if (integer(root, "schema", 1, 1) != 1) throw new IllegalArgumentException("schema");
        JsonObject r = root.getAsJsonObject("rogues");
        fields(r, "rogues", "interval_ticks", "spawn_radius", "withdraw_radius", "min_distance", "max_per_region",
                "max_total", "attempts_per_pass", "probes_per_person");
        Rogues rogues = new Rogues(integer(r, "interval_ticks", 20, 1200), integer(r, "spawn_radius", 16, 128),
                integer(r, "withdraw_radius", 16, 192), integer(r, "min_distance", 1, 127),
                integer(r, "max_per_region", 1, 32), integer(r, "max_total", 1, 128),
                integer(r, "attempts_per_pass", 1, 128), integer(r, "probes_per_person", 1, 32));
        if (rogues.intervalTicks() % 20 != 0 || rogues.withdrawRadius() <= rogues.spawnRadius() || rogues.minDistance() >= rogues.spawnRadius()
                || rogues.maxPerRegion() > rogues.maxTotal()) throw new IllegalArgumentException("Invalid rogue caps/radii");
        List<RealmKey> ladder = new ArrayList<>();
        stats.realms().forEach(realm -> {
            for (int stage = 0; stage < realm.stages(); stage++) ladder.add(stats.realmKey(realm.id(), stage));
        });
        Map<String, Region> regions = new LinkedHashMap<>();
        JsonObject table = root.getAsJsonObject("regions");
        if (table.isEmpty()) throw new IllegalArgumentException("Empty regions");
        for (String id : table.keySet()) {
            if (!id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid region " + id);
            JsonObject row = table.getAsJsonObject(id);
            fields(row, id, "beast", "rogue");
            regions.put(id, new Region(range(row.getAsJsonObject("beast"), stats, ladder),
                    range(row.getAsJsonObject("rogue"), stats, ladder)));
        }
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
            return new WildernessRules(regions, rogues, hash);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static RealmRange range(JsonObject obj, StatsRules stats, List<RealmKey> ladder) {
        fields(obj, "range", "min", "max");
        RealmKey min = key(obj.getAsJsonObject("min"), stats), max = key(obj.getAsJsonObject("max"), stats);
        int first = ladder.indexOf(min), last = ladder.indexOf(max);
        if (first < 0 || last < first) throw new IllegalArgumentException("Reversed/unknown realm range");
        return new RealmRange(min, max, ladder.subList(first, last + 1));
    }
    private static RealmKey key(JsonObject obj, StatsRules stats) {
        fields(obj, "endpoint", "realm", "stage");
        JsonElement id = obj.get("realm");
        if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("realm must be a string");
        return stats.realmKey(id.getAsString(), integer(obj, "stage", 0, 100));
    }
    private static int integer(JsonObject obj, String name, int lo, int hi) {
        JsonElement e = obj.get(name);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(name);
        double value = e.getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < lo || value > hi) throw new IllegalArgumentException(name);
        return (int) value;
    }
    private static void fields(JsonObject obj, String at, String... keys) {
        if (obj == null || !obj.keySet().equals(Set.of(keys))) throw new IllegalArgumentException("Invalid fields at " + at);
    }
    /** Preserve duplicate fields as an error instead of letting JsonParser silently overwrite them. */
    private static JsonElement readJson(JsonReader reader, String path) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IllegalArgumentException("Duplicate wilderness field " + path + "." + name);
                    object.add(name, readJson(reader, path + "." + name));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) array.add(readJson(reader, path + "[" + array.size() + "]"));
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Unexpected wilderness JSON at " + path);
        };
    }
    /** A stable hash, independent of entity RNG, active chunks, wall clock and ledger settlement. */
    public static long mix(long value) {
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
