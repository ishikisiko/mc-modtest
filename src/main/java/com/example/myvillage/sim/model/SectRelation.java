package com.example.myvillage.sim.model;

/** One sect's standing towards another: -100..100, and none/feud/war with the event that caused it. */
public final class SectRelation {
    public static final String NONE = "none";
    public static final String FEUD = "feud";
    public static final String WAR = "war";

    public int other;
    public int value;
    public String state = NONE;
    public long causeEventId = -1;
    public long sinceDay = -1;
    /** War tally from this sect's side (victories minus defeats). */
    public int score;
    /** This sect pays tribute to {@code other} until this day (a war lost by tribute), or -1. */
    public long tributeUntilDay = -1;

    public SectRelation(int other) {
        this.other = other;
    }
}
