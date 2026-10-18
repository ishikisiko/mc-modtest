package com.example.myvillage.exploration.boss;

import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Independently authored effective attributes and pre-mitigation damage policies for vanilla bosses. */
public final class BossRules {
    public static final String PATH = "/data/myvillage/exploration/bosses.json";
    public enum Channel {
        GUARDIAN_BEAM, GUARDIAN_THORNS, DRAGON_CONTACT, DRAGON_BREATH,
        WITHER_SKULL, WITHER_SKULL_EXPLOSION, WITHER_SPAWN_EXPLOSION, WARDEN_SONIC
    }
    public record Damage(double factor, double maximum) {
        public float apply(float incoming) {
            if (!Float.isFinite(incoming) || incoming <= 0) return incoming;
            return (float) Math.min(maximum, incoming * factor);
        }
    }
    public record Profile(String id, String entity, RealmKey realm, double health,
                          Double attributeAttack, Map<Channel, Damage> damage) { }
    private static final Map<String, Set<Channel>> CHANNELS = Map.of(
            "minecraft:elder_guardian", Set.of(Channel.GUARDIAN_BEAM, Channel.GUARDIAN_THORNS),
            "minecraft:ender_dragon", Set.of(Channel.DRAGON_CONTACT, Channel.DRAGON_BREATH),
            "minecraft:wither", Set.of(Channel.WITHER_SKULL, Channel.WITHER_SKULL_EXPLOSION, Channel.WITHER_SPAWN_EXPLOSION),
            "minecraft:warden", Set.of(Channel.WARDEN_SONIC));
    private final List<Profile> profiles;
    private final Map<String, Profile> byId;
    private final Map<String, Profile> byEntity;

    private BossRules(JsonObject root) {
        fields(root, "schema", "profiles");
        if (number(root, "schema", 1, 1) != 1) throw invalid("schema");
        var ids = new LinkedHashMap<String, Profile>();
        var entities = new LinkedHashMap<String, Profile>();
        var limits = StatsRules.bundled().limits();
        for (var element : root.getAsJsonArray("profiles")) {
            JsonObject o = element.getAsJsonObject();
            fields(o, "id", "entity", "realm", "stage", "health", "attribute_attack", "damage");
            String id = string(o, "id"), entity = string(o, "entity");
            if (!id.matches("[a-z0-9_]+") || !CHANNELS.containsKey(entity)) throw invalid("profile entity/id");
            double stage = number(o, "stage", 0, 100);
            if (stage != Math.rint(stage)) throw invalid("stage");
            RealmKey realm = StatsRules.bundled().realmKey(string(o, "realm"), (int) stage);
            double health = number(o, "health", 1, limits.maxHealth());
            Double attack = o.get("attribute_attack").isJsonNull() ? null : number(o, "attribute_attack", 0.01, limits.maxAttack());
            boolean needsAttack = entity.equals("minecraft:elder_guardian") || entity.equals("minecraft:warden");
            if ((attack != null) != needsAttack) throw invalid("attribute_attack must match native attack path");
            var damage = new EnumMap<Channel, Damage>(Channel.class);
            for (var entry : o.getAsJsonObject("damage").entrySet()) {
                Channel channel;
                try { channel = Channel.valueOf(entry.getKey().toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { throw invalid("damage channel " + entry.getKey()); }
                if (!entry.getKey().equals(channel.name().toLowerCase(Locale.ROOT))) throw invalid("damage channel spelling");
                var d = entry.getValue().getAsJsonObject(); fields(d, "factor", "maximum");
                damage.put(channel, new Damage(number(d, "factor", 0.01, limits.maxTotalMultiplier()),
                        number(d, "maximum", 0.01, limits.maxDamage())));
            }
            if (!damage.keySet().equals(CHANNELS.get(entity))) throw invalid("damage channels for " + entity);
            Profile profile = new Profile(id, entity, realm, health, attack, Map.copyOf(damage));
            if (ids.put(id, profile) != null || entities.put(entity, profile) != null) throw invalid("duplicate profile/entity");
        }
        if (!entities.keySet().equals(CHANNELS.keySet())) throw invalid("exactly four boss profiles required");
        profiles = List.copyOf(ids.values()); byId = Map.copyOf(ids); byEntity = Map.copyOf(entities);
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
    public List<Profile> profiles() { return profiles; }
    public Optional<Profile> profile(String id) { return Optional.ofNullable(byId.get(id)); }
    public Optional<Profile> forEntity(String entity) { return Optional.ofNullable(byEntity.get(entity)); }
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
