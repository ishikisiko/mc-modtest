package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.WorldPlanRules;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RoadRerouteTest {
    @ParameterizedTest
    @ValueSource(longs = {0L, 1L})
    @Timeout(180)
    void differentSeedsReachMoyuThroughTheFormalPassWithTheWholeCarriage(long seed) throws Exception {
        assertGate(seed);
    }
    @Test @Timeout(180)
    void seed20261104RetainsTheWholeCarriageThroughTheFormalPass() throws Exception {
        assertGate(20261104L);
    }
    private void assertGate(long seed) throws Exception {
        WorldPlan world;
        try (var input = getClass().getResourceAsStream(WorldPlanRules.RESOURCE)) {
            assertNotNull(input);
            world = new WorldPlan(seed, new WorldPlanRules(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        var rules = RoadRules.load();
        var border = world.borders().stream().filter(WorldPlan.Border::gate).findFirst().orElseThrow();
        assertTrue(border.a().equals("moyu") || border.b().equals("moyu"));
        var start = WorldRoadPlanner.exit(world, rules, border.a());
        var end = WorldRoadPlanner.exit(world, rules, border.b());
        System.out.println("ROAD_REPRO seed=" + seed + " start=" + start.region() + ":" + start.frame()
                + " feet=" + start.feet2() + " end=" + end.region() + ":" + end.frame() + " feet=" + end.feet2());
        var result = WorldRoadPlanner.plan(world, rules, start, end, List.of(), List.of());
        var cells = RoadNetworkGeometry.index(result.cells());
        for (var point : result.centerline()) {
            assertTrue(WorldRoadPlanner.legalBorderCrossing(world, rules, border.a(), border.b(), point.x(), point.z()));
            for (int dx = -rules.width / 2; dx <= rules.width / 2; dx++) {
                for (int dz = -rules.width / 2; dz <= rules.width / 2; dz++) {
                    var cell = cells.get(RoadPlan.Cell.key(point.x() + dx, point.z() + dz));
                    assertNotNull(cell, () -> "seed=" + seed + " point=" + point);
                    assertTrue(cell.band() < 2, () -> "Full carriage was narrowed at " + cell);
                    for (int[] direction : new int[][]{{1, 0}, {0, 1}}) {
                        var neighbor = cells.get(RoadPlan.Cell.key(cell.x() + direction[0], cell.z() + direction[1]));
                        if (neighbor != null && neighbor.band() < 2)
                            assertTrue(Math.abs(cell.feet2() - neighbor.feet2()) <= 1, () -> "Steep carriage edge: " + cell + " / " + neighbor);
                    }
                }
            }
        }
        assertFalse(result.centerline().isEmpty());
    }
}
