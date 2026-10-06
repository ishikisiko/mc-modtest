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
 * A heritage (传承), file {@code data/<ns>/myvillage/heritage/<id>.json}: an ordered chain of
 * techniques, lowest first. {@code school} may be absent for a chain that is not tied to a weapon
 * school; {@code exclusive} chains are only obtainable through the sect that holds them or its ruins.
 */
public record HeritageDefinition(
        String translationKey,
        Optional<ResourceLocation> school,
        List<ResourceLocation> techniques,
        boolean exclusive) {
    public static final int MIN_TECHNIQUES = 2;

    private static final Codec<SerializedHeritage> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("translation_key").forGetter(SerializedHeritage::translationKey),
            ResourceLocation.CODEC.optionalFieldOf("school").forGetter(SerializedHeritage::school),
            ResourceLocation.CODEC.listOf().fieldOf("techniques").forGetter(SerializedHeritage::techniques),
            Codec.BOOL.optionalFieldOf("exclusive", false).forGetter(SerializedHeritage::exclusive)
    ).apply(instance, SerializedHeritage::new));

    public static final Codec<HeritageDefinition> CODEC = SERIALIZED_CODEC
            .comapFlatMap(SerializedHeritage::decode, HeritageDefinition::serialize);

    public HeritageDefinition {
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException("Heritage translation_key must not be blank");
        }
        school = Objects.requireNonNull(school, "school");
        Objects.requireNonNull(techniques, "techniques");
        techniques = List.copyOf(techniques);
        if (techniques.size() < MIN_TECHNIQUES) {
            throw new IllegalArgumentException(
                    "A heritage needs at least " + MIN_TECHNIQUES + " techniques, got " + techniques.size());
        }
        Set<ResourceLocation> unique = new HashSet<>();
        for (ResourceLocation technique : techniques) {
            if (!unique.add(Objects.requireNonNull(technique, "technique id"))) {
                throw new IllegalArgumentException("Duplicate heritage technique " + technique);
            }
        }
    }

    /** Position of {@code techniqueId} in the chain (0 = first), or -1. */
    public int indexOf(ResourceLocation techniqueId) {
        return techniques.indexOf(techniqueId);
    }

    public boolean contains(ResourceLocation techniqueId) {
        return techniques.contains(techniqueId);
    }

    private SerializedHeritage serialize() {
        return new SerializedHeritage(translationKey, school, techniques, exclusive);
    }

    private record SerializedHeritage(
            String translationKey,
            Optional<ResourceLocation> school,
            List<ResourceLocation> techniques,
            boolean exclusive) {
        private DataResult<HeritageDefinition> decode() {
            try {
                return DataResult.success(new HeritageDefinition(translationKey, school, techniques, exclusive));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
