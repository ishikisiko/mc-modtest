package com.example.myvillage.combat.network;
import java.util.function.Consumer;
public final class SkillBindingsReceiver {
    private static Consumer<SkillBindingsSnapshotPayload> receiver=p->{};
    private SkillBindingsReceiver(){}
    public static void install(Consumer<SkillBindingsSnapshotPayload> handler){receiver=handler;}
    public static void receive(SkillBindingsSnapshotPayload payload){receiver.accept(payload);}
}
