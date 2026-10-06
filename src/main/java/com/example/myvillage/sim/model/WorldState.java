package com.example.myvillage.sim.model;

import com.example.myvillage.sim.SettlementScheduler;
import com.example.myvillage.sim.SimEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** The whole ledger. Everything that influences a future step lives here and is saved. */
public final class WorldState {
    public long seed;
    public String tierId = "";
    public int genesisDaysPerYear;
    public long prehistoryDays;
    /** Next day to settle; also days since genesis. */
    public long day;
    public int nextPersonId = 1;
    public int nextSectId = 1;
    public long nextEventId = 1;
    public final TreeMap<Integer, Person> persons = new TreeMap<>();
    public final TreeMap<Integer, Tombstone> tombstones = new TreeMap<>();
    public final TreeMap<Integer, Sect> sects = new TreeMap<>();
    public final TreeMap<String, RegionState> regions = new TreeMap<>();
    /** Heritages whose sect was destroyed, in the order they were lost. */
    public final List<LostHeritage> lostHeritages = new ArrayList<>();
    /** Kept chronicle entries in id order (pruned per design §3.5). */
    public final List<SimEvent> chronicle = new ArrayList<>();
    public SettlementScheduler scheduler = new SettlementScheduler();
}
