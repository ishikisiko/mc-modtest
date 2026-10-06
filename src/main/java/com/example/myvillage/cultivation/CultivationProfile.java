package com.example.myvillage.cultivation;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.mojang.datafixers.util.Either;
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
        Optional<ResourceLocation> activeCoreTechnique) {
    public static final int CURRENT_SCHEMA_VERSION = 4;
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

    private static final Codec<SerializedV1Profile> V1_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(SerializedV1Profile::schemaVersion),
            ResourceLocation.CODEC.fieldOf("realm_id").forGetter(SerializedV1Profile::realmId),
            ResourceLocation.CODEC.fieldOf("stage_id").forGetter(SerializedV1Profile::stageId),
            Codec.LONG.fieldOf("cultivation_progress").forGetter(SerializedV1Profile::cultivationProgress),
            Codec.INT.fieldOf("stability").forGetter(SerializedV1Profile::stability),
            Codec.LONG.fieldOf("current_spiritual_power").forGetter(SerializedV1Profile::currentSpiritualPower),
            SpiritualRoot.CODEC.optionalFieldOf("spiritual_root").forGetter(SerializedV1Profile::spiritualRoot),
            LEARNED_TECHNIQUES_CODEC.fieldOf("learned_techniques").forGetter(SerializedV1Profile::learnedTechniques)
    ).apply(instance, SerializedV1Profile::new));

    private static final Codec<SerializedV2Profile> V2_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(SerializedV2Profile::schemaVersion),
            ResourceLocation.CODEC.fieldOf("realm_id").forGetter(SerializedV2Profile::realmId),
            ResourceLocation.CODEC.fieldOf("stage_id").forGetter(SerializedV2Profile::stageId),
            Codec.LONG.fieldOf("cultivation_progress").forGetter(SerializedV2Profile::cultivationProgress),
            Codec.INT.fieldOf("stability").forGetter(SerializedV2Profile::stability),
            Codec.LONG.fieldOf("current_spiritual_power").forGetter(SerializedV2Profile::currentSpiritualPower),
            Codec.LONG.fieldOf("lifespan_consumed_ticks").forGetter(SerializedV2Profile::lifespanConsumedTicks),
            Codec.LONG.fieldOf("meditation_qi_reserve").forGetter(SerializedV2Profile::meditationQiReserve),
            SpiritualRoot.CODEC.optionalFieldOf("spiritual_root").forGetter(SerializedV2Profile::spiritualRoot),
            LEARNED_TECHNIQUES_CODEC.fieldOf("learned_techniques").forGetter(SerializedV2Profile::learnedTechniques)
    ).apply(instance, SerializedV2Profile::new));

    private static final Codec<SerializedV3Profile> V3_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(SerializedV3Profile::schemaVersion),
            ResourceLocation.CODEC.fieldOf("realm_id").forGetter(SerializedV3Profile::realmId),
            ResourceLocation.CODEC.fieldOf("stage_id").forGetter(SerializedV3Profile::stageId),
            Codec.LONG.fieldOf("cultivation_progress").forGetter(SerializedV3Profile::cultivationProgress),
            Codec.INT.fieldOf("stability").forGetter(SerializedV3Profile::stability),
            Codec.LONG.fieldOf("current_spiritual_power").forGetter(SerializedV3Profile::currentSpiritualPower),
            Codec.INT.fieldOf("spiritual_affinity").forGetter(SerializedV3Profile::spiritualAffinity),
            Codec.LONG.fieldOf("lifespan_consumed_ticks").forGetter(SerializedV3Profile::lifespanConsumedTicks),
            Codec.LONG.fieldOf("meditation_qi_reserve").forGetter(SerializedV3Profile::meditationQiReserve),
            SpiritualRoot.CODEC.optionalFieldOf("spiritual_root").forGetter(SerializedV3Profile::spiritualRoot),
            LEARNED_TECHNIQUES_CODEC.fieldOf("learned_techniques").forGetter(SerializedV3Profile::learnedTechniques)
    ).apply(instance, SerializedV3Profile::new));

    /** v4 differs from v3 only by an optional field, so it is selected by its schema version. */
    private static final Codec<SerializedV4Profile> V4_CODEC = RecordCodecBuilder.<SerializedV4Profile>create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(SerializedV4Profile::schemaVersion),
            ResourceLocation.CODEC.fieldOf("realm_id").forGetter(SerializedV4Profile::realmId),
            ResourceLocation.CODEC.fieldOf("stage_id").forGetter(SerializedV4Profile::stageId),
            Codec.LONG.fieldOf("cultivation_progress").forGetter(SerializedV4Profile::cultivationProgress),
            Codec.INT.fieldOf("stability").forGetter(SerializedV4Profile::stability),
            Codec.LONG.fieldOf("current_spiritual_power").forGetter(SerializedV4Profile::currentSpiritualPower),
            Codec.INT.fieldOf("spiritual_affinity").forGetter(SerializedV4Profile::spiritualAffinity),
            Codec.LONG.fieldOf("lifespan_consumed_ticks").forGetter(SerializedV4Profile::lifespanConsumedTicks),
            Codec.LONG.fieldOf("meditation_qi_reserve").forGetter(SerializedV4Profile::meditationQiReserve),
            SpiritualRoot.CODEC.optionalFieldOf("spiritual_root").forGetter(SerializedV4Profile::spiritualRoot),
            LEARNED_TECHNIQUES_CODEC.fieldOf("learned_techniques").forGetter(SerializedV4Profile::learnedTechniques),
            ResourceLocation.CODEC.optionalFieldOf("active_core_technique")
                    .forGetter(SerializedV4Profile::activeCoreTechnique)
    ).apply(instance, SerializedV4Profile::new)).validate(SerializedV4Profile::requireVersion);

    public static final Codec<CultivationProfile> CODEC = Codec.either(
                    V4_CODEC, Codec.either(V3_CODEC, Codec.either(V2_CODEC, V1_CODEC)))
            .comapFlatMap(
                    serialized -> serialized.map(
                            SerializedV4Profile::decode,
                            legacy -> legacy.map(
                                    SerializedV3Profile::migrate,
                                    older -> older.map(SerializedV2Profile::migrate, SerializedV1Profile::migrate))),
                    profile -> Either.left(profile.serializeV4()));

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
                spiritualAffinity, lifespanConsumedTicks, meditationQiReserve, spiritualRoot, techniques, active);
    }

    /** Sets or clears the running core technique (心法); a present id must already be learned. */
    public CultivationProfile withActiveCoreTechnique(Optional<ResourceLocation> techniqueId) {
        return new CultivationProfile(
                schemaVersion, realmId, stageId, cultivationProgress, stability, currentSpiritualPower,
                spiritualAffinity, lifespanConsumedTicks, meditationQiReserve, spiritualRoot, learnedTechniques,
                Objects.requireNonNull(techniqueId, "techniqueId"));
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
        techniques.put(techniqueId, new TechniqueProgress(masteryPoints));
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
                affinity, consumedTicks, reserve, root, techniques, activeCoreTechnique);
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

    private SerializedV4Profile serializeV4() {
        return new SerializedV4Profile(
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
                activeCoreTechnique);
    }

    private record SerializedV1Profile(
            int schemaVersion,
            ResourceLocation realmId,
            ResourceLocation stageId,
            long cultivationProgress,
            int stability,
            long currentSpiritualPower,
            Optional<SpiritualRoot> spiritualRoot,
            Map<ResourceLocation, TechniqueProgress> learnedTechniques) {
        private DataResult<CultivationProfile> migrate() {
            if (schemaVersion != 1) {
                return DataResult.error(() ->
                        "Unsupported cultivation profile schema version " + schemaVersion
                                + "; expected 1, 2, 3, or " + CURRENT_SCHEMA_VERSION);
            }
            try {
                return new SerializedV2Profile(
                        2,
                        realmId,
                        stageId,
                        cultivationProgress,
                        stability,
                        currentSpiritualPower,
                        0,
                        0,
                        spiritualRoot,
                        learnedTechniques).migrate();
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }

    private record SerializedV2Profile(
            int schemaVersion,
            ResourceLocation realmId,
            ResourceLocation stageId,
            long cultivationProgress,
            int stability,
            long currentSpiritualPower,
            long lifespanConsumedTicks,
            long meditationQiReserve,
            Optional<SpiritualRoot> spiritualRoot,
            Map<ResourceLocation, TechniqueProgress> learnedTechniques) {
        private DataResult<CultivationProfile> migrate() {
            if (schemaVersion != 2) {
                return DataResult.error(() ->
                        "Unsupported cultivation profile schema version " + schemaVersion
                                + "; expected 1, 2, 3, or " + CURRENT_SCHEMA_VERSION);
            }
            try {
                return new SerializedV3Profile(
                        3,
                        realmId,
                        stageId,
                        cultivationProgress,
                        stability,
                        currentSpiritualPower,
                        DEFAULT_SPIRITUAL_AFFINITY,
                        lifespanConsumedTicks,
                        meditationQiReserve,
                        spiritualRoot,
                        learnedTechniques).migrate();
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }

    private record SerializedV3Profile(
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
            Map<ResourceLocation, TechniqueProgress> learnedTechniques) {
        /**
         * v3 to v4: the running core technique is Basic Breathing when it is learned, otherwise none.
         */
        private DataResult<CultivationProfile> migrate() {
            if (schemaVersion != 3) {
                return DataResult.error(() ->
                        "Unsupported cultivation profile schema version " + schemaVersion
                                + "; expected 1, 2, 3, or " + CURRENT_SCHEMA_VERSION);
            }
            try {
                return DataResult.success(new CultivationProfile(
                        CURRENT_SCHEMA_VERSION,
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
                        migratedActiveCoreTechnique(learnedTechniques)));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }

    private static Optional<ResourceLocation> migratedActiveCoreTechnique(
            Map<ResourceLocation, TechniqueProgress> learnedTechniques) {
        ResourceLocation basicBreathing = ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID;
        return learnedTechniques != null && learnedTechniques.containsKey(basicBreathing)
                ? Optional.of(basicBreathing)
                : Optional.empty();
    }

    private record SerializedV4Profile(
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
            Optional<ResourceLocation> activeCoreTechnique) {
        private static DataResult<SerializedV4Profile> requireVersion(SerializedV4Profile serialized) {
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
                                && learnedTechniques.containsKey(id))));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
