package com.example.myvillage.sim.fate;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.HashSet;

/** 出生时保存的三个词条和当时预算；配置、灵根成长和化身变化均不重抽。 */
public record NpcFateRoll(List<String> selected, int budget, int spent) {
    public static final int COUNT = 3;

    public NpcFateRoll {
        selected = List.copyOf(selected);
        if (selected.size() != COUNT || new HashSet<>(selected).size() != COUNT
                || selected.stream().anyMatch(id -> !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                || budget < COUNT + 1 || budget > COUNT * 6 || spent < COUNT || spent > budget)
            throw new IllegalArgumentException("NPC 天赋记录无效");
    }

    public NpcFateRoll validateIdentity(boolean prodigy) {
        if (prodigy ? spent != budget : spent >= budget)
            throw new IllegalArgumentException("NPC 天赋花费与天骄身份不符");
        return this;
    }

    public JsonObject toJson() {
        var json = new JsonObject();
        var ids = new com.google.gson.JsonArray();
        selected.forEach(ids::add);
        json.add("selected", ids);
        json.addProperty("budget", budget);
        json.addProperty("spent", spent);
        return json;
    }

    public static NpcFateRoll fromJson(JsonObject json, boolean prodigy) {
        var ids = new java.util.ArrayList<String>();
        for (var value : json.getAsJsonArray("selected")) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("NPC 天赋 ID 必须是字符串");
            ids.add(value.getAsString());
        }
        return new NpcFateRoll(ids, integer(json, "budget"), integer(json, "spent")).validateIdentity(prodigy);
    }

    private static int integer(JsonObject json, String key) {
        var value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("NPC 天赋 " + key + " 必须是整数");
        return value.getAsBigDecimal().intValueExact();
    }
}
