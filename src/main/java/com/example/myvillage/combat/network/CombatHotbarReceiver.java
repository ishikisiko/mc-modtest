package com.example.myvillage.combat.network;

import java.util.Objects;
import java.util.function.Consumer;

public final class CombatHotbarReceiver {
    private static Consumer<CombatHotbarPayload> receiver=ignored->{};
    private CombatHotbarReceiver(){}
    public static void install(Consumer<CombatHotbarPayload> sink){receiver=Objects.requireNonNull(sink);}
    public static void receive(CombatHotbarPayload payload){receiver.accept(payload);}
}
