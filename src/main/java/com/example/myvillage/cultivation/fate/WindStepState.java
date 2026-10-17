package com.example.myvillage.cultivation.fate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Saved air charge and shared cooldown. Logging in never manufactures a landing. */
public record WindStepState(boolean airSpent, long readyAt) {
    public static final WindStepState EMPTY = new WindStepState(true, 0);
    public static final Codec<WindStepState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("air_spent", true).forGetter(WindStepState::airSpent),
            Codec.LONG.optionalFieldOf("ready_at", 0L).forGetter(WindStepState::readyAt)
    ).apply(i, WindStepState::new));

    public WindStepState {
        if (readyAt < 0) throw new IllegalArgumentException("Invalid wind step cooldown");
    }

    public boolean canAirDodge(long now) { return !airSpent && now >= readyAt; }

    /** A geometrically observed landing restores the charge, never the cooldown. */
    public WindStepState landed() { return airSpent ? new WindStepState(false, readyAt) : this; }

    public WindStepState dodged(boolean airborne, long now, int cooldownTicks) {
        if (now < 0 || cooldownTicks < 0) throw new IllegalArgumentException("Invalid dodge time");
        long end = now > Long.MAX_VALUE - cooldownTicks ? Long.MAX_VALUE : now + cooldownTicks;
        return new WindStepState(airSpent || airborne, Math.max(readyAt, end));
    }

    /** Death/operator reset removes a benefit without making an active cooldown ready. */
    public WindStepState clearAirCharge() { return airSpent ? this : new WindStepState(true, readyAt); }
}
