package com.example.myvillage.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Objects;

/** The best stage ever reached and the condition of the player's own nascent soul. */
public record CultivationMemory(Optional<Stage> highest, int nascentQuality, boolean intact, int reformation) {
    public static final CultivationMemory EMPTY = new CultivationMemory(Optional.empty(), 0, false, 0);
    public static final Codec<CultivationMemory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Stage.CODEC.optionalFieldOf("highest").forGetter(CultivationMemory::highest),
            Codec.intRange(0, 3).optionalFieldOf("nascent_quality", 0).forGetter(CultivationMemory::nascentQuality),
            Codec.BOOL.optionalFieldOf("intact", false).forGetter(CultivationMemory::intact),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("reformation", 0).forGetter(CultivationMemory::reformation)
    ).apply(instance, CultivationMemory::new));

    public CultivationMemory {
        Objects.requireNonNull(highest, "highest");
        if (nascentQuality < 0 || nascentQuality > 3 || reformation < 0
                || (nascentQuality == 0 && (intact || reformation > 0)) || (intact && reformation > 0)) {
            throw new IllegalArgumentException("Invalid nascent soul state");
        }
    }

    public record Stage(ResourceLocation realm, ResourceLocation id) {
        public static final Codec<Stage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("realm").forGetter(Stage::realm),
                ResourceLocation.CODEC.fieldOf("stage").forGetter(Stage::id)
        ).apply(instance, Stage::new));
    }

    public CultivationMemory reach(Stage stage) {
        return new CultivationMemory(Optional.of(stage), nascentQuality, intact, reformation);
    }

    public CultivationMemory form(int quality) {
        return new CultivationMemory(highest, quality, true, 0);
    }

    public CultivationMemory shatter() {
        return new CultivationMemory(highest, nascentQuality, false, 0);
    }

    public CultivationMemory reform(int progress, int target) {
        return progress >= target ? form(nascentQuality) : new CultivationMemory(highest, nascentQuality, false, progress);
    }
}
