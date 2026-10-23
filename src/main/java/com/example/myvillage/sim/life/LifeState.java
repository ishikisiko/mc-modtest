package com.example.myvillage.sim.life;

import java.util.TreeMap;

/** Persistent life state owned by WorldState; maps have deterministic iteration order. */
public final class LifeState {
    public long clockTicks = -1;
    /** Global monotonic leases also distinguish unassign/reassign of the same person. */
    public long nextEpoch = 1;
    /** Unanchored denominator; runtime supplies the shared calendar scale before advancing. */
    public int ticksPerDay = 1;
    /** Runtime observation of the world's sun; elapsed work/cooldowns still use clockTicks. */
    public transient double daylightFraction = Double.NaN;
    public final TreeMap<String, LifeSite> sites = new TreeMap<>();
    public final TreeMap<String, StoryActorBinding> storyActors = new TreeMap<>();
    public final TreeMap<Integer, LifeResident> residents = new TreeMap<>();
    /** Assigned identity survives vacancy/death; automatic staffing must not resurrect a guide. */
    public final TreeMap<String, Integer> guideAssignments = new TreeMap<>();
    /** Only confirmed observations are kept. Missing means unknown, not destroyed. */
    public final TreeMap<String, String> availability = new TreeMap<>();
    public final TreeMap<String, Supply> supplies = new TreeMap<>();

    public static final class Supply {
        public long quotaDay = -1;
        public int gatheredToday;
        public int stock;
        public int medicine;
        public int treasury;
        public long services;
    }

    public static String placeKey(String siteId, String placeId) { return siteId + "|" + placeId; }
}
