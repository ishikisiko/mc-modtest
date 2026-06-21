package com.example.myvillage.region.runtime;

import java.util.List;

/**
 * A region in the generated per-seed graph. Java mirror of {@code GenRegion}
 * in {@code tools/buildgen/region_topology.py}. {@code tier} is the
 * <em>assigned</em> tier (authoritative for invariants); {@code nominalTier}
 * is the catalog identity kept for traceability.
 *
 * @param id                stable region id
 * @param displayName       human glyph name
 * @param tier              assigned tier (authoritative)
 * @param role              anchor | peripheral | walled
 * @param qi                qi range
 * @param danger            danger range
 * @param admittedSubjects  subjects this region admits
 * @param nominalTier       nominal (catalog) tier
 * @param posX              graph-units x (rounded to 4 decimals at embed time)
 * @param posZ              graph-units z (rounded to 4 decimals at embed time)
 */
public record GenRegion(
        String id,
        String displayName,
        int tier,
        String role,
        IntRange qi,
        IntRange danger,
        List<String> admittedSubjects,
        int nominalTier,
        double posX,
        double posZ) {
}
