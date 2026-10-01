package com.example.myvillage.combat.definition;

import java.util.Locale;
import java.util.Optional;

/**
 * The shape of a move's strike. Presentation reads it to choose the trail form: a thrust sweeps
 * no area and draws one streak along the blade, a cut draws a swept band.
 */
public enum MoveKind {
    THRUST,
    CUT;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<MoveKind> bySerializedName(String name) {
        for (MoveKind kind : values()) {
            if (kind.serializedName().equals(name)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
