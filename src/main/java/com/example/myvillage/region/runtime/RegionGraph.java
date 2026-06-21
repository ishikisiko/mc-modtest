package com.example.myvillage.region.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * A generated per-seed region graph. Java mirror of {@code RegionGraph} in
 * {@code tools/buildgen/region_topology.py}.
 *
 * <p>The {@link #toCanonicalJson()} method emits the graph in the exact
 * canonical form the offline Python generator produces
 * ({@code json.dumps(graph.to_dict(), ensure_ascii=False, indent=2, sort_keys=True)}),
 * which is the parity contract asserted by
 * {@code RegionRuntimeParityTest}: the Java port's output SHALL be
 * byte-identical to the golden fixture for every fixture seed.
 *
 * @param seed        world seed
 * @param rulesetId   ruleset id
 * @param count       number of regions
 * @param tierRange   tier range
 * @param tierStep    tier step
 * @param regions     assigned regions (sorted by id)
 * @param edges       typed edges (sorted by endpoint pair)
 */
public record RegionGraph(
        long seed,
        String rulesetId,
        int count,
        IntRange tierRange,
        int tierStep,
        List<GenRegion> regions,
        List<GenEdge> edges) {

    /** Region whose id equals {@code id}; throws if absent. */
    public GenRegion regionById(String id) {
        for (GenRegion r : regions) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        throw new IllegalArgumentException("no region with id " + id);
    }

    /**
     * Canonical JSON: {@code indent=2}, keys sorted ascending at every level,
     * UTF-8, matching the offline generator's
     * {@code json.dumps(..., ensure_ascii=False, indent=2, sort_keys=True)}.
     */
    public String toCanonicalJson() {
        return RegionJson.PRETTY.toJson(toTree());
    }

    TreeMap<String, Object> toTree() {
        TreeMap<String, Object> root = new TreeMap<>();
        root.put("seed", seed);
        root.put("ruleset", rulesetId);
        root.put("count", count);
        root.put("tier_range", intList(tierRange.lo(), tierRange.hi()));
        root.put("tier_step", tierStep);
        root.put("regions", regionTree());
        root.put("edges", edgeTree());
        return root;
    }

    private List<TreeMap<String, Object>> regionTree() {
        List<TreeMap<String, Object>> out = new ArrayList<>(regions.size());
        for (GenRegion r : regions) {
            TreeMap<String, Object> m = new TreeMap<>();
            m.put("id", r.id());
            m.put("display_name", r.displayName());
            m.put("tier", r.tier());
            m.put("role", r.role());
            m.put("qi", intList(r.qi().lo(), r.qi().hi()));
            m.put("danger", intList(r.danger().lo(), r.danger().hi()));
            m.put("admitted_subjects", new ArrayList<>(r.admittedSubjects()));
            m.put("nominal_tier", r.nominalTier());
            m.put("position", doubleList(r.posX(), r.posZ()));
            out.add(m);
        }
        return out;
    }

    private List<TreeMap<String, Object>> edgeTree() {
        List<TreeMap<String, Object>> out = new ArrayList<>(edges.size());
        for (GenEdge e : edges) {
            TreeMap<String, Object> m = new TreeMap<>();
            m.put("from", e.a());
            m.put("to", e.b());
            m.put("type", e.type());
            if (RegionContract.EDGE_GE.equals(e.type())) {
                m.put("separator", e.separator());
            }
            if (e.pass() != null) {
                m.put("pass", e.pass());
            }
            out.add(m);
        }
        return out;
    }

    private static List<Integer> intList(int a, int b) {
        List<Integer> l = new ArrayList<>(2);
        l.add(a);
        l.add(b);
        return l;
    }

    private static List<Double> doubleList(double a, double b) {
        List<Double> l = new ArrayList<>(2);
        l.add(a);
        l.add(b);
        return l;
    }
}
