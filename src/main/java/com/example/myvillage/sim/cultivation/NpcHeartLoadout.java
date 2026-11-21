package com.example.myvillage.sim.cultivation;

import java.util.List;
import java.util.TreeSet;

/** 命簿保存功法 ID。实体读取这些 ID，并使用玩家的修炼档案解析效果。 */
public record NpcHeartLoadout(List<String> found, List<String> learned, String attack, String defense, String spirit) {
    public static final NpcHeartLoadout EMPTY = new NpcHeartLoadout(List.of(), List.of(), "", "", "");

    public NpcHeartLoadout {
        found = normalized(found);
        learned = normalized(learned);
        for (String id : List.of(attack, defense, spirit)) {
            if (!id.isEmpty() && !learned.contains(id)) throw new IllegalArgumentException("装备的心法尚未习得");
        }
    }

    public String equipped(String slot) {
        return switch (slot) {
            case "attack" -> attack;
            case "defense" -> defense;
            case "spirit" -> spirit;
            default -> throw new IllegalArgumentException("未知心法栏位 " + slot);
        };
    }

    public NpcHeartLoadout find(String id) {
        if (found.contains(id) || learned.contains(id)) return this;
        var next = new TreeSet<>(found);
        next.add(id);
        return new NpcHeartLoadout(List.copyOf(next), learned, attack, defense, spirit);
    }

    private static List<String> normalized(List<String> ids) {
        if (ids.size() > 1024) throw new IllegalArgumentException("命簿心法过多");
        var sorted = new TreeSet<String>();
        for (String id : ids) {
            if (id.length() > 128 || !id.matches("[a-z0-9_]+") || !sorted.add(id))
                throw new IllegalArgumentException("命簿心法 ID 无效 " + id);
        }
        return List.copyOf(sorted);
    }
}
