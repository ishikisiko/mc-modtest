package com.example.myvillage.sim.economy;

/** 江湖榜归属、目标及奖励随玩家命簿保存。 */
public record TownAssignment(String taskId, String siteId, String regionId, long day, int progress, TaskReward reward) {
    public TownAssignment {
        if (taskId == null || !taskId.matches("[a-z0-9_]{1,64}") || siteId == null || !siteId.matches("[a-z0-9_]{1,96}")
                || regionId == null || !regionId.matches("[a-z0-9_]{1,96}") || day < 0 || progress < 0 || reward == null)
            throw new IllegalArgumentException("江湖事务存档无效");
    }
}
