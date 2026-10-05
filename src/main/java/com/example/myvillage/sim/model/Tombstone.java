package com.example.myvillage.sim.model;

/** The compact, permanent record of a dead person, so later events can still name them. */
public final class Tombstone {
    public int id;
    public String name = "";
    public String title = "";
    public String daoName = "";
    public String gender = "m";
    public int sectId = -1;
    public String rank = "rogue";
    /** Root grade id at death (roots are not kept for the dead). */
    public String rootGrade = "";
    public int realm;
    public int stage;
    public long birthDay;
    public long deathDay;
    /** Cause id: old_age, qi_deviation, slain, beast, misadventure, trap, ... */
    public String cause = "";
    public int killerId = -1;
    public long deathEventId = -1;
    public int masterId = -1;
}
