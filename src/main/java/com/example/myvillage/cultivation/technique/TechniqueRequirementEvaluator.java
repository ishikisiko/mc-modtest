package com.example.myvillage.cultivation.technique;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.example.myvillage.cultivation.data.ModCultivationRegistries.REALMS;
import static com.example.myvillage.cultivation.data.ModCultivationRegistries.SPIRITUAL_ELEMENTS;

public final class TechniqueRequirementEvaluator {
    private TechniqueRequirementEvaluator() {
    }

    public static Evaluation evaluate(
            RegistryAccess registryAccess,
            CultivationProfile profile,
            TechniqueDefinition technique) {
        Objects.requireNonNull(registryAccess, "registryAccess");
        var realms = registryAccess.registry(REALMS);
        var elements = registryAccess.registry(SPIRITUAL_ELEMENTS);
        if (realms.isEmpty() || elements.isEmpty()) {
            return new Evaluation(Status.DEFINITION_UNAVAILABLE);
        }
        return evaluate(profile, technique, realms.get()::get, elements.get()::containsKey);
    }

    public static Evaluation evaluate(
            CultivationProfile profile,
            TechniqueDefinition technique,
            Function<ResourceLocation, RealmDefinition> realmLookup) {
        return evaluate(profile, technique, realmLookup, ignored -> true);
    }

    public static Evaluation evaluate(
            CultivationProfile profile,
            TechniqueDefinition technique,
            Function<ResourceLocation, RealmDefinition> realmLookup,
            Predicate<ResourceLocation> elementAvailable) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(technique, "technique");
        Objects.requireNonNull(realmLookup, "realmLookup");
        Objects.requireNonNull(elementAvailable, "elementAvailable");
        TechniqueRequirements requirements = technique.requirements();

        if (requirements.minimumRealm().isPresent()) {
            ResourceLocation currentRealmId = profile.realmId();
            ResourceLocation minimumRealmId = requirements.minimumRealm().orElseThrow();
            RealmDefinition currentRealm = realmLookup.apply(currentRealmId);
            RealmDefinition minimumRealm = realmLookup.apply(minimumRealmId);
            if (currentRealm == null || minimumRealm == null) {
                return new Evaluation(Status.DEFINITION_UNAVAILABLE);
            }
            if (!currentRealm.containsStage(profile.stageId())) {
                return new Evaluation(Status.CURRENT_STAGE_UNAVAILABLE);
            }
            if (currentRealm.sortOrder() < minimumRealm.sortOrder()) {
                return new Evaluation(Status.REALM_TOO_LOW);
            }
            if (currentRealm.sortOrder() == minimumRealm.sortOrder()
                    && !currentRealmId.equals(minimumRealmId)) {
                return new Evaluation(Status.AMBIGUOUS_REALM_ORDER);
            }
            if (requirements.minimumStage().isPresent()) {
                Optional<RealmStageDefinition> minimumStage = stage(
                        minimumRealm, requirements.minimumStage().orElseThrow());
                if (minimumStage.isEmpty()) {
                    return new Evaluation(Status.DEFINITION_UNAVAILABLE);
                }
                if (currentRealmId.equals(minimumRealmId)) {
                    Optional<RealmStageDefinition> currentStage = stage(currentRealm, profile.stageId());
                    if (currentStage.isEmpty()) {
                        return new Evaluation(Status.CURRENT_STAGE_UNAVAILABLE);
                    }
                    if (currentStage.get().sortOrder() < minimumStage.get().sortOrder()) {
                        return new Evaluation(Status.STAGE_TOO_LOW);
                    }
                }
            }
        }

        if (!requirements.minimumElementAffinity().isEmpty()) {
            Optional<SpiritualRoot> root = profile.spiritualRoot();
            if (root.isEmpty()) {
                return new Evaluation(Status.ROOT_REQUIRED);
            }
            for (var entry : requirements.minimumElementAffinity().entrySet()) {
                if (!elementAvailable.test(entry.getKey())) {
                    return new Evaluation(Status.DEFINITION_UNAVAILABLE);
                }
                int actual = root.orElseThrow().affinitiesBasisPoints().getOrDefault(entry.getKey(), 0);
                if (actual < entry.getValue()) {
                    return new Evaluation(Status.AFFINITY_TOO_LOW);
                }
            }
        }
        return new Evaluation(Status.SATISFIED);
    }

    private static Optional<RealmStageDefinition> stage(
            RealmDefinition realm,
            ResourceLocation stageId) {
        return realm.stages().stream().filter(stage -> stage.id().equals(stageId)).findFirst();
    }

    public enum Status {
        SATISFIED,
        DEFINITION_UNAVAILABLE,
        CURRENT_STAGE_UNAVAILABLE,
        REALM_TOO_LOW,
        AMBIGUOUS_REALM_ORDER,
        STAGE_TOO_LOW,
        ROOT_REQUIRED,
        AFFINITY_TOO_LOW
    }

    public record Evaluation(Status status) {
        public Evaluation {
            Objects.requireNonNull(status, "status");
        }

        public boolean satisfied() {
            return status == Status.SATISFIED;
        }
    }
}
