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
        Map<ResourceLocation, Integer> minimumElementAptitude) {
    private static final Codec<Map<ResourceLocation, Integer>> AFFINITIES_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT);

    private static final Codec<SerializedRequirements> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("minimum_realm").forGetter(SerializedRequirements::minimumRealm),
            AFFINITIES_CODEC.optionalFieldOf("minimum_element_aptitude", Map.of())
                    .forGetter(SerializedRequirements::minimumElementAptitude)
    ).apply(instance, SerializedRequirements::new));

    public static final Codec<TechniqueRequirements> CODEC = SERIALIZED_CODEC
            .comapFlatMap(SerializedRequirements::decode, TechniqueRequirements::serialize);

    public TechniqueRequirements {
        minimumRealm = Objects.requireNonNull(minimumRealm, "minimumRealm");

        Objects.requireNonNull(minimumElementAptitude, "minimumElementAptitude");
        LinkedHashMap<ResourceLocation, Integer> sorted = new LinkedHashMap<>();
        minimumElementAptitude.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation elementId = Objects.requireNonNull(entry.getKey(), "minimum affinity element id");
                    Integer affinity = Objects.requireNonNull(entry.getValue(), "minimum affinity for " + elementId);
                    if (affinity < 0) {
                        throw new IllegalArgumentException(
                                "Minimum affinity for " + elementId + " must be non-negative, got " + affinity);
                    }
                    sorted.put(elementId, affinity);
                });
        minimumElementAptitude = Collections.unmodifiableMap(sorted);
    }

    public static TechniqueRequirements none() {
        return new TechniqueRequirements(Optional.empty(), Map.of());
    }

    public Map<ResourceLocation, Integer> minimumElementAptitudes() {
        return minimumElementAptitude;
    }

    private SerializedRequirements serialize() {
        return new SerializedRequirements(minimumRealm, minimumElementAptitude);
    }

    private record SerializedRequirements(
            Optional<ResourceLocation> minimumRealm,
            Map<ResourceLocation, Integer> minimumElementAptitude) {
        private DataResult<TechniqueRequirements> decode() {
            try {
                return DataResult.success(new TechniqueRequirements(
                        minimumRealm, minimumElementAptitude));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
