package com.example.myvillage.arrival;

import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.region.runtime.RegionQueries;
import com.example.myvillage.worldplan.WorldPlan;

/** Read-only geometry queries: no chunks, heightmap requests, world-sim writes or tickets. */
public final class ArrivalPlaces {
    private ArrivalPlaces() { }

    public static ArrivalLocation at(RegionGraph graph, WorldPlan plan, int x, int z) {
        String region = RegionQueries.regionAt(graph, x, z).orElse("");
        if (region.isEmpty()) return ArrivalLocation.EMPTY;
        String regionName = graph.regionById(region).displayName();
        if (plan != null) {
            var capital = plan.capitalAt(x, z).orElse(null);
            if (capital != null) {
                String name = capital.bundle().displayName;
                if (name.startsWith(regionName + "·")) name = name.substring(regionName.length() + 1);
                return new ArrivalLocation(region, regionName, capital.cityId(), name);
            }
            int size = plan.rules().settlements.size;
            for (var site : plan.settlements()) {
                if (x < site.x() || z < site.z() || x >= site.x() + size || z >= site.z() + size) continue;
                String name = ArrivalRules.INSTANCE.settlementNames().get(site.layout());
                if (name != null) return new ArrivalLocation(region, regionName, site.id(), name);
            }
        }
        return new ArrivalLocation(region, regionName, "", "");
    }
}
