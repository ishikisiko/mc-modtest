package com.example.myvillage.sim.engine;

/**
 * Players' yearly business in the ledger (player sect entry, slice 1): promotion by the
 * qualification snapshot against {@code rules.player.promotion}, negative standings recovering
 * toward zero, and dropping a master who died or left. Runs in {@link Engine#step} right after
 * {@link SectAffairs#yearly}.
 *
 * <p>Contract stub: slice 1 package A fills this in. Until then it does nothing, so the world
 * steps exactly as before.
 */
public final class PlayerAffairs {
    private PlayerAffairs() {
    }

    /** Called once at the start of every sim year. */
    public static void yearly(SimContext ctx) {
    }
}
