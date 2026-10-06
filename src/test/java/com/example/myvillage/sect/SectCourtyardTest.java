package com.example.myvillage.sect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Courtyard cells against the real sect plan. The expected rectangles below are worked out by hand
 * from {@code SectGenerator}'s layout constants (site 64 x 180, terraces 28 deep rising 8, the slot
 * roster and the template footprints), independently of how {@link SectCourtyard} reads the plan.
 */
class SectCourtyardTest {
    private static final BlockPos ANCHOR = new BlockPos(100, 70, -40);
    /** base = anchor - (32, 0, 90). */
    private static final int BX = 68;
    private static final int BZ = -130;

    private static BlockPos local(int x, int terrace, int z) {
        return new BlockPos(BX + x, ANCHOR.getY() + SectGenerator.TERRACE_RISE * terrace, BZ + z);
    }

    private static List<BlockPos> onTerrace(List<BlockPos> cells, int terrace) {
        int y = ANCHOR.getY() + SectGenerator.TERRACE_RISE * terrace;
        return cells.stream().filter(c -> c.getY() == y).toList();
    }

    @Test
    void cellsStandOnTerraceInteriorsAtFloorPlusOne() {
        List<BlockPos> cells = SectCourtyard.cells(7L, ANCHOR, SectCourtyard.NO_SPIRE);
        assertFalse(cells.isEmpty());
        int total = 0;
        for (int t = 0; t < SectGenerator.TERRACE_COUNT; t++) {
            total += onTerrace(cells, t).size();
        }
        assertEquals(cells.size(), total, "every cell is at some terrace's elevation (floor + 1)");
        // Terrace 0 interior: local x 4..59, z 5..30 (the floor x 3..60, z 4..31 minus its edge row).
        for (BlockPos c : onTerrace(cells, 0)) {
            int x = c.getX() - BX;
            int z = c.getZ() - BZ;
            assertTrue(x >= 4 && x <= 59 && z >= 5 && z <= 30, "inside the gate terrace interior: " + c);
        }
    }

    @Test
    void theGateTerraceKeepsTheCourtyardBehindTheGateAndTheFrontCorners() {
        List<BlockPos> gate = onTerrace(SectCourtyard.cells(7L, ANCHOR, SectCourtyard.NO_SPIRE), 0);
        // Gate x 21..41 z 4..19, towers x 4..20 / 42..58 z 11..31, each plus one block:
        // open are x 22..40 z 21..30 (190), x 4..19 z 5..9 (80) and x 43..59 z 5..9 (85).
        assertEquals(190 + 80 + 85, gate.size());
        assertTrue(gate.contains(local(31, 0, 25)), "the middle of the courtyard behind the gate");
        assertTrue(gate.contains(local(22, 0, 21)));
        assertTrue(gate.contains(local(40, 0, 30)));
        assertFalse(gate.contains(local(31, 0, 10)), "inside the gate building");
        assertFalse(gate.contains(local(21, 0, 25)), "the margin of the left tower");
        assertFalse(gate.contains(local(31, 0, 20)), "the margin behind the gate");
        assertFalse(gate.contains(local(12, 0, 20)), "inside the left tower");
        assertFalse(gate.contains(local(31, 0, 31)), "the terrace's back edge row");
    }

    @Test
    void theDiscipleTerraceLosesItsQuartersAndTheCrossGallery() {
        List<BlockPos> disciple = onTerrace(SectCourtyard.cells(7L, ANCHOR, SectCourtyard.NO_SPIRE), 1);
        // Interior x 4..58 z 41..66 (1430); quarters x 8..28 / 34..54 z 45..62 plus one block (460 each);
        // the roofed gallery between them runs x 28..34 at z 53, of which x 30..32 is not yet blocked.
        assertEquals(1430 - 460 - 460 - 3, disciple.size());
        assertFalse(disciple.contains(local(31, 1, 53)), "under the cross gallery's roof");
        assertTrue(disciple.contains(local(31, 1, 52)), "the open axis beside the gallery");
        assertFalse(disciple.contains(local(18, 1, 53)), "inside the left quarters");
    }

    @Test
    void theSummitKeepsTheForecourtOfTheMainHall() {
        List<BlockPos> summit = onTerrace(SectCourtyard.cells(7L, ANCHOR, SectCourtyard.NO_SPIRE), 4);
        // Interior x 6..57 z 149..174 (1352); hall x 18..44 z 151..175 plus one block (29 x 25 inside).
        assertEquals(1352 - 29 * 25, summit.size());
        assertFalse(summit.contains(local(31, 4, 160)), "inside the main hall");
        assertTrue(summit.contains(local(31, 4, 149)), "the forecourt in front of the hall");
    }

    @Test
    void aSpireOnlyRemovesCellsAndNeverTouchesTheLowerTerraces() {
        Set<BlockPos> plain = new HashSet<>(SectCourtyard.cells(7L, ANCHOR, SectCourtyard.NO_SPIRE));
        for (String variant : SectCourtyard.variants()) {
            if (variant.equals(SectCourtyard.NO_SPIRE)) {
                continue;
            }
            List<BlockPos> cells = SectCourtyard.cells(7L, ANCHOR, variant);
            assertTrue(plain.containsAll(cells), variant + " adds no cell");
            assertTrue(cells.size() < plain.size(), variant + " removes the cells under its spire and bridge");
            assertEquals(355, onTerrace(cells, 0).size(), variant);
            assertEquals(507, onTerrace(cells, 1).size(), variant);
        }
    }

    @Test
    void cellsAreDeterministicOrderedAndDistinct() {
        for (long seed : new long[]{7L, -123456789L}) {
            for (String variant : SectCourtyard.variants()) {
                List<BlockPos> a = SectCourtyard.cells(seed, ANCHOR, variant);
                assertEquals(a, SectCourtyard.cells(seed, ANCHOR, variant));
                assertEquals(a.size(), new HashSet<>(a).size());
                Comparator<BlockPos> order = Comparator.<BlockPos>comparingInt(c -> c.getY())
                        .thenComparingInt(c -> c.getZ()).thenComparingInt(c -> c.getX());
                assertEquals(a.stream().sorted(order).toList(), a, "lowest terrace first, then z, then x");
            }
        }
    }

    @Test
    void theFootprintIsTheBuildSite() {
        SectCourtyard.Footprint f = SectCourtyard.footprint(ANCHOR);
        assertEquals(new SectCourtyard.Footprint(BX, BZ, BX + 63, BZ + 179), f);
        assertEquals(0.0, f.distanceTo(ANCHOR.getX(), ANCHOR.getZ()));
        assertEquals(10.0, f.distanceTo(BX - 10, BZ + 50), 1e-9);
        assertEquals(5.0, f.distanceTo(BX + 64 + 3, BZ + 180 + 4), 1e-9);
    }

    @Test
    void variantsStartWithNoSpire() {
        List<String> variants = SectCourtyard.variants();
        assertEquals(SectCourtyard.NO_SPIRE, variants.get(0));
        assertEquals(1 + SectGenerator.featureVariantNames().length, variants.size());
    }
}
