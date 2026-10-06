package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SettlementSchedulerTest {
    private static final int CAP = 30;

    @Test
    void eachNewCalendarDayAddsOnePendingDay() {
        SettlementScheduler s = new SettlementScheduler();
        s.observe(100, CAP);
        assertEquals(0, s.pendingDays(), "the first observation only anchors");
        s.observe(100, CAP);
        assertEquals(0, s.pendingDays());
        s.observe(101, CAP);
        s.observe(102, CAP);
        assertEquals(2, s.pendingDays());
        assertEquals(1, s.take(1));
        assertEquals(1, s.take(5));
        assertEquals(0, s.take(5));
    }

    @Test
    void forwardJumpIsCapped() {
        SettlementScheduler s = new SettlementScheduler();
        s.observe(0, CAP);
        s.observe(10_000, CAP);
        assertEquals(CAP, s.pendingDays());
        s.observe(10_001, CAP);
        assertEquals(CAP, s.pendingDays());
    }

    @Test
    void backwardsAddsNothingAndReanchors() {
        SettlementScheduler s = new SettlementScheduler();
        s.observe(50, CAP);
        s.observe(52, CAP);
        assertEquals(2, s.pendingDays());
        s.observe(10, CAP);
        assertEquals(2, s.pendingDays());
        assertEquals(10, s.lastCalendarDay());
        s.observe(11, CAP);
        assertEquals(3, s.pendingDays());
    }

    @Test
    void pausedFollowsTheCalendarAndNeverCatchesUp() {
        SettlementScheduler s = new SettlementScheduler();
        s.observe(0, CAP);
        s.setPaused(true);
        for (int day = 1; day <= 20; day++) {
            s.observe(day, CAP);
            assertEquals(0, s.take(10), "no steps while paused");
        }
        assertEquals(20, s.lastCalendarDay());
        s.setPaused(false);
        s.observe(20, CAP);
        assertEquals(0, s.pendingDays(), "resuming does not catch up");
        s.observe(21, CAP);
        assertEquals(1, s.take(10));
    }

    /** Pausing a world through the scheduler delays it but does not change a single step. */
    @Test
    void pausedWorldResumesIdenticallyOnlyLater() {
        int dpy = WorldSimHealthTest.DPY;
        long seed = 5;
        WorldSim straight = SimFixtures.genesis(seed, "small", dpy);
        WorldSim paused = SimFixtures.genesis(seed, "small", dpy);
        SimFixtures.Collector a = new SimFixtures.Collector();
        SimFixtures.Collector b = new SimFixtures.Collector();
        straight.setObserver(a);
        paused.setObserver(b);
        int days = 40 * dpy;
        long calendar = 1000;
        straight.scheduler().observe(calendar, CAP);
        paused.scheduler().observe(calendar, CAP);
        int pauseFrom = days / 3;
        int pauseLength = 50;
        int stepsPaused = 0;
        for (int i = 0; i < days + pauseLength; i++) {
            calendar++;
            if (i < days) {
                straight.scheduler().observe(calendar, CAP);
                for (int n = straight.scheduler().take(4); n > 0; n--) {
                    straight.step(dpy);
                }
            }
            paused.scheduler().setPaused(i >= pauseFrom && i < pauseFrom + pauseLength);
            long before = paused.day();
            paused.scheduler().observe(calendar, CAP);
            for (int n = paused.scheduler().take(4); n > 0; n--) {
                paused.step(dpy);
            }
            if (paused.scheduler().paused()) {
                assertEquals(before, paused.day(), "no steps while paused");
                stepsPaused++;
            }
        }
        assertEquals(pauseLength, stepsPaused);
        assertEquals(straight.day(), paused.day());
        assertEquals(a.events, b.events);
        assertTrue(a.events.size() > 20);
        assertEquals(withoutScheduler(straight), withoutScheduler(paused));
    }

    private static JsonObject withoutScheduler(WorldSim sim) {
        JsonObject o = JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        o.remove("scheduler");
        return o;
    }
}
