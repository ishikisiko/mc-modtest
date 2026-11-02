package com.example.myvillage.entity.undead;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bundled authored values shared by registration, attacks, assets and spawn eligibility. */
public final class UndeadRules {
    public static final Set<String> REGIONS = Set.of("common", "ximo", "donghai", "nanjiang", "beiyuan", "donghuang", "lingyue", "moyu", "zhongzhou");
    public static final Set<String> PROJECTILES = Set.of("normal", "spread", "tide", "gu", "delayed_pair", "arc", "piercing", "explosive", "burst");
    public record Action(String animation, String mode, List<Integer> strikes, String effect, String projectile, JsonObject data) {
        public boolean strikesAt(int tick) { return strikes.contains(tick); }
        public int totalTicks() { return data.get("total_ticks").getAsInt(); }
    }
    public record Variant(String id, String name, String family, String region, JsonObject data, List<Action> actions) {
        public boolean archer() { return family.equals("archer"); }
        public boolean acceptsRegion(String profile) { return region.equals("common") || region.equals(profile); }
        public double physical(String key) { return data.getAsJsonObject("physical").get(key).getAsDouble(); }
        public int color(String key) { return Integer.parseInt(data.getAsJsonObject("palette").get(key).getAsString(), 16); }
        public Action action(String animation) { return actions.stream().filter(a -> a.animation.equals(animation)).findFirst().orElseThrow(); }
    }
    private final Map<String, Variant> variants;
    private final JsonObject runtime;
    private UndeadRules(Map<String, Variant> variants, JsonObject runtime) { this.variants = Collections.unmodifiableMap(variants); this.runtime = runtime; }
    private static final class Holder { static final UndeadRules INSTANCE = loadBundled(); }
    public static UndeadRules get() { return Holder.INSTANCE; }
    private static UndeadRules loadBundled() {
        try (var stream = UndeadRules.class.getResourceAsStream("/data/myvillage/wilderness/undead.json")) {
            if (stream == null) throw new IllegalStateException("Missing wilderness/undead.json");
            return parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static UndeadRules parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("undead: unsupported schema");
        JsonObject limits = root.getAsJsonObject("runtime");
        for (var entry : limits.entrySet()) {
            double value = entry.getValue().getAsDouble();
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("undead runtime: " + entry.getKey());
        }
        for (String key : List.of("decision_ticks", "maximum_projectiles_per_shooter", "maximum_fields_per_shooter", "maximum_targets", "field_ticks", "field_interval", "projectile_lifetime", "particle_interval", "particle_points", "piercing_targets", "wave_lifetime", "cultivator_target_priority")) {
            double n = limits.get(key).getAsDouble();
            if (n != Math.rint(n) || n < 1 || n > 200) throw new IllegalArgumentException("undead bounded integer: " + key);
        }
        Map<String, Variant> variants = new LinkedHashMap<>();
        Set<String> pairs = new HashSet<>();
        for (JsonElement element : root.getAsJsonArray("variants")) {
            JsonObject v = element.getAsJsonObject();
            String id = v.get("id").getAsString(), family = v.get("family").getAsString(), region = v.get("region").getAsString();
            if (!id.matches("[a-z][a-z0-9_]+") || !Set.of("corpse", "archer").contains(family) || !REGIONS.contains(region)) throw new IllegalArgumentException("undead identity: " + id);
            if (!pairs.add(region + ":" + family)) throw new IllegalArgumentException("duplicate undead family/region");
            List<Action> actions = new ArrayList<>();
            for (JsonElement item : v.getAsJsonArray("moves")) {
                JsonObject a = item.getAsJsonObject();
                String mode = a.get("mode").getAsString();
                if (!Set.of("melee", "arrow", "wave", "cone").contains(mode)) throw new IllegalArgumentException("undead mode: " + mode);
                List<Integer> ticks = new ArrayList<>();
                int total = a.get("total_ticks").getAsInt(), windup = a.get("windup_ticks").getAsInt(), lock = a.get("turn_lock_tick").getAsInt();
                for (JsonElement t : a.getAsJsonArray("strike_ticks")) {
                    double raw = t.getAsDouble(); int tick = t.getAsInt();
                    if (raw != tick || tick < windup || tick + limits.get("minimum_recovery_ticks").getAsInt() >= total || (!ticks.isEmpty() && tick <= ticks.getLast())) throw new IllegalArgumentException("undead strike schedule: " + id);
                    ticks.add(tick);
                }
                if (ticks.isEmpty() || lock < 0 || lock >= windup || total > 200) throw new IllegalArgumentException("undead timing: " + id);
                String projectile = a.has("projectile") ? a.get("projectile").getAsString() : "normal";
                if (!PROJECTILES.contains(projectile)) throw new IllegalArgumentException("undead projectile: " + projectile);
                String effect = a.has("effect") ? a.get("effect").getAsString() : "none";
                if (!Set.of("none", "grasp", "push", "sand", "poison", "leech").contains(effect)) throw new IllegalArgumentException("undead effect: " + effect);
                actions.add(new Action(a.get("animation").getAsString(), mode, List.copyOf(ticks), effect, projectile, a.deepCopy()));
            }
            if (actions.size() != 2 || actions.getFirst().animation.equals(actions.getLast().animation)) throw new IllegalArgumentException("undead requires two distinct moves");
            if (variants.put(id, new Variant(id, v.get("name").getAsString(), family, region, v.deepCopy(), List.copyOf(actions))) != null) throw new IllegalArgumentException("duplicate undead id");
        }
        if (pairs.size() != REGIONS.size() * 2) throw new IllegalArgumentException("undead roster must cover both families in every region");
        return new UndeadRules(variants, limits.deepCopy());
    }
    public Collection<Variant> variants() { return variants.values(); }
    public Variant require(String id) { Variant v = variants.get(id); if (v == null) throw new IllegalArgumentException("Unknown undead: " + id); return v; }
    public double number(String key) { return runtime.get(key).getAsDouble(); }
    public int ticks(String key) { return runtime.get(key).getAsInt(); }
}
