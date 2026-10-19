package com.example.myvillage.boss.lair;

import java.util.UUID;

/** A bound but unloaded boss is still alive. Only an explicit receipt changes its slot. */
public final class LairProgress {
    public enum State { DORMANT, READY, ACTIVE, CLEARED }
    private State state;
    private UUID boss;

    public LairProgress(String savedState, UUID savedBoss) {
        try {
            state = State.valueOf(savedState);
            if ((state == State.ACTIVE || state == State.CLEARED) != (savedBoss != null))
                throw new IllegalArgumentException("Inconsistent boss identity");
            boss = savedBoss;
        } catch (RuntimeException invalid) {
            state = State.DORMANT;
            boss = null;
        }
    }

    public State state() { return state; }
    public UUID boss() { return boss; }
    public boolean bind(UUID id) {
        if (state != State.READY || id == null) return false;
        boss = id;
        state = State.ACTIVE;
        return true;
    }
    public boolean died(UUID id) {
        if (state != State.ACTIVE || !boss.equals(id)) return false;
        state = State.CLEARED;
        return true;
    }
    /** Only failed admission or an explicit Peaceful-removal receipt permits a replacement. */
    public boolean retry(UUID id) {
        if (state != State.ACTIVE || !boss.equals(id)) return false;
        boss = null;
        state = State.READY;
        return true;
    }
}
