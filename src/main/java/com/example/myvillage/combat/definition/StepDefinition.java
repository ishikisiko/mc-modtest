package com.example.myvillage.combat.definition;

public record StepDefinition(int actionTick, double maximumDistance, double supportDepth) {
    public static final double MAXIMUM_STEP_DISTANCE = 1.6;

    public StepDefinition {
        if (actionTick < 0) {
            throw new IllegalArgumentException("Step tick must be non-negative");
        }
        if (!(maximumDistance > 0.0) || maximumDistance > MAXIMUM_STEP_DISTANCE) {
            throw new IllegalArgumentException("Step distance must be in (0, 1.6]");
        }
        if (!(supportDepth > 0.0) || supportDepth > 1.0) {
            throw new IllegalArgumentException("Support depth must be in (0, 1]");
        }
    }
}
