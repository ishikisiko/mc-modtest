package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;

public enum TechniqueCategory {
    CORE("core"),
    ACTIVE("active"),
    MOVEMENT("movement"),
    BODY("body");

    public static final Codec<TechniqueCategory> CODEC = Codec.STRING.comapFlatMap(
            TechniqueCategory::decode,
            TechniqueCategory::serializedName);

    private final String serializedName;

    TechniqueCategory(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    private static DataResult<TechniqueCategory> decode(String value) {
        return Arrays.stream(values())
                .filter(category -> category.serializedName.equals(value))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Unknown technique category: " + value));
    }
}
