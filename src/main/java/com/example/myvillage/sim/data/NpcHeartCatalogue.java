package com.example.myvillage.sim.data;

import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.model.Person;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 命簿读取玩家功法的故读门槛及离线修炼所需数值。实际命中使用同一目录的注册定义。 */
public record NpcHeartCatalogue(Map<String, Entry> entries, Map<String, List<Long>> stageCaps) {
    public static final String BASIC_BREATHING = "basic_breathing";
    private static final String DEFINITIONS = "data/yuanxian/yuanxian/";

    public static NpcHeartCatalogue bundled() {
        return Bundled.VALUE;
    }

    private static final class Bundled {
        private static final SimData.ResourceOpener RESOURCES = NpcHeartCatalogue.class.getClassLoader()::getResourceAsStream;
        private static final NpcHeartCatalogue VALUE = SimDataLoader.load(RESOURCES).npcHearts();
    }

    public record Entry(String id, String slot, int grade, String element, String previous,
            String minimumRealm, int minimumStage, Map<String, Integer> minimumRoots,
            int capacity, int cultivation, double maxHealth) {
        public boolean eligible(RealmTable realms, Person person) {
            if (!minimumRealm.isEmpty()) {
                int minimum = realms.indexOf(minimumRealm);
                if (minimum < 0 && !minimumRealm.equals("mortal")
                        || person.realm < minimum || person.realm == minimum && person.stage < minimumStage)
                    return false;
            }
            for (var requirement : minimumRoots.entrySet()) {
                int elementIndex = Person.ELEMENTS.indexOf(requirement.getKey());
                if (elementIndex < 0 || person.root[elementIndex] < requirement.getValue()) return false;
            }
            return true;
        }
    }

    public Entry get(String id) {
        return entries.get(id);
    }

    public int capacity(String id) {
        var entry = get(id);
        return entry == null ? 0 : entry.capacity();
    }

    public double maxHealth(String id) {
        var entry = get(id);
        return entry == null ? 0 : entry.maxHealth();
    }

    public double cultivation(String id, RealmTable.Realm realm, int stage) {
        var entry = get(id);
        var caps = stageCaps.get(realm.id());
        if (entry == null || entry.cultivation() == 0 || caps == null || stage >= caps.size() || caps.get(stage) <= 0) return 0;
        return entry.cultivation() * realm.stage(stage).cap() / caps.get(stage);
    }

    static NpcHeartCatalogue load(SimData.ResourceOpener opener, List<ContentTables.Technique> techniques, RealmTable realms) {
        var stageOrders = new LinkedHashMap<String, Integer>();
        var caps = new LinkedHashMap<String, List<Long>>();
        for (var realm : realms.realms()) {
            var definition = SimDataLoader.readOptional(opener, DEFINITIONS + "realm/" + realm.id() + ".json");
            if (definition == null) continue;
            var stages = new java.util.TreeMap<Integer, Long>();
            for (var element : definition.getAsJsonArray("stages")) {
                var stage = element.getAsJsonObject();
                int order = stage.get("sort_order").getAsInt();
                stages.put(order, stage.has("cultivation_cap") ? stage.get("cultivation_cap").getAsLong() : 0);
                stageOrders.put(stage.get("id").getAsString(), order);
            }
            caps.put(realm.id(), List.copyOf(stages.values()));
        }
        var elements = techniques.stream().collect(Collectors.toMap(ContentTables.Technique::id, ContentTables.Technique::element));
        elements.put(BASIC_BREATHING, "none");
        var entries = new LinkedHashMap<String, Entry>();
        for (var technique : elements.entrySet()) {
            String id = technique.getKey();
            var definition = SimDataLoader.readOptional(opener, DEFINITIONS + "technique/" + id + ".json");
            if (definition == null || !definition.get("category").getAsString().equals("core")) continue;
            var effects = definition.getAsJsonObject("effects");
            var core = effects == null ? null : effects.getAsJsonObject("core");
            var heart = core == null ? null : core.getAsJsonObject("heart");
            var req = definition.getAsJsonObject("requirements");
            var roots = new LinkedHashMap<String, Integer>();
            if (req != null && req.has("minimum_element_aptitude")) {
                req.getAsJsonObject("minimum_element_aptitude").entrySet().forEach(
                        e -> roots.put(path(e.getKey()), e.getValue().getAsInt()));
            }
            String minStage = string(req, "minimum_stage", "");
            if (!minStage.isEmpty() && !stageOrders.containsKey(minStage))
                throw new IllegalArgumentException("心法 " + id + " 引用了未知小境界 " + minStage);
            entries.put(id, new Entry(id, string(heart, "slot", "spirit"), definition.get("grade").getAsInt(),
                    technique.getValue(), path(string(definition.getAsJsonObject("lineage"), "previous", "")),
                    path(string(req, "minimum_realm", "")), stageOrders.getOrDefault(minStage, 0), Map.copyOf(roots),
                    integer(heart, "capacity"), integer(heart, "cultivation"),
                    heart != null && heart.has("max_health") ? heart.get("max_health").getAsDouble() : 0));
        }
        return new NpcHeartCatalogue(Map.copyOf(entries), Map.copyOf(caps));
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object == null || !object.has(key) ? fallback : object.get(key).getAsString();
    }

    private static int integer(JsonObject object, String key) {
        return object == null || !object.has(key) ? 0 : object.get(key).getAsInt();
    }
}
