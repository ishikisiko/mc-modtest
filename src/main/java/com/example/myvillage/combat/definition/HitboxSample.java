package com.example.myvillage.combat.definition;

public record HitboxSample(
        int actionTick,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ,
        double horizontalRadius,
        double verticalRadius) {
    public HitboxSample {
        if (actionTick < 0) {
            throw new IllegalArgumentException("Sample action tick must be non-negative");
        }
        if (!Double.isFinite(startX) || !Double.isFinite(startY) || !Double.isFinite(startZ)
                || !Double.isFinite(endX) || !Double.isFinite(endY) || !Double.isFinite(endZ)) {
            throw new IllegalArgumentException("Sample coordinates must be finite");
        }
        if (!(horizontalRadius > 0.0) || !(verticalRadius > 0.0)) {
            throw new IllegalArgumentException("Sample radii must be positive");
        }
    }
}
