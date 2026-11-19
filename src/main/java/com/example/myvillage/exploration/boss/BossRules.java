package com.example.myvillage.exploration.boss;

import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.example.myvillage.stats.CultivationStatsRules;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntUnaryOperator;

/** Independently authored effective attributes and pre-mitigation damage policies for vanilla bosses. */
public final class BossRules {
    public static final String PATH = "/data/yuanxian/exploration/bosses.json";
    public enum Channel {
        GUARDIAN_BEAM, GUARDIAN_THORNS, DRAGON_CONTACT, DRAGON_BREATH,
        WITHER_SKULL, WITHER_SKULL_EXPLOSION, WITHER_SPAWN_EXPLOSION, WARDEN_SONIC
    }
    public record Damage(double factor, double attackGrowth) {
        public float apply(float incoming) {
            if (!Float.isFinite(incoming) || incoming <= 0) return incoming;
            return (float) Math.min(Float.MAX_VALUE, incoming * factor + attackGrowth);
        }
    }
    public record Growth(int stage, int foundation, int goldenCore, int nascentSoul) { }
    public record Profile(String id, String entity, RealmKey realm, Growth growth, double health,
                          Double attributeAttack, double bodyDefense, Map<Channel, Damage> damage) { }

    public static final class ProfileSet {
        private final List<Profile> variants;
        private final Map<Growth, Profile> byGrowth;

        private ProfileSet(Map<Growth, Profile> choices) {
            variants = List.copyOf(choices.values());
            byGrowth = Map.copyOf(choices);
        }

        public List<Profile> variants() { return variants; }
        public RealmKey minimumRealm() { return variants.getFirst().realm(); }
        public RealmKey maximumRealm() { return variants.getLast().realm(); }
        public Profile sample(IntUnaryOperator nextInt) {
            return variants.size() == 1 ? variants.getFirst() : variants.get(nextInt.applyAsInt(variants.size()));
        }
        public Optional<Profile> variant(Growth growth) { return Optional.ofNullable(byGrowth.get(growth)); }
    }
    private static final Map<String, Set<Channel>> CHANNELS = Map.of(
            "minecraft:elder_guardian", Set.of(Channel.GUARDIAN_BEAM, Channel.GUARDIAN_THORNS),
            "minecraft:ender_dragon", Set.of(Channel.DRAGON_CONTACT, Channel.DRAGON_BREATH),
            "minecraft:wither", Set.of(Channel.WITHER_SKULL, Channel.WITHER_SKULL_EXPLOSION, Channel.WITHER_SPAWN_EXPLOSION),
            "minecraft:warden", Set.of(Channel.WARDEN_SONIC));
    private final List<ProfileSet> profiles;
    private final Map<String, ProfileSet> byId;
    private final Map<String, ProfileSet> byEntity;

    private BossRules(JsonObject root) {
        fields(root, "schema", "profiles");
        number(root, "schema", 2, 2);
        var ids = new LinkedHashMap<String, ProfileSet>();
        var entities = new LinkedHashMap<String, ProfileSet>();
        var limits = StatsRules.bundled().limits();
        for (var element : root.getAsJsonArray("profiles")) {
            JsonObject o = element.getAsJsonObject();
            fields(o, "id", "entity", "realm", "cultivation", "health", "attribute_attack", "damage");
            String id = string(o, "id"), entity = string(o, "entity");
            if (!id.matches("[a-z0-9_]+") || !CHANNELS.containsKey(entity)) throw invalid("profile entity/id");
            String realm = string(o, "realm");
            double health = number(o, "health", 1, Float.MAX_VALUE);
            Double attack = o.get("attribute_attack").isJsonNull() ? null : number(o, "attribute_attack", 0.01, limits.maxAttack());
            boolean needsAttack = entity.equals("minecraft:elder_guardian") || entity.equals("minecraft:warden");
            if ((attack != null) != needsAttack) throw invalid("attribute_attack must match native attack path");
            var factors = new EnumMap<Channel, Double>(Channel.class);
            for (var entry : o.getAsJsonObject("damage").entrySet()) {
                Channel channel;
                try { channel = Channel.valueOf(entry.getKey().toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { throw invalid("damage channel " + entry.getKey()); }
                if (!entry.getKey().equals(channel.name().toLowerCase(Locale.ROOT))) throw invalid("damage channel spelling");
                var d = entry.getValue().getAsJsonObject(); fields(d, "factor");
                factors.put(channel, number(d, "factor", 0.01, limits.maxTotalMultiplier()));
            }
            if (!factors.keySet().equals(CHANNELS.get(entity))) throw invalid("damage channels for " + entity);
            var profile = variants(id, entity, realm, o.getAsJsonObject("cultivation"), health, attack, factors);
            if (ids.put(id, profile) != null || entities.put(entity, profile) != null) throw invalid("duplicate profile/entity");
        }
        if (!entities.keySet().equals(CHANNELS.keySet())) throw invalid("exactly four boss profiles required");
        profiles = List.copyOf(ids.values()); byId = Map.copyOf(ids); byEntity = Map.copyOf(entities);
    }

    private static ProfileSet variants(String id, String entity, String realmId, JsonObject cultivation,
                                       double baseHealth, Double baseAttack, Map<Channel, Double> factors) {
        fields(cultivation, "stages", "foundation", "golden_core", "nascent_soul");
        var stages = choices(cultivation, "stages", 8);
        var foundations = choices(cultivation, "foundation", 4);
        var goldenCores = choices(cultivation, "golden_core", 3);
        var nascentSouls = choices(cultivation, "nascent_soul", 3);
        var rules = CultivationStatsRules.bundled();
        var reference = rules.reference();
        var profiles = new LinkedHashMap<Growth, Profile>();
        for (int stage : stages) {
            var realm = StatsRules.bundled().realmKey(realmId, stage);
            for (int foundation : foundations) for (int goldenCore : goldenCores) for (int nascentSoul : nascentSouls) {
                var growth = new Growth(stage, foundation, goldenCore, nascentSoul);
                var body = rules.at(realm, foundation, goldenCore, nascentSoul);
                double health = baseHealth + body.health() - reference.health();
                double attackGrowth = body.attack() - reference.attack();
                Double attack = baseAttack == null ? null : baseAttack + attackGrowth;
                var damage = new EnumMap<Channel, Damage>(Channel.class);
                factors.forEach((channel, factor) -> damage.put(channel, new Damage(factor, attackGrowth)));
                profiles.put(growth, new Profile(id, entity, realm, growth, health, attack, body.defense(), Map.copyOf(damage)));
            }
        }
        return new ProfileSet(profiles);
    }

    private static List<Integer> choices(JsonObject source, String field, int maximum) {
        var values = new TreeSet<Integer>(); // The lowest stage is also the guide's recommended minimum.
        for (var value : source.getAsJsonArray(field)) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid(field);
            double index = value.getAsDouble();
            if (index < 0 || index > maximum || index != Math.rint(index) || !values.add((int) index)) throw invalid(field);
        }
        if (values.isEmpty()) throw invalid(field);
        return List.copyOf(values);
    }

    public static BossRules get() { return Holder.VALUE; }
    private static final class Holder {
        static final BossRules VALUE = load();
        private static BossRules load() {
            try (var in = BossRules.class.getResourceAsStream(PATH)) {
                if (in == null) throw invalid("missing " + PATH);
                return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) { throw new IllegalStateException(e); }
        }
    }
    public List<ProfileSet> profiles() { return profiles; }
    public Optional<ProfileSet> profile(String id) { return Optional.ofNullable(byId.get(id)); }
    public Optional<ProfileSet> forEntity(String entity) { return Optional.ofNullable(byEntity.get(entity)); }
    public static BossRules parse(String json) {
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false); JsonElement root = read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("trailing JSON");
            return new BossRules(root.getAsJsonObject());
        } catch (IOException | IllegalStateException | NullPointerException | ClassCastException e) { throw invalid("invalid JSON: " + e); }
    }
    private static void fields(JsonObject o, String... names) {
        if (o == null || !o.keySet().equals(Set.of(names))) throw invalid("fields " + Arrays.toString(names));
    }
    private static String string(JsonObject o, String name) {
        var e = o.get(name);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw invalid(name);
        return e.getAsString();
    }
    private static double number(JsonObject o, String name, double minimum, double maximum) {
        var e = o.get(name);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw invalid(name);
        double n = e.getAsDouble();
        if (!Double.isFinite(n) || n < minimum || n > maximum) throw invalid(name);
        return n;
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
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("Boss rules: " + message); }
}
