package com.example.myvillage.client.combat;

/**
 * Maps real elapsed action ticks to visual ticks. A confirmed hit briefly slows the visual
 * clock (hit-stop); the rest of the action then runs slightly faster so the visual move still
 * ends exactly at the server-owned total.
 */
final class SwingClock {
    static final float HIT_STOP_TICKS = 2.5F;
    static final float HIT_STOP_RATE = 0.08F;
    private static final float MINIMUM_CATCH_UP_TICKS = 2.0F;

    private final float totalTicks;
    private float hitStopStart = Float.NaN;

    SwingClock(float totalTicks) {
        if (!(totalTicks > 0.0F)) {
            throw new IllegalArgumentException("Swing clock needs a positive duration");
        }
        this.totalTicks = totalTicks;
    }

    /** Starts hit-stop at the given real tick; ignored when one already ran or too little time remains. */
    boolean beginHitStop(float realTick) {
        if (!Float.isNaN(hitStopStart)
                || realTick < 0.0F
                || realTick + HIT_STOP_TICKS + MINIMUM_CATCH_UP_TICKS > totalTicks) {
            return false;
        }
        hitStopStart = realTick;
        return true;
    }

    boolean inHitStop(float realTick) {
        return !Float.isNaN(hitStopStart)
                && realTick >= hitStopStart
                && realTick < hitStopStart + HIT_STOP_TICKS;
    }

    float visualTick(float realTick) {
        float bounded = Math.max(0.0F, Math.min(totalTicks, realTick));
        if (Float.isNaN(hitStopStart) || bounded <= hitStopStart) {
            return bounded;
        }
        float stopEnd = hitStopStart + HIT_STOP_TICKS;
        float frozenAt = hitStopStart + HIT_STOP_TICKS * HIT_STOP_RATE;
        if (bounded < stopEnd) {
            return hitStopStart + (bounded - hitStopStart) * HIT_STOP_RATE;
        }
        float catchUpRate = (totalTicks - frozenAt) / (totalTicks - stopEnd);
        return frozenAt + (bounded - stopEnd) * catchUpRate;
    }
}
