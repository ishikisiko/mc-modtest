package com.example.myvillage.cultivation.fate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Absolute overworld-clock deadlines; clearing the opening never resets the proc cooldown. */
public record SwordHeartState(long openingExpiresAt, long cooldownReadyAt) {
    public static final SwordHeartState EMPTY = new SwordHeartState(0, 0);
    public static final Codec<SwordHeartState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("opening_expires_at", 0L).forGetter(SwordHeartState::openingExpiresAt),
            Codec.LONG.optionalFieldOf("cooldown_ready_at", 0L).forGetter(SwordHeartState::cooldownReadyAt)
    ).apply(i, SwordHeartState::new));

    public SwordHeartState {
        if (openingExpiresAt < 0 || cooldownReadyAt < 0)
            throw new IllegalArgumentException("Invalid sword heart deadlines");
    }

    public boolean opening(long now) { return now >= 0 && openingExpiresAt > now; }

    public SwordHeartState clearOpening() {
        return openingExpiresAt == 0 ? this : new SwordHeartState(0, cooldownReadyAt);
    }

    public SwordHeartState expire(long now) {
        return openingExpiresAt > 0 && now >= openingExpiresAt ? clearOpening() : this;
    }
}
