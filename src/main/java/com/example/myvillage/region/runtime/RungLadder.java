package com.example.myvillage.region.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Tier queries for the current fixed progression and offline topology fixtures. */
public final class RungLadder {

    private static boolean onLadder(RegionGraph graph, GenRegion r) {
        return graph.progressionOrder().isEmpty() ? !"walled".equals(r.role()) : graph.progressionOrder().contains(r.id());
    }

    private RungLadder() {
    }

    public static List<Integer> ladder(RegionGraph graph) {
        TreeSet<Integer> distinct = new TreeSet<>();
        for (GenRegion r : graph.regions()) {
            if (onLadder(graph, r)) {
                distinct.add(r.tier());
            }
        }
        return List.copyOf(distinct);
    }

    public static Set<String> regionsAtRung(RegionGraph graph, int tier) {
        Set<String> out = new HashSet<>();
        for (GenRegion r : graph.regions()) {
            if (onLadder(graph, r) && r.tier() == tier) {
                out.add(r.id());
            }
        }
        return out;
    }

    public static Optional<String> currentRegion(RegionGraph graph, int worldX, int worldZ) {
        return RegionQueries.regionAt(graph, worldX, worldZ);
    }

    public static Optional<Integer> currentRung(RegionGraph graph, int worldX, int worldZ) {
        Optional<String> region = currentRegion(graph, worldX, worldZ);
        if (region.isEmpty()) {
            return Optional.empty();
        }
        GenRegion r = graph.regionById(region.get());
        if (!onLadder(graph, r)) {
            return Optional.empty();
        }
        return Optional.of(r.tier());
    }

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

    static int nextHigherRung(RegionGraph graph, int tier) {
        List<Integer> rungs = ladder(graph);
        for (int t : rungs) {
            if (t > tier) {
                return t;
            }
        }
        return tier;
    }

    static boolean isTopRung(RegionGraph graph, int tier) {
        List<Integer> rungs = ladder(graph);
        return !rungs.isEmpty() && tier == rungs.get(rungs.size() - 1);
    }

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
