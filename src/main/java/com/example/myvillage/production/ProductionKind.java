package com.example.myvillage.production;

import com.example.myvillage.gathering.GatheringTier;

public enum ProductionKind {
    MINE("mine", ProductionPart.MINE_STATION, null, 1),
    LOW("low", ProductionPart.BRONZE_CONDENSER, GatheringTier.LOW, 0),
    MEDIUM("medium", ProductionPart.JADE_CONDENSER, GatheringTier.MEDIUM, 1),
    HIGH("high", ProductionPart.GOLDEN_CONDENSER, GatheringTier.HIGH, 3);

    final String id;
    final ProductionPart controller;
    final GatheringTier gatheringTier;
    final int lastMode;

    ProductionKind(String id, ProductionPart controller, GatheringTier gatheringTier, int lastMode) {
        this.id = id;
        this.controller = controller;
        this.gatheringTier = gatheringTier;
        this.lastMode = lastMode;
    }

    public boolean mining() { return this == MINE; }
    public String id() { return id; }
    public int gradeCount() { return mining() ? 1 : ordinal(); }
    public boolean hasModes() { return lastMode != 0; }

    static ProductionKind forController(ProductionPart part) {
        for (var kind : values()) if (kind.controller == part) return kind;
        throw new IllegalArgumentException(part.id);
    }
}
