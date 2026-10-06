package com.example.myvillage.sim.runtime.player;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.TaskView;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The pure decisions of the sect-task runtime: tribute stones, patrol and courier steps, master guidance. */
class SectTasksTest {
    private static TaskView task(String kind, int progress, int count, int target) {
        return new TaskView(kind + "_task", kind, count, 10, progress, target, target < 0 ? "" : "天音宗", 3, false);
    }

    @Test
    void stonesAreCountedOverEverySlot() {
        assertEquals(0, SectTasks.countStones(new int[0]));
        assertEquals(7, SectTasks.countStones(new int[] {0, 3, 0, 4}));
        assertEquals(Integer.MAX_VALUE, SectTasks.countStones(new int[] {Integer.MAX_VALUE, 5}));
    }

    @Test
    void aTributeTakesFirstSlotsFirstAndNothingWhenShort() {
        assertArrayEquals(new int[] {0, 3, 0, 2}, SectTasks.takePlan(new int[] {0, 3, 0, 4}, 5));
        assertArrayEquals(new int[] {0, 3, 0, 4}, SectTasks.takePlan(new int[] {0, 3, 0, 4}, 7));
        assertArrayEquals(new int[] {5, 0}, SectTasks.takePlan(new int[] {64, 1}, 5));
        assertNull(SectTasks.takePlan(new int[] {0, 3, 0, 1}, 5));
        assertNull(SectTasks.takePlan(new int[0], 1));
        assertArrayEquals(new int[] {0, 0}, SectTasks.takePlan(new int[] {2, 2}, 0));
    }

    @Test
    void aBeastStepsOnlyAnUnfinishedPatrolInsideTheSectRegion() {
        TaskView patrol = task("patrol", 1, 3, -1);
        assertTrue(SectTasks.patrolCounts(patrol, Optional.of("qingyun_hills"), "qingyun_hills"));
        assertFalse(SectTasks.patrolCounts(patrol, Optional.of("east_marsh"), "qingyun_hills"));
        assertFalse(SectTasks.patrolCounts(patrol, Optional.empty(), "qingyun_hills"));
        assertFalse(SectTasks.patrolCounts(patrol, Optional.of(""), ""));
        assertFalse(SectTasks.patrolCounts(task("patrol", 3, 3, -1), Optional.of("qingyun_hills"), "qingyun_hills"));
        assertFalse(SectTasks.patrolCounts(task("tribute", 0, 5, -1), Optional.of("qingyun_hills"), "qingyun_hills"));
    }

    @Test
    void aCourierWaitsUntilItsLetterArrives() {
        assertTrue(SectTasks.courierPending(task("courier", 0, 1, 4)));
        assertFalse(SectTasks.courierPending(task("courier", 1, 1, 4)));
        assertFalse(SectTasks.courierPending(task("courier", 0, 1, -1)));
        assertFalse(SectTasks.courierPending(task("patrol", 0, 3, -1)));
    }

    @Test
    void progressDecidesWhetherATaskMayBeTurnedIn() {
        assertFalse(SectDialogueScenes.taskReady(task("patrol", 2, 3, -1), true));
        assertTrue(SectDialogueScenes.taskReady(task("patrol", 3, 3, -1), false));
        assertTrue(SectDialogueScenes.taskReady(task("courier", 1, 1, 4), false));
        // a tribute is ready by the inventory alone
        assertFalse(SectDialogueScenes.taskReady(task("tribute", 0, 5, -1), false));
        assertTrue(SectDialogueScenes.taskReady(task("tribute", 0, 5, -1), true));
    }

    @Test
    void aLivingMasterOfTheSameSectGuides() {
        assertEquals(11_500, SectTasks.guidanceBasisPoints(7, 42, true, 7, 0.15));
        assertEquals(10_000, SectTasks.guidanceBasisPoints(7, -1, true, 7, 0.15));
        assertEquals(10_000, SectTasks.guidanceBasisPoints(7, 42, false, 7, 0.15));
        assertEquals(10_000, SectTasks.guidanceBasisPoints(7, 42, true, 3, 0.15));
        assertEquals(10_000, SectTasks.guidanceBasisPoints(-1, 42, true, -1, 0.15));
        assertEquals(10_000, SectTasks.guidanceBasisPoints(7, 42, true, 7, 0.0));
        assertEquals(10_000, SectTasks.guidanceBasisPoints(7, 42, true, 7, Double.NaN));
    }
}
