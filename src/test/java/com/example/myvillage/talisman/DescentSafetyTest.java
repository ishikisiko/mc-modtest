package com.example.myvillage.talisman;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DescentSafetyTest {
    @Test void floatingArrivalCanReachGroundThroughAContinuousClearPath() {
        assertTrue(DescentSafety.supportedWithin(4, step -> true, step -> step == 2));
    }
    @Test void obstructionOrHazardOnTheWayDownCannotBeSkippedToReachLowerGround() {
        assertFalse(DescentSafety.supportedWithin(4, step -> step != 1, step -> step == 3));
        assertFalse(DescentSafety.supportedWithin(4, step -> step != 0, step -> step == 0));
    }
    @Test void voidOrGroundBeyondTheBoundIsRejected() {
        assertFalse(DescentSafety.supportedWithin(4, step -> true, step -> false));
        assertFalse(DescentSafety.supportedWithin(4, step -> true, step -> step == 5));
        assertThrows(IllegalArgumentException.class, () -> DescentSafety.supportedWithin(5, step -> true, step -> true));
    }
    @Test void groundedArrivalDoesNotInspectUnrelatedSpaceBelowItsFloor() {
        var visited = new ArrayList<Integer>();
        assertTrue(DescentSafety.supportedWithin(4, step -> { visited.add(step); return true; }, step -> step == 0));
        assertEquals(List.of(0), visited);
    }
}
