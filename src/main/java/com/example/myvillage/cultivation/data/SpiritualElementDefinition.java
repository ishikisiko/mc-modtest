package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Optional;

public record SpiritualElementDefinition(
        String translationKey,
        int sortOrder,
        Optional<Integer> displayColor,
        int awakeningWeight) {
    public static final int MAX_DISPLAY_COLOR = 0xFFFFFF;
    public static final int MAX_AWAKENING_WEIGHT = 1_000_000;

    private static final Codec<SerializedElement> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("translation_key").forGetter(SerializedElement::translationKey),
            Codec.INT.fieldOf("sort_order").forGetter(SerializedElement::sortOrder),
            Codec.INT.optionalFieldOf("display_color").forGetter(SerializedElement::displayColor),
            Codec.intRange(0, MAX_AWAKENING_WEIGHT)
                    .optionalFieldOf("awakening_weight", 1)
                    .forGetter(SerializedElement::awakeningWeight)
    ).apply(instance, SerializedElement::new));

    public static final Codec<SpiritualElementDefinition> CODEC = SERIALIZED_CODEC
            .comapFlatMap(SerializedElement::decode, SpiritualElementDefinition::serialize);

    public SpiritualElementDefinition {
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException("Spiritual element translation_key must not be blank");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException(
                    "Spiritual element sort_order must be non-negative, got " + sortOrder);
        }
        displayColor = Objects.requireNonNull(displayColor, "displayColor");
        displayColor.ifPresent(color -> {
            if (color < 0 || color > MAX_DISPLAY_COLOR) {
                throw new IllegalArgumentException(
                        "Spiritual element display_color must be in 0..16777215, got " + color);
            }
        });
        if (awakeningWeight < 0 || awakeningWeight > MAX_AWAKENING_WEIGHT) {
            throw new IllegalArgumentException(
                    "Spiritual element awakening_weight must be in 0..1000000, got "
                            + awakeningWeight);
        }
    }

    public SpiritualElementDefinition(
            String translationKey,
            int sortOrder,
            Optional<Integer> displayColor) {
        this(translationKey, sortOrder, displayColor, 1);
    }

    private SerializedElement serialize() {
        return new SerializedElement(translationKey, sortOrder, displayColor, awakeningWeight);
    }

    private record SerializedElement(
            String translationKey,
            int sortOrder,
            Optional<Integer> displayColor,
            int awakeningWeight) {
        private DataResult<SpiritualElementDefinition> decode() {
            try {
                return DataResult.success(new SpiritualElementDefinition(
                        translationKey, sortOrder, displayColor, awakeningWeight));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
