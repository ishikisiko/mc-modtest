package com.example.myvillage.cultivation.technique;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class TechniqueInheritanceService {
    private TechniqueInheritanceService() {
    }

    public static Outcome inheritBasicBreathing(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        CultivationProfile current = CultivationService.getProfile(player);
        Optional<Registry<TechniqueDefinition>> techniques =
                player.registryAccess().registry(ModCultivationRegistries.TECHNIQUES);
        TechniqueDefinition definition = techniques
                .map(registry -> registry.get(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID))
                .orElse(null);
        Optional<Registry<RealmDefinition>> realms =
                player.registryAccess().registry(ModCultivationRegistries.REALMS);
        Function<ResourceLocation, RealmDefinition> realmLookup = realms
                .<Function<ResourceLocation, RealmDefinition>>map(registry -> registry::get)
                .orElse(id -> null);
        Predicate<ResourceLocation> elementAvailable = player.registryAccess()
                .registry(ModCultivationRegistries.SPIRITUAL_ELEMENTS)
                .<Predicate<ResourceLocation>>map(registry -> registry::containsKey)
                .orElse(id -> false);
        return inheritBasicBreathing(
                current,
                definition,
                realmLookup,
                elementAvailable,
                replacement -> CultivationService.replaceProfile(player, replacement).success());
    }

    public static Outcome inheritBasicBreathing(
            CultivationProfile current,
            TechniqueDefinition definition,
            Function<ResourceLocation, RealmDefinition> realmLookup,
            ProfileCommitter committer) {
        return inheritBasicBreathing(current, definition, realmLookup, ignored -> true, committer);
    }

    public static Outcome inheritBasicBreathing(
            CultivationProfile current,
            TechniqueDefinition definition,
            Function<ResourceLocation, RealmDefinition> realmLookup,
            Predicate<ResourceLocation> elementAvailable,
            ProfileCommitter committer) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(realmLookup, "realmLookup");
        Objects.requireNonNull(elementAvailable, "elementAvailable");
        Objects.requireNonNull(committer, "committer");
        ResourceLocation techniqueId = ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID;
        if (definition == null) {
            return new Outcome(Status.TECHNIQUE_NOT_REGISTERED, current);
        }
        if (current.learnedTechniques().containsKey(techniqueId)) {
            return new Outcome(Status.ALREADY_LEARNED, current);
        }
        if (current.spiritualRoot().isEmpty()) {
            return new Outcome(Status.NOT_AWAKENED, current);
        }
        if (!TechniqueRequirementEvaluator.evaluate(
                current, definition, realmLookup, elementAvailable).satisfied()) {
            return new Outcome(Status.REQUIREMENTS_NOT_MET, current);
        }

        try {
            CultivationProfile replacement = current.learnTechnique(techniqueId);
            if (!committer.commit(replacement)) {
                return new Outcome(Status.UPDATE_REJECTED, current);
            }
            return new Outcome(Status.SUCCESS, replacement);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return new Outcome(Status.UPDATE_REJECTED, current);
        }
    }

    public enum Status {
        SUCCESS,
        NOT_AWAKENED,
        REQUIREMENTS_NOT_MET,
        TECHNIQUE_NOT_REGISTERED,
        ALREADY_LEARNED,
        UPDATE_REJECTED
    }

    public record Outcome(Status status, CultivationProfile profile) {
        public Outcome {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(profile, "profile");
        }

        public boolean success() {
            return status == Status.SUCCESS;
        }
    }

    @FunctionalInterface
    public interface ProfileCommitter {
        boolean commit(CultivationProfile replacement);
    }
}
