package com.example.myvillage.combat.network;

import java.util.Objects;
import java.util.function.Consumer;

public final class CombatSkillReceiver {
    private static Consumer<CombatSkillStatePayload> receiver = ignored -> {};
    private CombatSkillReceiver() {}
    public static void install(Consumer<CombatSkillStatePayload> value) { receiver = Objects.requireNonNull(value); }
    public static void receive(CombatSkillStatePayload payload) { receiver.accept(payload); }
}
