package com.example.myvillage.worldplan.capital;

import java.util.List;
import java.util.Objects;

/** Proposed transform and metadata; deliberately carries no claim that the world can host it. */
public record CapitalCandidate(CapitalLayoutIdentity layout, CapitalManifest manifest,
                               CapitalGeometry.Placement placement) {
    public CapitalCandidate {
        Objects.requireNonNull(layout); Objects.requireNonNull(manifest); Objects.requireNonNull(placement);
    }

    public CapitalGeometry.Bounds worldBounds() { return placement.toWorld(manifest.bounds()); }

    public List<CapitalManifest.Entry> worldEntries() {
        return manifest.entries().stream().map(entry -> new CapitalManifest.Entry(entry.id(),
                placement.toWorld(entry.position()), placement.toWorldFacing(entry.facing()))).toList();
    }
}
