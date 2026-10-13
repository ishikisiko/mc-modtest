package com.example.myvillage.cultivation.qi;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import net.minecraft.core.RegistryAccess;

import java.util.OptionalLong;

/** Shared cap lookup and pure bounded arithmetic; it never mutates a player attachment. */
public final class SpiritualPower {
    private SpiritualPower() { }

    public static long cap(RegistryAccess registries, CultivationProfile profile) {
        return registries.registry(ModCultivationRegistries.REALMS)
                .flatMap(registry -> registry.getOptional(profile.realmId()))
                .flatMap(realm -> realm.stages().stream()
                        .filter(stage -> stage.id().equals(profile.stageId())).findFirst())
                .map(stage -> stage.spiritualPowerCap()).orElse(0L);
    }

    public static long clamp(long current, long cap) {
        if (current < 0 || cap < 0) throw new IllegalArgumentException("Spiritual power must be non-negative");
        return Math.min(current, cap);
    }

    /** Refusal has no replacement value, so the caller cannot accidentally commit a partial cost. */
    public static OptionalLong spend(long current, long cap, long cost) {
        if (cost < 0) throw new IllegalArgumentException("Spiritual power cost must be non-negative");
        long available = clamp(current, cap);
        return available < cost ? OptionalLong.empty() : OptionalLong.of(available - cost);
    }

    public static long recover(long current, long cap, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Recovery must be non-negative");
        long available = clamp(current, cap);
        return available + Math.min(amount, cap - available);
    }
}
