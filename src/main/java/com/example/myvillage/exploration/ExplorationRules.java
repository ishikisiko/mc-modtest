package com.example.myvillage.exploration;

import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.example.myvillage.wilderness.WildernessRules.RealmRange;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One authored source for recommendations, encounters, loot and the town guide. Stages are zero-based. */
public final class ExplorationRules {
    public static final String PATH = "/data/myvillage/exploration/rules.json";
    public record Location(String id, String dimension, List<String> structures, List<String> biomes,
                           int minY, int maxY, int priority, RealmRange recommended,
                           RealmRange encounters, boolean regional, boolean contextOnly, String reward) { }
    public record Reward(String item, int min, int max, int weight, String technique) { }
    public record Pool(int rolls, List<Reward> entries) { }
    public record Tier(RealmKey minimum, String pool) { }
    public record Milestone(String id, RealmKey minimum, List<String> flags, List<String> tips) { }
    public record Settings(int checkTicks, int noticeTicks, int guideCooldownTicks, double guideRange, int contextRadius) { }
    private final List<RealmKey> ladder;
    private final List<Location> locations;
    private final Map<String, Location> byId;
    private final Map<String, Pool> pools;
    private final Map<String, String> lootTables;
    private final List<Tier> tiers;
    private final List<Milestone> milestones;
    private final Map<String, String> advancements;
    private final Settings settings;
    private final Map<String, String> births;
    private final String guideHome;
    private final String guideWork;
    private static final Set<String> SPECIAL_FLAGS = Set.of("awakened", "initiated");

    private ExplorationRules(JsonObject root) {
        fields(root, "schema", "settings", "locations", "special_births", "pools", "reward_tiers", "loot_tables", "advancements", "milestones", "guide_station");
        integer(root, "schema", 1, 1);
        var stats = StatsRules.bundled();
        var keys = new ArrayList<RealmKey>();
        stats.realms().forEach(r -> { for (int s = 0; s < r.stages(); s++) keys.add(stats.realmKey(r.id(), s)); });
        ladder = List.copyOf(keys);
        var s = object(root, "settings"); fields(s, "check_ticks", "notice_ticks", "guide_cooldown_ticks", "guide_range", "context_radius");
        settings = new Settings(integer(s, "check_ticks", 20, 1200), integer(s, "notice_ticks", 100, 72000),
                integer(s, "guide_cooldown_ticks", 20, 1200), integer(s, "guide_range", 2, 8), integer(s, "context_radius", 1, 24));
        var station = object(root, "guide_station"); fields(station, "home", "work");
        guideHome = word(station, "home"); guideWork = word(station, "work");
        var ls = new ArrayList<Location>(); var index = new LinkedHashMap<String, Location>();
        for (var element : array(root, "locations")) {
            var o = element.getAsJsonObject();
            fields(o, "id", "dimension", "structures", "biomes", "min_y", "max_y", "priority", "recommended", "encounters", "regional", "context_only", "reward");
            String id = word(o, "id"), dimension = resource(o, "dimension");
            int min = integer(o, "min_y", -2048, 2048), max = integer(o, "max_y", -2048, 2048);
            if (min > max) throw invalid("reversed height " + id);
            for (String b : List.of("regional", "context_only"))
                if (!o.get(b).isJsonPrimitive() || !o.getAsJsonPrimitive(b).isBoolean()) throw invalid(b);
            var location = new Location(id, dimension, strings(o, "structures", true), strings(o, "biomes", true),
                    min, max, integer(o, "priority", 0, 1000), range(object(o, "recommended")),
                    o.get("encounters").isJsonNull() ? null : range(object(o, "encounters")), o.get("regional").getAsBoolean(), o.get("context_only").getAsBoolean(), word(o, "reward"));
            if (index.put(id, location) != null) throw invalid("duplicate location " + id);
            ls.add(location);
        }
        if (ls.isEmpty()) throw invalid("no locations");
        // A stable priority is also the explicit overlap policy; JSON order resolves equal priorities.
        ls.sort(Comparator.comparingInt(Location::priority).reversed()); locations = List.copyOf(ls); byId = Map.copyOf(index);
        var ps = new LinkedHashMap<String, Pool>();
        for (var entry : object(root, "pools").entrySet()) {
            var o = entry.getValue().getAsJsonObject(); fields(o, "rolls", "entries");
            var entries = new ArrayList<Reward>();
            for (var value : array(o, "entries")) {
                var r = value.getAsJsonObject(); fields(r, "item", "min", "max", "weight", "technique");
                int min = integer(r, "min", 1, 64), max = integer(r, "max", min, 64);
                String technique = string(r, "technique");
                if (!technique.isEmpty() && !technique.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw invalid("technique");
                entries.add(new Reward(resource(r, "item"), min, max, integer(r, "weight", 1, 10000), technique));
            }
            int rolls = integer(o, "rolls", 0, 4);
            if (rolls > 0 && entries.isEmpty()) throw invalid("empty pool " + entry.getKey());
            ps.put(entry.getKey(), new Pool(rolls, List.copyOf(entries)));
        }
        pools = Map.copyOf(ps);
        for (var l : locations) requirePool(l.reward());
        var tierList = new ArrayList<Tier>(); int previous = -1;
        for (var element : array(root, "reward_tiers")) {
            var t = element.getAsJsonObject(); fields(t, "minimum", "pool");
            var key = key(object(t, "minimum")); int position = rank(key);
            if (position <= previous) throw invalid("reward tier order"); previous = position;
            String pool = word(t, "pool"); requirePool(pool); tierList.add(new Tier(key, pool));
        }
        tiers = List.copyOf(tierList);
        var tables = new LinkedHashMap<String, String>();
        object(root, "loot_tables").entrySet().forEach(e -> {
            if (!e.getKey().matches("minecraft:(chests|archaeology)/[a-z0-9_/]+")) throw invalid("non-exploration loot table");
            String pool = e.getValue().getAsString(); requirePool(pool); tables.put(e.getKey(), pool);
        }); lootTables = Map.copyOf(tables);
        var birthMap = new LinkedHashMap<String, String>();
        object(root, "special_births").entrySet().forEach(e -> {
            if (!byId.containsKey(e.getValue().getAsString())) throw invalid("unknown birth location");
            birthMap.put(e.getKey(), e.getValue().getAsString());
        }); births = Map.copyOf(birthMap);
        var advances = new LinkedHashMap<String, String>();
        object(root, "advancements").entrySet().forEach(e -> {
            String id = e.getValue().getAsString();
            if (SPECIAL_FLAGS.contains(e.getKey()) || !id.matches("minecraft:[a-z0-9_/]+")) throw invalid("advancement");
            advances.put(e.getKey(), id);
        }); advancements = Map.copyOf(advances);
        var stages = new ArrayList<Milestone>(); var ids = new HashSet<String>();
        for (var element : array(root, "milestones")) {
            var o = element.getAsJsonObject(); fields(o, "id", "minimum", "flags", "tips");
            String id = word(o, "id"); if (!ids.add(id)) throw invalid("duplicate milestone");
            var flags = strings(o, "flags", false);
            if (flags.stream().anyMatch(f -> !SPECIAL_FLAGS.contains(f) && !advancements.containsKey(f))) throw invalid("unknown progress flag");
            var tips = strings(o, "tips", false); if (tips.isEmpty()) throw invalid("milestone needs tips");
            stages.add(new Milestone(id, key(object(o, "minimum")), flags, tips));
        }
        if (stages.isEmpty()) throw invalid("no guide milestones"); milestones = List.copyOf(stages);
        // Every dimension in the catalogue needs a catch-all; arbitrary mod dimensions have no invented profile.
        for (String dimension : List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"))
            if (locations.stream().noneMatch(l -> l.dimension().equals(dimension) && l.priority() == 0
                    && l.structures().isEmpty() && l.biomes().isEmpty() && l.minY() == -2048 && l.maxY() == 2048))
                throw invalid("missing dimension fallback " + dimension);
    }

    public static ExplorationRules get() { return Holder.VALUE; }
    private static final class Holder {
        static final ExplorationRules VALUE = load();
        private static ExplorationRules load() {
            try (var in = ExplorationRules.class.getResourceAsStream(PATH)) {
                if (in == null) throw invalid("missing " + PATH);
                return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) { throw new IllegalStateException(e); }
        }
    }
    public static ExplorationRules parse(String json) {
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false); var root = read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("trailing content");
            return new ExplorationRules(root.getAsJsonObject());
        } catch (IOException | IllegalStateException | NullPointerException e) { throw invalid("invalid JSON: " + e); }
    }
    public List<Location> locations() { return locations; }
    public Location location(String id) { return Objects.requireNonNull(byId.get(id), "unknown location " + id); }
    public Settings settings() { return settings; }
    public String guideHome() { return guideHome; }
    public String guideWork() { return guideWork; }
    public Map<String, Pool> pools() { return pools; }
    public Map<String, String> lootTables() { return lootTables; }
    public Map<String, String> advancements() { return advancements; }
    public List<Milestone> milestones() { return milestones; }
    public Optional<String> birth(String reason) { return Optional.ofNullable(births.get(reason)); }
    public int rank(RealmKey key) { int rank = ladder.indexOf(key); if (rank < 0) throw invalid("unknown realm " + key); return rank; }
    public RealmRange floor(RealmRange base, RealmRange floor) {
        if (floor == null) return base;
        if (base == null) return floor;
        int lo = Math.max(rank(base.min()), rank(floor.min())), hi = Math.max(rank(base.max()), rank(floor.max()));
        return new RealmRange(ladder.get(lo), ladder.get(hi), ladder.subList(lo, hi + 1));
    }
    public String rewardPool(String baseline, RealmKey maximum) {
        requirePool(baseline);
        int baselineTier = -1;
        for (int i = 0; i < tiers.size(); i++) if (tiers.get(i).pool().equals(baseline)) baselineTier = i;
        int actualTier = baselineTier;
        if (maximum != null) for (int i = 0; i < tiers.size(); i++)
            if (rank(maximum) >= rank(tiers.get(i).minimum())) actualTier = Math.max(actualTier, i);
        return actualTier < 0 ? baseline : tiers.get(actualTier).pool();
    }
    public Milestone next(RealmKey realm, Set<String> flags) {
        return milestones.stream().filter(m -> rank(realm) < rank(m.minimum()) || !flags.containsAll(m.flags()))
                .findFirst().orElse(null);
    }
    private RealmRange range(JsonObject o) {
        fields(o, "min", "max"); var min = key(object(o, "min")); var max = key(object(o, "max"));
        int a = rank(min), b = rank(max); if (a > b) throw invalid("reversed range");
        return new RealmRange(min, max, ladder.subList(a, b + 1));
    }
    private static RealmKey key(JsonObject o) { fields(o, "realm", "stage"); return StatsRules.bundled().realmKey(resource(o, "realm"), integer(o, "stage", 0, 100)); }
    private void requirePool(String id) { if (!pools.containsKey(id)) throw invalid("unknown pool " + id); }
    private static void fields(JsonObject o, String... fields) { if (o == null || !o.keySet().equals(Set.of(fields))) throw invalid("fields " + Arrays.toString(fields)); }
    private static JsonObject object(JsonObject o, String key) { return o.getAsJsonObject(key); }
    private static JsonArray array(JsonObject o, String key) { return o.getAsJsonArray(key); }
    private static String string(JsonObject o, String key) {
        var e = o.get(key); if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw invalid(key);
        return e.getAsString();
    }
    private static String word(JsonObject o, String key) { String s = string(o, key); if (!s.matches("[a-z0-9_]+")) throw invalid(key); return s; }
    private static String resource(JsonObject o, String key) { String s = string(o, key); if (!s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw invalid(key); return s; }
    private static List<String> strings(JsonObject o, String key, boolean resource) {
        var result = new ArrayList<String>();
        for (var e : array(o, key)) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw invalid(key);
            String s = e.getAsString();
            if (!s.matches(resource ? "[a-z0-9_.-]+:[a-z0-9_./-]+" : "[a-z0-9_]+") || result.contains(s)) throw invalid(key);
            result.add(s);
        }
        return List.copyOf(result);
    }
    private static int integer(JsonObject o, String key, int lo, int hi) {
        var e = o.get(key); if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw invalid(key);
        double n = e.getAsDouble(); if (!Double.isFinite(n) || n != Math.rint(n) || n < lo || n > hi) throw invalid(key); return (int)n;
    }
    private static JsonElement read(JsonReader r) throws IOException {
        return switch (r.peek()) {
            case BEGIN_OBJECT -> { var o = new JsonObject(); r.beginObject(); while (r.hasNext()) { String k = r.nextName(); if (o.has(k)) throw invalid("duplicate " + k); o.add(k, read(r)); } r.endObject(); yield o; }
            case BEGIN_ARRAY -> { var a = new JsonArray(); r.beginArray(); while (r.hasNext()) a.add(read(r)); r.endArray(); yield a; }
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> { r.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw invalid("unexpected token");
        };
    }
    private static IllegalArgumentException invalid(String what) { return new IllegalArgumentException("Exploration rules: " + what); }
}
