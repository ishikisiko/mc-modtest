package com.example.myvillage.sim.runtime;

import com.example.myvillage.sim.SettlementScheduler;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.WorldSim;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Drives a live ledger from the cultivation calendar (no Minecraft types, so it is unit-tested).
 * Each server tick feeds the calendar's day index to the core's {@link SettlementScheduler} and
 * settles at most one pending sim day. Pausing stops settlement only; the calendar keeps running
 * and resuming never catches up (the scheduler's rules, design §2.2).
 */
public final class WorldSimDriver {
    /** Days settled per server tick, at most. */
    public static final int DAYS_PER_TICK = 1;
    public static final int MAX_ADVANCE_DAYS = 3650;

    private final WorldSim sim;
    private Mark schedulerMark;

    private record Mark(long lastCalendarDay, int pendingDays, boolean paused) {
    }

    public WorldSimDriver(WorldSim sim) {
        this.sim = Objects.requireNonNull(sim, "sim");
        this.schedulerMark = mark();
    }

    public WorldSim sim() {
        return sim;
    }

    /**
     * The pending-day cap the runtime passes to {@link #tick}: the rules' {@code
     * scheduler.max_pending_days} is a hard ceiling, the config's {@code catch_up_cap_days} may lower it.
     */
    public static int pendingCap(int configCapDays, SimData data) {
        return Math.min(configCapDays, data.rules().scheduler().maxPendingDays());
    }

    /**
     * One server tick: observe the calendar day, then settle at most one day.
     *
     * @return the settled day's events, or null when no day was settled
     */
    public List<SimEvent> tick(long calendarDay, int maxPending, int daysPerYear) {
        SettlementScheduler scheduler = sim.scheduler();
        scheduler.observe(calendarDay, Math.max(1, maxPending));
        if (scheduler.take(DAYS_PER_TICK) == 0) {
            return null;
        }
        return sim.step(daysPerYear);
    }

    /**
     * Settles {@code days} days now, whether or not settlement is paused (an explicit admin
     * action; the scheduler's state is left alone). Each day's events go to {@code perDay}.
     *
     * @return the number of events emitted
     */
    public long advance(int days, int daysPerYear, Consumer<List<SimEvent>> perDay) {
        if (days < 1 || days > MAX_ADVANCE_DAYS) {
            throw new IllegalArgumentException("days must be 1.." + MAX_ADVANCE_DAYS + ", got " + days);
        }
        long events = 0;
        for (int i = 0; i < days; i++) {
            List<SimEvent> day = sim.step(daysPerYear);
            events += day.size();
            perDay.accept(day);
        }
        return events;
    }

    public void setPaused(boolean paused) {
        sim.scheduler().setPaused(paused);
    }

    public boolean paused() {
        return sim.scheduler().paused();
    }

    public int pendingDays() {
        return sim.scheduler().pendingDays();
    }

    /** True once after the scheduler's saved state (anchor, pending, paused) changed. */
    public boolean consumeSchedulerChange() {
        Mark now = mark();
        if (now.equals(schedulerMark)) {
            return false;
        }
        schedulerMark = now;
        return true;
    }

    private Mark mark() {
        SettlementScheduler s = sim.scheduler();
        return new Mark(s.lastCalendarDay(), s.pendingDays(), s.paused());
    }
}
