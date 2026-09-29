package com.example.myvillage.combat.network;

import java.util.Objects;
import java.util.function.Consumer;

public final class CombatAttackReceiver {
    private static Consumer<CombatAttackStartPayload> startReceiver = ignored -> {
    };
    private static Consumer<CombatAttackStopPayload> stopReceiver = ignored -> {
    };
    private static Consumer<CombatHitConfirmPayload> hitReceiver = ignored -> {
    };
    private static Consumer<CombatImpactPayload> impactReceiver = ignored -> {
    };

    private CombatAttackReceiver() {
    }

    public static void install(
            Consumer<CombatAttackStartPayload> start,
            Consumer<CombatAttackStopPayload> stop,
            Consumer<CombatHitConfirmPayload> hit,
            Consumer<CombatImpactPayload> impact) {
        startReceiver = Objects.requireNonNull(start, "start");
        stopReceiver = Objects.requireNonNull(stop, "stop");
        hitReceiver = Objects.requireNonNull(hit, "hit");
        impactReceiver = Objects.requireNonNull(impact, "impact");
    }

    public static void receiveStart(CombatAttackStartPayload payload) {
        startReceiver.accept(Objects.requireNonNull(payload, "payload"));
    }

    public static void receiveStop(CombatAttackStopPayload payload) {
        stopReceiver.accept(Objects.requireNonNull(payload, "payload"));
    }

    public static void receiveHit(CombatHitConfirmPayload payload) {
        hitReceiver.accept(Objects.requireNonNull(payload, "payload"));
    }

    public static void receiveImpact(CombatImpactPayload payload) {
        impactReceiver.accept(Objects.requireNonNull(payload, "payload"));
    }
}
