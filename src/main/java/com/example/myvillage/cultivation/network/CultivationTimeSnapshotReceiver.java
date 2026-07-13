package com.example.myvillage.cultivation.network;

import java.util.Objects;
import java.util.function.Consumer;

public final class CultivationTimeSnapshotReceiver {
    private static final Consumer<CultivationTimeSnapshotPayload> NO_OP = ignored -> { };
    private static volatile Consumer<CultivationTimeSnapshotPayload> sink = NO_OP;

    private CultivationTimeSnapshotReceiver() {
    }

    public static void install(Consumer<CultivationTimeSnapshotPayload> newSink) {
        sink = Objects.requireNonNull(newSink, "newSink");
    }

    static void receive(CultivationTimeSnapshotPayload payload) {
        sink.accept(Objects.requireNonNull(payload, "payload"));
    }
}
