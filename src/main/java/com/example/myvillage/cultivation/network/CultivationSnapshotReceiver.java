package com.example.myvillage.cultivation.network;

import com.example.myvillage.cultivation.CultivationProfile;

import java.util.Objects;
import java.util.function.Consumer;

public final class CultivationSnapshotReceiver {
    private static final Consumer<CultivationSnapshotPayload> NO_OP = ignored -> { };
    private static volatile Consumer<CultivationSnapshotPayload> sink = NO_OP;

    private CultivationSnapshotReceiver() {
    }

    public static void install(Consumer<CultivationSnapshotPayload> newSink) {
        sink = Objects.requireNonNull(newSink, "newSink");
    }

    static void receive(CultivationSnapshotPayload profile) {
        sink.accept(Objects.requireNonNull(profile, "profile"));
    }
}
