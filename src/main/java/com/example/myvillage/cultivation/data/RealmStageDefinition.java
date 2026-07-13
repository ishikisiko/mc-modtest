package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

public record RealmStageDefinition(
        ResourceLocation id,
        String translationKey,
        int sortOrder,
        Optional<Long> cultivationCap,
        Optional<Integer> spiritStoneCost,
        Optional<AdvancementDefinition> advancement) {
    private static final Codec<SerializedStage> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(SerializedStage::id),
            Codec.STRING.fieldOf("translation_key").forGetter(SerializedStage::translationKey),
            Codec.INT.fieldOf("sort_order").forGetter(SerializedStage::sortOrder),
            Codec.LONG.optionalFieldOf("cultivation_cap").forGetter(SerializedStage::cultivationCap),
            Codec.INT.optionalFieldOf("spirit_stone_cost").forGetter(SerializedStage::spiritStoneCost),
            AdvancementDefinition.CODEC.optionalFieldOf("advancement").forGetter(SerializedStage::advancement)
    ).apply(instance, SerializedStage::new));

    public static final Codec<RealmStageDefinition> CODEC = SERIALIZED_CODEC
            .comapFlatMap(SerializedStage::decode, RealmStageDefinition::serialize);

    public RealmStageDefinition {
        Objects.requireNonNull(id, "id");
        requireTranslationKey(translationKey);
        if (sortOrder < 0) {
            throw new IllegalArgumentException("Realm stage sort_order must be non-negative, got " + sortOrder);
        }
        cultivationCap = Objects.requireNonNull(cultivationCap, "cultivationCap");
        cultivationCap.ifPresent(cap -> {
            if (cap <= 0) {
                throw new IllegalArgumentException(
                        "Realm stage cultivation_cap must be positive, got " + cap);
            }
            stabilityCapFor(cap);
        });
        spiritStoneCost = Objects.requireNonNull(spiritStoneCost, "spiritStoneCost");
        spiritStoneCost.ifPresent(cost -> {
            if (cost <= 0) {
                throw new IllegalArgumentException(
                        "Realm stage spirit_stone_cost must be positive, got " + cost);
            }
        });
        if (cultivationCap.isPresent() && spiritStoneCost.isEmpty()) {
            throw new IllegalArgumentException(
                    "A realm stage with cultivation_cap must declare spirit_stone_cost");
        }
        advancement = Objects.requireNonNull(advancement, "advancement");
        if (cultivationCap.isPresent() && advancement.isPresent()) {
            int stabilityCap = stabilityCapFor(cultivationCap.orElseThrow());
            AdvancementDefinition definition = advancement.orElseThrow();
            if (definition.requiredStability() != stabilityCap) {
                throw new IllegalArgumentException(
                        "Advancement required_stability must equal half of cultivation_cap, got "
                                + definition.requiredStability() + " for cap " + cultivationCap.orElseThrow());
            }
            int expectedCost = stabilityCap - stabilityCap / 2;
            if (definition.stabilityCost() != expectedCost) {
                throw new IllegalArgumentException(
                        "Advancement stability_cost must retain half of required stability, got "
                                + definition.stabilityCost() + " for requirement " + stabilityCap);
            }
        }
    }

    public RealmStageDefinition(ResourceLocation id, String translationKey, int sortOrder) {
        this(id, translationKey, sortOrder, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public Optional<Integer> stabilityCap() {
        return cultivationCap.map(RealmStageDefinition::stabilityCapFor);
    }

    public static int stabilityCapFor(long cultivationCap) {
        if (cultivationCap < 2) {
            throw new IllegalArgumentException(
                    "Realm stage cultivation_cap must be at least 2 to derive stability, got "
                            + cultivationCap);
        }
        long stabilityCap = cultivationCap / 2;
        if (stabilityCap > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Derived stability cap exceeds integer range for cultivation_cap "
                            + cultivationCap);
        }
        return (int) stabilityCap;
    }

    private static void requireTranslationKey(String translationKey) {
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException("Realm stage translation_key must not be blank");
        }
    }

    private SerializedStage serialize() {
        return new SerializedStage(id, translationKey, sortOrder, cultivationCap, spiritStoneCost, advancement);
    }

    private record SerializedStage(
            ResourceLocation id,
            String translationKey,
            int sortOrder,
            Optional<Long> cultivationCap,
            Optional<Integer> spiritStoneCost,
            Optional<AdvancementDefinition> advancement) {
        private DataResult<RealmStageDefinition> decode() {
            try {
                return DataResult.success(new RealmStageDefinition(
                        id, translationKey, sortOrder, cultivationCap, spiritStoneCost, advancement));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
