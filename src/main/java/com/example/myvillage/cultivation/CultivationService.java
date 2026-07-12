package com.example.myvillage.cultivation;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.SpiritualElementDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.network.CultivationSnapshotPayload;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class CultivationService {
    private CultivationService() {
    }

    public static CultivationProfile getProfile(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return player.getData(CultivationAttachments.PROFILE.get());
    }

    public static Result replaceProfile(ServerPlayer player, CultivationProfile replacement) {
        Objects.requireNonNull(player, "player");
        CultivationProfile oldProfile = getProfile(player);
        if (replacement == null) {
            return Result.failure("Replacement cultivation profile must not be null", oldProfile);
        }
        Optional<String> referenceError = validateChangedReferences(
                player.registryAccess(), oldProfile, replacement);
        if (referenceError.isPresent()) {
            return Result.failure(referenceError.get(), oldProfile);
        }
        return commit(player, replacement, "replaced cultivation profile");
    }

    public static Result updateProfile(ServerPlayer player, UnaryOperator<CultivationProfile> update) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(update, "update");
        CultivationProfile oldProfile = getProfile(player);
        try {
            CultivationProfile replacement = Objects.requireNonNull(
                    update.apply(oldProfile), "Cultivation profile update returned null");
            Optional<String> referenceError = validateChangedReferences(
                    player.registryAccess(), oldProfile, replacement);
            if (referenceError.isPresent()) {
                return Result.failure(referenceError.get(), oldProfile);
            }
            return commit(player, replacement, "updated cultivation profile");
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Result.failure(message(exception), oldProfile);
        }
    }

    public static Result resetProfile(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        CultivationProfile oldProfile = getProfile(player);
        Optional<String> realmError = validateRealmAndStage(
                player.registryAccess(),
                CultivationProfile.DEFAULT_REALM_ID,
                CultivationProfile.DEFAULT_STAGE_ID);
        if (realmError.isPresent()) {
            return Result.failure("Cannot reset profile: " + realmError.get(), oldProfile);
        }
        return commit(player, CultivationProfile.defaultProfile(), "reset cultivation profile");
    }

    public static Result setRealmAndStage(
            ServerPlayer player,
            ResourceLocation realmId,
            ResourceLocation stageId) {
        Objects.requireNonNull(player, "player");
        CultivationProfile oldProfile = getProfile(player);
        if (realmId == null || stageId == null) {
            return Result.failure("Realm and stage ids must not be null", oldProfile);
        }
        Optional<String> error = validateRealmAndStage(player.registryAccess(), realmId, stageId);
        if (error.isPresent()) {
            return Result.failure(error.get(), oldProfile);
        }
        return constructAndCommit(
                player,
                oldProfile,
                profile -> profile.withRealmAndStage(realmId, stageId),
                "set realm=" + realmId + " stage=" + stageId);
    }

    public static Result setProgress(ServerPlayer player, long amount) {
        return updateScalar(
                player,
                profile -> profile.withCultivationProgress(amount),
                "set cultivation progress=" + amount);
    }

    public static Result setStability(ServerPlayer player, int amount) {
        return updateScalar(
                player,
                profile -> profile.withStability(amount),
                "set stability=" + amount);
    }

    public static Result setSpiritualPower(ServerPlayer player, long amount) {
        return updateScalar(
                player,
                profile -> profile.withCurrentSpiritualPower(amount),
                "set current spiritual power=" + amount);
    }

    public static Result setSpiritualRoot(ServerPlayer player, SpiritualRoot root) {
        Objects.requireNonNull(player, "player");
        CultivationProfile oldProfile = getProfile(player);
        if (root == null) {
            return Result.failure("Spiritual root must not be null", oldProfile);
        }
        Optional<Registry<SpiritualElementDefinition>> registry =
                player.registryAccess().registry(ModCultivationRegistries.SPIRITUAL_ELEMENTS);
        if (registry.isEmpty()) {
            return Result.failure("Cultivation spiritual-element registry is unavailable", oldProfile);
        }
        for (ResourceLocation elementId : root.affinitiesBasisPoints().keySet()) {
            if (!registry.get().containsKey(elementId)) {
                return Result.failure("Unknown spiritual element: " + elementId, oldProfile);
            }
        }
        return constructAndCommit(
                player,
                oldProfile,
                profile -> profile.withSpiritualRoot(Optional.of(root)),
                "set spiritual root");
    }

    public static Result clearSpiritualRoot(ServerPlayer player) {
        return updateScalar(
                player,
                profile -> profile.withSpiritualRoot(Optional.empty()),
                "cleared spiritual root");
    }

    public static Result learnTechnique(ServerPlayer player, ResourceLocation techniqueId) {
        Objects.requireNonNull(player, "player");
        CultivationProfile oldProfile = getProfile(player);
        Optional<String> registryError = validateTechnique(player.registryAccess(), techniqueId);
        if (registryError.isPresent()) {
            return Result.failure(registryError.get(), oldProfile);
        }
        if (oldProfile.learnedTechniques().containsKey(techniqueId)) {
            return Result.failure("Technique is already learned: " + techniqueId, oldProfile);
        }
        return constructAndCommit(
                player,
                oldProfile,
                profile -> profile.learnTechnique(techniqueId),
                "learned technique " + techniqueId);
    }

    public static Result forgetTechnique(ServerPlayer player, ResourceLocation techniqueId) {
        Objects.requireNonNull(player, "player");
        CultivationProfile oldProfile = getProfile(player);
        Optional<String> registryError = validateTechnique(player.registryAccess(), techniqueId);
        if (registryError.isPresent()) {
            return Result.failure(registryError.get(), oldProfile);
        }
        if (!oldProfile.learnedTechniques().containsKey(techniqueId)) {
            return Result.failure("Technique is not learned: " + techniqueId, oldProfile);
        }
        return constructAndCommit(
                player,
                oldProfile,
                profile -> profile.forgetTechnique(techniqueId),
                "forgot technique " + techniqueId);
    }

    public static Result setTechniqueMastery(
            ServerPlayer player,
            ResourceLocation techniqueId,
            long masteryPoints) {
        Objects.requireNonNull(player, "player");
        CultivationProfile oldProfile = getProfile(player);
        Optional<String> registryError = validateTechnique(player.registryAccess(), techniqueId);
        if (registryError.isPresent()) {
            return Result.failure(registryError.get(), oldProfile);
        }
        if (!oldProfile.learnedTechniques().containsKey(techniqueId)) {
            return Result.failure("Technique is not learned: " + techniqueId, oldProfile);
        }
        if (masteryPoints < 0) {
            return Result.failure("Technique mastery must be non-negative, got " + masteryPoints, oldProfile);
        }
        return constructAndCommit(
                player,
                oldProfile,
                profile -> profile.withTechniqueMastery(techniqueId, masteryPoints),
                "set technique mastery " + techniqueId + "=" + masteryPoints);
    }

    public static void syncToClient(ServerPlayer player) {
        syncToClient(player, getProfile(player));
    }

    private static void syncToClient(ServerPlayer player, CultivationProfile profile) {
        PacketDistributor.sendToPlayer(player, new CultivationSnapshotPayload(profile));
    }

    private static Result updateScalar(
            ServerPlayer player,
            UnaryOperator<CultivationProfile> update,
            String successMessage) {
        Objects.requireNonNull(player, "player");
        CultivationProfile oldProfile = getProfile(player);
        return constructAndCommit(player, oldProfile, update, successMessage);
    }

    private static Result constructAndCommit(
            ServerPlayer player,
            CultivationProfile oldProfile,
            UnaryOperator<CultivationProfile> update,
            String successMessage) {
        try {
            CultivationProfile replacement = Objects.requireNonNull(
                    update.apply(oldProfile), "Cultivation profile update returned null");
            return commit(player, replacement, successMessage);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Result.failure(message(exception), oldProfile);
        }
    }

    private static Result commit(
            ServerPlayer player,
            CultivationProfile replacement,
            String successMessage) {
        player.setData(CultivationAttachments.PROFILE.get(), replacement);
        syncToClient(player, replacement);
        return Result.success(successMessage, replacement);
    }

    private static Optional<String> validateChangedReferences(
            RegistryAccess registryAccess,
            CultivationProfile oldProfile,
            CultivationProfile replacement) {
        if (!oldProfile.realmId().equals(replacement.realmId())
                || !oldProfile.stageId().equals(replacement.stageId())) {
            Optional<String> realmError = validateRealmAndStage(
                    registryAccess, replacement.realmId(), replacement.stageId());
            if (realmError.isPresent()) {
                return realmError;
            }
        }

        if (!oldProfile.spiritualRoot().equals(replacement.spiritualRoot())
                && replacement.spiritualRoot().isPresent()) {
            Optional<Registry<SpiritualElementDefinition>> elements =
                    registryAccess.registry(ModCultivationRegistries.SPIRITUAL_ELEMENTS);
            if (elements.isEmpty()) {
                return Optional.of("Cultivation spiritual-element registry is unavailable");
            }
            for (ResourceLocation elementId : replacement.spiritualRoot()
                    .orElseThrow().affinitiesBasisPoints().keySet()) {
                if (!elements.get().containsKey(elementId)) {
                    return Optional.of("Unknown spiritual element: " + elementId);
                }
            }
        }

        for (Map.Entry<ResourceLocation, TechniqueProgress> entry
                : replacement.learnedTechniques().entrySet()) {
            if (!Objects.equals(oldProfile.learnedTechniques().get(entry.getKey()), entry.getValue())) {
                Optional<String> techniqueError = validateTechnique(registryAccess, entry.getKey());
                if (techniqueError.isPresent()) {
                    return techniqueError;
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<String> validateRealmAndStage(
            RegistryAccess registryAccess,
            ResourceLocation realmId,
            ResourceLocation stageId) {
        Optional<Registry<RealmDefinition>> registry =
                registryAccess.registry(ModCultivationRegistries.REALMS);
        if (registry.isEmpty()) {
            return Optional.of("Cultivation realm registry is unavailable");
        }
        RealmDefinition realm = registry.get().get(realmId);
        if (realm == null) {
            return Optional.of("Unknown cultivation realm: " + realmId);
        }
        if (!realm.containsStage(stageId)) {
            return Optional.of("Stage " + stageId + " does not belong to realm " + realmId);
        }
        return Optional.empty();
    }

    private static Optional<String> validateTechnique(
            RegistryAccess registryAccess,
            ResourceLocation techniqueId) {
        if (techniqueId == null) {
            return Optional.of("Technique id must not be null");
        }
        Optional<Registry<TechniqueDefinition>> registry =
                registryAccess.registry(ModCultivationRegistries.TECHNIQUES);
        if (registry.isEmpty()) {
            return Optional.of("Cultivation technique registry is unavailable");
        }
        if (!registry.get().containsKey(techniqueId)) {
            return Optional.of("Unknown cultivation technique: " + techniqueId);
        }
        return Optional.empty();
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    public record Result(boolean success, String message, CultivationProfile profile) {
        public Result {
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(profile, "profile");
        }

        private static Result success(String message, CultivationProfile profile) {
            return new Result(true, message, profile);
        }

        private static Result failure(String message, CultivationProfile profile) {
            return new Result(false, message, profile);
        }
    }
}
