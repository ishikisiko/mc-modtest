package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.WorldPlanRules;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RoadRerouteTest {
    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 20261104L})
    @Timeout(180)
    void differentSeedsReachMoyuThroughTheFormalPassWithTheWholeCarriage(long seed) throws Exception {
        WorldPlan world;
        try (var input = getClass().getResourceAsStream(WorldPlanRules.RESOURCE)) {
            assertNotNull(input);
            world = new WorldPlan(seed, new WorldPlanRules(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        var rules = RoadRules.load();
        var result = WorldRoadPlanner.plan(world, rules, WorldRoadPlanner.exit(world, rules, "beiyuan"),
                WorldRoadPlanner.exit(world, rules, "moyu"), List.of(), List.of());
        var cells = RoadNetworkGeometry.index(result.cells());
        for (var point : result.centerline()) {
            assertTrue(WorldRoadPlanner.legalBorderCrossing(world, rules, "beiyuan", "moyu", point.x(), point.z()));
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
