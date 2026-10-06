package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;

/** 流派 kind: a weapon-family school (剑, 枪, 拳掌) or a special school with its own runtime. */
public enum SchoolKind {
    WEAPON("weapon"),
    SPECIAL("special");

    public static final Codec<SchoolKind> CODEC = Codec.STRING.comapFlatMap(
            SchoolKind::decode,
            SchoolKind::serializedName);

    private final String serializedName;

    SchoolKind(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    private static DataResult<SchoolKind> decode(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.serializedName.equals(value))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Unknown school kind: " + value));
    }
}
