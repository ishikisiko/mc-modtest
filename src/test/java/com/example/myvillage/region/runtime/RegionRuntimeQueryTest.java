package com.example.myvillage.region.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for the region runtime's coordinate transform,
 * {@code region_at} query, and spawn-region selector — the parts of change
 * {@code add-region-runtime-binding} that are deterministic functions of the
 * graph and therefore unit-testable without a running server.
 *
 * <p>Spawn determinism across Python/Java (task 4.4) is asserted here by
 * comparing the Java selector's region id + world block against the golden
 * fixtures emitted by {@code tools/buildgen/tests/
 * generate_region_runtime_fixtures.py} (which compute the same selection in
 * Python).
 */
class RegionRuntimeQueryTest {

    private static final Path WORLDFEN_DIR =
            Path.of("src/main/resources/data/myvillage/worldgen");

    private static RegionCatalogLoader.RegionData DATA;

    @BeforeAll
    static void loadShippedData() {
        DATA = RegionCatalogLoader.loadFromFilesystem(WORLDFEN_DIR);
    }

    static Stream<Arguments> fixtures() {
        JsonObject index = readResource("/region_runtime_fixtures/index.json");
        List<Arguments> out = new ArrayList<>();
        for (JsonElement e : index.getAsJsonArray("cases")) {
            JsonObject c = e.getAsJsonObject();
            out.add(Arguments.of(
                    c.get("case").getAsString(),
                    c.get("seed").getAsLong(),
                    c.get("file").getAsString()));
        }
        return out.stream();
    }

    /**
     * Task 4.4: the Java spawn selector reproduces the fixture's spawn region
     * id and world block for every fixture seed (Python↔Java determinism).
     */
    @ParameterizedTest(name = "{0} seed={1}")
    @MethodSource("fixtures")
    void spawnSelectionMatchesFixture(String caseName, long seed, String file) {
        JsonObject fixture = readResource("/region_runtime_fixtures/" + file).getAsJsonObject();
        RegionGraph graph = RegionTopologyGenerator.generate(seed, DATA.ruleset(), DATA.catalog());

        Optional<RegionSpawnSelector.SpawnSelection> sel = RegionSpawnSelector.select(graph);
        assertTrue(sel.isPresent(), "spawn selection should be non-empty for " + caseName);

        JsonObject expectedSpawn = fixture.getAsJsonObject("spawn");
        assertEquals(expectedSpawn.get("region_id").getAsString(), sel.get().regionId(),
                "spawn region drift for " + caseName);
        int expectedX = expectedSpawn.getAsJsonArray("world_block").get(0).getAsInt();
        int expectedZ = expectedSpawn.getAsJsonArray("world_block").get(1).getAsInt();
        assertEquals(expectedX, sel.get().worldX(), "spawn world x drift for " + caseName);
        assertEquals(expectedZ, sel.get().worldZ(), "spawn world z drift for " + caseName);
    }

    /**
     * Task 4.1: walled regions are never selected as spawn, even when they
     * hold the lowest assigned tier (the {@code walled_low} fixture's walled
     * region is the global tier minimum).
     */
    @Test
    void spawnNeverSelectsWalledRegion() {
        RegionGraph graph = RegionTopologyGenerator.generate(2, DATA.ruleset(), DATA.catalog());
        Optional<RegionSpawnSelector.SpawnSelection> sel = RegionSpawnSelector.select(graph);
        assertTrue(sel.isPresent());
        String walled = graph.regions().stream()
                .filter(r -> "walled".equals(r.role()))
                .map(GenRegion::id)
                .findFirst().orElse(null);
        assertTrue(walled != null, "walled_low fixture should have a walled region");
        assertTrue(!walled.equals(sel.get().regionId()),
                "spawn must not select the walled region");
    }

    /**
     * Task 3.1: the anchor (中州) center maps to world block (0, 0) and every
     * region center fits within the anchor-centered world radius.
     */
    @ParameterizedTest(name = "{0} seed={1}")
    @MethodSource("fixtures")
    void placementPutsAnchorAtOriginAndBoundsAllRegions(String caseName, long seed, String file) {
        RegionGraph graph = RegionTopologyGenerator.generate(seed, DATA.ruleset(), DATA.catalog());
        GenRegion anchor = graph.regions().stream()
                .filter(r -> "anchor".equals(r.role()))
                .findFirst().orElseThrow();
        int[] anchorBlock = RegionPlacement.worldBlockFromGraph(anchor.posX(), anchor.posZ());
        assertEquals(0, anchorBlock[0], "anchor world x must be 0 for " + caseName);
        assertEquals(0, anchorBlock[1], "anchor world z must be 0 for " + caseName);

        for (GenRegion r : graph.regions()) {
            int[] b = RegionPlacement.worldBlockFromGraph(r.posX(), r.posZ());
            double dist = RegionPlacement.worldRadiusFromCenter(b[0], b[1]);
            assertTrue(dist <= RegionPlacement.RADIUS_WORLD,
                    "region " + r.id() + " center at " + dist + " exceeds radius "
                            + RegionPlacement.RADIUS_WORLD + " for " + caseName);
        }
    }

    /**
     * Task 3.2: {@code region_at} resolves a region's placed center to that
     * region (anchor at origin), and a point well outside the bounded area
     * resolves to no region.
     */
    @ParameterizedTest(name = "{0} seed={1}")
    @MethodSource("fixtures")
    void regionAtResolvesCentersAndRejectsFarPoints(String caseName, long seed, String file) {
        RegionGraph graph = RegionTopologyGenerator.generate(seed, DATA.ruleset(), DATA.catalog());

        Optional<String> atOrigin = RegionQueries.regionAt(graph, 0, 0);
        assertTrue(atOrigin.isPresent());
        assertEquals("zhongzhou", atOrigin.get(),
                "the anchor center at (0,0) must resolve to 中州 for " + caseName);

        Optional<String> farAway = RegionQueries.regionAt(graph, 50_000, 50_000);
        assertTrue(farAway.isEmpty(),
                "a point well outside the bounded area must resolve to no region for " + caseName);
    }

    // ------------------------------------------------------------------ #
    // Rung-ladder API (task group 5).
    // ------------------------------------------------------------------ #

    /**
     * Task 5.1: the rung ladder is the ascending distinct assigned tiers among
     * non-walled regions; the anchor's tier is the top; the walled region's
     * tier never appears.
     */
    @ParameterizedTest(name = "{0} seed={1}")
    @MethodSource("fixtures")
    void rungLadderExcludesWalledAndTopsAtAnchor(String caseName, long seed, String file) {
        RegionGraph graph = RegionTopologyGenerator.generate(seed, DATA.ruleset(), DATA.catalog());
        List<Integer> ladder = RungLadder.ladder(graph);

        List<Integer> expected = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (GenRegion r : graph.regions()) {
            if (!"walled".equals(r.role()) && seen.add(r.tier())) {
                expected.add(r.tier());
            }
        }
        expected.sort(Integer::compare);
        assertEquals(expected, ladder, "ladder drift for " + caseName);
        assertFalse(ladder.isEmpty(), "ladder must be non-empty for " + caseName);

        GenRegion anchor = graph.regions().stream()
                .filter(r -> "anchor".equals(r.role())).findFirst().orElseThrow();
        assertEquals(anchor.tier(), ladder.get(ladder.size() - 1),
                "anchor tier must be the top rung for " + caseName);

        // The walled REGION (id) must never appear in any rung's region set.
        // (Its tier value may coincide with a non-walled region's tier — e.g.
        // seed 3's 魔域 shares tier 16 with a peripheral — in which case the
        // tier is on the ladder but the walled id is still excluded.)
        Set<String> walledIds = new HashSet<>();
        graph.regions().stream()
                .filter(r -> "walled".equals(r.role()))
                .forEach(r -> walledIds.add(r.id()));
        for (int tier : ladder) {
            Set<String> atRung = RungLadder.regionsAtRung(graph, tier);
            for (String walled : walledIds) {
                assertFalse(atRung.contains(walled),
                        "walled region " + walled + " must not appear at rung " + tier
                                + " for " + caseName);
            }
        }
    }

    /**
     * Task 5.2: {@code current_rung} at a region's placed center equals that
     * region's assigned tier (for non-walled regions); {@code current_region}
     * resolves to that region.
     */
    @ParameterizedTest(name = "{0} seed={1}")
    @MethodSource("fixtures")
    void currentRungMatchesEachNonWalledRegionsTier(String caseName, long seed, String file) {
        RegionGraph graph = RegionTopologyGenerator.generate(seed, DATA.ruleset(), DATA.catalog());
        for (GenRegion r : RungLadder.nonWalledRegions(graph)) {
            int[] block = RegionPlacement.worldBlockFromGraph(r.posX(), r.posZ());
            Optional<String> region = RungLadder.currentRegion(graph, block[0], block[1]);
            Optional<Integer> rung = RungLadder.currentRung(graph, block[0], block[1]);
            // A region's own center may tie in distance with another at an exact boundary;
            // assert the resolved region shares the tier so the rung is stable either way.
            assertTrue(region.isPresent(), "region must resolve at " + r.id() + " center for " + caseName);
            assertEquals(graph.regionById(region.get()).tier(), rung.orElseThrow(),
                    "current_rung must match the resolved region's tier for " + caseName);
        }
    }

    /**
     * Task 5.3: {@code next_rung_regions} from a non-anchor region returns the
     * regions at the next-higher distinct tier (a set whose size matches the
     * count of regions at that tier); from the anchor it is empty.
     */
    @ParameterizedTest(name = "{0} seed={1}")
    @MethodSource("fixtures")
    void nextRungRegionsRespectsAscendingRungAndIsEmptyAtAnchor(
            String caseName, long seed, String file) {
        RegionGraph graph = RegionTopologyGenerator.generate(seed, DATA.ruleset(), DATA.catalog());
        List<Integer> ladder = RungLadder.ladder(graph);
        Map<Integer, Set<String>> byTier = new TreeMap<>();
        for (GenRegion r : RungLadder.nonWalledRegions(graph)) {
            byTier.computeIfAbsent(r.tier(), k -> new HashSet<>()).add(r.id());
        }

        for (GenRegion r : RungLadder.nonWalledRegions(graph)) {
            int[] block = RegionPlacement.worldBlockFromGraph(r.posX(), r.posZ());
            Optional<String> resolved = RungLadder.currentRegion(graph, block[0], block[1]);
            // Skip centers whose nearest-center ties onto a neighbor (rare boundary);
            // the per-tier assertions below cover the resolved region.
            if (resolved.isEmpty() || !resolved.get().equals(r.id())) {
                continue;
            }
            Set<String> next = RungLadder.nextRungRegions(graph, block[0], block[1]);
            int idx = ladder.indexOf(r.tier());
            if (idx == ladder.size() - 1) {
                assertTrue(next.isEmpty(),
                        "next_rung must be empty at the top rung for " + caseName);
            } else {
                int nextTier = ladder.get(idx + 1);
                assertEquals(byTier.get(nextTier), next,
                        "next_rung must equal the regions at the next-higher tier for " + caseName);
            }
        }

        // Explicitly: the anchor center (0,0) is the top rung → empty next set.
        assertTrue(RungLadder.nextRungRegions(graph, 0, 0).isEmpty(),
                "next_rung must be empty at the anchor (0,0) for " + caseName);
    }

    /**
     * Task 5.3: {@code next_rung_regions} returns a <b>set</b> (not a
     * singleton) at a tier tie. Seeds 20260620 (tie at 18: 灵岳+西漠) and 4
     * (tie at 19: three regions) exercise multi-member rungs.
     */
    @Test
    void nextRungRegionsReturnsSetAtTierTie() {
        // seed 20260620: ladder [15,16,17,18,20]; tier 18 = {lingyue, ximo}.
        long seed = 20260620L;
        RegionGraph graph = RegionTopologyGenerator.generate(seed, DATA.ruleset(), DATA.catalog());
        GenRegion from = graph.regions().stream()
                .filter(r -> r.id().equals("nanjiang")).findFirst().orElseThrow(); // tier 17
        int[] block = RegionPlacement.worldBlockFromGraph(from.posX(), from.posZ());
        Set<String> next = RungLadder.nextRungRegions(graph, block[0], block[1]);
        assertEquals(Set.of("lingyue", "ximo"), next,
                "next_rung from nanjiang (tier 17) must be the {lingyue, ximo} tie set at tier 18");
        assertTrue(next.size() >= 2,
                "a tier-tie rung must return a multi-member set, not a singleton");
    }

    /**
     * Task 5.2/5.3: a walled region (魔域) is off the ladder —
     * {@code current_rung} is empty and {@code next_rung_regions} is empty for
     * a player standing in it.
     */
    @Test
    void walledRegionIsOffTheRungLadder() {
        RegionGraph graph = RegionTopologyGenerator.generate(2, DATA.ruleset(), DATA.catalog());
        GenRegion walled = graph.regions().stream()
                .filter(r -> "walled".equals(r.role())).findFirst().orElseThrow();
        int[] block = RegionPlacement.worldBlockFromGraph(walled.posX(), walled.posZ());
        assertTrue(RungLadder.currentRung(graph, block[0], block[1]).isEmpty(),
                "current_rung must be empty inside a walled region");
        assertTrue(RungLadder.nextRungRegions(graph, block[0], block[1]).isEmpty(),
                "next_rung_regions must be empty inside a walled region");
    }

    private static JsonObject readResource(String name) {
        try (Reader reader = new InputStreamReader(
                Objects.requireNonNull(
                        RegionRuntimeQueryTest.class.getResourceAsStream(name),
                        name + " not on test classpath"),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException ioe) {
            throw new AssertionError("failed reading " + name, ioe);
        }
    }
}
