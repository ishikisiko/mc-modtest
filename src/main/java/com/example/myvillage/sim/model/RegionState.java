package com.example.myvillage.sim.model;

/** Per-region ledger state: how much unclaimed fortune is left (fortunes deplete, then regrow). */
public final class RegionState {
    public String id;
    /** 0..100; 100 is untouched. */
    public int richness = 100;

    public RegionState(String id) {
        this.id = id;
    }
}
