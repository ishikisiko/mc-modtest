package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record AdvancementDefinition(
        ResourceLocation targetRealm,
        ResourceLocation targetStage,
        AdvancementKind kind,
        int durationTicks,
        int requiredStability,
        int stabilityCost,
        int interruptionStabilityLoss) {
    private static final Codec<SerializedAdvancement> SERIALIZED_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("target_realm")
                            .forGetter(SerializedAdvancement::targetRealm),
                    ResourceLocation.CODEC.fieldOf("target_stage")
                            .forGetter(SerializedAdvancement::targetStage),
                    AdvancementKind.CODEC.fieldOf("kind")
                            .forGetter(SerializedAdvancement::kind),
                    Codec.INT.fieldOf("duration_ticks")
                            .forGetter(SerializedAdvancement::durationTicks),
                    Codec.INT.fieldOf("required_stability")
                            .forGetter(SerializedAdvancement::requiredStability),
                    Codec.INT.fieldOf("stability_cost")
                            .forGetter(SerializedAdvancement::stabilityCost),
                    Codec.INT.fieldOf("interruption_stability_loss")
                            .forGetter(SerializedAdvancement::interruptionStabilityLoss)
            ).apply(instance, SerializedAdvancement::new));

    public static final Codec<AdvancementDefinition> CODEC = SERIALIZED_CODEC
            .comapFlatMap(SerializedAdvancement::decode, AdvancementDefinition::serialize);

    public AdvancementDefinition {
        Objects.requireNonNull(targetRealm, "targetRealm");
        Objects.requireNonNull(targetStage, "targetStage");
        Objects.requireNonNull(kind, "kind");
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Advancement duration_ticks must be positive, got " + durationTicks);
        }
        requireNonNegative(requiredStability, "required_stability");
        requireNonNegative(stabilityCost, "stability_cost");
        requireNonNegative(interruptionStabilityLoss, "interruption_stability_loss");
        if (stabilityCost > requiredStability) {
            throw new IllegalArgumentException(
                    "Advancement stability_cost must not exceed required_stability");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "Advancement " + name + " must be non-negative, got " + value);
        }
    }

    private SerializedAdvancement serialize() {
        return new SerializedAdvancement(
                targetRealm,
                targetStage,
                kind,
                durationTicks,
                requiredStability,
                stabilityCost,
                interruptionStabilityLoss);
    }

    private record SerializedAdvancement(
            ResourceLocation targetRealm,
            ResourceLocation targetStage,
            AdvancementKind kind,
            int durationTicks,
            int requiredStability,
            int stabilityCost,
            int interruptionStabilityLoss) {
        private DataResult<AdvancementDefinition> decode() {
            try {
                return DataResult.success(new AdvancementDefinition(
                        targetRealm,
                        targetStage,
                        kind,
                        durationTicks,
                        requiredStability,
                        stabilityCost,
                        interruptionStabilityLoss));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
