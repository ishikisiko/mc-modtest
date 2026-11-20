package com.example.myvillage.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Objects;

/** The best stage ever reached and the condition of the player's own nascent soul. */
public record CultivationMemory(Optional<Stage> highest, int nascentQuality, boolean intact, int reformation,
                                int spiritQuality, boolean seedLost) {
    public static final CultivationMemory EMPTY = new CultivationMemory(Optional.empty(), 0, false, 0, 0, false);
    public static final Codec<CultivationMemory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Stage.CODEC.optionalFieldOf("highest").forGetter(CultivationMemory::highest),
            Codec.intRange(0, 3).optionalFieldOf("nascent_quality", 0).forGetter(CultivationMemory::nascentQuality),
            Codec.BOOL.optionalFieldOf("intact", false).forGetter(CultivationMemory::intact),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("reformation", 0).forGetter(CultivationMemory::reformation),
            Codec.intRange(0, 3).optionalFieldOf("spirit_quality", 0).forGetter(CultivationMemory::spiritQuality),
            Codec.BOOL.optionalFieldOf("seed_lost", false).forGetter(CultivationMemory::seedLost)
    ).apply(instance, CultivationMemory::new));

    public CultivationMemory {
        Objects.requireNonNull(highest, "highest");
        if (nascentQuality < 0 || nascentQuality > 3 || reformation < 0
                || spiritQuality < 0 || spiritQuality > 3
                || (nascentQuality == 0 && (intact || reformation > 0 || seedLost || spiritQuality > 0))
                || (intact && (reformation > 0 || seedLost))) {
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
        return new CultivationMemory(Optional.of(stage), nascentQuality, intact, reformation, spiritQuality, seedLost);
    }

    public CultivationMemory form(int quality) {
        return new CultivationMemory(highest, quality, true, 0, spiritQuality, false);
    }

    public CultivationMemory shatter() {
        return new CultivationMemory(highest, nascentQuality, false, 0, spiritQuality, seedLost);
    }

    public CultivationMemory disperseSeed() {
        return new CultivationMemory(highest, nascentQuality, false, 0, spiritQuality, true);
    }

    public CultivationMemory spirit(int quality) {
        return new CultivationMemory(highest, nascentQuality, intact, reformation, quality, seedLost);
    }

    public CultivationMemory reform(int progress, int target) {
        if (progress < target) {
            return new CultivationMemory(highest, nascentQuality, false, progress, spiritQuality, seedLost);
        }
        if (seedLost) {
            return new CultivationMemory(highest, nascentQuality, false, 0, spiritQuality, false);
        }
        return form(nascentQuality);
    }
}
