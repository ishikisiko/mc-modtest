package com.example.myvillage.sect;

import java.util.List;

/**
 * Deterministic 反推山形 mountain derivation from a sect terrace profile.
 *
 * Mirrors the offline planner/validator in {@code tools/buildgen/sect_mountain.py}:
 * the compound's terrace elevations + bounds are the mountain's skeleton, the
 * slopes beneath and between terraces are filled with seed-driven value noise,
 * an outer blend skirt grades the man-made relief into the surrounding natural
 * heightmap, a sheer cliff face rises behind the summit, and — when the compound
 * selects the detached-spire feature — a solitary peak (孤峰) is raised one
 * terrace-rise above the summit under the detached volume.
 *
 * Coordinates are LOCAL to the compound base (x cross-slope, z fall-line); the
 * returned height is the absolute world Y of the derived ground surface. A
 * terrace platform surface sits at {@code elevation - 1} so volumes rest on
 * solid ground with no float/bury. {@link #PARITY} mirrors the Python constants.
 */
public final class SectMountain {
    public static final int SKIRT_RADIUS = 24;
    public static final int OUTER_SLOPE = 1;
    public static final int NOISE_AMP_INTER = 3;
    public static final int NOISE_AMP_OUTER = 5;
    public static final int SEAM_SLOPE_LIMIT = 6;
    public static final int SPIRE_GAP = 3;

    /** Natural (pre-mountain) surface height at a local cell, for the blend skirt. */
    @FunctionalInterface
    public interface NaturalHeight {
        int at(int localX, int localZ);
    }

    /** A terrace platform footprint + its surface elevation, in local coords. */
    public record TerraceBox(int index, String name, int elevation,
                             int x0, int z0, int x1, int z1, boolean cliffBack) {
        boolean contains(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }
    }

    /** Solitary peak raised under a detached-spire feature volume. */
    public record SpirePeak(int x0, int z0, int x1, int z1, int top) {
        boolean contains(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }
    }

    private final long seed;
    private final List<TerraceBox> terraces;
    private final int coreX0;
    private final int coreZ0;
    private final int coreX1;
    private final int coreZ1;
    private final int rise;
    private final int cloudSeaY;
    private final int cliffBackTop;
    private final SpirePeak spire;
    private final NaturalHeight natural;

    private SectMountain(long seed, List<TerraceBox> terraces, int rise, int cliffBackHeight,
                         int[] detachedBounds, NaturalHeight natural) {
        this.seed = seed;
        this.terraces = terraces;
        this.rise = rise;
        this.natural = natural;
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (TerraceBox t : terraces) {
            x0 = Math.min(x0, t.x0);
            z0 = Math.min(z0, t.z0);
            x1 = Math.max(x1, t.x1);
            z1 = Math.max(z1, t.z1);
        }
        this.coreX0 = x0;
        this.coreZ0 = z0;
        this.coreX1 = x1;
        this.coreZ1 = z1;
        TerraceBox gate = terraces.get(0);
        TerraceBox disciple = terraces.size() > 1 ? terraces.get(1) : gate;
        this.cloudSeaY = (gate.elevation + disciple.elevation) / 2;
        TerraceBox summit = terraces.get(terraces.size() - 1);
        this.cliffBackTop = summit.elevation + cliffBackHeight;
        if (detachedBounds != null) {
            this.spire = new SpirePeak(detachedBounds[0], detachedBounds[1],
                    detachedBounds[2], detachedBounds[3], (summit.elevation - 1) + rise);
        } else {
            this.spire = null;
        }
    }

    public static SectMountain derive(long seed, List<TerraceBox> terraces, int rise,
                                      int cliffBackHeight, int[] detachedBounds,
                                      NaturalHeight natural) {
        return new SectMountain(seed, terraces, rise, cliffBackHeight, detachedBounds, natural);
    }

    public int cloudSeaY() {
        return cloudSeaY;
    }

    public int cliffBackTop() {
        return cliffBackTop;
    }

    public SpirePeak spire() {
        return spire;
    }

    public int coreX0() {
        return coreX0;
    }

    public int coreZ0() {
        return coreZ0;
    }

    public int coreX1() {
        return coreX1;
    }

    public int coreZ1() {
        return coreZ1;
    }

    /** Natural (pre-mountain) surface height at a local cell. */
    public int naturalAt(int x, int z) {
        return natural.at(x, z);
    }

    /** Deterministic feathering noise for the cloud-sea edge, in [-amp, amp]. */
    public int featherNoise(int x, int z, int amp) {
        return noise(seed ^ 0x5DEECE66DL, x / 2, z / 2, amp);
    }

    private boolean onPlatform(int x, int z) {
        for (TerraceBox t : terraces) {
            if (t.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    /** Derived absolute world Y of the mountain surface at local (x, z). */
    public int height(int x, int z) {
        // detached-spire pillar: solid up to one rise above the summit surface,
        // so the volume stands clear of the platform as a solitary peak.
        if (spire != null && spire.contains(x, z)) {
            return spire.top;
        }

        TerraceBox summit = terraces.get(terraces.size() - 1);
        if (summit.cliffBack && z > summit.z1 && x >= summit.x0 && x <= summit.x1) {
            int backDist = z - summit.z1;
            if (backDist <= 2) {
                return cliffBackTop;             // sheer face, no graded slope
            }
            int dropped = cliffBackTop - OUTER_SLOPE * 2 * (backDist - 2);
            return Math.max(dropped, natural.at(x, z));
        }

        long[] skel = nearestTerraceHeight(x, z);
        int h = (int) skel[0];
        int dist = (int) skel[1];
        if (dist == 0) {
            if (onPlatform(x, z)) {
                return h;                        // platforms stay flat at elevation-1
            }
            return h + noise(seed, x, z, NOISE_AMP_INTER);
        }

        int flank = h - OUTER_SLOPE * dist + noise(seed, x, z, NOISE_AMP_OUTER);
        int nat = natural.at(x, z);
        if (dist >= SKIRT_RADIUS) {
            return nat;
        }
        double frac = (double) dist / SKIRT_RADIUS;
        int blended = (int) Math.round(flank * (1 - frac) + nat * frac);
        return Math.max(blended, nat);
    }

    private long[] nearestTerraceHeight(int x, int z) {
        for (TerraceBox t : terraces) {
            if (t.contains(x, z)) {
                return new long[]{t.elevation - 1, 0};
            }
        }
        for (int i = 0; i < terraces.size() - 1; i++) {
            TerraceBox lower = terraces.get(i);
            TerraceBox upper = terraces.get(i + 1);
            if (z > lower.z1 && z < upper.z0 && x >= coreX0 && x <= coreX1) {
                int span = upper.z0 - lower.z1;
                double frac = (double) (z - lower.z1) / span;
                int hh = (int) Math.round((lower.elevation - 1) * (1 - frac)
                        + (upper.elevation - 1) * frac);
                return new long[]{hh, 0};
            }
        }
        int dx = Math.max(Math.max(coreX0 - x, 0), x - coreX1);
        int dz = Math.max(Math.max(coreZ0 - z, 0), z - coreZ1);
        int dist = Math.max(dx, dz);
        TerraceBox nearest = terraces.get(0);
        int best = Integer.MAX_VALUE;
        for (TerraceBox t : terraces) {
            int d = Math.min(Math.abs(z - t.z0), Math.abs(z - t.z1));
            if (d < best) {
                best = d;
                nearest = t;
            }
        }
        return new long[]{nearest.elevation - 1, dist};
    }

    // --- deterministic value noise (mirrors sect_mountain.py _hash2/_noise) ---

    static long hash2(long seed, int x, int z) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h *= 0xC2B2AE3D27D4EB4FL;
        h ^= z * 0x165667B19E3779F9L;
        h *= 0x9E3779B97F4A7C15L;
        h ^= (h >>> 31);
        return h;
    }

    static int noise(long seed, int x, int z, int amp) {
        if (amp <= 0) {
            return 0;
        }
        long span = 2L * amp + 1;
        return (int) (Long.remainderUnsigned(hash2(seed, x, z), span)) - amp;
    }

    public static final java.util.Map<String, Integer> PARITY = java.util.Map.of(
            "SKIRT_RADIUS", SKIRT_RADIUS,
            "OUTER_SLOPE", OUTER_SLOPE,
            "NOISE_AMP_INTER", NOISE_AMP_INTER,
            "NOISE_AMP_OUTER", NOISE_AMP_OUTER,
            "SEAM_SLOPE_LIMIT", SEAM_SLOPE_LIMIT,
            "SPIRE_GAP", SPIRE_GAP);
}
