package com.example.myvillage.cultivation.fate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwordObsessionStacksTest {
    @Test void sweepUsesStartingBonusAndEarnsOnlyOneStack() {
        var state = new SwordObsessionStacks();
        assertEquals(0, state.beforeMove(1, 0, 100));
        assertFalse(state.accepted(1, 0, 100, 5, false));
        assertEquals(0, state.beforeMove(1, 1, 100));
        assertFalse(state.accepted(1, 1, 100, 5, false));
        assertEquals(1, state.stacks(1, 100));
        assertEquals(1, state.beforeMove(2, 2, 100));
    }
    @Test void fifthAcceptedFinisherTriggersOnceThenRetainsCappedStreak() {
        var state = new SwordObsessionStacks();
        for (int i = 1; i <= 4; i++) assertFalse(state.accepted(i, i, 100, 5, false));
        assertTrue(state.accepted(5, 5, 100, 5, true));
        assertFalse(state.accepted(5, 6, 100, 5, true));
        assertFalse(state.accepted(6, 7, 100, 5, false));
        assertTrue(state.accepted(7, 8, 100, 5, true));
        assertEquals(5, state.stacks(8, 100));
    }
    @Test void missesDoNotEarnOrRefreshAndTimeoutBoundaryIsExact() {
        var state = new SwordObsessionStacks();
        state.accepted(1, 10, 100, 5, false);
        assertEquals(1, state.beforeMove(2, 99, 100));
        assertEquals(1, state.stacks(109, 100));
        assertEquals(0, state.beforeMove(3, 110, 100));
        assertFalse(state.accepted(3, 110, 100, 5, true));
        assertEquals(1, state.stacks(110, 100));
    }
    @Test void everyNewHitRefreshesTimeoutButRepeatedTargetsDoNot() {
        var state = new SwordObsessionStacks();
        state.accepted(1, 0, 100, 5, false);
        state.accepted(2, 90, 100, 5, false);
        state.accepted(2, 95, 100, 5, false);
        assertEquals(2, state.stacks(189, 100));
        assertEquals(0, state.stacks(190, 100));
    }
    @Test void rewindAndNewLifecycleHaveNoCarriedStreak() {
        var state = new SwordObsessionStacks();
        state.accepted(1, 100, 100, 5, false);
        assertEquals(0, state.stacks(99, 100));
        assertEquals(0, new SwordObsessionStacks().beforeMove(1, 100, 100));
    }
}
