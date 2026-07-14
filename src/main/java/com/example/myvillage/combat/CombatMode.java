package com.example.myvillage.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public enum CombatMode {
    VANILLA,
    CULTIVATION;

    public static final Codec<CombatMode> CODEC = Codec.STRING.comapFlatMap(
            value -> switch (value) {
                case "vanilla" -> DataResult.success(VANILLA);
                case "cultivation" -> DataResult.success(CULTIVATION);
                default -> DataResult.error(() -> "Unknown combat mode: " + value);
            },
            mode -> mode == VANILLA ? "vanilla" : "cultivation");

    public CombatMode toggled() {
        return this == VANILLA ? CULTIVATION : VANILLA;
    }
}
