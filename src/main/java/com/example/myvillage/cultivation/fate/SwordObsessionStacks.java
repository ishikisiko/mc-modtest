package com.example.myvillage.cultivation.fate;

/** Online-only streak. A sweep earns one stack; every target of that move uses its starting bonus. */
public final class SwordObsessionStacks {
    private int stacks;
    private long lastHit = Long.MIN_VALUE;
    private long moveRevision = Long.MIN_VALUE;
    private int moveStacks;
    private boolean hit;
    private boolean waveClaimed;

    public int stacks(long now, int timeout) {
        if (lastHit != Long.MIN_VALUE && (now < lastHit || now - lastHit >= timeout)) stacks = 0;
        return stacks;
    }

    public int beforeMove(long revision, long now, int timeout) {
        if (moveRevision != revision) {
            moveStacks = stacks(now, timeout);
            moveRevision = revision;
            hit = false;
            waveClaimed = false;
        }
        return moveStacks;
    }

    public boolean accepted(long revision, long now, int timeout, int maximum, boolean finisher) {
        beforeMove(revision, now, timeout);
        if (!hit) {
            stacks = Math.min(maximum, stacks(now, timeout) + 1);
            lastHit = now;
            hit = true;
        }
        if (!finisher || stacks < maximum || waveClaimed) return false;
        waveClaimed = true;
        return true;
    }
}
