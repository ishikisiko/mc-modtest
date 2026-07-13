package com.example.myvillage.cultivation.network;

import com.example.myvillage.cultivation.meditation.MeditationStatus;

import java.util.Objects;
import java.util.function.Consumer;

public final class MeditationStatusReceiver {
    private static final Consumer<MeditationStatus> NO_OP = ignored -> { };
    private static volatile Consumer<MeditationStatus> sink = NO_OP;

    private MeditationStatusReceiver() {
    }

    public static void install(Consumer<MeditationStatus> newSink) {
        sink = Objects.requireNonNull(newSink, "newSink");
    }

    static void receive(MeditationStatus status) {
        sink.accept(Objects.requireNonNull(status, "status"));
    }
}
