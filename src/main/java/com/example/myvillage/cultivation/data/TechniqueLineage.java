package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** A technique's place in a heritage chain: the technique that comes before it ({@code previous}). */
public record TechniqueLineage(ResourceLocation previous) {
    public static final Codec<TechniqueLineage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("previous").forGetter(TechniqueLineage::previous)
    ).apply(instance, TechniqueLineage::new));

    public TechniqueLineage {
        Objects.requireNonNull(previous, "previous");
    }
}
