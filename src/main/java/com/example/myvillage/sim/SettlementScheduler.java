package com.example.myvillage.sim;

/**
 * Decides how many sim days the runtime should settle, from the cultivation calendar's day index
 * (design §2.2). Each new calendar day adds one pending day; pending is capped so a forward jump of
 * the day index cannot stall the server; a day index that moves backwards adds nothing and just
 * re-anchors; while paused the anchor follows the calendar and nothing accumulates, so resuming
 * never catches up. Its state serialises with the world.
 */
public final class SettlementScheduler {
    /** No calendar day seen yet. */
    public static final long UNANCHORED = Long.MIN_VALUE;

    private long lastCalendarDay = UNANCHORED;
    private int pendingDays;
    private boolean paused;

    public SettlementScheduler() {
    }

    public SettlementScheduler(long lastCalendarDay, int pendingDays, boolean paused) {
        this.lastCalendarDay = lastCalendarDay;
        this.pendingDays = Math.max(0, pendingDays);
        this.paused = paused;
    }

    /**
     * Observes the calendar's current day index; call it whenever the calendar may have moved.
     *
     * @param maxPending the cap on accumulated days: rules {@code scheduler.max_pending_days} is the
     *     ceiling; the runtime passes the smaller of it and the server config's {@code catch_up_cap_days}
     */
    public void observe(long calendarDay, int maxPending) {
        if (lastCalendarDay == UNANCHORED || paused || calendarDay < lastCalendarDay) {
            lastCalendarDay = calendarDay;
            return;
        }
        long advanced = calendarDay - lastCalendarDay;
        lastCalendarDay = calendarDay;
        pendingDays = (int) Math.min((long) maxPending, pendingDays + advanced);
    }

    /** Takes up to {@code budget} pending days for settlement now (none while paused). */
    public int take(int budget) {
        if (paused) {
            return 0;
        }
        int n = Math.max(0, Math.min(budget, pendingDays));
        pendingDays -= n;
        return n;
    }

    /** Pausing drops nothing already pending but stops accumulation; resuming never catches up. */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean paused() {
        return paused;
    }

    public int pendingDays() {
        return pendingDays;
    }

    public long lastCalendarDay() {
        return lastCalendarDay;
    }
}
