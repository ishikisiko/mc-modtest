package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum AdvancementKind {
    ORDINARY,
    BOTTLENECK;

    public static final Codec<AdvancementKind> CODEC = Codec.STRING.comapFlatMap(
            value -> switch (value) {
                case "ordinary" -> com.mojang.serialization.DataResult.success(ORDINARY);
                case "bottleneck" -> com.mojang.serialization.DataResult.success(BOTTLENECK);
                default -> com.mojang.serialization.DataResult.error(
                        () -> "Unsupported advancement kind: " + value);
            },
            AdvancementKind::serializedName);

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
