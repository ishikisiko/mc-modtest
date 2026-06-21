package com.example.myvillage.region.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The tier-rung ladder and per-position rung queries — Java mirror of the
 * rung-ladder contract in {@code docs/ai-kb/13_region_topology.md}
 * ("Runtime binding") and the {@code region-runtime-binding} spec.
 *
 * <p>The rung ladder for a seed is the sorted ascending list of <b>distinct
 * assigned tiers among non-walled regions</b>; the anchor (中州) is the top
 * rung by construction. The ladder is a tier ladder, not a tree-parent ladder:
 * the spanning tree is star-shaped in practice (the anchor hubs every
 * peripheral), so tree-parent would jump straight to 中州 and skip the tier
 * gradient. The distinct-tier ladder is the cultivation-meaningful progression
 * axis.
 *
 * <p>These methods are pure functions of the graph + world coordinates, so they
 * are unit-testable without a running server and are delegated to by
 * {@link RegionRuntimeService} from its cached graph.
 */
public final class RungLadder {

    private RungLadder() {
    }

    /**
     * The rung ladder: sorted ascending distinct assigned tiers among
     * non-walled regions. The anchor's tier is the top (last) entry by
     * construction. Walled regions (魔域) are excluded — they never join the
     * 连 spanning tree and are not part of the tier progression.
     */
    public static List<Integer> ladder(RegionGraph graph) {
        TreeSet<Integer> distinct = new TreeSet<>();
        for (GenRegion r : graph.regions()) {
            if (!"walled".equals(r.role())) {
                distinct.add(r.tier());
            }
        }
        return List.copyOf(distinct);
    }

    /**
     * The set of non-walled regions sitting at a given rung (tier). Used by
     * {@link #nextRungRegions} and the {@code /myvillage spawn info} query.
     */
    public static Set<String> regionsAtRung(RegionGraph graph, int tier) {
        Set<String> out = new HashSet<>();
        for (GenRegion r : graph.regions()) {
            if (!"walled".equals(r.role()) && r.tier() == tier) {
                out.add(r.id());
            }
        }
        return out;
    }

    /**
     * The region at a world block (alias of {@link RegionQueries#regionAt}).
     * Exposed here so rung callers have one entry point for
     * "where is the player".
     */
    public static Optional<String> currentRegion(RegionGraph graph, int worldX, int worldZ) {
        return RegionQueries.regionAt(graph, worldX, worldZ);
    }

    /**
     * The rung (tier value) the player is currently on. Empty if the player is
     * outside the bounded area, in no region, or inside a walled region (魔域,
     * which is off the ladder by construction).
     */
    public static Optional<Integer> currentRung(RegionGraph graph, int worldX, int worldZ) {
        Optional<String> region = currentRegion(graph, worldX, worldZ);
        if (region.isEmpty()) {
            return Optional.empty();
        }
        GenRegion r = graph.regionById(region.get());
        if ("walled".equals(r.role())) {
            return Optional.empty();
        }
        return Optional.of(r.tier());
    }

    /**
     * The <b>set</b> of non-walled regions at the next-higher rung above the
     * player's current rung. Returns the empty set when the player is already
     * at the top rung (anchor 中州), is outside the bounded area, or is in a
     * walled region.
     *
     * <p>The result is a <b>set, not a singleton</b> because tier ties are
     * genuine branch points (e.g. 灵岳 + 西漠 both at tier 18): a future
     * 正道/魔道 alignment system resolves which member of the set is "the"
     * next destination for a given player; until that system exists,
     * downstream UI shows all members.
     */
    public static Set<String> nextRungRegions(RegionGraph graph, int worldX, int worldZ) {
        Optional<Integer> current = currentRung(graph, worldX, worldZ);
        if (current.isEmpty()) {
            return Set.of();
        }
        int nextTier = nextHigherRung(graph, current.get());
        if (nextTier == current.get()) {
            // Already at the top rung — no next rung exists.
            return Set.of();
        }
        return regionsAtRung(graph, nextTier);
    }

    /**
     * The next-higher rung (tier) strictly above {@code tier}, or {@code tier}
     * itself if {@code tier} is the top rung (no higher rung exists).
     */
    static int nextHigherRung(RegionGraph graph, int tier) {
        List<Integer> rungs = ladder(graph);
        for (int t : rungs) {
            if (t > tier) {
                return t;
            }
        }
        return tier;
    }

    /** True iff {@code tier} is the top rung (the anchor's tier). */
    static boolean isTopRung(RegionGraph graph, int tier) {
        List<Integer> rungs = ladder(graph);
        return !rungs.isEmpty() && tier == rungs.get(rungs.size() - 1);
    }

    /** All non-walled regions, for tests/inspection. */
    static List<GenRegion> nonWalledRegions(RegionGraph graph) {
        List<GenRegion> out = new ArrayList<>();
        for (GenRegion r : graph.regions()) {
            if (!"walled".equals(r.role())) {
                out.add(r);
            }
        }
        return out;
    }
}
