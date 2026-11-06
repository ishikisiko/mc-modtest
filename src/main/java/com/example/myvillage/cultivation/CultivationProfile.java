package com.example.myvillage.cultivation;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueCategory;
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
        int spiritualAffinity,
        long lifespanConsumedTicks,
        long meditationQiReserve,
        Optional<SpiritualRoot> spiritualRoot,
        Map<ResourceLocation, TechniqueProgress> learnedTechniques,
        Optional<ResourceLocation> activeCoreTechnique, int foundationQuality, int goldenCoreQuality) {
    public static final int CURRENT_SCHEMA_VERSION = 5;
    public static final int DEFAULT_SPIRITUAL_AFFINITY = 10;
    public static final ResourceLocation DEFAULT_REALM_ID = ModCultivationRegistries.MORTAL_REALM_ID;
    public static final ResourceLocation DEFAULT_STAGE_ID = ModCultivationRegistries.MORTAL_UNAWAKENED_STAGE_ID;
    public static final CultivationProfile DEFAULT = new CultivationProfile(
            CURRENT_SCHEMA_VERSION,
            DEFAULT_REALM_ID,
            DEFAULT_STAGE_ID,
            0,
            0,
            0,
            DEFAULT_SPIRITUAL_AFFINITY,
            0,
            0,
            Optional.empty(),
            Map.of(),
            Optional.empty());

    private static final Codec<Map<ResourceLocation, TechniqueProgress>> LEARNED_TECHNIQUES_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, TechniqueProgress.CODEC);

    /** 当前开发格式保存筑基来源与金丹品质，不维护历史档案迁移。 */
    private static final Codec<SerializedV5Profile> V5_CODEC = RecordCodecBuilder.<SerializedV5Profile>create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(SerializedV5Profile::schemaVersion),
            ResourceLocation.CODEC.fieldOf("realm_id").forGetter(SerializedV5Profile::realmId),
            ResourceLocation.CODEC.fieldOf("stage_id").forGetter(SerializedV5Profile::stageId),
            Codec.LONG.fieldOf("cultivation_progress").forGetter(SerializedV5Profile::cultivationProgress),
            Codec.INT.fieldOf("stability").forGetter(SerializedV5Profile::stability),
            Codec.LONG.fieldOf("current_spiritual_power").forGetter(SerializedV5Profile::currentSpiritualPower),
            Codec.INT.fieldOf("spiritual_affinity").forGetter(SerializedV5Profile::spiritualAffinity),
            Codec.LONG.fieldOf("lifespan_consumed_ticks").forGetter(SerializedV5Profile::lifespanConsumedTicks),
            Codec.LONG.fieldOf("meditation_qi_reserve").forGetter(SerializedV5Profile::meditationQiReserve),
            SpiritualRoot.CODEC.optionalFieldOf("spiritual_root").forGetter(SerializedV5Profile::spiritualRoot),
            LEARNED_TECHNIQUES_CODEC.fieldOf("learned_techniques").forGetter(SerializedV5Profile::learnedTechniques),
            ResourceLocation.CODEC.optionalFieldOf("active_core_technique")
                    .forGetter(SerializedV5Profile::activeCoreTechnique),
            Codec.intRange(0,4).optionalFieldOf("foundation_quality",0).forGetter(SerializedV5Profile::foundationQuality),
            Codec.intRange(0,3).optionalFieldOf("golden_core_quality",0).forGetter(SerializedV5Profile::goldenCoreQuality)
    ).apply(instance, SerializedV5Profile::new)).validate(SerializedV5Profile::requireVersion);

    public static final Codec<CultivationProfile> CODEC = V5_CODEC.comapFlatMap(
            SerializedV5Profile::decode, CultivationProfile::serializeV5);

    public CultivationProfile(int schemaVersion, ResourceLocation realmId, ResourceLocation stageId,
            long cultivationProgress,int stability,long currentSpiritualPower,int spiritualAffinity,
            long lifespanConsumedTicks,long meditationQiReserve,Optional<SpiritualRoot> spiritualRoot,
            Map<ResourceLocation,TechniqueProgress> learnedTechniques,Optional<ResourceLocation> activeCoreTechnique) {
        this(schemaVersion,realmId,stageId,cultivationProgress,stability,currentSpiritualPower,spiritualAffinity,
            lifespanConsumedTicks,meditationQiReserve,spiritualRoot,learnedTechniques,activeCoreTechnique,0,0);
    }
    public CultivationProfile(int schemaVersion, ResourceLocation realmId, ResourceLocation stageId,
            long cultivationProgress, int stability, long currentSpiritualPower, int spiritualAffinity,
            long lifespanConsumedTicks, long meditationQiReserve, Optional<SpiritualRoot> spiritualRoot,
            Map<ResourceLocation, TechniqueProgress> learnedTechniques, Optional<ResourceLocation> activeCoreTechnique,
            int foundationQuality) {
        this(schemaVersion, realmId, stageId, cultivationProgress, stability, currentSpiritualPower,
                spiritualAffinity, lifespanConsumedTicks, meditationQiReserve, spiritualRoot, learnedTechniques,
                activeCoreTechnique, foundationQuality, 0);
    }
    public CultivationProfile withGoldenCoreQuality(int quality) {
        return new CultivationProfile(schemaVersion, realmId, stageId, cultivationProgress, stability,
                currentSpiritualPower, spiritualAffinity, lifespanConsumedTicks, meditationQiReserve,
                spiritualRoot, learnedTechniques, activeCoreTechnique, foundationQuality, quality);
    }
    public CultivationProfile withFoundationQuality(int quality) {
        return new CultivationProfile(schemaVersion,realmId,stageId,cultivationProgress,stability,currentSpiritualPower,
            spiritualAffinity,lifespanConsumedTicks,meditationQiReserve,spiritualRoot,learnedTechniques,activeCoreTechnique,quality,goldenCoreQuality);
    }
    public CultivationProfile {
        if (goldenCoreQuality < 0 || goldenCoreQuality > 3) throw new IllegalArgumentException("Invalid golden core quality");
        if(foundationQuality<0 || foundationQuality>4) throw new IllegalArgumentException("Invalid foundation quality");
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
        if (stability < 0) {
            throw new IllegalArgumentException("Stability must be non-negative, got " + stability);
        }
        if (currentSpiritualPower < 0) {
            throw new IllegalArgumentException(
                    "Current spiritual power must be non-negative, got " + currentSpiritualPower);
        }
        if (spiritualAffinity < 0) {
            throw new IllegalArgumentException(
                    "Spiritual affinity must be non-negative, got " + spiritualAffinity);
        }
        if (lifespanConsumedTicks < 0) {
            throw new IllegalArgumentException(
                    "Lifespan consumed ticks must be non-negative, got " + lifespanConsumedTicks);
        }
        if (meditationQiReserve < 0) {
            throw new IllegalArgumentException(
                    "Meditation qi reserve must be non-negative, got " + meditationQiReserve);
        }
        spiritualRoot = Objects.requireNonNull(spiritualRoot, "spiritualRoot");
        learnedTechniques = immutableTechniqueMap(learnedTechniques);
        activeCoreTechnique = Objects.requireNonNull(activeCoreTechnique, "activeCoreTechnique");
        if (activeCoreTechnique.isPresent() && !learnedTechniques.containsKey(activeCoreTechnique.get())) {
            throw new IllegalArgumentException(
                    "Active core technique is not learned: " + activeCoreTechnique.get());
        }
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
        return copy(realm, stage, cultivationProgress, stability, currentSpiritualPower, spiritualAffinity,
                lifespanConsumedTicks, meditationQiReserve, spiritualRoot, learnedTechniques);
    }

    public CultivationProfile withCultivationProgress(long progress) {
        return copy(realmId, stageId, progress, stability, currentSpiritualPower, spiritualAffinity,
                lifespanConsumedTicks, meditationQiReserve, spiritualRoot, learnedTechniques);
    }

    public CultivationProfile withStability(int newStability) {
        return copy(realmId, stageId, cultivationProgress, newStability, currentSpiritualPower, spiritualAffinity,
                lifespanConsumedTicks, meditationQiReserve, spiritualRoot, learnedTechniques);
    }

    public CultivationProfile withCurrentSpiritualPower(long power) {
        return copy(realmId, stageId, cultivationProgress, stability, power, spiritualAffinity,
                lifespanConsumedTicks, meditationQiReserve, spiritualRoot, learnedTechniques);
    }

    public CultivationProfile withSpiritualAffinity(int affinity) {
        return copy(realmId, stageId, cultivationProgress, stability, currentSpiritualPower, affinity,
                lifespanConsumedTicks, meditationQiReserve, spiritualRoot, learnedTechniques);
    }

    public CultivationProfile withLifespanConsumedTicks(long consumedTicks) {
        return copy(realmId, stageId, cultivationProgress, stability, currentSpiritualPower, spiritualAffinity,
                consumedTicks, meditationQiReserve, spiritualRoot, learnedTechniques);
    }

    public CultivationProfile withMeditationQiReserve(long reserve) {
        return copy(realmId, stageId, cultivationProgress, stability, currentSpiritualPower, spiritualAffinity,
                lifespanConsumedTicks, reserve, spiritualRoot, learnedTechniques);
    }

    public CultivationProfile withSpiritualRoot(Optional<SpiritualRoot> root) {
        return copy(realmId, stageId, cultivationProgress, stability, currentSpiritualPower, spiritualAffinity,
                lifespanConsumedTicks, meditationQiReserve, root, learnedTechniques);
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
                spiritualAffinity,
                lifespanConsumedTicks,
                meditationQiReserve,
                Optional.of(Objects.requireNonNull(root, "root")),
                learnedTechniques);
    }

    public CultivationProfile withoutSpiritualRoot() {
        return withSpiritualRoot(Optional.empty());
    }

    /**
     * Replaces the learned-technique map. The running core technique is kept while it is still
     * learned and cleared otherwise.
     */
    public CultivationProfile withLearnedTechniques(Map<ResourceLocation, TechniqueProgress> techniques) {
        Objects.requireNonNull(techniques, "learnedTechniques");
        Optional<ResourceLocation> active = activeCoreTechnique.filter(techniques::containsKey);
        return new CultivationProfile(
                schemaVersion, realmId, stageId, cultivationProgress, stability, currentSpiritualPower,
                spiritualAffinity, lifespanConsumedTicks, meditationQiReserve, spiritualRoot, techniques, active, foundationQuality, goldenCoreQuality);
    }

    /** Sets or clears the running core technique (心法); a present id must already be learned. */
    public CultivationProfile withActiveCoreTechnique(Optional<ResourceLocation> techniqueId) {
        return new CultivationProfile(
                schemaVersion, realmId, stageId, cultivationProgress, stability, currentSpiritualPower,
                spiritualAffinity, lifespanConsumedTicks, meditationQiReserve, spiritualRoot, learnedTechniques,
                Objects.requireNonNull(techniqueId, "techniqueId"), foundationQuality, goldenCoreQuality);
    }

    public CultivationProfile withActiveCoreTechnique(ResourceLocation techniqueId) {
        return withActiveCoreTechnique(Optional.of(Objects.requireNonNull(techniqueId, "techniqueId")));
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

    /**
     * Learns a technique of a known category. The first core technique learned while none is
     * running becomes the running one, so initiation leaves Basic Breathing active.
     */
    public CultivationProfile learnTechnique(ResourceLocation techniqueId, TechniqueCategory category) {
        Objects.requireNonNull(category, "category");
        CultivationProfile learned = learnTechnique(techniqueId);
        if (category == TechniqueCategory.CORE && learned.activeCoreTechnique().isEmpty()) {
            return learned.withActiveCoreTechnique(techniqueId);
        }
        return learned;
    }

    /** Forgets a technique; forgetting the running core technique leaves none running. */
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
        techniques.put(techniqueId, learnedTechniques.get(techniqueId).withMasteryPoints(masteryPoints));
        return withLearnedTechniques(techniques);
    }

    /** Pure replacement; the server installs it only through CultivationService. */
    public CultivationProfile withTechniqueProgress(ResourceLocation techniqueId, TechniqueProgress progress) {
        if (!learnedTechniques.containsKey(techniqueId)) throw new IllegalArgumentException("Technique is not learned: " + techniqueId);
        var techniques = new LinkedHashMap<>(learnedTechniques);
        techniques.put(techniqueId, Objects.requireNonNull(progress, "progress"));
        return withLearnedTechniques(techniques);
    }

    private CultivationProfile copy(
            ResourceLocation realm,
            ResourceLocation stage,
            long progress,
            int newStability,
            long power,
            int affinity,
            long consumedTicks,
            long reserve,
            Optional<SpiritualRoot> root,
            Map<ResourceLocation, TechniqueProgress> techniques) {
        return new CultivationProfile(
                schemaVersion, realm, stage, progress, newStability, power,
                affinity, consumedTicks, reserve, root, techniques, activeCoreTechnique, foundationQuality, goldenCoreQuality);
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

    private SerializedV5Profile serializeV5() {
        return new SerializedV5Profile(
                schemaVersion,
                realmId,
                stageId,
                cultivationProgress,
                stability,
                currentSpiritualPower,
                spiritualAffinity,
                lifespanConsumedTicks,
                meditationQiReserve,
                spiritualRoot,
                learnedTechniques,
                activeCoreTechnique, foundationQuality, goldenCoreQuality);
    }

    private record SerializedV5Profile(
            int schemaVersion,
            ResourceLocation realmId,
            ResourceLocation stageId,
            long cultivationProgress,
            int stability,
            long currentSpiritualPower,
            int spiritualAffinity,
            long lifespanConsumedTicks,
            long meditationQiReserve,
            Optional<SpiritualRoot> spiritualRoot,
            Map<ResourceLocation, TechniqueProgress> learnedTechniques,
            Optional<ResourceLocation> activeCoreTechnique, int foundationQuality, int goldenCoreQuality) {
        private static DataResult<SerializedV5Profile> requireVersion(SerializedV5Profile serialized) {
            return serialized.schemaVersion == CURRENT_SCHEMA_VERSION
                    ? DataResult.success(serialized)
                    : DataResult.error(() -> "Not a v" + CURRENT_SCHEMA_VERSION
                            + " cultivation profile: schema version " + serialized.schemaVersion);
        }

        /** A running core id that is no longer learned (a damaged save) reads as none running. */
        private DataResult<CultivationProfile> decode() {
            try {
                return DataResult.success(new CultivationProfile(
                        schemaVersion,
                        realmId,
                        stageId,
                        cultivationProgress,
                        stability,
                        currentSpiritualPower,
                        spiritualAffinity,
                        lifespanConsumedTicks,
                        meditationQiReserve,
                        spiritualRoot,
                        learnedTechniques,
                        activeCoreTechnique.filter(id -> learnedTechniques != null
                                && learnedTechniques.containsKey(id)), foundationQuality, goldenCoreQuality));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
