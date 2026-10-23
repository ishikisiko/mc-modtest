package com.example.myvillage.sim.life;

import java.util.List;

/** Authored values only; no Minecraft types, generated names or duplicated combat statistics. */
public record StoryActorDefinition(String key, String surname, String given, String gender,
        int ageYears, String realm, int stage, List<Integer> root, List<Integer> traits,
        String role, String homeId, String initialPlaceId, String returnPlaceId, String policy) {
    public StoryActorDefinition {
        LifePlace.requireId(key);
        LifePlace.requireId(homeId);
        LifePlace.requireId(initialPlaceId);
        if (!returnPlaceId.isEmpty()) LifePlace.requireId(returnPlaceId);
        root = List.copyOf(root);
        traits = List.copyOf(traits);
        if (surname == null || surname.isBlank() || given == null || given.isBlank()
                || surname.length() + given.length() > 32 || !List.of("m", "f").contains(gender)
                || ageYears < 1 || ageYears > 1000 || !"qi_refining".equals(realm) || stage < 0 || stage > 2
                || root.size() != 5 || root.stream().anyMatch(v -> v < 0 || v > 10000)
                || root.stream().mapToInt(Integer::intValue).sum() != 10000
                || traits.size() != 5 || traits.stream().anyMatch(v -> v < 0 || v > 100)
                || !LifeEngine.ROLES.contains(role) || !"scripted_only".equals(policy))
            throw new IllegalArgumentException("invalid_story_actor_definition");
    }
}
