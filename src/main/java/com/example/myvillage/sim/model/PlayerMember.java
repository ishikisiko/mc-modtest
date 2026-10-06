package com.example.myvillage.sim.model;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * A player's ledger record (player sect entry, slice 1). Keyed by the player's UUID string in
 * {@link WorldState#playerMembers}. A player is never a {@link Person}: they are not in
 * {@code SimContext.members()} and never counted as a sect's people.
 *
 * <p>The record outlives membership: after leaving, {@code sectId} is -1 and {@code leftSectId}/
 * {@code leftDay} remember the last sect, while {@code standings} (交情, one -100..100 value per
 * sect id) and {@code borrowed} carry over to a later join. The qualification snapshot
 * ({@code realmId}, {@code stageIndex}, {@code awakened}, {@code rootPeakBp}) is refreshed by the
 * runtime (login, settlement days, joining); the yearly review reads only the snapshot.
 */
public final class PlayerMember {
    /** The player's UUID as a string. */
    public String playerId = "";
    /** The player's last known name, for chronicle text. */
    public String playerName = "";
    /** The sect the player belongs to, or -1. */
    public int sectId = -1;
    /** outer, inner or elder. */
    public String rank = "outer";
    /** Day the player joined the current sect, or -1. */
    public long joinedDay = -1;
    /** The player's master (a person id), or -1. */
    public int masterId = -1;
    public int contribution;
    /** Ids of the manuals borrowed from the scripture hall. */
    public final List<String> borrowed = new ArrayList<>();
    /** Standing (交情) with each sect by sect id, -100..100. */
    public final TreeMap<Integer, Integer> standings = new TreeMap<>();
    /** The last sect the player left, or -1. */
    public int leftSectId = -1;
    /** Day the player last left a sect, or -1. */
    public long leftDay = -1;

    /** Qualification snapshot: the player-realm registry path ({@code mortal}, {@code qi_refining}, ...). */
    public String realmId = "mortal";
    /** Qualification snapshot: 0-based stage within {@link #realmId}. */
    public int stageIndex;
    /** Qualification snapshot: the spiritual root is awakened. */
    public boolean awakened;
    /** Qualification snapshot: the highest single-element affinity of the root, in basis points. */
    public int rootPeakBp;

    /** The open sect task (an id in {@code sect_tasks.json}), or "" (slice 3). */
    public String taskId = "";
    /** Progress on the open task (beasts slain, letters delivered); tribute keeps none. */
    public int taskProgress;
    /** The courier task's destination sect, or -1. */
    public int taskTargetSectId = -1;
    /** The sim year the last task was taken, or -1; kept after completion (one task a year). */
    public long taskYear = -1;

    public boolean inSect() {
        return sectId >= 0;
    }
}
