package com.example.myvillage.worldplan.capital;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * All-or-nothing future cities collection. Incomplete previews and legacy single-city snapshots
 * cannot be exposed through this type. Querying it never requests chunks or initializes the ledger.
 */
public final class CapitalCities {
    private final List<PlannedCapital> cities;
    private final Map<String, PlannedCapital> byId;
    private final Map<CapitalRegion, PlannedCapital> byRegion;

    public CapitalCities(CapitalCatalog catalog, List<PlannedCapital> input) {
        var ids = new HashMap<String, PlannedCapital>();
        var regions = new EnumMap<CapitalRegion, PlannedCapital>(CapitalRegion.class);
        List<PlannedCapital> snapshot = List.copyOf(input);
        for (PlannedCapital city : snapshot) {
            var definition = catalog.capitalOf(city.regionId());
            if (!definition.cityId().equals(city.cityId()) || !definition.appearanceId().equals(city.appearanceId()))
                throw new IllegalArgumentException("主城记录没有使用对应的目录身份");
            String problem = CapitalPlanner.metadataProblem(definition,
                    new CapitalCandidate(city.layout(), city.manifest(), city.placement()));
            if (problem != null) throw new IllegalArgumentException(problem);
            if (ids.putIfAbsent(city.cityId(), city) != null || regions.putIfAbsent(city.regionId(), city) != null)
                throw new IllegalArgumentException("主城集合不允许重复城市或州域");
        }
        if (regions.size() != CapitalRegion.values().length)
            throw new IllegalArgumentException("主城集合必须八州齐全，每州一城，包括魔域");
        var ordered = new ArrayList<>(snapshot);
        ordered.sort(Comparator.comparing(PlannedCapital::regionId));
        for (int i = 0; i < ordered.size(); i++) for (int j = i + 1; j < ordered.size(); j++)
            if (ordered.get(i).reservationBounds().intersectsHorizontally(ordered.get(j).reservationBounds()))
                throw new IllegalArgumentException("两座主城的预留相交");
        cities = List.copyOf(ordered); byId = Map.copyOf(ids); byRegion = Map.copyOf(regions);
    }

    /** Future WorldPlan facade should delegate cities() here, without selecting an arbitrary first city. */
    public List<PlannedCapital> cities() { return cities; }
    public Optional<PlannedCapital> cityById(String cityId) { return Optional.ofNullable(byId.get(cityId)); }
    public PlannedCapital capitalOf(CapitalRegion regionId) {
        PlannedCapital result = byRegion.get(regionId);
        if (result == null) throw new IllegalArgumentException("非核心州域没有主城: " + regionId);
        return result;
    }
    public List<PlannedCapital> intersectingReservations(CapitalGeometry.Bounds bounds) {
        return cities.stream().filter(city -> city.reservationBounds().intersectsHorizontally(bounds)).toList();
    }
}
