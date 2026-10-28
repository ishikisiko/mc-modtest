package com.example.myvillage.worldplan.capital;

import java.util.List;

/**
 * Identity/coordinate handoff only. This class never registers sites, assigns people, initializes
 * stock, or edits the ledger; future callers must perform those writes through WorldSim.
 */
public final class CapitalBindings {
    private CapitalBindings() {}

    /** A local service id is meaningful only together with its owning city id. */
    public record SlotKey(String cityId, String serviceId) {
        public SlotKey { CapitalJson.id(cityId); CapitalJson.id(serviceId); }
    }

    public record ServiceSlot(SlotKey key, String role, String buildingId, CapitalGeometry.Point worldPosition) {}

    /** New-city LifeSite id; never use this method to rename a saved legacy Yunshan binding. */
    public static String ledgerSiteId(PlannedCapital city) { return city.cityId(); }

    /** Existing commerce markets and life supplies can use this per-city site id without a new ledger. */
    public static String warehouseSiteId(PlannedCapital city) { return city.cityId(); }

    public static List<ServiceSlot> serviceSlots(PlannedCapital city) {
        return city.manifest().services().stream().map(service -> new ServiceSlot(
                new SlotKey(city.cityId(), service.id()), service.role(), service.buildingId(),
                city.placement().toWorld(service.position()))).toList();
    }

    public static List<CapitalManifest.Entry> entries(PlannedCapital city) {
        return new CapitalCandidate(city.layout(), city.manifest(), city.placement()).worldEntries();
    }
}
