package com.example.myvillage.sim.life;

/** Mutable only inside the ledger. Entity UUIDs and execution ownership are never persisted. */
public final class LifeResident {
    public int personId;
    public String siteId = "";
    public String role = "rogue";
    public String homeId = "";
    public String workId = "";
    public String phase = "waiting";
    public String activity = "rest";
    public String targetId = "";
    public long revision;
    public long epoch;
    public transient boolean embodied;
    /** Presence observation is independent of whether the global avatar budget admitted a body. */
    public transient boolean observed;
    public int x, y, z;
    public long progressTicks;
    public long requiredTicks;
    public long pauseTicks;
    public String reason = "registered";
    public long completedActivities;
    public long completedRevision = -1;
    public int herbs;
    public int coins;
    public int fatigue;
    public long cultivationTicks;
    public String previousActivity = "";
    public String previousTarget = "";
    /** A failed route needs a witnessed arrival before cold simulation may use it again. */
    public String blockedTarget = "";
    public long retryAfter;

    public LifeResidentView view() {
        return new LifeResidentView(personId, siteId, role, homeId, workId, phase, activity,
                targetId, revision, epoch, embodied, x, y, z, progressTicks, requiredTicks,
                pauseTicks, reason, completedActivities, herbs, coins, fatigue, observed);
    }
}
