package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A technique (功法). {@code grade} is 0 凡 (only {@code basic_breathing}), 1 黄, 2 玄, 3 地, 4 天; the
 * world ledger only knows 1..4. {@code school}, {@code lineage}, {@code effects} and {@code study} are
 * optional, so files written before the technique catalogue stay valid. An absent {@code study} block reads
 * as {@link TechniqueStudy#DEFAULT}.
 */
public record TechniqueDefinition(
        String translationKey,
        TechniqueCategory category,
        int grade,
        List<ResourceLocation> elements,
        TechniqueRequirements requirements,
        Optional<ResourceLocation> school,
        Optional<TechniqueLineage> lineage,
        Optional<TechniqueEffects> effects,
        TechniqueStudy study) {
    public static final int MIN_GRADE = 0;
    public static final int MAX_GRADE = 4;

    private static final Codec<SerializedTechnique> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("translation_key").forGetter(SerializedTechnique::translationKey),
            TechniqueCategory.CODEC.fieldOf("category").forGetter(SerializedTechnique::category),
            Codec.INT.fieldOf("grade").forGetter(SerializedTechnique::grade),
            ResourceLocation.CODEC.listOf().fieldOf("elements").forGetter(SerializedTechnique::elements),
            TechniqueRequirements.CODEC.fieldOf("requirements").forGetter(SerializedTechnique::requirements),
            ResourceLocation.CODEC.optionalFieldOf("school").forGetter(SerializedTechnique::school),
            TechniqueLineage.CODEC.optionalFieldOf("lineage").forGetter(SerializedTechnique::lineage),
            TechniqueEffects.CODEC.optionalFieldOf("effects").forGetter(SerializedTechnique::effects),
            TechniqueStudy.CODEC.optionalFieldOf("study", TechniqueStudy.DEFAULT).forGetter(SerializedTechnique::study)
    ).apply(instance, SerializedTechnique::new));

    public static final Codec<TechniqueDefinition> CODEC = SERIALIZED_CODEC
            .comapFlatMap(SerializedTechnique::decode, TechniqueDefinition::serialize);

    public TechniqueDefinition {
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException("Technique translation_key must not be blank");
        }
        category = Objects.requireNonNull(category, "category");
        if (grade < MIN_GRADE || grade > MAX_GRADE) {
            throw new IllegalArgumentException(
                    "Technique grade must be in " + MIN_GRADE + ".." + MAX_GRADE + ", got " + grade);
        }
        Objects.requireNonNull(elements, "elements");
        elements = List.copyOf(elements);
        Set<ResourceLocation> uniqueElements = new HashSet<>();
        for (ResourceLocation element : elements) {
            Objects.requireNonNull(element, "element id");
            if (!uniqueElements.add(element)) {
                throw new IllegalArgumentException("Duplicate technique element id " + element);
            }
        }
        requirements = Objects.requireNonNull(requirements, "requirements");
        school = Objects.requireNonNull(school, "school");
        lineage = Objects.requireNonNull(lineage, "lineage");
        effects = Objects.requireNonNull(effects, "effects");
        if (effects.isPresent()) {
            effects.get().requireCategory(category);
        }
        study = Objects.requireNonNull(study, "study");
    }

    /** A technique with the default study block (the shape before technique manuals). */
    public TechniqueDefinition(
            String translationKey,
            TechniqueCategory category,
            int grade,
            List<ResourceLocation> elements,
            TechniqueRequirements requirements,
            Optional<ResourceLocation> school,
            Optional<TechniqueLineage> lineage,
            Optional<TechniqueEffects> effects) {
        this(translationKey, category, grade, elements, requirements, school, lineage, effects,
                TechniqueStudy.DEFAULT);
    }

    /** A technique without school, lineage or effects (the shape before the technique catalogue). */
    public TechniqueDefinition(
            String translationKey,
            TechniqueCategory category,
            int grade,
            List<ResourceLocation> elements,
            TechniqueRequirements requirements) {
        this(translationKey, category, grade, elements, requirements,
                Optional.empty(), Optional.empty(), Optional.empty(), TechniqueStudy.DEFAULT);
    }

    public boolean isCore() {
        return category == TechniqueCategory.CORE;
    }

    /** The technique that precedes this one in its heritage chain, if any. */
    public Optional<ResourceLocation> previous() {
        return lineage.map(TechniqueLineage::previous);
    }

    /** The meditation route of a core technique ({@code effects.core.meditation_route}), if any. */
    public Optional<String> meditationRoute() {
        return effects.flatMap(TechniqueEffects::core).map(TechniqueEffects.Core::meditationRoute);
    }

    private SerializedTechnique serialize() {
        return new SerializedTechnique(
                translationKey, category, grade, elements, requirements, school, lineage, effects, study);
    }

    /**
     * How a manual of this technique is studied (研读): {@code points} study points to finish, {@code gates}
     * gates spread evenly over them, each costing {@code gate_stability_cost} stability to pass. The default
     * (4000, 0, 0) applies when a technique file has no {@code study} block.
     */
    public record TechniqueStudy(int points, int gates, int gateStabilityCost) {
        public static final TechniqueStudy DEFAULT = new TechniqueStudy(4000, 0, 0);

        private static final Codec<SerializedStudy> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("points").forGetter(SerializedStudy::points),
                Codec.INT.optionalFieldOf("gates", 0).forGetter(SerializedStudy::gates),
                Codec.INT.optionalFieldOf("gate_stability_cost", 0).forGetter(SerializedStudy::gateStabilityCost)
        ).apply(instance, SerializedStudy::new));

        public static final Codec<TechniqueStudy> CODEC = SERIALIZED_CODEC.comapFlatMap(
                SerializedStudy::decode,
                study -> new SerializedStudy(study.points(), study.gates(), study.gateStabilityCost()));

        public TechniqueStudy {
            if (points <= 0) {
                throw new IllegalArgumentException("Technique study points must be > 0, got " + points);
            }
            if (gates < 0) {
                throw new IllegalArgumentException("Technique study gates must be >= 0, got " + gates);
            }
            if (gateStabilityCost < 0) {
                throw new IllegalArgumentException(
                        "Technique study gate_stability_cost must be >= 0, got " + gateStabilityCost);
            }
        }

        private record SerializedStudy(int points, int gates, int gateStabilityCost) {
            private DataResult<TechniqueStudy> decode() {
                try {
                    return DataResult.success(new TechniqueStudy(points, gates, gateStabilityCost));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(exception::getMessage);
                }
            }
        }
    }

    private record SerializedTechnique(
            String translationKey,
            TechniqueCategory category,
            int grade,
            List<ResourceLocation> elements,
            TechniqueRequirements requirements,
            Optional<ResourceLocation> school,
            Optional<TechniqueLineage> lineage,
            Optional<TechniqueEffects> effects,
            TechniqueStudy study) {
        private DataResult<TechniqueDefinition> decode() {
            try {
                return DataResult.success(new TechniqueDefinition(
                        translationKey, category, grade, elements, requirements, school, lineage, effects, study));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
