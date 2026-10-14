package com.example.myvillage.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable startup rules. A malformed candidate never produces a partially usable ruleset. */
public final class StatsRules {
    public static final String RESOURCE = "/data/myvillage/stats/rules.json";

    public record RealmDefinition(String id, int order, int stages, boolean mortal) {}
    public record Limits(double maxBaseHealth, double maxBaseAttack, double maxHealth, double maxAttack,
                         double minResistance, double maxResistance, double maxBonus, double maxFlatDamage,
                         double maxMoveMultiplier, double maxTotalMultiplier, double maxPenetration, double maxDamage) {}

    private final double entryMultiplier;
    private final double majorRatio;
    private final double minorSlope;
    private final List<RealmDefinition> realms;
    private final Map<String, RealmDefinition> byId;
    private final Map<String, String> aliases;
    private final Map<String, RealmKey> playerAliases;
    private final List<DamageType> damageTypes;
    private final Limits limits;
    private final String ruleHash;

    private StatsRules(JsonObject root, String hash) {
        fields(root, "rules", "schema", "growth", "realms", "player_stage_aliases", "damage_types", "limits");
        if (integer(root, "schema") != 1) throw invalid("schema", "unsupported version");
        JsonObject growth = object(root.get("growth"), "growth");
        fields(growth, "growth", "entry_multiplier", "major_ratio", "minor_slope");
        entryMultiplier = greater(number(growth, "entry_multiplier"), 1, "growth.entry_multiplier");
        majorRatio = greater(number(growth, "major_ratio"), 1, "growth.major_ratio");
        minorSlope = greater(number(growth, "minor_slope"), 0, "growth.minor_slope");
        JsonObject bounds = object(root.get("limits"), "limits");
        fields(bounds, "limits", "max_base_health", "max_base_attack", "max_health", "max_attack",
                "min_resistance", "max_resistance", "max_bonus", "max_flat_damage", "max_move_multiplier",
                "max_total_multiplier", "max_penetration", "max_damage");
        limits = new Limits(positive(bounds, "max_base_health"), positive(bounds, "max_base_attack"),
                positive(bounds, "max_health"), positive(bounds, "max_attack"), number(bounds, "min_resistance"),
                number(bounds, "max_resistance"), positive(bounds, "max_bonus"), positive(bounds, "max_flat_damage"),
                positive(bounds, "max_move_multiplier"), positive(bounds, "max_total_multiplier"),
                positive(bounds, "max_penetration"), positive(bounds, "max_damage"));
        // These are engine compatibility ceilings, not balance defaults.
        bounded(limits.maxHealth(), 0, 1024, "limits.max_health");
        bounded(limits.maxAttack(), 0, 2048, "limits.max_attack");
        bounded(limits.minResistance(), 0, 0, "limits.min_resistance");
        if (limits.maxResistance() < 0 || limits.maxResistance() >= 1)
            throw invalid("limits.max_resistance", "must be in [0,1)");
        bounded(limits.maxPenetration(), 0, 1, "limits.max_penetration");
        if (limits.maxMoveMultiplier() < 1 || limits.maxTotalMultiplier() < 1)
            throw invalid("limits", "neutral multipliers must be supported");

        List<RealmDefinition> definitions = new ArrayList<>();
        Map<String, RealmDefinition> idMap = new HashMap<>();
        Map<String, String> aliasMap = new HashMap<>();
        Set<String> explicitAliases = new HashSet<>();
        JsonArray realmData = array(root.get("realms"), "realms");
        if (realmData.size() < 2) throw invalid("realms", "requires mortal and cultivated realms");
        int expectedOrder = -1;
        for (JsonElement element : realmData) {
            JsonObject realm = object(element, "realms[]");
            fields(realm, "realms[]", "id", "order", "stages", "mortal", "aliases");
            String id = RealmKey.normalizeId(string(realm, "id"));
            int order = integer(realm, "order");
            int stages = integer(realm, "stages");
            boolean mortal = bool(realm, "mortal");
            if (stages < 1 || stages > 256) throw invalid(id + ".stages", "must be in [1,256]");
            if (order != expectedOrder++ || mortal != (order == -1))
                throw invalid(id + ".order", "requires one mortal (-1), then consecutive cultivated orders");
            RealmDefinition definition = new RealmDefinition(id, order, stages, mortal);
            if (idMap.putIfAbsent(id, definition) != null) throw invalid("realms.id", "duplicate " + id);
            definitions.add(definition);
            registerAlias(aliasMap, id, id);
            JsonArray realmAliases = array(realm.get("aliases"), id + ".aliases");
            for (JsonElement value : realmAliases) {
                String alias = string(value, id + ".aliases[]");
                RealmKey.normalizeId(alias);
                if (!explicitAliases.add(alias)) throw invalid("realms.aliases", "duplicate " + alias);
                registerAlias(aliasMap, alias, id);
                registerAlias(aliasMap, RealmKey.normalizeId(alias), id);
            }
        }
        realms = List.copyOf(definitions);
        byId = Map.copyOf(idMap);
        aliases = Map.copyOf(aliasMap);
        double previousMaximum = 1;
        for (RealmDefinition realm : realms) {
            double first = growth(new RealmKey(realm.id(), 0));
            double last = growth(new RealmKey(realm.id(), realm.stages() - 1));
            if (!realm.mortal() && first <= previousMaximum)
                throw invalid(realm.id(), "growth boundary must strictly increase");
            bounded(last * limits.maxBaseHealth(), 0, limits.maxHealth(), realm.id() + ".max_health");
            bounded(last * limits.maxBaseAttack(), 0, limits.maxAttack(), realm.id() + ".max_attack");
            previousMaximum = last;
        }

        Map<String, RealmKey> stageMap = new HashMap<>();
        for (JsonElement element : array(root.get("player_stage_aliases"), "player_stage_aliases")) {
            JsonObject alias = object(element, "player_stage_aliases[]");
            fields(alias, "player_stage_aliases[]", "id", "realm", "stage");
            String id = RealmKey.normalizeId(string(alias, "id"));
            RealmKey key = realmKey(string(alias, "realm"), integer(alias, "stage"));
            if (stageMap.putIfAbsent(id, key) != null) throw invalid("player_stage_aliases", "duplicate " + id);
        }
        if (stageMap.isEmpty()) throw invalid("player_stage_aliases", "must not be empty");
        playerAliases = Map.copyOf(stageMap);
        List<DamageType> types = new ArrayList<>();
        for (JsonElement element : array(root.get("damage_types"), "damage_types")) {
            DamageType type = DamageType.fromId(string(element, "damage_types[]"));
            if (types.contains(type)) throw invalid("damage_types", "duplicate " + type.id());
            types.add(type);
        }
        if (types.size() != DamageType.values().length) throw invalid("damage_types", "must register every supported channel");
        damageTypes = List.copyOf(types);
        ruleHash = hash;
    }

    public static StatsRules bundled() {
        return BundledHolder.RULES;
    }

    private static final class BundledHolder {
        private static final StatsRules RULES = loadBundled();
    }

    private static StatsRules loadBundled() {
        try (InputStream input = StatsRules.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing stats resource " + RESOURCE);
            return fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException("Cannot read " + RESOURCE, e); }
    }

    public static StatsRules fromJson(Reader reader) {
        try {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) text.append(buffer, 0, count);
            return fromJson(text.toString());
        } catch (IOException e) { throw invalid("rules", "read failure: " + e.getMessage()); }
    }

    public static StatsRules fromJson(String text) {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement root = readJson(reader, "rules");
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("rules", "trailing content");
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
            return new StatsRules(object(root, "rules"), hash);
        } catch (IOException | NoSuchAlgorithmException | NumberFormatException e) {
            throw invalid("rules", "invalid JSON: " + e.getMessage());
        }
    }

    public RealmKey realmKey(String id, int stage) {
        String canonical = aliases.get(id);
        if (canonical == null) canonical = aliases.get(RealmKey.normalizeId(id));
        if (canonical == null) throw invalid("realm.id", "unknown " + id);
        RealmKey key = new RealmKey(canonical, stage);
        definition(key);
        return key;
    }

    public RealmKey playerKey(String realmId, String stageId) {
        RealmKey key = playerAliases.get(RealmKey.normalizeId(stageId));
        if (key == null) throw invalid("player.stage", "unknown " + stageId);
        if (!realmKey(realmId, key.stage()).equals(key)) throw invalid("player.realm", "does not match stage " + stageId);
        return key;
    }

    public RealmDefinition definition(RealmKey key) {
        RealmDefinition definition = byId.get(key.realmId());
        if (definition == null) throw invalid("realm.id", "unknown " + key.realmId());
        if (key.stage() >= definition.stages()) throw invalid("realm.stage", "outside " + definition.id());
        return definition;
    }

    public double growth(RealmKey key) {
        RealmDefinition realm = definition(key);
        double factor = realm.mortal() ? 1 : entryMultiplier * Math.pow(majorRatio, realm.order()) * (1 + minorSlope * key.stage());
        if (!Double.isFinite(factor) || factor <= 0) throw invalid("growth", "must be finite and positive");
        return factor;
    }

    public List<RealmDefinition> realms() { return realms; }
    public List<DamageType> damageTypes() { return damageTypes; }
    public Limits limits() { return limits; }
    public String ruleHash() { return ruleHash; }
    public double entryMultiplier() { return entryMultiplier; }
    public double majorRatio() { return majorRatio; }
    public double minorSlope() { return minorSlope; }
    public Map<String, RealmKey> playerAliases() { return playerAliases; }

    static double bounded(double value, double min, double max, String field) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw invalid(field, "must be finite and in [" + min + "," + max + "]");
        return value;
    }

    private static double positive(JsonObject object, String field) { return greater(number(object, field), 0, "limits." + field); }
    private static double greater(double value, double minimum, String field) {
        if (!Double.isFinite(value) || value <= minimum) throw invalid(field, "must be finite and > " + minimum);
        return value;
    }
    private static IllegalArgumentException invalid(String field, String message) {
        return new IllegalArgumentException(field + ": " + message);
    }
    private static void registerAlias(Map<String, String> aliases, String alias, String id) {
        String previous = aliases.putIfAbsent(alias, id);
        if (previous != null && !previous.equals(id)) throw invalid("realms.aliases", "ambiguous " + alias);
    }
    private static void fields(JsonObject object, String path, String... names) {
        Set<String> expected = Set.of(names);
        for (String field : object.keySet()) if (!expected.contains(field)) throw invalid(path + "." + field, "unknown field");
        for (String field : names) if (!object.has(field)) throw invalid(path + "." + field, "missing field");
    }
    private static JsonObject object(JsonElement element, String path) {
        if (element == null || !element.isJsonObject()) throw invalid(path, "must be an object");
        return element.getAsJsonObject();
    }
    private static JsonArray array(JsonElement element, String path) {
        if (element == null || !element.isJsonArray()) throw invalid(path, "must be an array");
        return element.getAsJsonArray();
    }
    private static String string(JsonObject object, String field) { return string(object.get(field), field); }
    private static String string(JsonElement element, String field) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            throw invalid(field, "must be a string");
        return element.getAsString();
    }
    private static double number(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw invalid(field, "must be a number");
        double result = value.getAsDouble();
        if (!Double.isFinite(result)) throw invalid(field, "must be finite");
        return result;
    }
    private static int integer(JsonObject object, String field) {
        double value = number(object, field);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw invalid(field, "must be an integer");
        return (int) value;
    }
    private static boolean bool(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw invalid(field, "must be boolean");
        return value.getAsBoolean();
    }

    /** JsonParser normally discards duplicate keys; keep them a validation error instead. */
    private static JsonElement readJson(JsonReader reader, String path) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw invalid(path + "." + name, "duplicate field");
                    object.add(name, readJson(reader, path + "." + name));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readJson(reader, path + "[" + array.size() + "]"));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw invalid(path, "unexpected token " + reader.peek());
        };
    }
}
