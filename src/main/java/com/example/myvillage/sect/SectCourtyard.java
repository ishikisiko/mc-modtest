package com.example.myvillage.sect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * Open courtyard ground of a terraced sect compound: the positions where a figure can stand in the
 * open air on a terrace floor, never on a roof or inside a building. Derived from the same plan
 * {@link SectGenerator} builds from ({@code plan(seed, base, variant)} with the base the build
 * commands use), so it holds for any compound built by {@code generateForcedAt(source, seed,
 * variant, anchor)}.
 *
 * <p>A terrace's cells are its floor rectangle minus its edge row (retaining faces, the cliff
 * back), minus every building slot on it (the larger of the slot bounds and the template's own
 * footprint, plus {@value #BUILDING_MARGIN} block), minus every grand stair with its cheek walls
 * (plus {@value #BUILDING_MARGIN} block), minus the axis corridor (kept clear for walking), minus
 * a built detached spire's volume and its flying bridge (plus {@value #BUILDING_MARGIN} block). The
 * position is the block above the floor, {@code terrace elevation}, i.e. terrace floor + 1.
 */
public final class SectCourtyard {
    /** The {@code variant} that builds no detached spire. */
    public static final String NO_SPIRE = "none";
    /** Free blocks kept around buildings, the spire and its bridge. */
    static final int BUILDING_MARGIN = 1;

    private SectCourtyard() {
    }

    /** Every variant a build may force: {@link #NO_SPIRE} first, then the spire variants. */
    public static List<String> variants() {
        List<String> out = new ArrayList<>();
        out.add(NO_SPIRE);
        out.addAll(List.of(SectGenerator.featureVariantNames()));
        return List.copyOf(out);
    }

    /** The compound's site rectangle (world x/z, inclusive) for a build anchored at {@code anchor}. */
    public static Footprint footprint(BlockPos anchor) {
        BlockPos base = base(anchor);
        return new Footprint(base.getX(), base.getZ(),
                base.getX() + SectGenerator.SITE_WIDTH - 1, base.getZ() + SectGenerator.SITE_DEPTH - 1);
    }

    /**
     * Standable open-air positions of the compound built at {@code anchor} with {@code seed} and
     * {@code variant}, lowest terrace first, then by z, then by x. Deterministic.
     */
    public static List<BlockPos> cells(long seed, BlockPos anchor, String variant) {
        BlockPos base = base(anchor);
        SectGenerator.SectPlan plan = SectGenerator.plan(seed, base, variant);
        Set<Long> common = new HashSet<>();
        SectGenerator.FlyingBridgeFeature feature = plan.feature();
        if (feature != null && SectGenerator.featureBuildable(plan)) {
            SectGenerator.Rect detached = feature.detachedBounds();
            int[] fp = SectGenerator.templateFootprint(feature.detachedTemplate());
            block(common, union(detached, detached.x0(), detached.z0(), fp), BUILDING_MARGIN);
            SectGenerator.GalleryLink bridge = feature.bridge();
            for (SectGenerator.Cell cell : SectGenerator.bresenham(bridge.fromCell(), bridge.toCell())) {
                block(common, new SectGenerator.Rect(cell.x(), cell.z(), cell.x(), cell.z()),
                        BUILDING_MARGIN + 1); // the deck spans x ± 1
            }
        }
        for (SectGenerator.AxisStair stair : plan.stairs()) {
            block(common, stair.withCheeks(), BUILDING_MARGIN);
        }
        block(common, new SectGenerator.Rect(SectGenerator.AXIS_X0, SectGenerator.APRON_Z0,
                SectGenerator.AXIS_X1, plan.axisEndZ()), 0);
        List<BlockPos> out = new ArrayList<>();
        for (SectGenerator.Terrace terrace : plan.terraces()) {
            Set<Long> blocked = new HashSet<>(common);
            for (SectGenerator.Slot slot : plan.slots()) {
                if (slot.terraceIndex() != terrace.index()) {
                    continue;
                }
                SectGenerator.Rect bounds = slot.bounds();
                int[] fp = SectGenerator.templateFootprint(slot.templateId());
                block(blocked, union(bounds, bounds.x0(), bounds.z0(), fp), BUILDING_MARGIN);
            }
            SectGenerator.Rect r = terrace.bounds();
            for (int z = r.z0() + 1; z <= r.z1() - 1; z++) {
                for (int x = r.x0() + 1; x <= r.x2() - 1; x++) {
                    if (!blocked.contains(key(x, z))) {
                        out.add(new BlockPos(base.getX() + x, terrace.elevation(), base.getZ() + z));
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    /** World x/z rectangle, inclusive. */
    public record Footprint(int minX, int minZ, int maxX, int maxZ) {
        /** Horizontal distance from (x, z) to the rectangle; 0 inside it. */
        public double distanceTo(double x, double z) {
            double dx = Math.max(0.0, Math.max(minX - x, x - (maxX + 1)));
            double dz = Math.max(0.0, Math.max(minZ - z, z - (maxZ + 1)));
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    /** The compound base for an anchor, exactly as {@code SectGenerator.buildAt} computes it. */
    static BlockPos base(BlockPos anchor) {
        return anchor.offset(-SectGenerator.SITE_WIDTH / 2, 0, -SectGenerator.SITE_DEPTH / 2);
    }

    private static SectGenerator.Rect union(SectGenerator.Rect bounds, int originX, int originZ, int[] footprint) {
        return new SectGenerator.Rect(Math.min(bounds.x0(), originX), Math.min(bounds.z0(), originZ),
                Math.max(bounds.x2(), originX + footprint[0] - 1), Math.max(bounds.z1(), originZ + footprint[1] - 1));
    }

    private static void block(Set<Long> blocked, SectGenerator.Rect r, int margin) {
        for (int x = r.x0() - margin; x <= r.x2() + margin; x++) {
            for (int z = r.z0() - margin; z <= r.z1() + margin; z++) {
                blocked.add(key(x, z));
            }
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
