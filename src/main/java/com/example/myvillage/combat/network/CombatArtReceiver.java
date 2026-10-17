package com.example.myvillage.combat.network;

import java.util.Objects;
import java.util.function.Consumer;

/** No client-only class is reachable from dedicated-server registration. */
public final class CombatArtReceiver {
    private static Consumer<CombatArtStatePayload> receiver = ignored -> {};
    private CombatArtReceiver() {}
    public static void install(Consumer<CombatArtStatePayload> value) { receiver = Objects.requireNonNull(value); }
    public static void receive(CombatArtStatePayload value) { receiver.accept(value); }
}
