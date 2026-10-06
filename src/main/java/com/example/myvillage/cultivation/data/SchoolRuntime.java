package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;

/** The runtime hook a special school uses instead of a move set. */
public enum SchoolRuntime {
    FLYING_SWORD("flying_sword"),
    SPELL("spell"),
    TALISMAN("talisman");

    public static final Codec<SchoolRuntime> CODEC = Codec.STRING.comapFlatMap(
            SchoolRuntime::decode,
            SchoolRuntime::serializedName);

    private final String serializedName;

    SchoolRuntime(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    private static DataResult<SchoolRuntime> decode(String value) {
        return Arrays.stream(values())
                .filter(runtime -> runtime.serializedName.equals(value))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Unknown school runtime: " + value));
    }
}
