package com.example.myvillage.combat.network;

import java.util.Objects;
import java.util.function.Consumer;

public final class CombatAttackReceiver {
    private static Consumer<CombatAttackStartPayload> startReceiver = ignored -> {
    };
    private static Consumer<CombatAttackStopPayload> stopReceiver = ignored -> {
    };

    private CombatAttackReceiver() {
    }

    public static void install(
            Consumer<CombatAttackStartPayload> start,
            Consumer<CombatAttackStopPayload> stop) {
        startReceiver = Objects.requireNonNull(start, "start");
        stopReceiver = Objects.requireNonNull(stop, "stop");
    }

    public static void receiveStart(CombatAttackStartPayload payload) {
        startReceiver.accept(Objects.requireNonNull(payload, "payload"));
    }

    public static void receiveStop(CombatAttackStopPayload payload) {
        stopReceiver.accept(Objects.requireNonNull(payload, "payload"));
    }
}
