package com.example.myvillage.sim.profession;
import java.util.Map;

/** Detached read-only details; deliberately outside the existing resident network protocol. */
public record ProfessionView(int personId, String role, int gatheringExperience, int gatheringLevel,
        int plantingExperience, int plantingLevel, int alchemyExperience, int alchemyLevel,
        Map<String,Integer> herbInventory, int legacyHerbs) {
    public ProfessionView { herbInventory = Map.copyOf(herbInventory); }
}
