package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record TechniqueDefinition(
        String translationKey,
        TechniqueCategory category,
        int grade,
        List<ResourceLocation> elements,
        TechniqueRequirements requirements) {
    private static final Codec<SerializedTechnique> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("translation_key").forGetter(SerializedTechnique::translationKey),
            TechniqueCategory.CODEC.fieldOf("category").forGetter(SerializedTechnique::category),
            Codec.INT.fieldOf("grade").forGetter(SerializedTechnique::grade),
            ResourceLocation.CODEC.listOf().fieldOf("elements").forGetter(SerializedTechnique::elements),
            TechniqueRequirements.CODEC.fieldOf("requirements").forGetter(SerializedTechnique::requirements)
    ).apply(instance, SerializedTechnique::new));

    public static final Codec<TechniqueDefinition> CODEC = SERIALIZED_CODEC
            .comapFlatMap(SerializedTechnique::decode, TechniqueDefinition::serialize);

    public TechniqueDefinition {
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException("Technique translation_key must not be blank");
        }
        category = Objects.requireNonNull(category, "category");
        if (grade < 0) {
            throw new IllegalArgumentException("Technique grade must be non-negative, got " + grade);
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
    }

    private SerializedTechnique serialize() {
        return new SerializedTechnique(translationKey, category, grade, elements, requirements);
    }

    private record SerializedTechnique(
            String translationKey,
            TechniqueCategory category,
            int grade,
            List<ResourceLocation> elements,
            TechniqueRequirements requirements) {
        private DataResult<TechniqueDefinition> decode() {
            try {
                return DataResult.success(new TechniqueDefinition(
                        translationKey, category, grade, elements, requirements));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
