package com.example.myvillage.region.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Constructive region-graph generator — Java mirror of {@code generate()} and
 * its helpers in {@code tools/buildgen/region_topology.py}.
 *
 * <p><b>Parity contract.</b> Every RNG draw, sort key, and control-flow branch
 * is replicated bit-for-bit from the Python module so {@link #generate}
 * produces a {@link RegionGraph} whose {@link RegionGraph#toCanonicalJson()}
 * is byte-identical to the offline generator's
 * {@code json.dumps(graph.to_dict(), ensure_ascii=False, indent=2, sort_keys=True)}
 * for the same seed. The Python module remains the single source of truth; this
 * class is its Java reader.
 *
 * <p>Generation is constructive (no re-roll): connectivity and the tier-step
 * invariant are built in by the 连 spanning tree and the tier-assignment walk,
 * so a satisfiable ruleset never dead-ends and an unsatisfiable one is reported
 * explicitly via {@link UnsatisfiableRulesetException}.
 *
 * <p>Design reference: {@code docs/ai-kb/13_region_topology.md} and
 * {@code openspec/changes/add-region-topology/design.md}.
 */
public final class RegionTopologyGenerator {

    private RegionTopologyGenerator() {
    }

    /** An endpoint pair in stable order ({@code a <= b} lexicographically). */
    private record EdgeKey(String a, String b) implements Comparable<EdgeKey> {
        EdgeKey {
            if (a.compareTo(b) > 0) {
                String t = a;
                a = b;
                b = t;
            }
        }

        static EdgeKey of(String x, String y) {
            return x.compareTo(y) <= 0 ? new EdgeKey(x, y) : new EdgeKey(y, x);
        }

        @Override
        public int compareTo(EdgeKey o) {
            int c = a.compareTo(o.a);
            return c != 0 ? c : b.compareTo(o.b);
        }

        String other(String id) {
            return a.equals(id) ? b : a;
        }
    }

    /** Graph-units point (rounded to 4 decimals at embed time). */
    private record Point(double x, double z) {
    }

    /**
     * Construct a region graph for {@code seed}. Deterministic; never re-rolls.
     */
    public static RegionGraph generate(long seed, Ruleset ruleset, List<RegionProfile> catalog) {
        validateInputs(ruleset, catalog);
        RegionRng rng = new RegionRng(seed);
        int tlo = ruleset.tierRange().lo();
        int thi = ruleset.tierRange().hi();
        int nStep = ruleset.tierStep();

        List<RegionProfile> selected = selectRegions(rng, ruleset, catalog);
        Map<String, Point> positions = embed(rng, ruleset, selected);
        RegionProfile anchor = firstWithRole(selected, "anchor");
        Set<EdgeKey> geoEdges = geometricEdges(ruleset, selected, positions, anchor.id());

        Set<String> walledIds = idsWithRole(selected, "walled");
        Set<String> nonWalledIds = new HashSet<>();
        for (RegionProfile r : selected) {
            if (!"walled".equals(r.role())) {
                nonWalledIds.add(r.id());
            }
        }

        SpanningResult span = spanningTree(nonWalledIds, geoEdges, anchor.id());

        // --- Tier assignment (constructive, outward from the anchor). ---
        Map<String, Integer> tiers = new HashMap<>();
        tiers.put(anchor.id(), thi);
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(anchor.id());
        seen.add(anchor.id());
        while (!queue.isEmpty()) {
            String u = queue.pollFirst();
            List<String> children = new ArrayList<>();
            for (Map.Entry<String, String> e : span.parent.entrySet()) {
                if (e.getValue().equals(u)) {
                    children.add(e.getKey());
                }
            }
            children.sort(Comparator.naturalOrder());
            for (String c : children) {
                if (seen.contains(c)) {
                    continue;
                }
                seen.add(c);
                int loD = (u.equals(anchor.id()) && nStep >= 1) ? 1 : 0;
                int d = rng.range(loD, nStep, "tier:" + c);
                tiers.put(c, Math.max(tlo, tiers.get(u) - d));
                queue.add(c);
            }
        }

        // --- Type edges. ---
        Map<EdgeKey, GenEdge> typed = new LinkedHashMap<>();

        for (EdgeKey e : span.treeEdges) {
            typed.put(e, new GenEdge(e.a, e.b, RegionContract.EDGE_LIAN, null, null));
        }

        for (EdgeKey e : new TreeSet<>(geoEdges)) {
            if (typed.containsKey(e)) {
                continue;
            }
            if (walledIds.contains(e.a) || walledIds.contains(e.b)) {
                continue;
            }
            int dtier = Math.abs(tiers.get(e.a) - tiers.get(e.b));
            if (dtier > nStep) {
                String sep = rng.pick(ruleset.separatorPalette(), "sep:" + e.a + ":" + e.b);
                typed.put(e, new GenEdge(e.a, e.b, RegionContract.EDGE_GE, sep, null));
            } else if (rng.chance(ruleset.nonTreeEdgeLianChance(), "lian:" + e.a + ":" + e.b)) {
                typed.put(e, new GenEdge(e.a, e.b, RegionContract.EDGE_LIAN, null, null));
            } else {
                String sep = rng.pick(ruleset.separatorPalette(), "sep:" + e.a + ":" + e.b);
                typed.put(e, new GenEdge(e.a, e.b, RegionContract.EDGE_GE, sep, null));
            }
        }

        // --- Walled-region rule: sealed except at most one 关隘. ---
        List<RegionProfile> walledSorted = new ArrayList<>();
        for (RegionProfile r : selected) {
            if ("walled".equals(r.role())) {
                walledSorted.add(r);
            }
        }
        walledSorted.sort(Comparator.comparing(RegionProfile::id));
        for (RegionProfile w : walledSorted) {
            List<EdgeKey> wEdges = new ArrayList<>();
            for (EdgeKey e : geoEdges) {
                if (e.a.equals(w.id()) || e.b.equals(w.id())) {
                    wEdges.add(e);
                }
            }
            final String wid = w.id();
            wEdges.sort(Comparator.comparing(
                    (EdgeKey e) -> dist(positions.get(wid), positions.get(e.other(wid))),
                    Comparator.naturalOrder())
                    .thenComparing(e -> e.other(wid)));

            EdgeKey gate = null;
            if (!wEdges.isEmpty() && rng.chance(ruleset.walledGateChance(), "gate:" + w.id())) {
                gate = wEdges.get(0);
                String other = gate.other(w.id());
                int d = rng.range(0, nStep, "wtier:" + w.id());
                tiers.put(w.id(), Math.max(tlo, tiers.get(other) - d));
                typed.put(gate, new GenEdge(gate.a, gate.b, RegionContract.EDGE_LIAN, null,
                        ruleset.walledPassLabel()));
            } else {
                if (!wEdges.isEmpty()) {
                    String other = wEdges.get(0).other(w.id());
                    int d = rng.range(0, nStep, "wtier:" + w.id());
                    tiers.put(w.id(), Math.max(tlo, tiers.get(other) - d));
                } else {
                    tiers.put(w.id(), thi);
                }
            }
            for (EdgeKey e : wEdges) {
                if (e.equals(gate)) {
                    continue;
                }
                String sep = rng.pick(ruleset.separatorPalette(),
                        "wsep:" + w.id() + ":" + e.other(w.id()));
                typed.put(e, new GenEdge(e.a, e.b, RegionContract.EDGE_GE, sep, null));
            }
        }

        // --- Assemble. ---
        List<GenRegion> regions = new ArrayList<>(selected.size());
        for (RegionProfile r : selected) {
            Point p = positions.get(r.id());
            regions.add(new GenRegion(
                    r.id(),
                    r.displayName(),
                    tiers.get(r.id()),
                    r.role(),
                    r.qi(),
                    r.danger(),
                    r.admittedSubjects(),
                    r.tier(),
                    p.x,
                    p.z));
        }
        regions.sort(Comparator.comparing(GenRegion::id));

        List<EdgeKey> sortedTypedKeys = new ArrayList<>(typed.keySet());
        sortedTypedKeys.sort(Comparator.naturalOrder());
        List<GenEdge> edges = new ArrayList<>(sortedTypedKeys.size());
        for (EdgeKey k : sortedTypedKeys) {
            edges.add(typed.get(k));
        }

        return new RegionGraph(
                seed,
                ruleset.id(),
                regions.size(),
                ruleset.tierRange(),
                ruleset.tierStep(),
                List.copyOf(regions),
                List.copyOf(edges));
    }

    // ------------------------------------------------------------------ #
    // Input validation.
    // ------------------------------------------------------------------ #

    private static void validateInputs(Ruleset ruleset, List<RegionProfile> catalog) {
        IntRange rc = ruleset.regionCount();
        if (!(rc.lo() >= 1 && rc.lo() <= rc.hi())) {
            throw new UnsatisfiableRulesetException(
                    "region_count range invalid: [" + rc.lo() + ", " + rc.hi() + "]");
        }
        IntRange tr = ruleset.tierRange();
        if (tr.lo() > tr.hi()) {
            throw new UnsatisfiableRulesetException(
                    "tier_range invalid: [" + tr.lo() + ", " + tr.hi() + "]");
        }
        if (ruleset.tierStep() < 0) {
            throw new UnsatisfiableRulesetException(
                    "tier_step must be >= 0, got " + ruleset.tierStep());
        }

        List<RegionProfile> anchors = roles(catalog, "anchor");
        if (anchors.size() != 1) {
            throw new UnsatisfiableRulesetException(
                    "catalog must declare exactly one anchor region, found " + anchors.size());
        }
        if (catalog.size() < rc.hi()) {
            throw new UnsatisfiableRulesetException(
                    "catalog has " + catalog.size() + " regions but region_count max is " + rc.hi());
        }
        if (catalog.size() < rc.lo()) {
            throw new UnsatisfiableRulesetException(
                    "catalog has " + catalog.size() + " regions but region_count min is " + rc.lo());
        }

        for (RegionProfile r : catalog) {
            if (!(r.tier() >= tr.lo() && r.tier() <= tr.hi())) {
                throw new UnsatisfiableRulesetException(
                        "region " + r.id() + " nominal tier " + r.tier()
                                + " outside tier_range [" + tr.lo() + ", " + tr.hi() + "]");
            }
            for (String s : r.admittedSubjects()) {
                if (!RegionContract.KNOWN_SUBJECTS.contains(s)) {
                    throw new UnsatisfiableRulesetException(
                            "region " + r.id() + " admits unknown subject " + s
                                    + "; known = " + RegionContract.KNOWN_SUBJECTS);
                }
            }
        }

        int nWalled = roles(catalog, "walled").size();
        int nPeriph = roles(catalog, "peripheral").size();
        int needPeriph = rc.lo() - 1 - nWalled;
        if (needPeriph < 0) {
            needPeriph = 0;
        }
        if (nPeriph < needPeriph) {
            throw new UnsatisfiableRulesetException(
                    "need >= " + needPeriph + " peripheral regions to reach region_count min "
                            + rc.lo() + ", catalog has " + nPeriph);
        }
    }

    // ------------------------------------------------------------------ #
    // Region selection.
    // ------------------------------------------------------------------ #

    private static List<RegionProfile> selectRegions(
            RegionRng rng, Ruleset ruleset, List<RegionProfile> catalog) {
        RegionProfile anchor = firstWithRole(catalog, "anchor");
        List<RegionProfile> walled = sortedById(roles(catalog, "walled"));
        List<RegionProfile> peripherals = sortedById(roles(catalog, "peripheral"));

        int lo = ruleset.regionCount().lo();
        int hi = ruleset.regionCount().hi();
        int count = rng.range(lo, hi, "region_count");
        int needPeriph = count - 1 - walled.size();
        if (needPeriph < 0) {
            needPeriph = 0;
        }
        if (needPeriph > peripherals.size()) {
            throw new UnsatisfiableRulesetException(
                    "need " + needPeriph + " peripherals but catalog has " + peripherals.size());
        }

        List<RegionProfile> pool = new ArrayList<>(peripherals);
        List<RegionProfile> chosen = new ArrayList<>(needPeriph);
        for (int i = 0; i < needPeriph; i++) {
            int j = rng.range(i, pool.size() - 1, "pick_periph:" + i);
            RegionProfile tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
            chosen.add(pool.get(i));
        }
        chosen.sort(Comparator.comparing(RegionProfile::id));

        List<RegionProfile> result = new ArrayList<>(1 + chosen.size() + walled.size());
        result.add(anchor);
        result.addAll(chosen);
        result.addAll(walled);
        return result;
    }

    // ------------------------------------------------------------------ #
    // Embedding.
    // ------------------------------------------------------------------ #

    private static Map<String, Point> embed(
            RegionRng rng, Ruleset ruleset, List<RegionProfile> selected) {
        RegionProfile anchor = firstWithRole(selected, "anchor");
        Map<String, Point> positions = new HashMap<>();
        positions.put(anchor.id(), new Point(0.0, 0.0));

        List<RegionProfile> peripherals = sortedById(roles(selected, "peripheral"));
        List<RegionProfile> walled = sortedById(roles(selected, "walled"));

        placeRing(positions, peripherals, ruleset.peripheralRingRadius(), 0.0, rng.seed());
        // Walled ring offset by half a sector so a walled region sits between peripherals.
        placeRing(positions, walled, ruleset.walledRingRadius(),
                Math.PI / Math.max(1, peripherals.size()), rng.seed());
        return positions;
    }

    private static void placeRing(
            Map<String, Point> positions, List<RegionProfile> members,
            double radius, double baseAngle, long seed) {
        int n = members.size();
        if (n == 0) {
            return;
        }
        for (int i = 0; i < n; i++) {
            RegionProfile r = members.get(i);
            double angle = baseAngle + 2.0 * Math.PI * i / n;
            // Small deterministic jitter on radius so neighbours sort distinctly.
            // Computed directly from hash64 (NOT via the rng stream/counter).
            long h = RegionHash.hash64(seed, "embed:" + r.id());
            double jitter = (Long.remainderUnsigned(h, 101L) - 50) / 1000.0;
            double x = round4((radius + jitter) * Math.cos(angle));
            double z = round4((radius + jitter) * Math.sin(angle));
            positions.put(r.id(), new Point(x, z));
        }
    }

    // ------------------------------------------------------------------ #
    // Geometric edges.
    // ------------------------------------------------------------------ #

    private static Set<EdgeKey> geometricEdges(
            Ruleset ruleset, List<RegionProfile> selected,
            Map<String, Point> positions, String anchorId) {
        Set<EdgeKey> edges = new TreeSet<>();
        List<RegionProfile> nonAnchor = new ArrayList<>();
        for (RegionProfile r : selected) {
            if (!r.id().equals(anchorId)) {
                nonAnchor.add(r);
            }
        }
        for (RegionProfile r : nonAnchor) {
            edges.add(EdgeKey.of(anchorId, r.id()));
        }
        int k = Math.max(0, ruleset.peripheralNearestPeers());
        for (RegionProfile r : nonAnchor) {
            List<RegionProfile> others = new ArrayList<>();
            for (RegionProfile o : nonAnchor) {
                if (!o.id().equals(r.id())) {
                    others.add(o);
                }
            }
            Point pr = positions.get(r.id());
            others.sort(Comparator.<RegionProfile>comparingDouble(
                    o -> dist(pr, positions.get(o.id()))).thenComparing(RegionProfile::id));
            for (int i = 0; i < k && i < others.size(); i++) {
                edges.add(EdgeKey.of(r.id(), others.get(i).id()));
            }
        }
        return edges;
    }

    // ------------------------------------------------------------------ #
    // Spanning tree (BFS over non-walled geometric graph, rooted at anchor).
    // ------------------------------------------------------------------ #

    private record SpanningResult(Set<EdgeKey> treeEdges, Map<String, String> parent) {
    }

    private static SpanningResult spanningTree(
            Set<String> nonWalledIds, Set<EdgeKey> geoEdges, String anchorId) {
        Map<String, TreeSet<String>> adj = new HashMap<>();
        for (String id : nonWalledIds) {
            adj.put(id, new TreeSet<>());
        }
        for (EdgeKey e : geoEdges) {
            if (nonWalledIds.contains(e.a) && nonWalledIds.contains(e.b)) {
                adj.get(e.a).add(e.b);
                adj.get(e.b).add(e.a);
            }
        }
        Set<EdgeKey> treeEdges = new TreeSet<>();
        Map<String, String> parent = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        Deque<String> frontier = new ArrayDeque<>();
        visited.add(anchorId);
        frontier.add(anchorId);
        while (!frontier.isEmpty()) {
            String u = frontier.pollFirst();
            TreeSet<String> neighbours = adj.get(u);
            if (neighbours == null) {
                continue;
            }
            for (String v : neighbours) {
                if (!visited.contains(v)) {
                    visited.add(v);
                    parent.put(v, u);
                    treeEdges.add(EdgeKey.of(u, v));
                    frontier.add(v);
                }
            }
        }
        if (!visited.equals(nonWalledIds)) {
            List<String> missing = new ArrayList<>(nonWalledIds);
            missing.removeAll(visited);
            missing.sort(Comparator.naturalOrder());
            throw new UnsatisfiableRulesetException(
                    "non-walled geometric graph disconnected; unreachable: " + missing);
        }
        return new SpanningResult(treeEdges, parent);
    }

    // ------------------------------------------------------------------ #
    // Helpers.
    // ------------------------------------------------------------------ #

    private static double round4(double x) {
        // Math.rint rounds half-to-even, matching Python's round(x, 4) policy.
        return Math.rint(x * 10000.0) / 10000.0;
    }

    private static double dist(Point p, Point q) {
        return Math.hypot(p.x - q.x, p.z - q.z);
    }

    private static List<RegionProfile> roles(List<RegionProfile> catalog, String role) {
        List<RegionProfile> out = new ArrayList<>();
        for (RegionProfile r : catalog) {
            if (r.role().equals(role)) {
                out.add(r);
            }
        }
        return out;
    }

    private static List<RegionProfile> sortedById(List<RegionProfile> in) {
        List<RegionProfile> out = new ArrayList<>(in);
        out.sort(Comparator.comparing(RegionProfile::id));
        return out;
    }

    private static Set<String> idsWithRole(List<RegionProfile> selected, String role) {
        Set<String> out = new TreeSet<>();
        for (RegionProfile r : selected) {
            if (r.role().equals(role)) {
                out.add(r.id());
            }
        }
        return out;
    }

    private static RegionProfile firstWithRole(List<RegionProfile> catalog, String role) {
        for (RegionProfile r : catalog) {
            if (r.role().equals(role)) {
                return r;
            }
        }
        throw new UnsatisfiableRulesetException("no region with role " + role + " in catalog");
    }
}
