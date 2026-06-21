package com.example.myvillage.region.runtime;

import java.util.Comparator;
import java.util.Optional;

/**
 * Selects the world spawn region deterministically from a region graph — Java
 * mirror of {@code _spawn_binding} in
 * {@code tools/buildgen/tests/generate_region_runtime_fixtures.py}.
 *
 * <p>The spawn region is the <em>eligible</em> region with the lowest assigned
 * tier, where eligible means {@code role != "walled"} AND
 * {@code admitted_subjects} is non-empty. Ties at the lowest tier are broken
 * deterministically by the sort key
 * {@code (assigned_tier ASC, distance_from_anchor DESC, qi_midpoint ASC, id ASC)}
 * — the encoding of "weakest 修为起点": lowest tier (least qi), farthest from
 * the anchor (most peripheral), thinnest qi range, stable id tiebreak.
 * Alignment is intentionally absent from the key (spawn happens before the
 * player has chosen a path).
 *
 * <p>{@link SpawnSelection#worldX/worldZ} is the spawn region's center
 * translated by {@link RegionPlacement}; the runtime's safe-surface search
 * resolves the standing {@code y} later (world-dependent).
 */
public final class RegionSpawnSelector {

    private RegionSpawnSelector() {
    }

    /** Immutable result of the spawn-region selection. */
    public record SpawnSelection(String regionId, int worldX, int worldZ, double graphX, double graphZ) {
    }

    /**
     * Select the spawn region and its world-block center for {@code graph}.
     *
     * @return empty if no region is eligible (no non-walled region admits a
     *         subject); otherwise the lowest-tier eligible region's selection
     */
    public static Optional<SpawnSelection> select(RegionGraph graph) {
        GenRegion anchor = null;
        for (GenRegion r : graph.regions()) {
            if ("anchor".equals(r.role())) {
                anchor = r;
                break;
            }
        }
        final GenRegion anchorRef = anchor;
        Comparator<GenRegion> key = Comparator
                .comparingInt(GenRegion::tier)
                .thenComparing(Comparator
                        .comparingDouble((GenRegion r) -> distanceFromAnchor(r, anchorRef))
                        .reversed())
                .thenComparingInt(r -> r.qi().lo() + r.qi().hi())
                .thenComparing(GenRegion::id);

        GenRegion chosen = null;
        for (GenRegion r : graph.regions()) {
            if ("walled".equals(r.role())) {
                continue;
            }
            if (r.admittedSubjects().isEmpty()) {
                continue;
            }
            if (chosen == null || key.compare(r, chosen) < 0) {
                chosen = r;
            }
        }
        if (chosen == null) {
            return Optional.empty();
        }
        int[] block = RegionPlacement.worldBlockFromGraph(chosen.posX(), chosen.posZ());
        return Optional.of(new SpawnSelection(
                chosen.id(), block[0], block[1], chosen.posX(), chosen.posZ()));
    }

    private static double distanceFromAnchor(GenRegion r, GenRegion anchor) {
        if (anchor == null) {
            return Math.hypot(r.posX(), r.posZ());
        }
        return Math.hypot(r.posX() - anchor.posX(), r.posZ() - anchor.posZ());
    }
}
