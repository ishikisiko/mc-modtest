package com.example.myvillage.sim.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A living cultivator's ledger record (design §3.3). Mutable, owned by the engine; the dead are
 * replaced by a {@link Tombstone}. {@code realm} is an index into the realm table (the save stores
 * the realm id). Ranks: sect_master, elder, inner, outer, rogue. Statuses: at_sect, travelling,
 * secluded.
 */
public final class Person {
    public static final int METAL = 0;
    public static final int WOOD = 1;
    public static final int WATER = 2;
    public static final int FIRE = 3;
    public static final int EARTH = 4;
    public static final List<String> ELEMENTS = List.of("metal", "wood", "water", "fire", "earth");

    public int id;
    public String surname = "";
    public String given = "";
    /** "m" or "f"; only used to pick a given name. */
    public String gender = "m";
    public long birthDay;
    /** Basis points over {@link #ELEMENTS}, summing to 10000. */
    public int[] root = new int[5];
    /** Fortune event that last improved the root, or -1. */
    public long rootEventId = -1;

    public int realm;
    public int stage;
    public double progress;
    /** Failed great-realm breakthroughs since the last success. */
    public int failures;
    public int bonusLifespanYears;
    /** Fortune events that granted lifespan, most recent last (for causes). */
    public long lifespanEventId = -1;

    public int sectId = -1;
    public String rank = "rogue";
    public int masterId = -1;
    public long joinedDay;

    public String regionId = "";
    public String status = "at_sect";
    /** Day the current travel or seclusion ends, or -1. */
    public long statusUntil = -1;
    /** Where a traveller returns to (a rogue's haunt, a disciple's sect seat). */
    public String homeRegionId = "";
    /** Region a traveller is heading for, or "" when wandering. */
    public String destRegionId = "";

    public int ambition;
    public int aggression;
    public int caution;
    public int wanderlust;
    public int loyalty;

    /** Sparse relations, kept sorted by (other, kind). */
    public final List<Relation> relations = new ArrayList<>();

    public String techniqueId = "";
    /** Fortune event that granted the current technique, or -1 (taught by a sect or bought). */
    public long techniqueEventId = -1;
    /** Artifacts and pending pills. */
    public final List<Boon> boons = new ArrayList<>();

    public int injury;
    /** Event that caused the current injury (a fight, a failed breakthrough), or -1. */
    public long injuryEventId = -1;
    /** Who dealt the current injury, or -1 (self-inflicted, a beast, a trap). */
    public int injurerId = -1;

    /** Daoist name given on reaching a titled realm (the title is name + realm suffix), or "". */
    public String daoName = "";

    /** Someone this person is hunting (revenge), or -1. */
    public int quarryId = -1;

    /** Day of the last quiet insight (folded into the next stage-up line), or -1. */
    public long insightDay = -1;

    /** Journeys taken (only the first is recorded as a line). */
    public int journeys;

    public int kills;
    public int fortunes;

    public String name() {
        return surname + given;
    }

    public Relation relation(int other, String kind) {
        for (Relation r : relations) {
            if (r.other == other && r.kind.equals(kind)) {
                return r;
            }
        }
        return null;
    }

    public boolean hasRelation(int other, String kind) {
        return relation(other, kind) != null;
    }

    /** Adds or strengthens a relation, keeping the list sorted. Returns it. */
    public Relation relate(int other, String kind, int strength, long causeEventId, String reason) {
        Relation r = relate(other, kind, strength, causeEventId);
        if (causeEventId >= 0 || r.reason.isEmpty()) {
            r.reason = reason;
        }
        return r;
    }

    public Relation relate(int other, String kind, int strength, long causeEventId) {
        Relation existing = relation(other, kind);
        if (existing != null) {
            existing.strength = Math.max(existing.strength, strength);
            if (causeEventId >= 0) {
                existing.causeEventId = causeEventId;
            }
            return existing;
        }
        Relation r = new Relation(other, kind, strength, causeEventId);
        int at = 0;
        while (at < relations.size() && relations.get(at).compareTo(r) < 0) {
            at++;
        }
        relations.add(at, r);
        return r;
    }

    public void forget(int other) {
        relations.removeIf(r -> r.other == other);
    }

    public void forget(int other, String kind) {
        relations.removeIf(r -> r.other == other && r.kind.equals(kind));
    }
}
