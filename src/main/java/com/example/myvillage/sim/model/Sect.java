package com.example.myvillage.sim.model;

import java.util.TreeMap;

/**
 * A sect's ledger record (design §3.4). Members are not listed here: a person's {@code sectId} is
 * the single source of truth. The gate coordinate is fixed at founding and never recomputed.
 */
public final class Sect {
    public static final String ACTIVE = "active";
    public static final String DESTROYED = "destroyed";

    public int id;
    public String name = "";
    public String homeRegionId = "";
    public int gateX;
    public int gateZ;
    public boolean gateRealized;
    public int founderId = -1;
    public long foundedDay;
    public int masterId = -1;
    public long masterSinceDay;
    public double resources;
    public double prestige;
    public String signatureTechniqueId = "";
    public String basicTechniqueId = "";
    public final TreeMap<Integer, SectRelation> relations = new TreeMap<>();
    public String state = ACTIVE;
    public long destroyedDay = -1;
    public int parentSectId = -1;
    /** Day a decline began (no one fit to lead), or -1. */
    public long declineSinceDay = -1;
    public long declineCauseEventId = -1;
    public int victories;
    /** The master's seat is empty; the event that emptied it (a death), or -1 when filled. */
    public long vacancyCauseEventId = -1;

    public boolean active() {
        return ACTIVE.equals(state);
    }

    public SectRelation relationTo(int other) {
        return relations.computeIfAbsent(other, SectRelation::new);
    }
}
