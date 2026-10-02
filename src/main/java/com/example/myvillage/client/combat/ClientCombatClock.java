package com.example.myvillage.client.combat;

/**
 * The client's own tick count for combat presentation and prediction. It advances once per client
 * tick in which the level's game time would advance (not paused, level running normally), and
 * nothing else ever sets it.
 *
 * <p>{@code Level#getGameTime()} on the client is not monotonic: every 20 ticks the server's
 * {@code ClientboundSetTimePacket} sets it to the server's time, which moves it by a tick or two
 * either way mid-move. Read as elapsed time, such a reset made the swing skip forward or stall.
 * The server's tick numbers enter here only once, when its START arrives ({@link #fromServer}),
 * using the game clock at that moment; from then on a reset is not elapsed time. A real desync
 * between client and server timelines is still corrected by the next authoritative start.
 */
final class ClientCombatClock {
    private static long ticks;

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

    /** One client tick in which the level ran. */
    static void advance() {
        ticks++;
    }

    /**
     * The local tick of a server tick, given the client's game clock now: as many ticks ago as the
     * game clock says have elapsed since it, never in the future.
     */
    static long fromServer(long serverTick, long gameTimeNow) {
        return ticks - Math.max(0L, gameTimeNow - serverTick);
    }

    /** Test hook: set the count. */
    static void setForTest(long value) {
        ticks = value;
    }
}
