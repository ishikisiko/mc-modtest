package com.example.myvillage.sim.runtime.avatar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure decisions of {@link GateRealizer}: which gate to realize next and how a build area is cut
 * into chunk clips. No world access, so it is unit-tested directly.
 */
public final class GateRealizePlan {
    /** Chunk edge in blocks. */
    public static final int CHUNK = 16;

    private GateRealizePlan() {
    }

    /** An unrealized gate and the planar distance from it to the nearest player. */
    public record Candidate(int sectId, double distance) {
    }

    /** One chunk's columns: x {@link #x0()}..{@link #x1()}, z {@link #z0()}..{@link #z1()} inclusive. */
    public record ChunkClip(int chunkX, int chunkZ) {
        public int x0() {
            return chunkX * CHUNK;
        }

        public int z0() {
            return chunkZ * CHUNK;
        }

        public int x1() {
            return chunkX * CHUNK + CHUNK - 1;
        }

        public int z1() {
            return chunkZ * CHUNK + CHUNK - 1;
        }
    }

    /** Planar (x/z) distance from a point to a gate column. */
    public static double planarDistance(double x, double z, int gateX, int gateZ) {
        double dx = x - gateX;
        double dz = z - gateZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Whether a gate this far from the nearest player is realized ({@code realize_radius} inclusive). */
    public static boolean withinRadius(double distance, int radius) {
        return distance <= radius;
    }

    /** The gate to realize next: the nearest, ties to the smaller sect id; empty when there is none. */
    public static Optional<Candidate> pick(Collection<Candidate> candidates) {
        return candidates.stream()
                .min(Comparator.comparingDouble(Candidate::distance).thenComparingInt(Candidate::sectId));
    }

    /**
     * The chunks covering the area of {@code width} x {@code depth} columns from (x0, z0), as
     * whole-chunk clips, row by row (z, then x). Together they cover the area exactly once each;
     * columns of an edge chunk outside the area belong to no other clip. For a chunk-aligned area
     * there are {@code ceil(width/16) * ceil(depth/16)} of them.
     */
    public static List<ChunkClip> clips(int x0, int z0, int width, int depth) {
        if (width <= 0 || depth <= 0) {
            throw new IllegalArgumentException("empty area " + width + "x" + depth);
        }
        int minCX = Math.floorDiv(x0, CHUNK);
        int maxCX = Math.floorDiv(x0 + width - 1, CHUNK);
        int minCZ = Math.floorDiv(z0, CHUNK);
        int maxCZ = Math.floorDiv(z0 + depth - 1, CHUNK);
        List<ChunkClip> out = new ArrayList<>((maxCX - minCX + 1) * (maxCZ - minCZ + 1));
        for (int cz = minCZ; cz <= maxCZ; cz++) {
            for (int cx = minCX; cx <= maxCX; cx++) {
                out.add(new ChunkClip(cx, cz));
            }
        }
        return out;
    }
}
