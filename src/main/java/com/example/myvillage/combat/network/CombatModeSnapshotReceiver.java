package com.example.myvillage.combat.network;

import java.util.Objects;
import java.util.function.Consumer;

public final class CombatModeSnapshotReceiver {
    private static Consumer<CombatModeSnapshotPayload> receiver = ignored -> {
    };

    private CombatModeSnapshotReceiver() {
    }

    public static void install(Consumer<CombatModeSnapshotPayload> replacement) {
        receiver = Objects.requireNonNull(replacement, "replacement");
    }

    public static void receive(CombatModeSnapshotPayload payload) {
        receiver.accept(Objects.requireNonNull(payload, "payload"));
    }
}
