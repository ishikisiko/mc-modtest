package com.example.myvillage.sect;

import java.util.List;

/**
 * Deterministic 反推山形 mountain derivation from a sect terrace profile.
 *
 * Mirrors the offline planner/validator in {@code tools/buildgen/sect_mountain.py}:
 * the compound's terrace elevations + bounds are the mountain's skeleton. Inside the
 * compound core (the terraces' bounding box) and on the forecourt in front of the
 * gate there is no noise: terraces and the forecourt sit at their floor, the band
 * between two terraces at the upper floor within the upper terrace's width (else the
 * lower floor), and the taper strips beside the narrower terraces slope down one block
 * per block from the nearest terrace. Outside the core the skirt is a smooth cone falling one
 * block per block from the terraces, with coarse-lattice value-noise relief, slope-limited so
 * no two neighbouring skirt columns differ by more than {@link #SKIRT_SLOPE_LIMIT}, and graded
 * into the natural heightmap by {@link #SKIRT_RADIUS}; a sheer cliff face rises behind the
 * summit, and, when the compound builds the detached-spire feature, a solitary peak (孤峰) is
 * raised one terrace-rise above the summit under it.
 *
 * Coordinates are LOCAL to the compound base (x cross-slope, z fall-line); the
 * returned height is the absolute world Y of the derived ground surface. A
 * terrace platform surface sits at {@code elevation - 1} so volumes rest on
 * solid ground with no float/bury. {@link #PARITY} mirrors the Python constants.
 */
public final class SectMountain {
    public static final int SKIRT_RADIUS = 24;
    public static final int OUTER_SLOPE = 1;
    /** Former band-noise amplitude; unused since the core went noise-free (0.35.1), kept in {@link #PARITY}. */
    public static final int NOISE_AMP_INTER = 3;
    /** Amplitude of the skirt's coarse relief (value noise on a {@link #SKIRT_LATTICE} lattice). */
    public static final int NOISE_AMP_OUTER = 4;
    public static final int SEAM_SLOPE_LIMIT = 6;
    public static final int SPIRE_GAP = 3;
    /** Lattice spacing (cells) of the skirt's coarse relief. */
    public static final int SKIRT_LATTICE = 6;
    /** Amplitude of the skirt's fine relief octave. */
    public static final int NOISE_AMP_FINE = 1;
    /** Lattice spacing (cells) of the skirt's fine relief octave. */
    public static final int SKIRT_FINE_LATTICE = 2;
    /** Max |height difference| between 4-neighbouring skirt columns (away from the cliff back). */
    public static final int SKIRT_SLOPE_LIMIT = 2;
    /** Cells over which the relief fades in from the core / forecourt edge. */
    public static final int SKIRT_NOISE_TAPER = 4;

    static final long SALT_COARSE = 0x5EC75C1A7E00C0A5L;
    static final long SALT_FINE = 0x5EC75C1A7E00F1E5L;
    static final long SALT_SURFACE = 0x5EC75C1A7E005EEDL;

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
    private final int[] apron;
    private final int cliffBackTop;
    private final SpirePeak spire;
    private final NaturalHeight natural;
    private final List<int[]> sources;
    private final int cliffBackHeight;
    private volatile SkirtGrid grid;

    private SectMountain(long seed, List<TerraceBox> terraces, int rise, int cliffBackHeight,
                         int[] detachedBounds, int[] apron, NaturalHeight natural) {
        this.seed = seed;
        this.apron = apron;
        this.terraces = terraces;
        this.rise = rise;
        this.natural = natural;
        this.cliffBackHeight = cliffBackHeight;
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
        TerraceBox summit = terraces.get(terraces.size() - 1);
        this.cliffBackTop = summit.elevation + cliffBackHeight;
        if (detachedBounds != null) {
            this.spire = new SpirePeak(detachedBounds[0], detachedBounds[1],
                    detachedBounds[2], detachedBounds[3], (summit.elevation - 1) + rise);
        } else {
            this.spire = null;
        }
        this.sources = skeletonSources(terraces, apron);
    }

    /**
     * The skirt skeleton's sources {x0, z0, x1, z1, floorY}: each terrace widened over the bands
     * on either side of it (the upper terrace forward, the lower one back, each at its own width,
     * at its own floor), plus the forecourt at the gate floor.
     */
    private static List<int[]> skeletonSources(List<TerraceBox> terraces, int[] apron) {
        List<int[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < terraces.size(); i++) {
            TerraceBox t = terraces.get(i);
            int z0 = i > 0 ? terraces.get(i - 1).z1 + 1 : t.z0;
            int z1 = i < terraces.size() - 1 ? terraces.get(i + 1).z0 - 1 : t.z1;
            out.add(new int[]{t.x0, z0, t.x1, z1, t.elevation - 1});
        }
        if (apron != null) {
            out.add(new int[]{apron[0], apron[1], apron[2], apron[3], terraces.get(0).elevation - 1});
        }
        return List.copyOf(out);
    }

    /**
     * @param detachedBounds {x0, z0, x1, z1} of a detached spire that is built, else null
     * @param apron {x0, z0, x1, z1} of the forecourt in front of the gate terrace, else null
     */
    public static SectMountain derive(long seed, List<TerraceBox> terraces, int rise,
                                      int cliffBackHeight, int[] detachedBounds, int[] apron,
                                      NaturalHeight natural) {
        return new SectMountain(seed, terraces, rise, cliffBackHeight, detachedBounds, apron, natural);
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

    private boolean inCore(int x, int z) {
        return x >= coreX0 && x <= coreX1 && z >= coreZ0 && z <= coreZ1;
    }

    private boolean onApron(int x, int z) {
        return apron != null && x >= apron[0] && x <= apron[2] && z >= apron[1] && z <= apron[3];
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

        if (onApron(x, z)) {
            return terraces.get(0).elevation - 1;  // the forecourt is level with the gate floor
        }
        if (inCore(x, z)) {
            return coreHeight(x, z);
        }

        int dist = coreDistance(x, z);
        int nat = natural.at(x, z);
        if (dist >= SKIRT_RADIUS) {
            return nat;
        }
        // the slope-limited relief, capped so it can still fall to natural ground by the skirt's
        // edge at SKIRT_SLOPE_LIMIT per cell, and never below natural ground
        int relief = grid().at(x, z);
        return Math.max(nat, Math.min(relief, nat + SKIRT_SLOPE_LIMIT * (SKIRT_RADIUS - dist)));
    }

    /**
     * Whether (x, z) is a skirt column: outside the core, the forecourt, the cliff back and the
     * spire. Its top block takes {@link #surfaceVariant}.
     */
    public boolean isSkirt(int x, int z) {
        return !inCore(x, z) && !onApron(x, z) && !inCliffBack(x, z) && !(spire != null && spire.contains(x, z));
    }

    private boolean inCliffBack(int x, int z) {
        TerraceBox summit = terraces.get(terraces.size() - 1);
        return summit.cliffBack && z > summit.z1 && x >= summit.x0 && x <= summit.x1;
    }

    /**
     * Surface material of a skirt column's top block: 0 stone (10 in 16), 1 andesite, 2 tuff,
     * 3 cobbled deepslate (2 in 16 each), by a hash of (seed, x, z).
     */
    public int surfaceVariant(int x, int z) {
        int r = (int) Long.remainderUnsigned(hash2(seed ^ SALT_SURFACE, x, z), 16);
        if (r < 10) return 0;
        if (r < 12) return 1;
        if (r < 14) return 2;
        return 3;
    }

    /** Chebyshev distance from (x, z) to the core rectangle (0 inside). */
    private int coreDistance(int x, int z) {
        int dx = Math.max(Math.max(coreX0 - x, 0), x - coreX1);
        int dz = Math.max(Math.max(coreZ0 - z, 0), z - coreZ1);
        return Math.max(dx, dz);
    }

    /**
     * Noise-free height inside the core: a terrace's floor; in the band in front of a terrace,
     * the upper floor within the upper terrace's width and the lower floor within the lower
     * one's; elsewhere (the taper strips beside the narrower terraces) the nearest terrace's floor
     * minus the distance to it, ties to the higher floor.
     */
    private int coreHeight(int x, int z) {
        for (TerraceBox t : terraces) {
            if (t.contains(x, z)) {
                return t.elevation - 1;
            }
        }
        for (int i = 0; i < terraces.size() - 1; i++) {
            TerraceBox lower = terraces.get(i);
            TerraceBox upper = terraces.get(i + 1);
            if (z > lower.z1 && z < upper.z0) {
                if (x >= upper.x0 && x <= upper.x1) {
                    return upper.elevation - 1;
                }
                if (x >= lower.x0 && x <= lower.x1) {
                    return lower.elevation - 1;
                }
            }
        }
        int best = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;
        for (TerraceBox t : terraces) {
            int dx = Math.max(Math.max(t.x0 - x, 0), x - t.x1);
            int dz = Math.max(Math.max(t.z0 - z, 0), z - t.z1);
            int d = Math.max(dx, dz);
            int h = t.elevation - 1 - OUTER_SLOPE * d;
            if (d < bestDist || (d == bestDist && h > best)) {
                bestDist = d;
                best = h;
            }
        }
        return best;
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

    /**
     * Smooth value noise in [-1, 1]: lattice values {@code (hash2(seed ^ salt, i, j) mod 2001 -
     * 1000) / 1000} at lattice points (i * lattice, j * lattice), blended with smoothstep weights.
     */
    static double valueNoise(long seed, long salt, int x, int z, int lattice) {
        int i = Math.floorDiv(x, lattice);
        int j = Math.floorDiv(z, lattice);
        double sx = smoothstep((x - i * lattice) / (double) lattice);
        double sz = smoothstep((z - j * lattice) / (double) lattice);
        double v00 = latticeValue(seed, salt, i, j);
        double v10 = latticeValue(seed, salt, i + 1, j);
        double v01 = latticeValue(seed, salt, i, j + 1);
        double v11 = latticeValue(seed, salt, i + 1, j + 1);
        double a = v00 + (v10 - v00) * sx;
        double b = v01 + (v11 - v01) * sx;
        return a + (b - a) * sz;
    }

    private static double latticeValue(long seed, long salt, int i, int j) {
        return (Long.remainderUnsigned(hash2(seed ^ salt, i, j), 2001) - 1000) / 1000.0;
    }

    private static double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    /**
     * Unlimited skirt relief at a cell: the skeleton (the highest of each source's floor minus the
     * Euclidean distance to it) plus the two noise octaves, faded in over
     * {@link #SKIRT_NOISE_TAPER} cells from the core and the forecourt; floored to a block y.
     */
    private int rawRelief(int x, int z) {
        double skel = Double.NEGATIVE_INFINITY;
        for (int[] r : sources) {
            int dx = Math.max(Math.max(r[0] - x, 0), x - r[2]);
            int dz = Math.max(Math.max(r[1] - z, 0), z - r[3]);
            skel = Math.max(skel, r[4] - Math.sqrt((double) (dx * dx + dz * dz)));
        }
        int edge = coreDistance(x, z);
        if (apron != null) {
            int dx = Math.max(Math.max(apron[0] - x, 0), x - apron[2]);
            int dz = Math.max(Math.max(apron[1] - z, 0), z - apron[3]);
            edge = Math.min(edge, Math.max(dx, dz));
        }
        double w = Math.min(1.0, edge / (double) SKIRT_NOISE_TAPER);
        double relief = NOISE_AMP_OUTER * valueNoise(seed, SALT_COARSE, x, z, SKIRT_LATTICE)
                + NOISE_AMP_FINE * valueNoise(seed, SALT_FINE, x, z, SKIRT_FINE_LATTICE);
        return (int) Math.floor(skel + w * relief);
    }

    private SkirtGrid grid() {
        SkirtGrid g = grid;
        if (g == null) {
            GridKey key = new GridKey(seed, terraces, cliffBackHeight, rise,
                    spire == null ? null : List.of(spire.x0, spire.z0, spire.x1, spire.z1),
                    apron == null ? null : List.of(apron[0], apron[1], apron[2], apron[3]));
            synchronized (GRID_CACHE) {
                g = GRID_CACHE.get(key);
            }
            if (g == null) {
                g = buildGrid();
                synchronized (GRID_CACHE) {
                    GRID_CACHE.put(key, g);
                }
            }
            grid = g;
        }
        return g;
    }

    /**
     * The slope-limited skirt relief over the core's bounding box grown by {@link #SKIRT_RADIUS}.
     * Free cells are the skirt columns; the forecourt is a fixed cell at the gate floor; the core,
     * cliff back and spire take no part. Each free cell starts at {@link #rawRelief}, raised to at
     * least {@code forecourt floor - SKIRT_SLOPE_LIMIT * (Manhattan distance to the forecourt)},
     * then is lowered to the min-plus closure {@code h(p) = min(h(p), h(q) + SKIRT_SLOPE_LIMIT)}
     * over its free and forecourt 4-neighbours (repeated raster sweeps to the fixpoint), so every
     * pair of neighbouring skirt columns, and every skirt column beside the forecourt, differs by at
     * most {@link #SKIRT_SLOPE_LIMIT}. Depends only on the plan and the seed, never on natural
     * ground, so every chunk slice sees the same relief.
     */
    private SkirtGrid buildGrid() {
        int x0 = coreX0 - SKIRT_RADIUS;
        int z0 = coreZ0 - SKIRT_RADIUS;
        int w = coreX1 - coreX0 + 1 + 2 * SKIRT_RADIUS;
        int d = coreZ1 - coreZ0 + 1 + 2 * SKIRT_RADIUS;
        int[] h = new int[w * d];
        byte[] kind = new byte[w * d];   // 0 none, 1 free, 2 fixed (forecourt)
        int apronFloor = terraces.get(0).elevation - 1;
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int x = x0 + i;
                int z = z0 + j;
                int k = j * w + i;
                if (spire != null && spire.contains(x, z) || inCliffBack(x, z)) {
                    continue;
                }
                if (onApron(x, z)) {
                    kind[k] = 2;
                    h[k] = apronFloor;
                    continue;
                }
                if (inCore(x, z)) {
                    continue;
                }
                kind[k] = 1;
                int v = rawRelief(x, z);
                if (apron != null) {
                    int dx = Math.max(Math.max(apron[0] - x, 0), x - apron[2]);
                    int dz = Math.max(Math.max(apron[1] - z, 0), z - apron[3]);
                    v = Math.max(v, apronFloor - SKIRT_SLOPE_LIMIT * (dx + dz));
                }
                h[k] = v;
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    int k = j * w + i;
                    if (kind[k] != 1) continue;
                    int v = h[k];
                    if (i > 0 && kind[k - 1] != 0) v = Math.min(v, h[k - 1] + SKIRT_SLOPE_LIMIT);
                    if (j > 0 && kind[k - w] != 0) v = Math.min(v, h[k - w] + SKIRT_SLOPE_LIMIT);
                    if (v != h[k]) {
                        h[k] = v;
                        changed = true;
                    }
                }
            }
            for (int j = d - 1; j >= 0; j--) {
                for (int i = w - 1; i >= 0; i--) {
                    int k = j * w + i;
                    if (kind[k] != 1) continue;
                    int v = h[k];
                    if (i < w - 1 && kind[k + 1] != 0) v = Math.min(v, h[k + 1] + SKIRT_SLOPE_LIMIT);
                    if (j < d - 1 && kind[k + w] != 0) v = Math.min(v, h[k + w] + SKIRT_SLOPE_LIMIT);
                    if (v != h[k]) {
                        h[k] = v;
                        changed = true;
                    }
                }
            }
        }
        return new SkirtGrid(x0, z0, w, d, h);
    }

    /** The slope-limited relief of the skirt, indexed by local (x, z). */
    private record SkirtGrid(int x0, int z0, int w, int d, int[] h) {
        int at(int x, int z) {
            return h[(z - z0) * w + (x - x0)];
        }
    }

    private record GridKey(long seed, List<TerraceBox> terraces, int cliffBackHeight, int rise,
                           List<Integer> spire, List<Integer> apron) {
    }

    /** The relief depends only on the plan, so chunk slices of one compound share it. */
    private static final java.util.Map<GridKey, SkirtGrid> GRID_CACHE =
            new java.util.LinkedHashMap<>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<GridKey, SkirtGrid> eldest) {
                    return size() > 8;
                }
            };

    public static final java.util.Map<String, Integer> PARITY = java.util.Map.ofEntries(
            java.util.Map.entry("SKIRT_RADIUS", SKIRT_RADIUS),
            java.util.Map.entry("OUTER_SLOPE", OUTER_SLOPE),
            java.util.Map.entry("NOISE_AMP_INTER", NOISE_AMP_INTER),
            java.util.Map.entry("NOISE_AMP_OUTER", NOISE_AMP_OUTER),
            java.util.Map.entry("SEAM_SLOPE_LIMIT", SEAM_SLOPE_LIMIT),
            java.util.Map.entry("SPIRE_GAP", SPIRE_GAP),
            java.util.Map.entry("SKIRT_LATTICE", SKIRT_LATTICE),
            java.util.Map.entry("NOISE_AMP_FINE", NOISE_AMP_FINE),
            java.util.Map.entry("SKIRT_FINE_LATTICE", SKIRT_FINE_LATTICE),
            java.util.Map.entry("SKIRT_SLOPE_LIMIT", SKIRT_SLOPE_LIMIT),
            java.util.Map.entry("SKIRT_NOISE_TAPER", SKIRT_NOISE_TAPER));
}
