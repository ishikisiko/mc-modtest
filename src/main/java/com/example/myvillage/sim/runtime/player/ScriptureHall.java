package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.sim.runtime.net.ScriptureBorrowPayload;
import com.example.myvillage.sim.runtime.net.ScriptureHallPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server side of the scripture hall (藏经阁). Server-authoritative: a right click on a
 * scripture shelf ({@link #open}) and every borrow the player asks for ({@link #handleBorrow}) are
 * checked here (the overworld, a shelf with an owning sect, within
 * {@code rules.player.steward.interact_range}, an active ledger, an active sect) before anything is
 * read or changed; the hall sent back ({@link ScriptureHallPayload}) is built from the ledger
 * ({@code WorldSim.borrowable}/{@code hasBorrowed}) and the technique registry. A borrow is
 * recorded through {@code WorldSim.recordBorrow} and hands the player a technique manual.
 */
public final class ScriptureHall {
    private ScriptureHall() {
    }

    /** A player used the scripture shelf at {@code pos}: checks and sends the hall, or one chat line. */
    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        throw new UnsupportedOperationException("slice 2 package S-C");
    }

    /** A borrow the player asked for: checked again, recorded in the ledger, the manual given, the hall resent. */
    public static void handleBorrow(ServerPlayer player, ScriptureBorrowPayload payload) {
        throw new UnsupportedOperationException("slice 2 package S-C");
    }
}
