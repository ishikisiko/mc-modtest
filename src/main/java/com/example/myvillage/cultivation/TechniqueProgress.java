package com.example.myvillage.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public record TechniqueProgress(long masteryPoints) {
    public static final TechniqueProgress ZERO = new TechniqueProgress(0);

    public static final Codec<TechniqueProgress> CODEC = Codec.LONG
            .fieldOf("mastery_points")
            .codec()
            .comapFlatMap(TechniqueProgress::decode, TechniqueProgress::masteryPoints);

    public TechniqueProgress {
        if (masteryPoints < 0) {
            throw new IllegalArgumentException("Technique mastery must be non-negative, got " + masteryPoints);
        }
    }

    public TechniqueProgress withMasteryPoints(long value) {
        return new TechniqueProgress(value);
    }

    private static DataResult<TechniqueProgress> decode(long masteryPoints) {
        try {
            return DataResult.success(new TechniqueProgress(masteryPoints));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }
}
