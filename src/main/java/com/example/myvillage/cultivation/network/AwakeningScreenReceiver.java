package com.example.myvillage.cultivation.network;

import java.util.Objects;
import java.util.function.Consumer;

/** Client installs the sink; common networking does not import a screen or Minecraft client. */
public final class AwakeningScreenReceiver {
    private static volatile Consumer<AwakeningOpenPayload> sink = ignored -> { };
    private AwakeningScreenReceiver() { }
    public static void install(Consumer<AwakeningOpenPayload> newSink) { sink = Objects.requireNonNull(newSink); }
    static void receive(AwakeningOpenPayload payload) { sink.accept(Objects.requireNonNull(payload)); }
}
