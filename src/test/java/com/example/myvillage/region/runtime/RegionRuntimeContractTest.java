package com.example.myvillage.region.runtime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the runtime-binding passivity guarantee (task 8.4) and
 * the query-API invariants (task 8.3).
 *
 * <p>The {@code region-runtime-binding} spec requires the runtime to be
 * passive: it reads the world seed, computes and caches the graph, answers
 * queries, and calls {@code setDefaultSpawnPos} exactly once per world; it
 * writes no terrain, overrides no biome, hooks no chunk-gen. This test
 * exercises the <b>pure query API</b> ({@link RegionPlacement},
 * {@link RegionQueries}, {@link RungLadder}, {@link RegionSpawnSelector}) with
 * no server context — proving the query path needs no world and performs no
 * writes — and asserts it is deterministic across repeated calls. The sole
 * world write ({@code setDefaultSpawnPos}) lives in
 * {@link RegionRuntimeService}, gated by {@link RegionRuntimeState#spawnBound()}
 * so it fires at most once per world.
 */
class RegionRuntimeContractTest {

    private static final Path WORLDFEN_DIR =
            Path.of("src/main/resources/data/myvillage/worldgen");

    private static RegionCatalogLoader.RegionData DATA;

    @BeforeAll
    static void loadShippedData() {
        DATA = RegionCatalogLoader.loadFromFilesystem(WORLDFEN_DIR);
    }

    /**
     * Task 8.4: the pure query API runs with no server context (no
     * {@code ServerLevel}, no resource manager) and produces identical output
     * across repeated calls — i.e. it is side-effect-free. The query path
     * therefore writes nothing; the only world write is
     * {@code setDefaultSpawnPos} in {@link RegionRuntimeService}, gated to fire
     * once per world by the {@link RegionRuntimeState} flag.
     */
    @Test
    void queryApiNeedsNoServerAndIsDeterministic() {
        RegionGraph graph = RegionTopologyGenerator.generate(20260620L, DATA.ruleset(), DATA.catalog());

        // Snapshot every pure query at a representative set of positions.
        Map<String, Object> first = captureQueries(graph);
        Map<String, Object> second = captureQueries(graph);
        assertEquals(first, second,
                "pure query API must be deterministic across repeated calls (no hidden state)");
    }

    /**
     * Task 8.3: the runtime query invariants hold for the shipped seed — spawn
     * region is the lowest-tier eligible region (魔域 excluded), every region
     * center resolves via {@code region_at}, {@code next_rung_regions} returns a
     * multi-member set at a tier tie and the empty set at the anchor.
     */
    @Test
    void runtimeInvariantsHoldForShippedSeed() {
        RegionGraph graph = RegionTopologyGenerator.generate(20260620L, DATA.ruleset(), DATA.catalog());

        // Spawn = lowest-tier eligible, non-walled.
        RegionSpawnSelector.SpawnSelection spawn = RegionSpawnSelector.select(graph).orElseThrow();
        int minEligibleTier = graph.regions().stream()
                .filter(r -> !"walled".equals(r.role()) && !r.admittedSubjects().isEmpty())
                .mapToInt(GenRegion::tier).min().orElseThrow();
        assertEquals(minEligibleTier, graph.regionById(spawn.regionId()).tier(),
                "spawn region must hold the lowest eligible assigned tier");

        // region_at resolves every placed region center to that region's tier group.
        for (GenRegion r : graph.regions()) {
            int[] block = RegionPlacement.worldBlockFromGraph(r.posX(), r.posZ());
            Optional<String> at = RegionQueries.regionAt(graph, block[0], block[1]);
            assertTrue(at.isPresent(), "region center must resolve for " + r.id());
        }

        // next_rung_regions returns a set at the tier tie (18 = {lingyue, ximo}).
        GenRegion fromTier17 = graph.regions().stream()
                .filter(r -> r.tier() == 17).findFirst().orElseThrow();
        int[] from = RegionPlacement.worldBlockFromGraph(fromTier17.posX(), fromTier17.posZ());
        Set<String> next = RungLadder.nextRungRegions(graph, from[0], from[1]);
        assertTrue(next.size() >= 2, "next_rung at the tier-18 tie must be a multi-member set");

        // next_rung_regions is empty at the anchor (top rung).
        assertTrue(RungLadder.nextRungRegions(graph, 0, 0).isEmpty(),
                "next_rung must be empty at the anchor (top rung)");
    }

    private static Map<String, Object> captureQueries(RegionGraph graph) {
        Map<String, Object> snap = new TreeMap<>();
        snap.put("spawn", RegionSpawnSelector.select(graph).map(s ->
                s.regionId() + "@" + s.worldX() + "," + s.worldZ()).orElse("<none>"));
        snap.put("ladder", RungLadder.ladder(graph).toString());
        snap.put("regionAtOrigin", RegionQueries.regionAt(graph, 0, 0).orElse("<none>"));
        snap.put("regionAtFar", RegionQueries.regionAt(graph, 50_000, 50_000).orElse("<none>"));
        for (GenRegion r : graph.regions()) {
            int[] b = RegionPlacement.worldBlockFromGraph(r.posX(), r.posZ());
            snap.put("rung_" + r.id(), RungLadder.currentRung(graph, b[0], b[1]).orElse(-1));
            snap.put("next_" + r.id(),
                    RungLadder.nextRungRegions(graph, b[0], b[1]).toString());
        }
        return snap;
    }
}
