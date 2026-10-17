package com.example.myvillage.sim.combat;

import java.util.TreeMap;

/** Ledger component. Only the WorldSim facade exposes mutations to game code. */
public final class NpcCombatState {
    public long nextEpoch = 1;
    public final TreeMap<Integer, NpcCombatProfile> profiles = new TreeMap<>();
    /** NPC-to-player grievances supplement the existing NPC Relation records. */
    public final TreeMap<Integer, TreeMap<String, Long>> grievances = new TreeMap<>();
    public final TreeMap<Integer, TreeMap<String, Long>> sectGrievances = new TreeMap<>();
    public final transient TreeMap<Integer, Long> leases = new TreeMap<>();
    public final transient java.util.Set<Integer> engaged = new java.util.HashSet<>();
}
