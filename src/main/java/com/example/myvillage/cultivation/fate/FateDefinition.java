package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.SpiritualRoot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Synced data, never client authority. No gameplay number lives in a trait-id branch. */
public record FateDefinition(String translationKey, String descriptionKey, int tier, int cost,
        String group, boolean enabled, Map<String, Double> effects,
        Optional<ResourceLocation> element, int minimumShare, int maximumRoots) {
    public static final Set<String> EFFECTS = Set.of("health", "speed", "knockback", "fall_reduction",
            "gain", "study", "fire_bonus", "environment_fire_reduction", "recovery_threshold",
            "recovery_fraction", "recovery_ticks", "recovery_cooldown", "hunter_bonus",
            "hunter_recovery", "hunter_cooldown", "low_threshold", "low_reduction", "low_knockback",
            "damage_bonus", "low_speed", "low_speed_threshold", "power_cap_bonus", "skill_refund_fraction",
            "pill_healing_bonus", "pill_shield_fraction", "pill_shield_ticks", "pill_shield_cooldown",
            "bones_threshold", "bones_damage_bonus", "bones_knockback_radius", "bones_knockback_strength");
    public static final Codec<FateDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("translation_key").forGetter(FateDefinition::translationKey),
            Codec.STRING.fieldOf("description_key").forGetter(FateDefinition::descriptionKey),
            Codec.intRange(0, 5).fieldOf("tier").forGetter(FateDefinition::tier),
            Codec.intRange(1, 6).fieldOf("cost").forGetter(FateDefinition::cost),
            Codec.STRING.fieldOf("group").forGetter(FateDefinition::group),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(FateDefinition::enabled),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("effects").forGetter(FateDefinition::effects),
            ResourceLocation.CODEC.optionalFieldOf("element").forGetter(FateDefinition::element),
            Codec.intRange(0, 10000).optionalFieldOf("minimum_share", 0).forGetter(FateDefinition::minimumShare),
            Codec.intRange(1, 5).optionalFieldOf("maximum_roots", 5).forGetter(FateDefinition::maximumRoots)
    ).apply(i, FateDefinition::new));
    public FateDefinition {
        if (translationKey.isBlank() || descriptionKey.isBlank() || group.isBlank()
                || tier < 0 || tier > 5 || cost != tier + 1) throw new IllegalArgumentException("Invalid fate metadata");
        effects = Map.copyOf(effects);
        effects.forEach((k, v) -> {
            if (!EFFECTS.contains(k) || !Double.isFinite(v) || v < -0.9 || v > 24000)
                throw new IllegalArgumentException("Invalid fate effect " + k);
            if (k.endsWith("ticks") || k.endsWith("cooldown")) {
                if (v < 1 || v != Math.rint(v)) throw new IllegalArgumentException("Invalid tick count " + k);
            } else if (k.equals("bones_knockback_radius")) {
                if (v <= 0 || v > 16) throw new IllegalArgumentException("Invalid bounded radius " + k);
            } else if (v > 1 || (v < 0 && !k.equals("health"))) {
                throw new IllegalArgumentException("Invalid fraction " + k);
            }
        });
        if (value(effects, "recovery_fraction") > 0 && (value(effects, "recovery_ticks") <= 0
                || value(effects, "recovery_cooldown") <= 0 || value(effects, "recovery_threshold") <= 0))
            throw new IllegalArgumentException("Incomplete recovery effect");
        if (value(effects, "hunter_recovery") > 0 && value(effects, "hunter_cooldown") <= 0)
            throw new IllegalArgumentException("Missing hunter cooldown");
        if (value(effects, "pill_shield_fraction") > 0 && (value(effects, "pill_shield_ticks") <= 0
                || value(effects, "pill_shield_cooldown") <= 0))
            throw new IllegalArgumentException("Incomplete pill shield effect");
        if (value(effects, "bones_threshold") > 0 && (value(effects, "bones_damage_bonus") <= 0
                || value(effects, "bones_knockback_radius") <= 0 || value(effects, "bones_knockback_strength") <= 0))
            throw new IllegalArgumentException("Incomplete tempered bones effect");
    }
    private static double value(Map<String, Double> values, String key) { return values.getOrDefault(key, 0.0); }
    public double effect(String key) { return value(effects, key); }
    public boolean eligible(Optional<SpiritualRoot> root) {
        return enabled && root.isPresent() && (element.isEmpty() || (
                root.get().affinitiesBasisPoints().getOrDefault(element.get(), 0) >= minimumShare
                && root.get().affinitiesBasisPoints().values().stream().filter(v -> v > 0).count() <= maximumRoots));
    }
}
