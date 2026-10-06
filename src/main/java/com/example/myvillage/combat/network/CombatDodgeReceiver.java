package com.example.myvillage.combat.network;

import java.util.Objects;
import java.util.function.Consumer;

/** Client-side sink for dodge payloads; the client installs its handler, the common code calls it. */
public final class CombatDodgeReceiver {
    private static Consumer<CombatDodgeStartPayload> startReceiver = ignored -> {
    };

    private CombatDodgeReceiver() {
    }

    public static void install(Consumer<CombatDodgeStartPayload> start) {
        startReceiver = Objects.requireNonNull(start, "start");
    }

    public static void receiveStart(CombatDodgeStartPayload payload) {
        startReceiver.accept(Objects.requireNonNull(payload, "payload"));
    }
}
