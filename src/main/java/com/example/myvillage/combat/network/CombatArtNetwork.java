package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.session.CombatSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid=MyVillageMod.MOD_ID, bus=EventBusSubscriber.Bus.MOD)
public final class CombatArtNetwork {
    private CombatArtNetwork() {}
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("advanced-arts-1");
        registrar.playToServer(CombatArtIntentPayload.TYPE, CombatArtIntentPayload.STREAM_CODEC,
                (payload,context) -> {
                    if (context.player() instanceof ServerPlayer player)
                        context.enqueueWork(() -> CombatSessionManager.handleArtIntent(player,payload.slot()));
                });
        registrar.playToClient(CombatArtStatePayload.TYPE, CombatArtStatePayload.STREAM_CODEC,
                (payload,context) -> context.enqueueWork(() -> CombatArtReceiver.receive(payload)));
    }
}
