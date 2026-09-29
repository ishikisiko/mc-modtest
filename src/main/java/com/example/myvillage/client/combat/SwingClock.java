package com.example.myvillage.client.combat;

/**
 * Maps real elapsed action ticks to visual ticks. A confirmed hit briefly stops the visual
 * clock (hit-stop): a true freeze for the first {@link #FREEZE_FRACTION} of the stop, then a slow
 * creep at {@link #CREEP_RATE}. The rest of the action then runs slightly faster so the visual
 * move still ends exactly at the server-owned total. The stop length is per move
 * ({@code MoveFeedback.hitStopTicks}); {@link #HIT_STOP_TICKS} is only the default.
 */
final class SwingClock {
    static final float HIT_STOP_TICKS = 2.5F;
    static final float FREEZE_FRACTION = 0.6F;
    static final float CREEP_RATE = 0.15F;
    private static final float MINIMUM_CATCH_UP_TICKS = 2.0F;

    private final float totalTicks;
    private float hitStopStart = Float.NaN;
    private float hitStopTicks = HIT_STOP_TICKS;

    SwingClock(float totalTicks) {
        if (!(totalTicks > 0.0F)) {
            throw new IllegalArgumentException("Swing clock needs a positive duration");
        }
        this.totalTicks = totalTicks;
    }

    /** Starts the default-length hit-stop at the given real tick. */
    boolean beginHitStop(float realTick) {
        return beginHitStop(realTick, HIT_STOP_TICKS);
    }

    /**
     * Starts a hit-stop of {@code stopTicks} at the given real tick, which may lie slightly in the
     * future so the freeze lands on the visual contact. Ignored when one already ran, when the stop
     * is empty, or when too little time remains to catch up before the server total.
     */
    boolean beginHitStop(float realTick, float stopTicks) {
        if (!Float.isNaN(hitStopStart)
                || !(stopTicks > 0.0F)
                || realTick < 0.0F
                || realTick + stopTicks + MINIMUM_CATCH_UP_TICKS > totalTicks) {
            return false;
        }
        hitStopStart = realTick;
        hitStopTicks = stopTicks;
        return true;
    }

    boolean hasHitStop() {
        return !Float.isNaN(hitStopStart);
    }

    float hitStopStart() {
        return hitStopStart;
    }

    float hitStopTicks() {
        return hitStopTicks;
    }

    boolean inHitStop(float realTick) {
        return !Float.isNaN(hitStopStart)
                && realTick >= hitStopStart
                && realTick < hitStopStart + hitStopTicks;
    }

    /** 0 at the start of the stop, rising to 1 at its end; 1 outside of it. */
    float hitStopProgress(float realTick) {
        if (!inHitStop(realTick)) {
            return 1.0F;
        }
        return (realTick - hitStopStart) / hitStopTicks;
    }

    float visualTick(float realTick) {
        float bounded = Math.max(0.0F, Math.min(totalTicks, realTick));
        if (Float.isNaN(hitStopStart) || bounded <= hitStopStart) {
            return bounded;
        }
        float freezeEnd = hitStopStart + hitStopTicks * FREEZE_FRACTION;
        float stopEnd = hitStopStart + hitStopTicks;
        float frozenAt = hitStopStart + (stopEnd - freezeEnd) * CREEP_RATE;
        if (bounded < freezeEnd) {
            return hitStopStart;
        }
        if (bounded < stopEnd) {
            return hitStopStart + (bounded - freezeEnd) * CREEP_RATE;
        }
        float catchUpRate = (totalTicks - frozenAt) / (totalTicks - stopEnd);
        return frozenAt + (bounded - stopEnd) * catchUpRate;
    }

    /** Visual ticks advanced per real tick at this moment (1 without hit-stop and after the move). */
    float rate(float realTick) {
        if (Float.isNaN(hitStopStart) || realTick < hitStopStart || realTick >= totalTicks) {
            return 1.0F;
        }
        float freezeEnd = hitStopStart + hitStopTicks * FREEZE_FRACTION;
        float stopEnd = hitStopStart + hitStopTicks;
        if (realTick < freezeEnd) {
            return 0.0F;
        }
        if (realTick < stopEnd) {
            return CREEP_RATE;
        }
        float frozenAt = hitStopStart + (stopEnd - freezeEnd) * CREEP_RATE;
        return (totalTicks - frozenAt) / (totalTicks - stopEnd);
    }

    /**
     * The earliest real tick whose visual tick reaches {@code visualTick}. Without a hit-stop, or
     * before one, visual and real time are the same.
     */
    float realTickForVisual(float visualTick) {
        float bounded = Math.max(0.0F, Math.min(totalTicks, visualTick));
        if (Float.isNaN(hitStopStart) || bounded <= hitStopStart) {
            return bounded;
        }
        float freezeEnd = hitStopStart + hitStopTicks * FREEZE_FRACTION;
        float stopEnd = hitStopStart + hitStopTicks;
        float frozenAt = hitStopStart + (stopEnd - freezeEnd) * CREEP_RATE;
        if (bounded <= frozenAt) {
            return freezeEnd + (bounded - hitStopStart) / CREEP_RATE;
        }
        float catchUpRate = (totalTicks - frozenAt) / (totalTicks - stopEnd);
        return stopEnd + (bounded - frozenAt) / catchUpRate;
    }
}
