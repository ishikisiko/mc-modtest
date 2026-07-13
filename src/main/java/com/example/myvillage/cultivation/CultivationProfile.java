package com.example.myvillage.cultivation;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record CultivationProfile(
        int schemaVersion,
        ResourceLocation realmId,
        ResourceLocation stageId,
        long cultivationProgress,
        int stability,
        long currentSpiritualPower,
        Optional<SpiritualRoot> spiritualRoot,
        Map<ResourceLocation, TechniqueProgress> learnedTechniques) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final ResourceLocation DEFAULT_REALM_ID = ModCultivationRegistries.MORTAL_REALM_ID;
    public static final ResourceLocation DEFAULT_STAGE_ID = ModCultivationRegistries.MORTAL_UNAWAKENED_STAGE_ID;
    public static final CultivationProfile DEFAULT = new CultivationProfile(
            CURRENT_SCHEMA_VERSION,
            DEFAULT_REALM_ID,
            DEFAULT_STAGE_ID,
            0,
            0,
            0,
            Optional.empty(),
            Map.of());

    private static final Codec<Map<ResourceLocation, TechniqueProgress>> LEARNED_TECHNIQUES_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, TechniqueProgress.CODEC);

    private static final Codec<SerializedProfile> V1_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(SerializedProfile::schemaVersion),
            ResourceLocation.CODEC.fieldOf("realm_id").forGetter(SerializedProfile::realmId),
            ResourceLocation.CODEC.fieldOf("stage_id").forGetter(SerializedProfile::stageId),
            Codec.LONG.fieldOf("cultivation_progress").forGetter(SerializedProfile::cultivationProgress),
            Codec.INT.fieldOf("stability").forGetter(SerializedProfile::stability),
            Codec.LONG.fieldOf("current_spiritual_power").forGetter(SerializedProfile::currentSpiritualPower),
            SpiritualRoot.CODEC.optionalFieldOf("spiritual_root").forGetter(SerializedProfile::spiritualRoot),
            LEARNED_TECHNIQUES_CODEC.fieldOf("learned_techniques").forGetter(SerializedProfile::learnedTechniques)
    ).apply(instance, SerializedProfile::new));

    public static final Codec<CultivationProfile> CODEC = V1_CODEC
            .comapFlatMap(SerializedProfile::decode, CultivationProfile::serialize);

    public CultivationProfile {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported cultivation profile schema version " + schemaVersion
                            + "; expected " + CURRENT_SCHEMA_VERSION);
        }
        Objects.requireNonNull(realmId, "realmId");
        Objects.requireNonNull(stageId, "stageId");
        if (cultivationProgress < 0) {
            throw new IllegalArgumentException(
                    "Cultivation progress must be non-negative, got " + cultivationProgress);
        }
        if (stability < 0 || stability > 100) {
            throw new IllegalArgumentException("Stability must be in 0..100, got " + stability);
        }
        if (currentSpiritualPower < 0) {
            throw new IllegalArgumentException(
                    "Current spiritual power must be non-negative, got " + currentSpiritualPower);
        }
        spiritualRoot = Objects.requireNonNull(spiritualRoot, "spiritualRoot");
        learnedTechniques = immutableTechniqueMap(learnedTechniques);
    }

    public static CultivationProfile defaultProfile() {
        return DEFAULT;
    }

    public boolean awakened() {
        return spiritualRoot.isPresent();
    }

    public boolean isAwakened() {
        return awakened();
    }

    public CultivationProfile withRealmAndStage(ResourceLocation realm, ResourceLocation stage) {
        return copy(realm, stage, cultivationProgress, stability, currentSpiritualPower, spiritualRoot, learnedTechniques);
    }

    public CultivationProfile withCultivationProgress(long progress) {
        return copy(realmId, stageId, progress, stability, currentSpiritualPower, spiritualRoot, learnedTechniques);
    }

    public CultivationProfile withStability(int newStability) {
        return copy(realmId, stageId, cultivationProgress, newStability, currentSpiritualPower, spiritualRoot, learnedTechniques);
    }

    public CultivationProfile withCurrentSpiritualPower(long power) {
        return copy(realmId, stageId, cultivationProgress, stability, power, spiritualRoot, learnedTechniques);
    }

    public CultivationProfile withSpiritualRoot(Optional<SpiritualRoot> root) {
        return copy(realmId, stageId, cultivationProgress, stability, currentSpiritualPower, root, learnedTechniques);
    }

    public CultivationProfile withSpiritualRoot(SpiritualRoot root) {
        return withSpiritualRoot(Optional.of(Objects.requireNonNull(root, "root")));
    }

    public CultivationProfile withSpiritualRootAndStage(
            ResourceLocation realm,
            ResourceLocation stage,
            SpiritualRoot root) {
        return copy(
                Objects.requireNonNull(realm, "realm"),
                Objects.requireNonNull(stage, "stage"),
                cultivationProgress,
                stability,
                currentSpiritualPower,
                Optional.of(Objects.requireNonNull(root, "root")),
                learnedTechniques);
    }

    public CultivationProfile withoutSpiritualRoot() {
        return withSpiritualRoot(Optional.empty());
    }

    public CultivationProfile withLearnedTechniques(Map<ResourceLocation, TechniqueProgress> techniques) {
        return copy(realmId, stageId, cultivationProgress, stability, currentSpiritualPower, spiritualRoot, techniques);
    }

    public CultivationProfile learnTechnique(ResourceLocation techniqueId) {
        Objects.requireNonNull(techniqueId, "techniqueId");
        if (learnedTechniques.containsKey(techniqueId)) {
            throw new IllegalArgumentException("Technique is already learned: " + techniqueId);
        }
        LinkedHashMap<ResourceLocation, TechniqueProgress> techniques =
                new LinkedHashMap<>(learnedTechniques);
        techniques.put(techniqueId, TechniqueProgress.ZERO);
        return withLearnedTechniques(techniques);
    }

    public CultivationProfile forgetTechnique(ResourceLocation techniqueId) {
        Objects.requireNonNull(techniqueId, "techniqueId");
        if (!learnedTechniques.containsKey(techniqueId)) {
            throw new IllegalArgumentException("Technique is not learned: " + techniqueId);
        }
        LinkedHashMap<ResourceLocation, TechniqueProgress> techniques =
                new LinkedHashMap<>(learnedTechniques);
        techniques.remove(techniqueId);
        return withLearnedTechniques(techniques);
    }

    public CultivationProfile withTechniqueMastery(ResourceLocation techniqueId, long masteryPoints) {
        Objects.requireNonNull(techniqueId, "techniqueId");
        if (!learnedTechniques.containsKey(techniqueId)) {
            throw new IllegalArgumentException("Technique is not learned: " + techniqueId);
        }
        LinkedHashMap<ResourceLocation, TechniqueProgress> techniques =
                new LinkedHashMap<>(learnedTechniques);
        techniques.put(techniqueId, new TechniqueProgress(masteryPoints));
        return withLearnedTechniques(techniques);
    }

    private CultivationProfile copy(
            ResourceLocation realm,
            ResourceLocation stage,
            long progress,
            int newStability,
            long power,
            Optional<SpiritualRoot> root,
            Map<ResourceLocation, TechniqueProgress> techniques) {
        return new CultivationProfile(
                schemaVersion, realm, stage, progress, newStability, power, root, techniques);
    }

    private static Map<ResourceLocation, TechniqueProgress> immutableTechniqueMap(
            Map<ResourceLocation, TechniqueProgress> techniques) {
        Objects.requireNonNull(techniques, "learnedTechniques");
        LinkedHashMap<ResourceLocation, TechniqueProgress> sorted = new LinkedHashMap<>();
        techniques.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> sorted.put(
                        Objects.requireNonNull(entry.getKey(), "technique id"),
                        Objects.requireNonNull(entry.getValue(), "technique progress for " + entry.getKey())));
        return Collections.unmodifiableMap(sorted);
    }

    private SerializedProfile serialize() {
        return new SerializedProfile(
                schemaVersion,
                realmId,
                stageId,
                cultivationProgress,
                stability,
                currentSpiritualPower,
                spiritualRoot,
                learnedTechniques);
    }

    private record SerializedProfile(
            int schemaVersion,
            ResourceLocation realmId,
            ResourceLocation stageId,
            long cultivationProgress,
            int stability,
            long currentSpiritualPower,
            Optional<SpiritualRoot> spiritualRoot,
            Map<ResourceLocation, TechniqueProgress> learnedTechniques) {
        private DataResult<CultivationProfile> decode() {
            try {
                return DataResult.success(new CultivationProfile(
                        schemaVersion,
                        realmId,
                        stageId,
                        cultivationProgress,
                        stability,
                        currentSpiritualPower,
                        spiritualRoot,
                        learnedTechniques));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
