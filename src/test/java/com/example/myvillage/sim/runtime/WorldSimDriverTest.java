package com.example.myvillage.sim.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.WorldSim;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Calendar-driven settlement: one day per tick, capped catch-up, pause without catch-up. */
class WorldSimDriverTest {
    private static final int DPY = RuntimeFixtures.DAYS_PER_YEAR;
    private static final int CAP = 30;

    private static int settle(WorldSimDriver driver, long calendarDay, int ticks) {
        int settled = 0;
        for (int i = 0; i < ticks; i++) {
            if (driver.tick(calendarDay, CAP, DPY) != null) {
                settled++;
            }
        }
        return settled;
    }

    @Test
    void firstObservationAnchorsWithoutSettling() {
        WorldSimDriver driver = new WorldSimDriver(RuntimeFixtures.world());
        long day = driver.sim().day();
        assertNull(driver.tick(500, CAP, DPY), "an old world's calendar must not be caught up at genesis");
        assertEquals(day, driver.sim().day());
        assertEquals(0, driver.pendingDays());
    }

    @Test
    void eachCalendarDaySettlesOneSimDayAtMostOnePerTick() {
        WorldSimDriver driver = new WorldSimDriver(RuntimeFixtures.world());
        long start = driver.sim().day();
        driver.tick(10, CAP, DPY);
        assertEquals(0, settle(driver, 10, 5), "no new calendar day, nothing to settle");
        List<SimEvent> events = driver.tick(11, CAP, DPY);
        assertNotNull(events);
        assertEquals(start + 1, driver.sim().day());
        // A jump of five calendar days settles one per tick, over five ticks.
        assertNotNull(driver.tick(16, CAP, DPY));
        assertEquals(4, driver.pendingDays());
        assertEquals(4, settle(driver, 16, 10));
        assertEquals(start + 6, driver.sim().day());
    }

    @Test
    void aForwardJumpIsCappedAndABackwardJumpAddsNothing() {
        WorldSimDriver driver = new WorldSimDriver(RuntimeFixtures.world());
        long start = driver.sim().day();
        driver.tick(0, CAP, DPY);
        assertEquals(CAP, settle(driver, 1000, 100), "a ticks_per_day change must not stall the server");
        assertEquals(start + CAP, driver.sim().day());
        assertEquals(0, settle(driver, 400, 10), "a day index moving backwards only re-anchors");
        assertEquals(1, settle(driver, 401, 10));
    }

    @Test
    void pauseStopsSettlementAndResumeDoesNotCatchUp() {
        WorldSimDriver driver = new WorldSimDriver(RuntimeFixtures.world());
        long start = driver.sim().day();
        driver.tick(0, CAP, DPY);
        driver.setPaused(true);
        assertTrue(driver.paused());
        for (long cal = 1; cal <= 20; cal++) {
            assertNull(driver.tick(cal, CAP, DPY));
        }
        assertEquals(start, driver.sim().day(), "the ledger does not move while paused");
        assertEquals(0, driver.pendingDays(), "nothing accumulates while paused");
        driver.setPaused(false);
        assertEquals(0, settle(driver, 20, 10), "resuming never catches up");
        assertEquals(1, settle(driver, 21, 10));
        assertEquals(start + 1, driver.sim().day());
    }

    @Test
    void pausedResumedRunMatchesAnUnpausedRunOnlyLater() {
        WorldSimDriver paused = new WorldSimDriver(RuntimeFixtures.world());
        WorldSimDriver straight = new WorldSimDriver(RuntimeFixtures.world());
        paused.tick(0, CAP, DPY);
        straight.tick(0, CAP, DPY);
        List<List<SimEvent>> a = new ArrayList<>();
        List<List<SimEvent>> b = new ArrayList<>();
        paused.setPaused(true);
        for (long cal = 1; cal <= 7; cal++) {
            paused.tick(cal, CAP, DPY);
        }
        paused.setPaused(false);
        for (long cal = 1; cal <= 12; cal++) {
            b.add(straight.tick(cal, CAP, DPY));
            a.add(paused.tick(cal + 7, CAP, DPY));
        }
        assertEquals(b, a, "after resuming, every settled day equals the never-paused run");
    }

    @Test
    void advanceSettlesNowEvenWhilePausedAndLeavesTheSchedulerAlone() {
        WorldSimDriver driver = new WorldSimDriver(RuntimeFixtures.world());
        long start = driver.sim().day();
        driver.tick(3, CAP, DPY);
        driver.setPaused(true);
        driver.consumeSchedulerChange();
        List<Integer> sizes = new ArrayList<>();
        long events = driver.advance(30, DPY, day -> sizes.add(day.size()));
        assertEquals(start + 30, driver.sim().day());
        assertEquals(30, sizes.size());
        assertEquals(events, sizes.stream().mapToLong(Integer::longValue).sum());
        assertTrue(driver.paused());
        assertFalse(driver.consumeSchedulerChange(), "advance does not touch the scheduler");
        assertThrows(IllegalArgumentException.class, () -> driver.advance(0, DPY, day -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> driver.advance(WorldSimDriver.MAX_ADVANCE_DAYS + 1, DPY, day -> { }));
    }

    @Test
    void schedulerChangesAreReportedOnceForTheSave() {
        WorldSimDriver driver = new WorldSimDriver(RuntimeFixtures.world());
        assertFalse(driver.consumeSchedulerChange());
        driver.tick(5, CAP, DPY);
        assertTrue(driver.consumeSchedulerChange(), "the first anchor must reach the save");
        assertFalse(driver.consumeSchedulerChange());
        driver.tick(5, CAP, DPY);
        assertFalse(driver.consumeSchedulerChange());
        driver.setPaused(true);
        assertTrue(driver.consumeSchedulerChange());
    }

    @Test
    void schedulerStateTravelsInsideTheCorePayload() {
        WorldSimDriver driver = new WorldSimDriver(RuntimeFixtures.world());
        driver.tick(40, CAP, DPY);
        driver.tick(45, CAP, DPY);
        driver.setPaused(true);
        byte[] saved = driver.sim().toBytes();
        WorldSim restored = WorldSim.fromBytes(saved, RuntimeFixtures.graph(), RuntimeFixtures.data());
        assertTrue(restored.scheduler().paused());
        assertEquals(driver.pendingDays(), restored.scheduler().pendingDays());
        assertEquals(45, restored.scheduler().lastCalendarDay());
        assertArrayEquals(saved, restored.toBytes());
    }
}
