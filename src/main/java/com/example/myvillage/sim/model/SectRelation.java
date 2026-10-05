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
    /** Day the last war between the two ended, or -1. */
    public long lastWarEndDay = -1;
    /** Day of the last set battle between the two and its champions (for spacing and "再度交锋"), or -1. */
    public long lastBattleDay = -1;
    /** No war may be declared between the two before this day (a split's grace period), or -1. */
    public long noWarUntilDay = -1;
    public int lastChampionA = -1;
    public int lastChampionB = -1;

    public SectRelation(int other) {
        this.other = other;
    }
}
