package com.example.myvillage.sim.model;

/**
 * Something a person carries from a fortune or a sect: an artifact (combat) or a pending
 * breakthrough pill. {@code sourceEventId} is the event that granted it, so a breakthrough or a
 * fight it tips can name its cause.
 */
public final class Boon {
    public static final String ARTIFACT = "artifact";
    public static final String BREAKTHROUGH_PILL = "breakthrough_pill";

    public String kind;
    /** Artifact id; "" for pills. */
    public String refId;
    /** Combat multiplier for an artifact, chance bonus for a pill. */
    public double amount;
    public long sourceEventId;

    public Boon(String kind, String refId, double amount, long sourceEventId) {
        this.kind = kind;
        this.refId = refId;
        this.amount = amount;
        this.sourceEventId = sourceEventId;
    }
}
