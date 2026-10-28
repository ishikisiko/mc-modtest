package com.example.myvillage.worldplan.capital;

import java.util.Objects;

/**
 * Future terrain-fitting boundary. No implementation, chunk access, height fallback or synthetic
 * success is supplied in this delivery. A preview heightfield alone cannot issue a FIT receipt.
 */
@FunctionalInterface
public interface CapitalTerrain {
    Assessment assess(CapitalCatalog.Definition definition, CapitalCandidate candidate,
                      CapitalPlanner.RegionContext region, CapitalGeometry.Bounds reservationBounds, int maxSamples);

    enum Outcome { FIT, REJECTED, UNAVAILABLE }

    /** FIT requires the actual planning terrain/region source and every listed obligation. */
    record Assessment(Outcome outcome, int samplesUsed, String sourceRef, String detail,
                      boolean hostContainsReservation, boolean footprintSupported,
                      boolean entriesReachRegionRoutes, boolean transitionBounded) {
        public Assessment {
            Objects.requireNonNull(outcome);
            if (samplesUsed < 0 || sourceRef == null || sourceRef.isBlank() || detail == null || detail.isBlank())
                throw new IllegalArgumentException("地形回执必须保留来源、耗用预算和原因");
            if (outcome == Outcome.FIT && (samplesUsed == 0 || !hostContainsReservation || !footprintSupported
                    || !entriesReachRegionRoutes || !transitionBounded))
                throw new IllegalArgumentException("缺少地形、归属或通路义务时不得接受城址");
        }
    }
}
