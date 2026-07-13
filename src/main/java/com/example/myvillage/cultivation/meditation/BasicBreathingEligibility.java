package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.technique.TechniqueRequirementEvaluator;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public final class BasicBreathingEligibility {
    private BasicBreathingEligibility() {
    }

    public static boolean requirementsSatisfied(
            RegistryAccess registryAccess,
            CultivationProfile profile,
            TechniqueDefinition definition) {
        return TechniqueRequirementEvaluator.evaluate(
                Objects.requireNonNull(registryAccess, "registryAccess"),
                Objects.requireNonNull(profile, "profile"),
                Objects.requireNonNull(definition, "definition"))
                .satisfied();
    }

    static boolean requirementsSatisfied(
            CultivationProfile profile,
            TechniqueDefinition definition,
            Function<ResourceLocation, RealmDefinition> realmLookup,
            Predicate<ResourceLocation> elementAvailable) {
        return TechniqueRequirementEvaluator.evaluate(
                Objects.requireNonNull(profile, "profile"),
                Objects.requireNonNull(definition, "definition"),
                Objects.requireNonNull(realmLookup, "realmLookup"),
                Objects.requireNonNull(elementAvailable, "elementAvailable"))
                .satisfied();
    }
}
