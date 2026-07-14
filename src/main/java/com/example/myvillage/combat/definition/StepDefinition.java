package com.example.myvillage.combat.definition;

public record StepDefinition(int actionTick, double maximumDistance, double supportDepth) {
    public StepDefinition {
        if (actionTick < 0) {
            throw new IllegalArgumentException("Step tick must be non-negative");
        }
        if (!(maximumDistance > 0.0) || maximumDistance > 0.8) {
            throw new IllegalArgumentException("Step distance must be in (0, 0.8]");
        }
        if (!(supportDepth > 0.0) || supportDepth > 1.0) {
            throw new IllegalArgumentException("Support depth must be in (0, 1]");
        }
    }
}
