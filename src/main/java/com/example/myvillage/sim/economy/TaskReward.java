package com.example.myvillage.sim.economy;

import java.util.Map;
import java.util.TreeMap;

/** 接取时冻结的奖励；重登、重启和交付重试沿用同一结果。 */
public record TaskReward(int contribution, int reputation, Map<String, Integer> stones) {
    public static final TaskReward NONE = new TaskReward(0, 0, Map.of());
    public TaskReward {
        if (contribution < 0 || reputation < 0) throw new IllegalArgumentException("任务奖励不能为负");
        stones = java.util.Collections.unmodifiableMap(new TreeMap<>(stones));
        if (stones.size() > 3 || stones.entrySet().stream().anyMatch(e ->
                !java.util.Set.of("low", "middle", "high").contains(e.getKey()) || e.getValue() < 1 || e.getValue() > 1000000))
            throw new IllegalArgumentException("灵石奖励无效");
    }
}
