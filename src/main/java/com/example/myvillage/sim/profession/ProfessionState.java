package com.example.myvillage.sim.profession;

/** Small saved accounts owned by LifeState. Skills outlive a particular life assignment. */
public final class ProfessionState {
    private ProfessionState() { }
    public static final class Profile {
        public int gatheringExperience;
        public int plantingExperience;
        public int alchemyExperience;
    }
    public static final class Gathering {
        public String herb = "";
        public int dailyQuota;
        public long quotaDay = -1;
        public int gatheredToday;
    }
    /** One bounded batch per registered garden, never a claim about real crop blocks. */
    public static final class Garden {
        public String herb = "";
        public long plantedAt = -1;
        public long matureAt = -1;
        public boolean tended;
    }
}
