package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record RealmStageDefinition(ResourceLocation id, String translationKey, int sortOrder) {
    private static final Codec<SerializedStage> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(SerializedStage::id),
            Codec.STRING.fieldOf("translation_key").forGetter(SerializedStage::translationKey),
            Codec.INT.fieldOf("sort_order").forGetter(SerializedStage::sortOrder)
    ).apply(instance, SerializedStage::new));

    public static final Codec<RealmStageDefinition> CODEC = SERIALIZED_CODEC
            .comapFlatMap(SerializedStage::decode, RealmStageDefinition::serialize);

    public RealmStageDefinition {
        Objects.requireNonNull(id, "id");
        requireTranslationKey(translationKey);
        if (sortOrder < 0) {
            throw new IllegalArgumentException("Realm stage sort_order must be non-negative, got " + sortOrder);
        }
    }

    private static void requireTranslationKey(String translationKey) {
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException("Realm stage translation_key must not be blank");
        }
    }

    private SerializedStage serialize() {
        return new SerializedStage(id, translationKey, sortOrder);
    }

    private record SerializedStage(ResourceLocation id, String translationKey, int sortOrder) {
        private DataResult<RealmStageDefinition> decode() {
            try {
                return DataResult.success(new RealmStageDefinition(id, translationKey, sortOrder));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
