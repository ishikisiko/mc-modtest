package com.example.myvillage.network;

import com.example.myvillage.entity.RideableFlyingSwordEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ModPayloads {
    private static final String PROTOCOL_VERSION = "1";

    private ModPayloads() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModPayloads::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToServer(
                FlyingSwordInputPayload.TYPE,
                FlyingSwordInputPayload.STREAM_CODEC,
                ModPayloads::handleFlyingSwordInput);
    }

    private static void handleFlyingSwordInput(
            FlyingSwordInputPayload payload,
            IPayloadContext context) {
        if (!payload.hasOnlyKnownFlags() || !(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.getVehicle() instanceof RideableFlyingSwordEntity sword)
                || !sword.hasPassenger(player)
                || !sword.isOwnedBy(player)
                || sword.level() != player.level()
                || !player.isAlive()
                || player.isRemoved()) {
            return;
        }

        sword.acceptInput(payload.flags(), player.serverLevel().getGameTime());
    }
}
