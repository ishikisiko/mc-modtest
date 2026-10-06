package com.example.myvillage.sim.runtime.avatar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sect.SectCourtyard;
import com.example.myvillage.sect.SectGenerator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Which gate is realized next, and how its build area is cut into chunk clips. */
class GateRealizePlanTest {

    @Test
    void theNearestGateGoesFirstAndTiesGoToTheSmallerId() {
        List<GateRealizePlan.Candidate> candidates = List.of(
                new GateRealizePlan.Candidate(7, 120.0),
                new GateRealizePlan.Candidate(3, 80.5),
                new GateRealizePlan.Candidate(9, 140.0));
        assertEquals(3, GateRealizePlan.pick(candidates).orElseThrow().sectId());

        List<GateRealizePlan.Candidate> tied = List.of(
                new GateRealizePlan.Candidate(12, 50.0),
                new GateRealizePlan.Candidate(4, 50.0),
                new GateRealizePlan.Candidate(8, 50.0));
        assertEquals(4, GateRealizePlan.pick(tied).orElseThrow().sectId());

        assertEquals(Optional.empty(), GateRealizePlan.pick(List.of()));
    }

    @Test
    void theRadiusIsPlanarAndInclusive() {
        // y plays no part; 3-4-5 triangle scaled to 160
        assertEquals(160.0, GateRealizePlan.planarDistance(96.0, -128.0, 0, 0), 1e-9);
        assertTrue(GateRealizePlan.withinRadius(GateRealizePlan.planarDistance(96.0, -128.0, 0, 0), 160));
        assertFalse(GateRealizePlan.withinRadius(GateRealizePlan.planarDistance(96.0, -128.1, 0, 0), 160));
        assertTrue(GateRealizePlan.withinRadius(0.0, 160));
        assertEquals(5.0, GateRealizePlan.planarDistance(1003.0, 2004.0, 1000, 2000), 1e-9);
    }

    @Test
    void anAlignedAreaIsCutIntoWholeChunksExactlyOnce() {
        int x0 = 32;
        int z0 = -64;
        int width = 120;
        int depth = 236;
        List<GateRealizePlan.ChunkClip> clips = GateRealizePlan.clips(x0, z0, width, depth);
        assertEquals(ceilDiv(width, 16) * ceilDiv(depth, 16), clips.size());
        assertCoversOnce(clips, x0, z0, width, depth);
    }

    @Test
    void anUnalignedAreaIsCoveredByTheChunksItTouches() {
        int x0 = -1037;
        int z0 = 213;
        int width = 120;
        int depth = 236;
        List<GateRealizePlan.ChunkClip> clips = GateRealizePlan.clips(x0, z0, width, depth);
        int chunksX = Math.floorDiv(x0 + width - 1, 16) - Math.floorDiv(x0, 16) + 1;
        int chunksZ = Math.floorDiv(z0 + depth - 1, 16) - Math.floorDiv(z0, 16) + 1;
        assertEquals(chunksX * chunksZ, clips.size());
        assertTrue(clips.size() <= (ceilDiv(width, 16) + 1) * (ceilDiv(depth, 16) + 1));
        assertCoversOnce(clips, x0, z0, width, depth);
    }

    @Test
    void theBuildAreaOfAGateHoldsItsWholeSiteAndIsCutIntoItsChunks() {
        BlockPos anchor = new BlockPos(1234, 70, -5678);
        BlockPos base = SectGenerator.baseFor(anchor);
        SectGenerator.BuildArea area = SectGenerator.worldgenBuildArea(base);
        SectCourtyard.Footprint site = SectCourtyard.footprint(anchor);
        assertEquals(base.getX(), site.minX(), "the avatars' courtyard uses the same base");
        assertEquals(base.getZ(), site.minZ());
        assertTrue(area.x0() < site.minX() && area.z0() < site.minZ(), "the mountain margin lies outside the site");
        assertTrue(area.x0() + area.width() - 1 > site.maxX() && area.z0() + area.depth() - 1 > site.maxZ());
        List<GateRealizePlan.ChunkClip> clips =
                GateRealizePlan.clips(area.x0(), area.z0(), area.width(), area.depth());
        assertCoversOnce(clips, area.x0(), area.z0(), area.width(), area.depth());
        // the gate column itself is in exactly one clip
        long holding = clips.stream().filter(c -> c.x0() <= anchor.getX() && anchor.getX() <= c.x1()
                && c.z0() <= anchor.getZ() && anchor.getZ() <= c.z1()).count();
        assertEquals(1, holding);
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    /** Every clip is one aligned chunk, no two share a chunk, and every column of the area is in one. */
    private static void assertCoversOnce(List<GateRealizePlan.ChunkClip> clips, int x0, int z0, int width, int depth) {
        Set<Long> chunks = new HashSet<>();
        for (GateRealizePlan.ChunkClip c : clips) {
            assertEquals(0, Math.floorMod(c.x0(), 16));
            assertEquals(0, Math.floorMod(c.z0(), 16));
            assertEquals(c.x0() + 15, c.x1());
            assertEquals(c.z0() + 15, c.z1());
            assertTrue(chunks.add(((long) c.chunkX() << 32) ^ (c.chunkZ() & 0xFFFFFFFFL)), "duplicate clip " + c);
            // every clip touches the area
            assertTrue(c.x1() >= x0 && c.x0() <= x0 + width - 1 && c.z1() >= z0 && c.z0() <= z0 + depth - 1, c.toString());
        }
        for (int x = x0; x < x0 + width; x++) {
            for (int z = z0; z < z0 + depth; z++) {
                int hits = 0;
                for (GateRealizePlan.ChunkClip c : clips) {
                    if (c.x0() <= x && x <= c.x1() && c.z0() <= z && z <= c.z1()) {
                        hits++;
                    }
                }
                assertEquals(1, hits, "column " + x + "," + z);
            }
        }
    }
}
