package com.example.myvillage.client.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * The client's own tick count for everything that times a combat action or reacts to a hit: the
 * first-person swing, prediction and chain ticks, impact freezes, the attacker's stop, the world
 * trail, camera kicks and the arm's relaxing lag. It advances once per client tick in which the
 * level ran (at the end of the tick, where the level's own game time has just advanced), and
 * nothing else ever sets it.
 *
 * <p>{@code Level#getGameTime()} on the client is not monotonic: every 20 ticks the server's
 * {@code ClientboundSetTimePacket} sets it to the server's time, a tick or more either way. Read as
 * elapsed time, such a reset made the swing skip or stall. This class is the only reader of the
 * game clock in combat code: {@link #elapsedSinceServer} converts a server tick (an action's start)
 * into elapsed ticks once, when its message arrives, and {@link #endOfTick} watches for resets.
 */
final class ClientCombatClock {
    private static long ticks;
    private static long lastGameTime = Long.MIN_VALUE;

    private ClientCombatClock() {
    }

    /** Whole local ticks so far. */
    static long ticks() {
        return ticks;
    }

    /** Local time now, with the frame's partial tick. */
    static double now(float partialTick) {
        return ticks + partialTick;
    }

    /**
     * The one conversion of a server tick: ticks elapsed since {@code serverTick} by the client's
     * game clock now, never negative (a start the client clock has not reached yet is now).
     */
    static long elapsedSinceServer(long serverTick) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0L : elapsedSince(serverTick, level.getGameTime());
    }

    static long elapsedSince(long serverTick, long gameTimeNow) {
        return Math.max(0L, gameTimeNow - serverTick);
    }

    /** The local tick that lies {@code elapsedTicks} before now. */
    static long localTickAgo(long elapsedTicks) {
        return ticks - Math.max(0L, elapsedTicks);
    }

    /**
     * End of a client tick: advances the count when the level ran, and reports a game-clock reset
     * (the server's time packet) in a tick where the level ran but its game time did not move by
     * exactly one. Paused or tick-frozen ticks neither advance nor report.
     */
    static Reset endOfTick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            lastGameTime = Long.MIN_VALUE;
            return null;
        }
        return endOfTick(levelRan(true, minecraft.isPaused(), level.tickRateManager().runsNormally()),
                level.getGameTime());
    }

    static Reset endOfTick(boolean levelRan, long gameTime) {
        Reset reset = null;
        if (levelRan) {
            ticks++;
            if (lastGameTime != Long.MIN_VALUE && gameTime - lastGameTime != 1L) {
                reset = new Reset(lastGameTime, gameTime, ticks);
            }
        }
        lastGameTime = gameTime;
        return reset;
    }

    /** True when the level ticked this client tick: its game time would have advanced by one. */
    static boolean levelRan(boolean hasLevel, boolean paused, boolean runsNormally) {
        return hasLevel && !paused && runsNormally;
    }

    /** A new level or player (respawn, dimension change, logout): the next game time is not a reset. */
    static void forgetGameTime() {
        lastGameTime = Long.MIN_VALUE;
    }

    /** Test hook: set the count and forget the game clock. */
    static void setForTest(long value) {
        ticks = value;
        lastGameTime = Long.MIN_VALUE;
    }

    /** A game-clock reset seen at the end of a tick: game time {@code from} became {@code to}. */
    record Reset(long from, long to, long local) {
    }
}
