package com.example.myvillage.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SpiritualRoot(Map<ResourceLocation, Integer> points) {
    private static final Codec<Map<ResourceLocation, Integer>> AFFINITIES_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.intRange(0, Integer.MAX_VALUE));

    public static final Codec<SpiritualRoot> CODEC = AFFINITIES_CODEC
            .fieldOf("element_points")
            .codec()
            .comapFlatMap(SpiritualRoot::decode, SpiritualRoot::points);

    public SpiritualRoot {
        Objects.requireNonNull(points, "points");
        if (points.isEmpty() || points.size() > 5) {
            throw new IllegalArgumentException("A root needs one through five elements");
        }
        LinkedHashMap<ResourceLocation, Integer> sorted = new LinkedHashMap<>();
        points.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation elementId = Objects.requireNonNull(entry.getKey(), "element id");
                    Integer affinity = Objects.requireNonNull(entry.getValue(), "affinity for " + elementId);
                    if (affinity < 0) {
                        throw new IllegalArgumentException(
                                "Element aptitude for " + elementId + " must be non-negative, got " + affinity);
                    }
                    sorted.put(elementId, affinity);
                });

        points = Collections.unmodifiableMap(sorted);
    }

    public SpiritualRoot with(ResourceLocation element, int value) {
        var next = new LinkedHashMap<>(points);
        next.put(element, value);
        return new SpiritualRoot(next);
    }

    private static DataResult<SpiritualRoot> decode(Map<ResourceLocation, Integer> affinities) {
        try {
            return DataResult.success(new SpiritualRoot(affinities));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return DataResult.error(exception::getMessage);
        }
    }
}
