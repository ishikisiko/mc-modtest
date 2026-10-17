package com.example.myvillage.cultivation.fate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Independent shield pool and persistent overworld-clock deadlines; never vanilla absorption. */
public record PillAegisState(double shield, long shieldExpiresAt, long cooldownReadyAt) {
    public static final PillAegisState EMPTY = new PillAegisState(0, 0, 0);
    public static final Codec<PillAegisState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("shield", 0.0).forGetter(PillAegisState::shield),
            Codec.LONG.optionalFieldOf("shield_expires_at", 0L).forGetter(PillAegisState::shieldExpiresAt),
            Codec.LONG.optionalFieldOf("cooldown_ready_at", 0L).forGetter(PillAegisState::cooldownReadyAt)
    ).apply(i, PillAegisState::new));

    public PillAegisState {
        if (!Double.isFinite(shield) || shield < 0 || shield > Float.MAX_VALUE
                || shieldExpiresAt < 0 || cooldownReadyAt < 0
                || (shield == 0) != (shieldExpiresAt == 0))
            throw new IllegalArgumentException("Invalid pill aegis state");
    }

    /** Death, expiry, and administrative reset discard only this fate's shield. */
    public PillAegisState clearShield() {
        return shield == 0 ? this : new PillAegisState(0, 0, cooldownReadyAt);
    }

    public PillAegisState expire(long now) {
        return shield > 0 && now >= shieldExpiresAt ? clearShield() : this;
    }
}
