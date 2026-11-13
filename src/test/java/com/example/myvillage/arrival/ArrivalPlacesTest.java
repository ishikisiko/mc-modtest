package com.example.myvillage.arrival;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.WorldPlanRules;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArrivalPlacesTest {
    private static WorldPlan plan;
    @BeforeAll static void plan() throws Exception {
        try (var in = ArrivalPlacesTest.class.getResourceAsStream(WorldPlanRules.V11_RESOURCE)) {
            assertNotNull(in);
            plan = new WorldPlan(20261029L, new WorldPlanRules(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    @Test void everyCapitalUsesItsActualNameRegionAndFootprint() {
        assertEquals(8, plan.cities().size());
        for (var city : plan.cities()) {
            var p = city.entrance();
            var arrival = ArrivalPlaces.at(plan.graph(), plan, p.x(), p.z());
            assertEquals(city.cityId(), arrival.townId()); assertEquals(city.regionId(), arrival.regionId());
            assertFalse(arrival.townName().isBlank());
            var outside = ArrivalPlaces.at(plan.graph(), plan, city.x() - 1, city.z() - 1);
            assertNotEquals(city.cityId(), outside.townId());
        }
    }

    @Test void qianzhenAndBayTerracesUseTheirPlacedSitesAndConfiguredNames() {
        var q = plan.qianzhen(); assertNotNull(q);
        assertEquals("阡镇", ArrivalPlaces.at(plan.graph(), plan, q.entry().x(), q.entry().z()).townName());
        assertFalse(plan.settlements().isEmpty());
        for (var town : plan.settlements()) {
            int half = plan.rules().settlements.size / 2;
            var arrival = ArrivalPlaces.at(plan.graph(), plan, town.x() + half, town.z() + half);
            assertEquals(town.id(), arrival.townId()); assertEquals("湾口台地集", arrival.townName());
        }
        assertEquals(ArrivalLocation.EMPTY, ArrivalPlaces.at(plan.graph(), plan, 100000, 100000));
    }
}
