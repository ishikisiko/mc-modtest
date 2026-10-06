package com.example.myvillage.cultivation.study;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.technique.TechniqueRequirementEvaluator;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The item-side checks before a study session may start, in the brief's order: a valid manual, a technique not
 * yet learned, an awakened root and the technique's requirements, the previous technique of its chain, and no
 * session already running. The physical checks (ground, mount, swimming, flight, sleep, recent damage, game
 * mode, lifespan) follow in {@code MeditationManager.requestStudy}, shared with meditation.
 */
public final class StudyStart {
    private StudyStart() {
    }

    public enum Refusal {
        INVALID_MANUAL,
        ALREADY_LEARNED,
        NOT_AWAKENED,
        REALM_TOO_LOW,
        AFFINITY_TOO_LOW,
        REQUIREMENTS_UNAVAILABLE,
        PREVIOUS_REQUIRED,
        BUSY
    }

    /** The first failing check, or empty when the session may be requested. */
    public static Optional<Refusal> refusal(
            Optional<ResourceLocation> manualTechnique,
            CultivationProfile profile,
            Function<ResourceLocation, TechniqueDefinition> techniques,
            Function<ResourceLocation, RealmDefinition> realms,
            Predicate<ResourceLocation> elementAvailable,
            boolean sessionRunning) {
        Objects.requireNonNull(manualTechnique, "manualTechnique");
        if (manualTechnique.isEmpty()) {
            return Optional.of(Refusal.INVALID_MANUAL);
        }
        Optional<Refusal> learn = learnRefusal(
                manualTechnique.get(), profile, techniques, realms, elementAvailable);
        if (learn.isPresent()) {
            return learn;
        }
        return sessionRunning ? Optional.of(Refusal.BUSY) : Optional.empty();
    }

    /**
     * Whether {@code profile} may learn {@code techniqueId} by study: registered, not learned, awakened,
     * requirements met, and the previous technique of its chain learned. Re-checked at every settlement.
     */
    public static Optional<Refusal> learnRefusal(
            ResourceLocation techniqueId,
            CultivationProfile profile,
            Function<ResourceLocation, TechniqueDefinition> techniques,
            Function<ResourceLocation, RealmDefinition> realms,
            Predicate<ResourceLocation> elementAvailable) {
        Objects.requireNonNull(techniqueId, "techniqueId");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(techniques, "techniques");
        Objects.requireNonNull(realms, "realms");
        Objects.requireNonNull(elementAvailable, "elementAvailable");
        TechniqueDefinition definition = techniques.apply(techniqueId);
        if (definition == null) {
            return Optional.of(Refusal.INVALID_MANUAL);
        }
        if (profile.learnedTechniques().containsKey(techniqueId)) {
            return Optional.of(Refusal.ALREADY_LEARNED);
        }
        if (profile.spiritualRoot().isEmpty()) {
            return Optional.of(Refusal.NOT_AWAKENED);
        }
        TechniqueRequirementEvaluator.Status status = TechniqueRequirementEvaluator
                .evaluate(profile, definition, realms, elementAvailable).status();
        switch (status) {
            case SATISFIED -> {
            }
            case REALM_TOO_LOW, STAGE_TOO_LOW -> {
                return Optional.of(Refusal.REALM_TOO_LOW);
            }
            case AFFINITY_TOO_LOW, ROOT_REQUIRED -> {
                return Optional.of(Refusal.AFFINITY_TOO_LOW);
            }
            default -> {
                return Optional.of(Refusal.REQUIREMENTS_UNAVAILABLE);
            }
        }
        Optional<ResourceLocation> previous = definition.previous();
        if (previous.isPresent() && !profile.learnedTechniques().containsKey(previous.get())) {
            return Optional.of(Refusal.PREVIOUS_REQUIRED);
        }
        return Optional.empty();
    }
}
