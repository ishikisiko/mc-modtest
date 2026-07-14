package com.example.myvillage.combat.definition;

import java.util.List;
import java.util.Objects;

public record HitboxDefinition(
        String shapeFamily,
        List<HitboxSample> samples,
        double horizontalTolerance,
        double verticalTolerance) {
    public HitboxDefinition {
        Objects.requireNonNull(shapeFamily, "shapeFamily");
        samples = List.copyOf(Objects.requireNonNull(samples, "samples"));
        if (shapeFamily.isBlank() || samples.isEmpty()) {
            throw new IllegalArgumentException("Hitbox shape and samples are required");
        }
        if (horizontalTolerance < 0.0 || horizontalTolerance > 0.25) {
            throw new IllegalArgumentException("Horizontal tolerance must be in 0..0.25");
        }
        if (verticalTolerance < 0.0 || verticalTolerance > 0.15) {
            throw new IllegalArgumentException("Vertical tolerance must be in 0..0.15");
        }
    }

    public List<HitboxSample> samplesAt(int actionTick) {
        return samples.stream().filter(sample -> sample.actionTick() == actionTick).toList();
    }
}
