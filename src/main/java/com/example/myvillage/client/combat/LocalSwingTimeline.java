package com.example.myvillage.client.combat;

/**
 * The local action's timeline on screen, free of Minecraft types so the animator's call sites can
 * be replayed in tests: its start on the {@link ClientCombatClock} (with a slew toward an
 * authoritative correction), its {@link SwingClock} with the hit-stop, and the hit confirmations it
 * received. Every reader passes the local time it reads at; each reading advances the clock's
 * present, so the drawn tick never moves backwards.
 */
final class LocalSwingTimeline {
    /** Authoritative start corrections up to this size are slewed instead of snapped. */
    static final float RESYNC_SLEW_LIMIT_TICKS = 2.0F;
    static final float RESYNC_SLEW_TICKS = 3.0F;

    private final int totalTicks;
    private final SwingClock clock;
    private double startTick;
    private double slewOffset;
    private double slewStartTick;
    private int confirmations;
    /** The latest local time any reader read at (a frame reads mid-tick, a packet at its start). */
    private double latestRead = Double.NEGATIVE_INFINITY;

    LocalSwingTimeline(int totalTicks, double startTick) {
        this.totalTicks = totalTicks;
        this.clock = new SwingClock(totalTicks);
        this.startTick = startTick;
    }

    int totalTicks() {
        return totalTicks;
    }

    SwingClock clock() {
        return clock;
    }

    /** Real ticks since the action started at local time {@code now}, slew included. */
    double realTick(double now) {
        return now - effectiveStartTick(now);
    }

    /** The action start including any in-progress slew toward an authoritative correction. */
    double effectiveStartTick(double now) {
        if (slewOffset == 0.0) {
            return startTick;
        }
        double remaining = 1.0 - (now - slewStartTick) / RESYNC_SLEW_TICKS;
        if (remaining <= 0.0) {
            slewOffset = 0.0;
            return startTick;
        }
        return startTick + slewOffset * Math.min(1.0, remaining);
    }

    /**
     * An authoritative start for the same move, at local time {@code now}. Returns the shift (0 when
     * it agrees): positive when the server started earlier than the prediction. Shifts up to
     * {@link #RESYNC_SLEW_LIMIT_TICKS} are slewed over {@link #RESYNC_SLEW_TICKS}; larger ones snap
     * (a snap back holds the pose, see {@link SwingClock#advance}).
     */
    double correct(double authoritativeStart, double now) {
        // A packet reads the tick start, which the last frame may already have drawn past: the
        // slew starts from the latest time drawn, so it begins exactly at the pose on screen.
        double at = Math.max(now, latestRead);
        double shift = effectiveStartTick(at) - authoritativeStart;
        if (Math.abs(shift) < 1.0E-3) {
            return 0.0;
        }
        startTick = authoritativeStart;
        if (slewed(shift)) {
            slewOffset = shift;
            slewStartTick = at;
        } else {
            slewOffset = 0.0;
        }
        return shift;
    }

    static boolean slewed(double shift) {
        return Math.abs(shift) <= RESYNC_SLEW_LIMIT_TICKS;
    }

    /** A reading of the timeline that advances the present (a frame, a client tick, a packet). */
    float read(double now) {
        latestRead = Math.max(latestRead, now);
        return clock.advance((float) realTick(now));
    }

    /** True once the server-timed move is over at {@code now} (the real tick, not the present). */
    boolean ended(double now) {
        return realTick(now) >= totalTicks;
    }

    /** True before the action's start (a correction moved it into the future). */
    boolean notStarted(double now) {
        return realTick(now) < 0.0;
    }

    /** Visual tick to draw at {@code now}; advances the present. */
    float visualTick(double now) {
        return clock.visualTick(read(now));
    }

    /** True while the hit-stop holds the present (read after {@link #visualTick}). */
    boolean inHitStop() {
        return clock.inHitStop(clock.present());
    }

    /**
     * What a rendered frame at local time {@code now} draws: nothing yet (a correction moved the
     * start ahead), nothing any more (the server-timed move is over: the real tick, not the present,
     * decides), or the visual tick with the hit-stop flag. Advances the present.
     */
    Drawn frame(double now) {
        latestRead = Math.max(latestRead, now);
        double real = realTick(now);
        if (real < 0.0) {
            return Drawn.NOT_STARTED;
        }
        if (real >= totalTicks) {
            return Drawn.ENDED;
        }
        float present = clock.advance((float) real);
        return new Drawn(true, false, clock.visualTick(present), clock.inHitStop(present));
    }

    record Drawn(boolean drawn, boolean ended, float tick, boolean hitStop) {
        static final Drawn NOT_STARTED = new Drawn(false, false, 0.0F, false);
        static final Drawn ENDED = new Drawn(false, true, 0.0F, false);
    }

    /** Visual ticks per real tick at {@code now}; advances the present. */
    float rate(double now) {
        return clock.rate(read(now));
    }

    /**
     * A hit confirmation handled at local time {@code now}. Only the first of an action may start
     * the stop; the result says what happened.
     */
    Confirmation confirm(double now, float contactTick, float stopTicks) {
        confirmations++;
        float reading = (float) realTick(now);
        float present = clock.advance(reading);
        boolean running = clock.hasHitStop();
        float start = clock.confirmHit(contactTick, stopTicks);
        String result = !Float.isNaN(start)
                ? (start > present ? "started_at_contact" : "started")
                : running ? "ignored_stop_already_started" : "ignored_too_late_to_catch_up";
        return new Confirmation(confirmations, reading, present, start, result);
    }

    record Confirmation(int number, float reading, float present, float start, String result) {
    }
}
