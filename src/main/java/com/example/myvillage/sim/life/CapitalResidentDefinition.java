package com.example.myvillage.sim.life;

import java.util.List;
import java.util.Set;

/** Ordinary residents: their identity is durable, but they retain normal life and combat rules. */
public record CapitalResidentDefinition(String role, String home, String work, String surname,
        String given, String gender, int ageYears, String realm, int stage,
        List<Integer> root, List<Integer> traits) {
    public CapitalResidentDefinition {
        LifePlace.requireId(home); LifePlace.requireId(work);
        root = List.copyOf(root); traits = List.copyOf(traits);
        if (!Set.of("guide", "innkeeper", "herbalist", "smith").contains(role)
                || surname == null || surname.isBlank() || given == null || given.isBlank()
                || surname.length() + given.length() > 32 || !Set.of("m", "f").contains(gender)
                || ageYears < 1 || ageYears > 1000 || !"qi_refining".equals(realm) || stage < 0 || stage > 2
                || root.size() != 5 || root.stream().anyMatch(v -> v < 0 || v > 10000)
                || root.stream().mapToInt(Integer::intValue).sum() != 10000
                || traits.size() != 5 || traits.stream().anyMatch(v -> v < 0 || v > 100))
            throw new IllegalArgumentException("invalid_capital_resident");
    }
}
