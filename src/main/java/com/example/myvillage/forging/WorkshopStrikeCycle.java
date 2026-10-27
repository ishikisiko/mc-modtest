package com.example.myvillage.forging;

/** One server-timed swing. No packet, repeated tick, cancellation or late callback can pay twice. */
public final class WorkshopStrikeCycle {
    public enum Step { NONE, CONTACT, FINISHED, CANCELLED }
    private long started = -1, nextAllowed;
    private boolean contacted;

    public long started() { return started; }
    public boolean active() { return started >= 0; }
    public boolean begin(long now) {
        if (active() || now < nextAllowed) return false;
        started = now; nextAllowed = now + WorkshopRules.get().strike_ticks(); contacted = false;
        return true;
    }
    public Step tick(long now, boolean valid) {
        if (!active()) return Step.NONE;
        if (!valid || now < started) { cancel(); return Step.CANCELLED; }
        if (now >= nextAllowed) { cancel(); return Step.FINISHED; }
        if (!contacted && now - started >= WorkshopRules.get().contact_tick()) {
            contacted = true;
            return Step.CONTACT;
        }
        return Step.NONE;
    }
    public void cancel() { started = -1; contacted = false; }
}
