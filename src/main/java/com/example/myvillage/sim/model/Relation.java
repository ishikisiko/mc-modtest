package com.example.myvillage.sim.model;

/** A person-to-person tie: master, disciple, friend or enemy, with the event that caused it. */
public final class Relation implements Comparable<Relation> {
    public static final String MASTER = "master";
    public static final String DISCIPLE = "disciple";
    public static final String FRIEND = "friend";
    public static final String ENEMY = "enemy";

    public int other;
    public String kind;
    public int strength;
    public long causeEventId;
    /**
     * Why an enmity exists, for the text: master, disciple, friend, sect (a fellow member was
     * killed), self (wronged in person), rival; "" when not recorded.
     */
    public String reason = "";

    public Relation(int other, String kind, int strength, long causeEventId) {
        this.other = other;
        this.kind = kind;
        this.strength = strength;
        this.causeEventId = causeEventId;
    }

    @Override
    public int compareTo(Relation o) {
        int c = Integer.compare(other, o.other);
        return c != 0 ? c : kind.compareTo(o.kind);
    }
}
