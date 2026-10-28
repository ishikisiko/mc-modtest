package com.example.myvillage.cultivation.network;

import java.util.function.Consumer;

/** Dedicated-server-safe receiver bridge. */
public final class MeditationPoseReceiver {
    private static Consumer<MeditationPosePayload> receiver = p -> {};
    public static void install(Consumer<MeditationPosePayload> value) { receiver = java.util.Objects.requireNonNull(value); }
    public static void receive(MeditationPosePayload payload) { receiver.accept(payload); }
    private MeditationPoseReceiver() {}
}
