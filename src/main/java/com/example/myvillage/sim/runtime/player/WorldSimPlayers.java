package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.PlayerQualification;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/**
 * The runtime side of player sect membership (player sect entry, slice 1): reads a player's
 * qualification from the cultivation profile, joins and leaves through the {@code WorldSim}
 * facade, marks {@code WorldSimSavedData} dirty, sends the player's chat lines and logs
 * {@code SECT_ENTRY}. {@link #register()} refreshes snapshots on login and on settlement days.
 *
 * <p>Contract stub: slice 1 package B fills in {@link #register()}, {@link #qualification},
 * {@link #join} and {@link #leave}.
 */
public final class WorldSimPlayers {
    private WorldSimPlayers() {
    }

    /** The outcome of a join or leave; {@code reason} is an {@code Admission} reason or "ok". */
    public record Result(boolean ok, String reason) {
    }

    /** Subscribes the login and settlement-day listeners. Called from {@code WorldSimRuntime.register()}. */
    public static void register() {
    }

    /** The player's qualification as the ledger judges it, from their cultivation profile. */
    public static PlayerQualification qualification(ServerPlayer player) {
        throw new UnsupportedOperationException("slice 1 package B");
    }

    /** The player's ledger record, or empty when the ledger is inactive or has none. */
    public static Optional<PlayerMemberView> member(ServerPlayer player) {
        return WorldSimRuntime.sim().flatMap(sim -> sim.playerMember(player.getUUID().toString()));
    }

    /**
     * Joins the player to the sect. {@code force} (admin command) skips admission and only needs
     * the sect to be active.
     */
    public static Result join(ServerPlayer player, int sectId, boolean force) {
        throw new UnsupportedOperationException("slice 1 package B");
    }

    /** The player leaves their sect. */
    public static Result leave(ServerPlayer player) {
        throw new UnsupportedOperationException("slice 1 package B");
    }
}
