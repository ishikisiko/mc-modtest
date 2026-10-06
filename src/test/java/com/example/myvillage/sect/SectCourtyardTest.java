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
 * from {@code SectGenerator}'s layout constants (site 64 x 180, terraces 28 deep rising 8 and
 * symmetric about x 31 with widths 59/57/55/53/51, the slot roster and the template footprints, the
 * grand stairs x 26..36 from three rows in front of each band to its last row, the axis corridor x
 * 28..34), independently of how {@link SectCourtyard} reads the plan.
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
        // Terrace 0 interior: local x 3..59, z 5..30 (the floor x 2..60, z 4..31 minus its edge row).
        for (BlockPos c : onTerrace(cells, 0)) {
            int x = c.getX() - BX;
            int z = c.getZ() - BZ;
            assertTrue(x >= 3 && x <= 59 && z >= 5 && z <= 30, "inside the gate terrace interior: " + c);
        }
        // Nothing on the axis corridor (x 28..34): it is kept clear for walking.
        for (BlockPos c : cells) {
            int x = c.getX() - BX;
            assertTrue(x < 28 || x > 34, "off the corridor: " + c);
        }
    }

    @Test
    void theGateTerraceKeepsTheCourtyardBehindTheGateAndTheFrontCorners() {
        List<BlockPos> gate = onTerrace(SectCourtyard.cells(7L, ANCHOR, SectCourtyard.NO_SPIRE), 0);
        // Gate x 21..41 z 4..19; seed 7 rolls bell_drum_tower_002 (17 x 21), back-aligned beside the
        // gate: x 4..20 / 42..58 z 11..31; each plus one block. The stair up to the disciple terrace
        // with its cheeks, x 26..36 z 29..39, plus one block: x 25..37 z 28..40.
        // Behind the gate x 22..40 z 21..30 minus the corridor: x 22..27 and 35..40 (120) minus the
        // stair margin x 25..27 / 35..37 at z 28..30 (18) = 102. Front corners x 3..19 and 43..59,
        // z 5..9: 85 each.
        assertEquals(102 + 85 + 85, gate.size());
        assertTrue(gate.contains(local(24, 0, 25)), "the courtyard behind the gate, left of the axis");
        assertTrue(gate.contains(local(38, 0, 25)), "and right of it");
        assertTrue(gate.contains(local(22, 0, 21)));
        assertTrue(gate.contains(local(40, 0, 30)));
        assertTrue(gate.contains(local(24, 0, 30)), "beside the stair's margin");
        assertFalse(gate.contains(local(31, 0, 25)), "the axis corridor");
        assertFalse(gate.contains(local(25, 0, 29)), "the margin of the stair's cheek wall");
        assertFalse(gate.contains(local(31, 0, 10)), "inside the gate building");
        assertFalse(gate.contains(local(21, 0, 25)), "the margin of the left tower");
        assertFalse(gate.contains(local(24, 0, 20)), "the margin behind the gate");
        assertFalse(gate.contains(local(12, 0, 20)), "inside the left tower");
        assertFalse(gate.contains(local(24, 0, 31)), "the terrace's back edge row");
    }

    @Test
    void theDiscipleTerraceLosesItsQuartersTheCorridorAndTheStair() {
        List<BlockPos> disciple = onTerrace(SectCourtyard.cells(7L, ANCHOR, SectCourtyard.NO_SPIRE), 1);
        // Interior x 4..58 z 41..66 (1430); quarters (21 x 18, centred) x 5..25 / 37..57 z 45..62,
        // plus one block x 4..26 / 36..58 z 44..63 (460 each); the corridor x 28..34 (7 x 26 = 182);
        // the stair up to the assembly terrace x 26..36 z 65..75 plus one block reaches z 64..66,
        // of which x 25..27 and 35..37 (18) are not yet counted.
        assertEquals(1430 - 460 - 460 - 182 - 18, disciple.size());
        assertTrue(disciple.contains(local(27, 1, 50)), "the strip between the quarters and the corridor");
        assertTrue(disciple.contains(local(27, 1, 63)));
        assertFalse(disciple.contains(local(27, 1, 64)), "the stair's margin");
        assertFalse(disciple.contains(local(31, 1, 53)), "the corridor");
        assertFalse(disciple.contains(local(18, 1, 53)), "inside the left quarters");
    }

    @Test
    void theSummitKeepsTheForecourtOfTheMainHall() {
        List<BlockPos> summit = onTerrace(SectCourtyard.cells(7L, ANCHOR, SectCourtyard.NO_SPIRE), 4);
        // Interior x 7..55 z 149..174 (49 x 26 = 1274); seed 7 rolls sect_main_hall_002 (25 x 25),
        // centred and back-aligned x 19..43 z 151..175, plus one block x 18..44 z 150..176 (27 x 25
        // inside); the corridor's last rows x 28..34 z 149..150, of which z 149 (7) is left.
        assertEquals(1274 - 27 * 25 - 7, summit.size());
        assertFalse(summit.contains(local(31, 4, 160)), "inside the main hall");
        assertFalse(summit.contains(local(31, 4, 149)), "the corridor in front of the hall");
        assertTrue(summit.contains(local(20, 4, 149)), "the forecourt in front of the hall");
    }

    @Test
    void aSpireNeverAddsCellsAndAnUnbuiltSpireTakesNone() {
        Set<BlockPos> plain = new HashSet<>(SectCourtyard.cells(7L, ANCHOR, SectCourtyard.NO_SPIRE));
        for (String variant : SectCourtyard.variants()) {
            if (variant.equals(SectCourtyard.NO_SPIRE)) {
                continue;
            }
            List<BlockPos> cells = SectCourtyard.cells(7L, ANCHOR, variant);
            assertTrue(plain.containsAll(cells), variant + " adds no cell");
            // every current variant would stand inside the summit, so the realizer skips it
            SectGenerator.SectPlan plan = SectGenerator.plan(7L, SectCourtyard.base(ANCHOR), variant);
            assertFalse(SectGenerator.featureBuildable(plan), variant);
            assertEquals(plain.size(), cells.size(), variant + " is not built and blocks nothing");
            assertEquals(272, onTerrace(cells, 0).size(), variant);
            assertEquals(310, onTerrace(cells, 1).size(), variant);
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
    void scriptureShelfSitesStandInTheScripturePavilionsAtTheTerraceElevation() {
        List<BlockPos> sites = SectCourtyard.scriptureShelfSites(7L, ANCHOR, SectCourtyard.NO_SPIRE);
        assertEquals(2, sites.size(), "one shelf per scripture pavilion");
        // flank_left then flank_right, mirrored about the axis x 31, in the scripture band (terrace 3)
        assertEquals(List.of(local(17, 3, 125), local(45, 3, 125)), sites);
        assertEquals(sites, SectCourtyard.scriptureShelfSites(7L, ANCHOR, SectCourtyard.NO_SPIRE));
        SectCourtyard.Footprint f = SectCourtyard.footprint(ANCHOR);
        int scriptureY = ANCHOR.getY() + SectGenerator.TERRACE_RISE * 3;
        List<BlockPos> cells = SectCourtyard.cells(7L, ANCHOR, SectCourtyard.NO_SPIRE);
        for (BlockPos site : sites) {
            assertTrue(site.getX() >= f.minX() && site.getX() <= f.maxX()
                    && site.getZ() >= f.minZ() && site.getZ() <= f.maxZ(), "inside the compound: " + site);
            assertEquals(scriptureY, site.getY(), "at the scripture terrace's elevation: " + site);
            assertFalse(cells.contains(site), "inside a pavilion, not in the open courtyard: " + site);
        }
        assertTrue(!sites.get(0).equals(sites.get(1)), "two pavilions, two sites");
    }

    @Test
    void variantsStartWithNoSpire() {
        List<String> variants = SectCourtyard.variants();
        assertEquals(SectCourtyard.NO_SPIRE, variants.get(0));
        assertEquals(1 + SectGenerator.featureVariantNames().length, variants.size());
    }
}
