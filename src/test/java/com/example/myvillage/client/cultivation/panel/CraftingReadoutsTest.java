package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingReadoutsTest {
    @Test void pendingAndGeneratedZeroAreDifferentStates() {
        var pending = CraftingReadouts.of(CraftingAptitudes.EMPTY, 0, 0);
        for (var row : pending) {
            assertFalse(row.initialized());
            assertEquals("crafting.myvillage.quality.pending", row.qualityKey());
        }
        var generated = CraftingReadouts.of(new CraftingAptitudes(1, true, 0, 0), 1, 1);
        for (var row : generated) {
            assertTrue(row.initialized()); assertEquals(0, row.score());
            assertEquals("crafting.myvillage.quality.lower", row.qualityKey());
        }
    }

    @Test void professionValuesAndCapsComeFromTheSnapshotWithoutClientRecalculation() {
        // Intentionally different from bundled thresholds: a display must trust supplied server caps.
        var rows = CraftingReadouts.of(new CraftingAptitudes(1, true, 1, 99), 4, 2);
        assertEquals("screen.myvillage.crafting.alchemy", rows.get(0).professionKey());
        assertEquals(1, rows.get(0).score());
        assertEquals("crafting.myvillage.quality.perfect", rows.get(0).qualityKey());
        assertEquals("screen.myvillage.crafting.forging", rows.get(1).professionKey());
        assertEquals(99, rows.get(1).score());
        assertEquals("crafting.myvillage.quality.middle", rows.get(1).qualityKey());
        assertThrows(UnsupportedOperationException.class, () -> rows.clear());
    }

    @Test void displayRejectsUnknownCapCodesInsteadOfInventingAQuality() {
        assertThrows(IllegalArgumentException.class, () -> CraftingReadouts.qualityKey(-1));
        assertThrows(IllegalArgumentException.class, () -> CraftingReadouts.qualityKey(5));
        assertThrows(IllegalArgumentException.class,
                () -> CraftingReadouts.of(new CraftingAptitudes(1, true, 60, 90), 0, 4));
    }
}
