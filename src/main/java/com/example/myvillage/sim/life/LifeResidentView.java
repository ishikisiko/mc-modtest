package com.example.myvillage.sim.life;

/** Read-only activity snapshot. A revision identifies one activity; epoch identifies its live executor. */
public record LifeResidentView(int personId, String siteId, String role, String homeId, String workId,
                               String phase, String activity, String targetId, long revision, long epoch,
                               boolean embodied, int x, int y, int z, long progressTicks, long requiredTicks,
                               long pauseTicks, String reason, long completedActivities, int herbs, int coins,
                               int fatigue, boolean observed) { }
