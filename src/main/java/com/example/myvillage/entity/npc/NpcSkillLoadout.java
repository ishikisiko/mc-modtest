package com.example.myvillage.entity.npc;

import com.google.gson.Gson;
import java.util.List;
import java.util.HashSet;

/** 测试身体自己的功法配置。优先级数值越小越先尝试，关闭自动仍保留已学记录。 */
public record NpcSkillLoadout(String weapon, String core, List<Learned> learned) {
    public static final int LIMIT = 128;
    public static final NpcSkillLoadout EMPTY = new NpcSkillLoadout("", "", List.of());
    public record Learned(String id, long mastery, boolean automatic, int priority) {
        public Learned {
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 128
                    || mastery < 0 || priority < 0 || priority > 999)
                throw new IllegalArgumentException("功法 ID、熟练度或优先级无效");
        }
    }
    public NpcSkillLoadout {
        if (weapon == null || core == null || weapon.length() > 128 || core.length() > 128
                || learned == null || learned.size() > LIMIT)
            throw new IllegalArgumentException("功法配置超出范围");
        learned = List.copyOf(learned);
        var ids = new HashSet<String>();
        for (var row : learned) if (!ids.add(row.id())) throw new IllegalArgumentException("同一功法不能重复添加");
        if (!core.isEmpty() && !ids.contains(core)) throw new IllegalArgumentException("运行心法必须已经学会");
    }
    public static NpcSkillLoadout parse(String json) {
        if (json == null || json.length() > 32768) throw new IllegalArgumentException("功法配置过长");
        try {
            var state = new Gson().fromJson(json, NpcSkillLoadout.class);
            if (state == null) throw new IllegalArgumentException("功法配置不能为空");
            return state;
        } catch (RuntimeException failure) { throw new IllegalArgumentException("功法配置格式无效", failure); }
    }
    public String json() { return new Gson().toJson(this); }
    public List<Learned> automatic() {
        return learned.stream().filter(Learned::automatic).sorted(java.util.Comparator.comparingInt(Learned::priority)
                .thenComparing(Learned::id)).toList();
    }
}
