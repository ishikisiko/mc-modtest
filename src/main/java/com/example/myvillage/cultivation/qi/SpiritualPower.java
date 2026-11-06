package com.example.myvillage.cultivation.qi;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.fate.FateState;
import net.minecraft.core.RegistryAccess;

import java.util.OptionalLong;

/** Shared cap lookup and pure bounded arithmetic; it never mutates a player attachment. */
public final class SpiritualPower {
    private SpiritualPower() { }

    /** Baseline stage capacity for callers without an owning fate attachment. */
    public static long cap(RegistryAccess registries, CultivationProfile profile) {
        return registries.registry(ModCultivationRegistries.REALMS)
                .flatMap(registry -> registry.getOptional(profile.realmId()))
                .flatMap(realm -> realm.stages().stream()
                        .filter(stage -> stage.id().equals(profile.stageId())).findFirst())
                .map(stage -> com.example.myvillage.cultivation.CultivationStats.find(profile).map(com.example.myvillage.stats.CultivationStatsRules.Body::power).orElse(0L)).orElse(0L);
    }

    /** Same definition lookup on both sides; eligibility uses the prospective profile on commit. */
    public static long cap(RegistryAccess registries, CultivationProfile profile, FateState fates) {
        double bonus = fates.confirmed() ? registries.registry(ModCultivationRegistries.INNATE_FATES)
                .map(registry -> fates.selected().stream()
                        .map(net.minecraft.resources.ResourceLocation::parse)
                        .map(registry::get)
                        .filter(java.util.Objects::nonNull)
                        .filter(definition -> definition.eligible(profile.spiritualRoot()))
                        .mapToDouble(definition -> definition.effect("power_cap_bonus")).sum()).orElse(0.0) : 0;
        return OpenMeridians.capacity(cap(registries, profile), bonus);
    }

    public static long clamp(long current, long cap) {
        return com.example.myvillage.sim.cultivation.SpiritualPowerMath.clamp(current, cap);
    }

    /** Refusal has no replacement value, so the caller cannot accidentally commit a partial cost. */
    public static OptionalLong spend(long current, long cap, long cost) {
        return com.example.myvillage.sim.cultivation.SpiritualPowerMath.spend(current, cap, cost);
    }

    public static long recover(long current, long cap, long amount) {
        return com.example.myvillage.sim.cultivation.SpiritualPowerMath.recover(current, cap, amount);
    }
}
