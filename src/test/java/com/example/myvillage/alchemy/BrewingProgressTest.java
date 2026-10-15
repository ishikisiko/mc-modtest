package com.example.myvillage.alchemy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrewingProgressTest {
    @Test void blockedOutputPausesWithoutCompletingThenCompletesExactlyOnce() {
        var progress = new BrewingProgress();
        assertFalse(progress.advance("huiling", 3, false, true));
        for (int i = 0; i < 50; i++) assertFalse(progress.advance("huiling", 3, false, false));
        assertEquals(1, progress.ticks());
        assertFalse(progress.advance("huiling", 3, false, true));
        assertTrue(progress.advance("huiling", 3, false, true)); assertEquals(0, progress.ticks());
        assertFalse(progress.advance("huiling", 3, false, true));
    }
    @Test void changedRecipeOrSameRecipeInputIdentityResetsProgress() {
        var progress = new BrewingProgress(); progress.restore("huiling", 9, 10);
        assertFalse(progress.advance("yangyuan", 10, false, true)); assertEquals(1, progress.ticks());
        progress.restore("huiling", 9, 10);
        assertFalse(progress.advance("huiling", 10, true, true)); assertEquals(1, progress.ticks());
        assertFalse(progress.advance("", 0, false, false)); assertEquals(0, progress.ticks()); assertEquals("", progress.recipe());
    }
    @Test void restoredProgressIsBoundedAndOnlyRemainingLoadedTicksCompleteIt() {
        var progress = new BrewingProgress(); progress.restore("ningshen", 6, 10);
        for (int i = 0; i < 3; i++) assertFalse(progress.advance("ningshen", 10, false, true));
        assertTrue(progress.advance("ningshen", 10, false, true));
        progress.restore("huiling", -99, 10); assertEquals(0, progress.ticks());
        progress.restore("huiling", Integer.MAX_VALUE, 10); assertEquals(9, progress.ticks());
        assertTrue(progress.advance("huiling", 10, false, true));
    }
    @Test void outputCapacityHonorsPillStackLimitAndComponentsBeforeAnyCommit() {
        assertTrue(BrewingProgress.outputFits(true, false, 0, 2, 16));
        assertTrue(BrewingProgress.outputFits(false, true, 14, 2, 16));
        assertFalse(BrewingProgress.outputFits(false, true, 15, 2, 16));
        assertFalse(BrewingProgress.outputFits(false, false, 1, 2, 16));
        assertFalse(BrewingProgress.outputFits(true, false, 0, 17, 16));
    }
}
