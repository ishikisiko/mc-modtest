package com.example.myvillage.cultivation.fate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Actual lost health accumulated during this life; readiness is latched until a hit or death. */
public record TemperedBonesState(double damage, boolean ready) {
    public static final TemperedBonesState EMPTY = new TemperedBonesState(0, false);
    public static final Codec<TemperedBonesState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("damage", 0.0).forGetter(TemperedBonesState::damage),
            Codec.BOOL.optionalFieldOf("ready", false).forGetter(TemperedBonesState::ready)
    ).apply(i, TemperedBonesState::new));

    public TemperedBonesState {
        if (!Double.isFinite(damage) || damage < 0 || (ready && damage == 0))
            throw new IllegalArgumentException("Invalid tempered bones charge");
    }
}
