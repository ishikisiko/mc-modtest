package com.example.myvillage.combat.foundation;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static com.example.myvillage.combat.foundation.ControlState.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class ControlStateTest {
    private static final UUID FIRST = new UUID(0, 1);
    private static final UUID SECOND = new UUID(0, 2);

    @Test
    void sameEffectFromDifferentCastersExpiresIndependently() {
        ControlState state = new ControlState();
        assertTrue(state.put(ROOT, FIRST, "myvillage:root", 100, 60, 1, 8));
        assertTrue(state.put(ROOT, SECOND, "myvillage:root", 110, 20, 1, 8));
        assertEquals(2, state.snapshot(110).size());
        state.expire(130);
        assertEquals(1, state.size());
        assertEquals(30, state.remaining(ROOT, 130));
        assertTrue(state.has(ROOT, 159));
        assertFalse(state.has(ROOT, 160), "截止 tick 精确到期，不额外多控制一 tick");
        assertTrue(state.idle(160, 40));
    }

    @Test
    void strongestSlowDoesNotMultiplyAndShorterSourceDoesNotEraseLongerSource() {
        ControlState state = new ControlState();
        state.put(SLOW, FIRST, "myvillage:ice", 0, 80, .3, 8);
        state.put(SLOW, SECOND, "myvillage:wood", 0, 20, .6, 8);
        assertEquals(.6, state.strongestSlow(10));
        assertEquals(.3, state.strongestSlow(20));
        assertEquals(0, state.strongestSlow(80));
    }

    @Test
    void cleanseKeepsPoiseAndRepeatHistory() {
        ControlState state = new ControlState();
        state.put(ROOT, FIRST, "myvillage:root", 100, 60, 1, 8);
        state.put(SLOW, SECOND, "myvillage:slow", 100, 80, .2, 8);
        state.put(POISE, FIRST, "myvillage:poise", 100, 100, 0, 8);
        state.recordRoot(100, 40);
        assertEquals(2, state.cleanse());
        assertFalse(state.has(ROOT, 100));
        assertFalse(state.has(SLOW, 100));
        assertTrue(state.has(POISE, 100));
        assertEquals(1, state.rootStacks(120, 40));
        assertEquals(0, state.rootStacks(141, 40));
    }

    @Test
    void sourceRevocationLeavesOtherEffectsAndCasters() {
        ControlState state = new ControlState();
        state.put(ROOT, FIRST, "myvillage:root", 0, 60, 1, 8);
        state.put(ROOT, SECOND, "myvillage:root", 0, 60, 1, 8);
        state.put(SLOW, FIRST, "myvillage:slow", 0, 60, .3, 8);
        assertEquals(1, state.revoke(FIRST, "myvillage:root"));
        assertTrue(state.has(ROOT, 0));
        assertTrue(state.has(SLOW, 0));
        assertEquals(1, state.forgetCaster(FIRST));
        assertTrue(state.has(ROOT, 0));
        assertFalse(state.has(SLOW, 0));
    }

    @Test
    void offlineOrNowFriendlyCastersArePrunedWithoutErasingPoise() {
        ControlState state = new ControlState();
        state.put(ROOT, FIRST, "myvillage:root", 0, 60, 1, 8);
        state.put(ROOT, SECOND, "myvillage:root", 0, 60, 1, 8);
        state.put(POISE, FIRST, "myvillage:poise", 0, 60, 0, 8);
        state.pruneHostileSources(SECOND::equals);
        assertEquals(2, state.size());
        assertTrue(state.has(POISE, 0));
        assertTrue(state.has(ROOT, 0));
    }

    @Test
    void capacityAndOverflowRejectWithoutDroppingExistingSources() {
        ControlState state = new ControlState();
        assertTrue(state.put(ROOT, FIRST, "myvillage:root", 0, 10, 1, 1));
        assertFalse(state.put(ROOT, SECOND, "myvillage:root", 0, 20, 1, 1));
        assertTrue(state.put(ROOT, FIRST, "myvillage:root", 0, 20, 1, 1));
        assertEquals(1, state.size());
        assertEquals(20, state.remaining(ROOT, 0));
        assertTrue(state.put(ROOT, SECOND, "myvillage:root", 20, 20, 1, 1), "过期来源应释放容量");
        assertFalse(state.put(ROOT, FIRST, "myvillage:bad", 20, -1, 1, 2));
        assertFalse(state.put(SLOW, FIRST, "myvillage:bad", 20, 20, Double.NaN, 2));
        assertFalse(state.put(ROOT, FIRST, "myvillage:overflow", Long.MAX_VALUE - 2, 10, 1, 2));
    }

    @Test
    void repeatWindowExpiresAfterBoundaryAndCannotBeResetByReadback() {
        ControlState state = new ControlState();
        state.recordRoot(100, 40);
        state.recordRoot(110, 40);
        assertEquals(2, state.rootStacks(150, 40));
        state.snapshot(150);
        assertEquals(0, state.rootStacks(151, 40));
        state.recordRoot(151, 40);
        assertEquals(1, state.rootStacks(151, 40));
    }

    @Test
    void readOnlySnapshotDoesNotExposeMutableStateOrRefreshDuration() {
        ControlState state = new ControlState();
        state.put(ROOT, FIRST, "myvillage:root", 10, 60, 1, 8);
        var snapshot = state.snapshot(30);
        assertEquals(40, snapshot.getFirst().remainingTicks());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertEquals(1, state.size());
        assertEquals(0, state.remaining(ROOT, 70));
        assertTrue(state.snapshot(70).isEmpty());
    }
}
