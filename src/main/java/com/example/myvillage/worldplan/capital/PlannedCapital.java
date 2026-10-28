package com.example.myvillage.worldplan.capital;

import java.util.Objects;

/** Immutable candidate accepted by a future terrain adapter; not an installed production city. */
public record PlannedCapital(String cityId, CapitalRegion regionId, int regionLevel, String appearanceId,
                             CapitalLayoutIdentity layout, CapitalGeometry.Placement placement,
                             CapitalManifest manifest, CapitalGeometry.Bounds reservationBounds,
                             CapitalTerrain.Assessment terrainAssessment) {
    public PlannedCapital {
        CapitalJson.id(cityId); Objects.requireNonNull(regionId); Objects.requireNonNull(layout);
        Objects.requireNonNull(placement); Objects.requireNonNull(manifest); Objects.requireNonNull(reservationBounds);
        Objects.requireNonNull(terrainAssessment);
        if (regionLevel < 1 || appearanceId == null || appearanceId.isBlank())
            throw new IllegalArgumentException("主城需要独立的等级值与外观身份");
        if (!cityId.equals(manifest.cityId()) || regionId != manifest.regionId())
            throw new IllegalArgumentException("主城记录与布局 manifest 身份不一致");
        if (!reservationBounds.contains(placement.toWorld(manifest.bounds()))
                || terrainAssessment.outcome() != CapitalTerrain.Outcome.FIT)
            throw new IllegalArgumentException("主城必须保留完整预留与接受城址的地形回执");
    }

    public CapitalGeometry.Bounds worldBounds() { return placement.toWorld(manifest.bounds()); }
}
