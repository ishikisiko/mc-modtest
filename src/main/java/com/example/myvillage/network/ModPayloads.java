package com.example.myvillage.network;

import com.example.myvillage.cultivation.network.CultivationPayloads;
import com.example.myvillage.entity.RideableFlyingSwordEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModPayloads {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModPayloads.class);
    private static final String PROTOCOL_VERSION = "3";

    private ModPayloads() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModPayloads::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                FlyingSwordInputPayload.TYPE,
                FlyingSwordInputPayload.STREAM_CODEC,
                ModPayloads::handleFlyingSwordInput);
        CultivationPayloads.register(registrar);
        LOGGER.info(
                "Payload handlers registered: flying-sword input plus cultivation profile/time/session/intent (protocol {})",
                PROTOCOL_VERSION);
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
