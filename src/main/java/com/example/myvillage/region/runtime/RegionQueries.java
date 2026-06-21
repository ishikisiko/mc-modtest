package com.example.myvillage.region.runtime;

import java.util.Comparator;
import java.util.Optional;

/**
 * Pure, server-state-free region queries over a cached {@link RegionGraph}.
 *
 * <p>These methods are pure functions of the graph + world coordinates, so they
 * are unit-testable without a running server and are delegated to by
 * {@link RegionRuntimeService} from its cached graph.
 */
public final class RegionQueries {

    private RegionQueries() {
    }

    /**
     * Resolve the region whose placed center is nearest (world-block Euclidean
     * distance) to {@code (worldX, worldZ)}. Returns empty for points outside
     * the anchor-centered bounded area ({@link RegionPlacement#RADIUS_WORLD}).
     *
     * <p><b>Known approximation (v0).</b> Until region extents are introduced
     * by a future change, {@code region_at} uses a nearest-center (Voronoi)
     * rule: every world block resolves to its closest region center, and
     * points on the boundary between two regions are assigned to one
     * deterministically. The rung API only needs the answer at playable
     * positions (inside regions), not in separator bands.
     *
     * @param graph  the cached per-seed region graph
     * @param worldX world block x
     * @param worldZ world block z
     * @return the nearest region's id, or empty if the point is outside the
     *         bounded area or the graph has no regions
     */
    public static Optional<String> regionAt(RegionGraph graph, int worldX, int worldZ) {
        if (graph.regions().isEmpty()) {
            return Optional.empty();
        }
        if (!RegionPlacement.isWithinBoundedArea(worldX, worldZ)) {
            return Optional.empty();
        }
        GenRegion nearest = null;
        double nearestDistSq = Double.POSITIVE_INFINITY;
        for (GenRegion r : graph.regions()) {
            int[] center = RegionPlacement.worldBlockFromGraph(r.posX(), r.posZ());
            double dx = center[0] - worldX;
            double dz = center[1] - worldZ;
            double distSq = dx * dx + dz * dz;
            // Tie-break by region id so the boundary assignment is deterministic.
            if (distSq < nearestDistSq
                    || (distSq == nearestDistSq
                            && (nearest == null || r.id().compareTo(nearest.id()) < 0))) {
                nearestDistSq = distSq;
                nearest = r;
            }
        }
        return nearest == null ? Optional.empty() : Optional.of(nearest.id());
    }

    /** Distance from a world block to a region's placed center (world blocks). */
    public static double distanceToCenter(GenRegion region, int worldX, int worldZ) {
        int[] center = RegionPlacement.worldBlockFromGraph(region.posX(), region.posZ());
        return Math.hypot(center[0] - worldX, center[1] - worldZ);
    }

    /** Comparator placing smaller distances first; stable for equal distances. */
    static Comparator<GenRegion> nearestFirst(int worldX, int worldZ) {
        return Comparator.<GenRegion>comparingDouble(
                r -> distanceToCenter(r, worldX, worldZ)).thenComparing(GenRegion::id);
    }
}
