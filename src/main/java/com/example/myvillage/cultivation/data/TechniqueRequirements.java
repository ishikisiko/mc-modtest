package com.example.myvillage.cultivation.data;

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

public record TechniqueRequirements(
        Optional<ResourceLocation> minimumRealm,
        Optional<ResourceLocation> minimumStage,
        Map<ResourceLocation, Integer> minimumElementAffinity) {
    private static final Codec<Map<ResourceLocation, Integer>> AFFINITIES_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT);

    private static final Codec<SerializedRequirements> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("minimum_realm").forGetter(SerializedRequirements::minimumRealm),
            ResourceLocation.CODEC.optionalFieldOf("minimum_stage").forGetter(SerializedRequirements::minimumStage),
            AFFINITIES_CODEC.optionalFieldOf("minimum_element_affinity", Map.of())
                    .forGetter(SerializedRequirements::minimumElementAffinity)
    ).apply(instance, SerializedRequirements::new));

    public static final Codec<TechniqueRequirements> CODEC = SERIALIZED_CODEC
            .comapFlatMap(SerializedRequirements::decode, TechniqueRequirements::serialize);

    public TechniqueRequirements {
        minimumRealm = Objects.requireNonNull(minimumRealm, "minimumRealm");
        minimumStage = Objects.requireNonNull(minimumStage, "minimumStage");
        if (minimumStage.isPresent() && minimumRealm.isEmpty()) {
            throw new IllegalArgumentException("minimum_stage requires minimum_realm");
        }

        Objects.requireNonNull(minimumElementAffinity, "minimumElementAffinity");
        LinkedHashMap<ResourceLocation, Integer> sorted = new LinkedHashMap<>();
        minimumElementAffinity.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation elementId = Objects.requireNonNull(entry.getKey(), "minimum affinity element id");
                    Integer affinity = Objects.requireNonNull(entry.getValue(), "minimum affinity for " + elementId);
                    if (affinity < 0 || affinity > 10_000) {
                        throw new IllegalArgumentException(
                                "Minimum affinity for " + elementId + " must be in 0..10000, got " + affinity);
                    }
                    sorted.put(elementId, affinity);
                });
        minimumElementAffinity = Collections.unmodifiableMap(sorted);
    }

    public static TechniqueRequirements none() {
        return new TechniqueRequirements(Optional.empty(), Optional.empty(), Map.of());
    }

    public Map<ResourceLocation, Integer> minimumElementAffinities() {
        return minimumElementAffinity;
    }

    private SerializedRequirements serialize() {
        return new SerializedRequirements(minimumRealm, minimumStage, minimumElementAffinity);
    }

    private record SerializedRequirements(
            Optional<ResourceLocation> minimumRealm,
            Optional<ResourceLocation> minimumStage,
            Map<ResourceLocation, Integer> minimumElementAffinity) {
        private DataResult<TechniqueRequirements> decode() {
            try {
                return DataResult.success(new TechniqueRequirements(
                        minimumRealm, minimumStage, minimumElementAffinity));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
