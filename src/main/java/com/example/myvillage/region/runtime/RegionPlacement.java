package com.example.myvillage.region.runtime;

/**
 * Placement transform: maps the per-seed region graph (unit-circle topology,
 * anchor at the origin) into world-block coordinates. Pure coordinate math —
 * it does <b>not</b> write world blocks, override biomes, or hook chunk
 * generation.
 *
 * <p>The anchor (中州) center is placed at world block {@code (0, 0)} and the
 * scale is chosen so the outermost region center (the walled ring at graph
 * radius {@value #RADIUS_GRAPH_OUTER}) fits within an anchor-centered circle
 * of {@value #RADIUS_WORLD} blocks. The transform applies <b>no rotation</b>
 * so the graph's deterministic {@code base_angle=0.0} (first peripheral at
 * {@code +x} = east) stays a stable reference direction.
 *
 * <pre>
 *   world_pos = SCALE * graph_pos
 *   SCALE     = RADIUS_WORLD / RADIUS_GRAPH_OUTER
 *             = 4000 / 1.45  ≈ 2759 blocks per graph unit
 * </pre>
 *
 * <p>{@link #RADIUS_GRAPH_OUTER} is the <em>effective</em> outermost center
 * radius: the walled ring ({@value #WALLED_RING_RADIUS}) plus the maximum embed
 * jitter ({@value #EMBED_JITTER_MAX}), so EVERY region center — including the
 * walled 魔域, whose deterministic radius jitter can push it to 1.45 — fits
 * within {@value #RADIUS_WORLD} blocks. Using the nominal walled ring (1.4)
 * alone would let walled centers overshoot the 4000-block bound by ~3.5%; the
 * {@code region-runtime-binding} spec mandates the bound for every region
 * center, so the jitter-inclusive radius is the correct divisor.
 *
 * <p>The constants mirror the Python fixture exporter
 * ({@code tools/buildgen/tests/generate_region_runtime_fixtures.py}) and the
 * runtime-binding note in {@code docs/ai-kb/13_region_topology.md}; there is
 * no second source of truth.
 */
public final class RegionPlacement {
    /** Anchor-centered world radius that bounds every region center (blocks). */
    public static final double RADIUS_WORLD = 4000.0;

    /** Nominal walled-ring radius (graph units) — matches the shipped ruleset. */
    public static final double WALLED_RING_RADIUS = 1.4;

    /** Maximum embed radius jitter ({@code 50/1000}) from the generator's embed step. */
    public static final double EMBED_JITTER_MAX = 0.05;

    /**
     * Effective outermost center radius: walled ring + max embed jitter
     * ({@code 1.4 + 0.05 = 1.45}), in graph units. The divisor for {@link #SCALE}.
     */
    public static final double RADIUS_GRAPH_OUTER = WALLED_RING_RADIUS + EMBED_JITTER_MAX;

    /** Blocks per graph unit ({@code RADIUS_WORLD / RADIUS_GRAPH_OUTER}). */
    public static final double SCALE = RADIUS_WORLD / RADIUS_GRAPH_OUTER;

    private RegionPlacement() {
    }

    /**
     * Map a graph-units position to a world-block column. Returns the
     * block coordinate {@code [x, z]} (rounded, no rotation): anchor
     * {@code (0, 0)} maps to {@code [0, 0]}.
     */
    public static int[] worldBlockFromGraph(double graphX, double graphZ) {
        return new int[] {
                (int) Math.round(SCALE * graphX),
                (int) Math.round(SCALE * graphZ),
        };
    }

    /** Graph units for a world block (inverse of {@link #worldBlockFromGraph}). */
    public static double[] graphFromWorldBlock(int worldX, int worldZ) {
        return new double[] { worldX / SCALE, worldZ / SCALE };
    }

    /**
     * Anchor-centered world radius of a point, in blocks. Used by
     * {@code region_at} to reject points outside the bounded region area.
     */
    public static double worldRadiusFromCenter(int worldX, int worldZ) {
        return Math.hypot(worldX, worldZ);
    }

    /**
     * True iff the world block is within the anchor-centered bounded area
     * (radius {@link #RADIUS_WORLD}). Points outside resolve to no region.
     */
    public static boolean isWithinBoundedArea(int worldX, int worldZ) {
        return worldRadiusFromCenter(worldX, worldZ) <= RADIUS_WORLD;
    }
}
