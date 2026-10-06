package com.example.myvillage.sect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.runtime.avatar.ScriptureShelves;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The scripture shelf sites ({@link SectCourtyard#scriptureShelfSites}) and the shelf placer's
 * upward floor scan ({@link ScriptureShelves#floorAbove}) against compounds built in memory with the
 * real templates ({@link SectCompoundRealizationTest.MemorySink}): each shelf lands on the hall
 * floor of its scripture pavilion, inside the template's footprint, under a roof, for both pavilion
 * templates and both build paths.
 */
class SectCourtyardScriptureTest {
    private static final BlockPos ANCHOR = new BlockPos(100, 70, -40);
    private static final BlockPos BASE = SectCourtyard.base(ANCHOR);
    private static final int E = ANCHOR.getY();
    private static final SectCompoundRealizationTest.Ground FLAT = (x, z) -> E - 1;
    private static final SectCompoundRealizationTest.Ground ROLLING =
            (x, z) -> E - 6 + Math.floorDiv(z, 9) + (int) Math.round(3 * Math.sin(x / 7.0));

    @BeforeAll
    static void bootstrap() throws IOException {
        SectCompoundRealizationTest.bootstrap();
    }

    // --- the pure scan ------------------------------------------------------

    @Test
    void floorAboveTakesTheFirstSolidWithTwoFreeAbove() {
        // solid at 69..71 and 75; free elsewhere: the floor is 71
        Set<Integer> solid = Set.of(69, 70, 71, 75);
        assertEquals(71, ScriptureShelves.floorAbove(solid::contains, y -> !solid.contains(y), 69, 82));
        // a one-block gap does not count: 69..71 solid, 72 free, 73 solid, then open from 74
        Set<Integer> gap = Set.of(69, 70, 71, 73);
        assertEquals(73, ScriptureShelves.floorAbove(gap::contains, y -> !gap.contains(y), 69, 82));
        // the start itself may be the floor
        assertEquals(69, ScriptureShelves.floorAbove(y -> y == 69, y -> y != 69, 69, 82));
    }

    @Test
    void floorAboveHonoursTheRange() {
        Set<Integer> solid = Set.of(60, 61, 62);
        assertEquals(ScriptureShelves.NONE, ScriptureShelves.floorAbove(solid::contains, y -> !solid.contains(y), 69, 82));
        // nothing but solid: no floor
        assertEquals(ScriptureShelves.NONE, ScriptureShelves.floorAbove(y -> true, y -> false, 69, 82));
        // nothing but air: no floor
        assertEquals(ScriptureShelves.NONE, ScriptureShelves.floorAbove(y -> false, y -> true, 69, 82));
        // the floor at the top of the range counts, one above does not
        assertEquals(82, ScriptureShelves.floorAbove(y -> y == 82, y -> y != 82, 69, 82));
        assertEquals(ScriptureShelves.NONE, ScriptureShelves.floorAbove(y -> y == 83, y -> y != 83, 69, 82));
    }

    @Test
    void anExistingShelfIsFoundAgainNotStackedOn() {
        // floor 72, a shelf at 73 (free, not standable), open above: the floor stays 72
        int shelf = 73;
        assertEquals(72, ScriptureShelves.floorAbove(y -> y <= 72, y -> y > 72 || y == shelf, 69, 82));
    }

    // --- the compounds ------------------------------------------------------

    @Test
    void sitesAreTheTwoPavilionCentresAtTheTerraceElevation() {
        List<BlockPos> sites = SectCourtyard.scriptureShelfSites(7L, ANCHOR, SectCourtyard.NO_SPIRE);
        assertEquals(List.of(new BlockPos(85, 94, -5), new BlockPos(113, 94, -5)), sites);
    }

    @Test
    void bothPavilionTemplatesAreCovered() {
        Set<String> templates = new TreeSet<>();
        templates.add(pavilionTemplate(7L));
        templates.add(pavilionTemplate(otherTemplateSeed()));
        assertEquals(Set.of("scripture_pavilion_001", "scripture_pavilion_002"), templates);
    }

    @Test
    void shelvesStandOnTheHallFloorOfBothPavilionTemplates() {
        for (long seed : new long[]{7L, otherTemplateSeed()}) {
            check(seed, commandBuild(seed), "command");
            check(seed, worldgenBuild(seed), "worldgen");
        }
    }

    private static void check(long seed, SectCompoundRealizationTest.MemorySink sink, String path) {
        SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, SectCourtyard.NO_SPIRE);
        List<SectGenerator.Slot> pavilions = new ArrayList<>();
        for (SectGenerator.Slot slot : plan.slots()) {
            if ("scripture".equals(slot.terraceName()) && slot.role().startsWith("flank_")) {
                pavilions.add(slot);
            }
        }
        List<BlockPos> sites = SectCourtyard.scriptureShelfSites(seed, ANCHOR, SectCourtyard.NO_SPIRE);
        assertEquals(2, sites.size(), "two pavilions, seed " + seed);
        for (BlockPos site : sites) {
            String what = path + " seed " + seed + " site " + site.toShortString();
            int x = site.getX();
            int z = site.getZ();
            int floor = ScriptureShelves.floorAbove(
                    y -> ScriptureShelves.standable(sink.get(new BlockPos(x, y, z)), EmptyBlockGetter.INSTANCE,
                            new BlockPos(x, y, z)),
                    y -> ScriptureShelves.free(sink.get(new BlockPos(x, y, z))),
                    site.getY() - ScriptureShelves.SCAN_BELOW, site.getY() + ScriptureShelves.SCAN_ABOVE);
            assertNotEquals(ScriptureShelves.NONE, floor, what + ": a floor");
            BlockPos shelf = new BlockPos(x, floor + 1, z);
            SectGenerator.Slot slot = pavilions.stream()
                    .filter(s -> BASE.getX() + s.center().x() == x && BASE.getZ() + s.center().z() == z)
                    .findFirst().orElseThrow();
            int[] fp = SectGenerator.templateFootprint(slot.templateId());
            int x0 = BASE.getX() + slot.bounds().x0();
            int z0 = BASE.getZ() + slot.bounds().z0();
            assertTrue(x >= x0 && x < x0 + fp[0] && z >= z0 && z < z0 + fp[1],
                    what + ": inside the " + slot.templateId() + " footprint");
            assertTrue(sink.get(shelf).isAir(), what + ": the shelf cell is air");
            assertTrue(sink.get(shelf.above()).isAir(), what + ": air above the shelf");
            BlockState below = sink.get(shelf.below());
            assertTrue(ScriptureShelves.standable(below, EmptyBlockGetter.INSTANCE, shelf.below()),
                    what + ": a sturdy floor under the shelf, got " + below);
            assertTrue(shelf.getY() - site.getY() <= 6, what + ": the shelf at y " + shelf.getY()
                    + " is within 6 of the terrace elevation " + site.getY());
            boolean roofed = false;
            for (int y = shelf.getY() + 2; y <= shelf.getY() + 16 && !roofed; y++) {
                roofed = !sink.get(new BlockPos(x, y, z)).isAir();
            }
            assertTrue(roofed, what + ": under the pavilion roof");
            System.out.println("SCRIPTURE_SHELF_TEST path=" + path + " seed=" + seed + " template="
                    + slot.templateId() + " site=" + site.toShortString() + " floor=" + floor + " shelf="
                    + shelf.toShortString() + " floor_block=" + below.getBlock());
        }
    }

    // --- helpers ------------------------------------------------------------

    private static String pavilionTemplate(long seed) {
        SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, SectCourtyard.NO_SPIRE);
        Set<String> ids = new TreeSet<>();
        for (SectGenerator.Slot slot : plan.slots()) {
            if ("scripture".equals(slot.terraceName()) && slot.role().startsWith("flank_")) {
                ids.add(slot.templateId());
            }
        }
        assertEquals(1, ids.size(), "both pavilions of seed " + seed + " share a template: " + ids);
        return ids.iterator().next();
    }

    /** The first seed after 7 whose pavilions use the other template. */
    private static long otherTemplateSeed() {
        String seven = pavilionTemplate(7L);
        for (long seed = 8L; seed < 200L; seed++) {
            if (!pavilionTemplate(seed).equals(seven)) {
                return seed;
            }
        }
        throw new AssertionError("no seed with the other pavilion template");
    }

    private static SectCompoundRealizationTest.MemorySink commandBuild(long seed) {
        SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, SectCourtyard.NO_SPIRE);
        SectCompoundRealizationTest.MemorySink sink = new SectCompoundRealizationTest.MemorySink(FLAT, null);
        SectGenerator.BuildStats stats = new SectGenerator.BuildStats();
        SectGenerator.realizeCompound(sink, plan, RandomSource.create(seed), seed, stats);
        assertEquals(plan.slots().size(), stats.placedSlots, "every building placed: " + stats.skippedSlotIds);
        return sink;
    }

    private static SectCompoundRealizationTest.MemorySink worldgenBuild(long seed) {
        SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, SectCourtyard.NO_SPIRE);
        SectMountain mountain = SectGenerator.buildMountain(seed, plan, ROLLING::top);
        SectCompoundRealizationTest.MemorySink sink = new SectCompoundRealizationTest.MemorySink(ROLLING, mountain);
        SectGenerator.BuildStats stats = new SectGenerator.BuildStats();
        SectGenerator.writeMountain(sink, plan, mountain, stats);
        SectGenerator.realizeCompound(sink, plan, RandomSource.create(seed), seed, stats);
        assertEquals(plan.slots().size(), stats.placedSlots, "every building placed: " + stats.skippedSlotIds);
        return sink;
    }
}
