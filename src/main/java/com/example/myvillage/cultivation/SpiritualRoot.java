package com.example.myvillage.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SpiritualRoot(Map<ResourceLocation, Integer> affinitiesBasisPoints) {
    public static final int TOTAL_BASIS_POINTS = 10_000;

    private static final Codec<Map<ResourceLocation, Integer>> AFFINITIES_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT);

    public static final Codec<SpiritualRoot> CODEC = AFFINITIES_CODEC
            .fieldOf("affinities_basis_points")
            .codec()
            .comapFlatMap(SpiritualRoot::decode, SpiritualRoot::affinitiesBasisPoints);

    public SpiritualRoot {
        Objects.requireNonNull(affinitiesBasisPoints, "affinitiesBasisPoints");
        LinkedHashMap<ResourceLocation, Integer> sorted = new LinkedHashMap<>();
        affinitiesBasisPoints.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation elementId = Objects.requireNonNull(entry.getKey(), "element id");
                    Integer affinity = Objects.requireNonNull(entry.getValue(), "affinity for " + elementId);
                    if (affinity < 0 || affinity > TOTAL_BASIS_POINTS) {
                        throw new IllegalArgumentException(
                                "Affinity for " + elementId + " must be in 0..10000, got " + affinity);
                    }
                    sorted.put(elementId, affinity);
                });

        long total = sorted.values().stream().mapToLong(Integer::longValue).sum();
        if (total != TOTAL_BASIS_POINTS) {
            throw new IllegalArgumentException(
                    "Spiritual-root affinities must total 10000 basis points, got " + total);
        }
        affinitiesBasisPoints = Collections.unmodifiableMap(sorted);
    }

    private static DataResult<SpiritualRoot> decode(Map<ResourceLocation, Integer> affinities) {
        try {
            return DataResult.success(new SpiritualRoot(affinities));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return DataResult.error(exception::getMessage);
        }
    }
}
