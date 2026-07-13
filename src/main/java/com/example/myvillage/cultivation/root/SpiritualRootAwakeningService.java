package com.example.myvillage.cultivation.root;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.SpiritualElementDefinition;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SpiritualRootAwakeningService {
    private SpiritualRootAwakeningService() {
    }

    public static Outcome awaken(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        CultivationProfile current = CultivationService.getProfile(player);
        if (current.spiritualRoot().isPresent()) {
            return new Outcome(Status.ALREADY_AWAKENED, current);
        }
        if (!validAwakeningState(current)) {
            return new Outcome(Status.INVALID_PROFILE_STATE, current);
        }

        Optional<Registry<SpiritualElementDefinition>> registry =
                player.registryAccess().registry(ModCultivationRegistries.SPIRITUAL_ELEMENTS);
        if (registry.isEmpty()) {
            return new Outcome(Status.NO_ELIGIBLE_ELEMENTS, current);
        }
        List<SpiritualRootGenerator.ElementCandidate> candidates = registry.get().keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .map(id -> new SpiritualRootGenerator.ElementCandidate(
                        id, registry.get().get(id).awakeningWeight()))
                .toList();
        long overworldSeed = player.getServer().overworld().getSeed();
        return awaken(
                current,
                overworldSeed,
                player.getUUID(),
                candidates,
                replacement -> CultivationService.replaceProfile(player, replacement).success());
    }

    public static Outcome awaken(
            CultivationProfile current,
            long overworldSeed,
            UUID playerUuid,
            List<SpiritualRootGenerator.ElementCandidate> candidates,
            ProfileCommitter committer) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(committer, "committer");
        if (current.spiritualRoot().isPresent()) {
            return new Outcome(Status.ALREADY_AWAKENED, current);
        }
        if (!validAwakeningState(current)) {
            return new Outcome(Status.INVALID_PROFILE_STATE, current);
        }
        if (candidates.stream().noneMatch(candidate -> candidate.awakeningWeight() > 0)) {
            return new Outcome(Status.NO_ELIGIBLE_ELEMENTS, current);
        }

        try {
            Optional<com.example.myvillage.cultivation.SpiritualRoot> generated =
                    SpiritualRootGenerator.generate(overworldSeed, playerUuid, candidates);
            if (generated.isEmpty()) {
                return new Outcome(Status.NO_ELIGIBLE_ELEMENTS, current);
            }
            CultivationProfile replacement = current.withSpiritualRootAndStage(
                    ModCultivationRegistries.MORTAL_REALM_ID,
                    ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID,
                    generated.orElseThrow());
            if (!committer.commit(replacement)) {
                return new Outcome(Status.UPDATE_REJECTED, current);
            }
            return new Outcome(Status.SUCCESS, replacement);
        } catch (ArithmeticException | IllegalArgumentException | IllegalStateException exception) {
            return new Outcome(Status.GENERATION_FAILED, current);
        }
    }

    private static boolean validAwakeningState(CultivationProfile profile) {
        return profile.realmId().equals(ModCultivationRegistries.MORTAL_REALM_ID)
                && (profile.stageId().equals(ModCultivationRegistries.MORTAL_UNAWAKENED_STAGE_ID)
                || profile.stageId().equals(ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID));
    }

    public enum Status {
        SUCCESS,
        ALREADY_AWAKENED,
        INVALID_PROFILE_STATE,
        NO_ELIGIBLE_ELEMENTS,
        GENERATION_FAILED,
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
