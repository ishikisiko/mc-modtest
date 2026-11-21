package com.example.myvillage.wilderness;

import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Vanilla-only scope, regional lower endpoint and starter mine encounter realm. */
public record VanillaMonsterRules(Set<String> excludedEntities, Set<String> spawnReasons,
                                  Set<String> nonAttributeDamageTypes, RealmKey minimumRealm, RealmKey starterMineRealm) {
    public static final String PATH = "data/yuanxian/wilderness/vanilla_monsters.json";
    public VanillaMonsterRules {
        excludedEntities = Set.copyOf(excludedEntities);
        spawnReasons = Set.copyOf(spawnReasons);
        nonAttributeDamageTypes = Set.copyOf(nonAttributeDamageTypes);
        StatsRules.bundled().definition(minimumRealm);
        StatsRules.bundled().definition(starterMineRealm);
    }
    private static class Holder {
        private static final VanillaMonsterRules INSTANCE = load();
    }
    public static VanillaMonsterRules bundled() { return Holder.INSTANCE; }
    private static VanillaMonsterRules load() {
        try (var in = VanillaMonsterRules.class.getResourceAsStream("/" + PATH)) {
            if (in == null) throw new IllegalArgumentException("Missing " + PATH);
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("Cannot load " + PATH, e); }
    }
    public static VanillaMonsterRules parse(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.keySet().equals(Set.of("schema", "excluded_entities", "spawn_reasons", "non_attribute_damage_types", "minimum_realm", "starter_mine_realm"))
                || !root.get("schema").toString().equals("1")) throw new IllegalArgumentException("Invalid vanilla monster rules");
        var sets = new java.util.ArrayList<Set<String>>();
        for (String field : new String[]{"excluded_entities", "spawn_reasons", "non_attribute_damage_types"}) {
            Set<String> values = new HashSet<>();
            for (var value : root.getAsJsonArray(field)) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                        || !value.getAsString().matches(field.equals("spawn_reasons") ? "[A-Z_]+" : "minecraft:[a-z0-9_]+")
                        || !values.add(value.getAsString())) throw new IllegalArgumentException("Invalid " + field);
            }
            if (values.isEmpty()) throw new IllegalArgumentException("Empty " + field);
            sets.add(values);
        }
        return new VanillaMonsterRules(sets.get(0), sets.get(1), sets.get(2),
                realm(root, "minimum_realm"), realm(root, "starter_mine_realm"));
    }
    private static RealmKey realm(JsonObject root, String field) {
        var value = root.getAsJsonObject(field);
        if (!value.keySet().equals(Set.of("realm", "stage")) || !value.get("realm").isJsonPrimitive()
                || !value.getAsJsonPrimitive("realm").isString() || !value.get("stage").isJsonPrimitive()
                || !value.getAsJsonPrimitive("stage").isNumber()) throw new IllegalArgumentException("Invalid " + field);
        int stage;
        try { stage = value.get("stage").getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException invalidStage) { throw new IllegalArgumentException(field + " stage must be an integer", invalidStage); }
        return StatsRules.bundled().realmKey(value.get("realm").getAsString(), stage);
    }
    public boolean includes(String entityId, boolean monsterCategory) {
        return monsterCategory && entityId.startsWith("minecraft:") && !excludedEntities.contains(entityId);
    }

}
