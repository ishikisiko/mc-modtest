package com.example.myvillage.cultivation.network;

import com.example.myvillage.cultivation.CultivationProfile;

import java.util.Objects;
import java.util.function.Consumer;

public final class CultivationSnapshotReceiver {
    private static final Consumer<CultivationProfile> NO_OP = ignored -> { };
    private static volatile Consumer<CultivationProfile> sink = NO_OP;

    private CultivationSnapshotReceiver() {
    }

    public static void install(Consumer<CultivationProfile> newSink) {
        sink = Objects.requireNonNull(newSink, "newSink");
    }

    static void receive(CultivationProfile profile) {
        sink.accept(Objects.requireNonNull(profile, "profile"));
    }
}
