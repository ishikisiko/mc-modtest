package com.example.myvillage.network;

import com.example.myvillage.combat.network.CombatPayloads;
import com.example.myvillage.cultivation.network.CultivationPayloads;
import com.example.myvillage.entity.RideableFlyingSwordEntity;
import com.example.myvillage.sim.runtime.net.WorldSimPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModPayloads {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModPayloads.class);
    /** Protocol 20 adds saved crafting aptitudes and server-derived quality caps to the owning snapshot (19: stele sessions). Protocol 19 adds stele screen open/close and a session token to bounded fate intents (18: fate snapshot/budget; 17: empty charging-only cancellation intent; 16: qi status and charge/release intents; 15: sect tasks; 14: scripture hall; 13: sect dialogue; 11: study progress; 10: core-technique switch). */
    public static final String PROTOCOL_VERSION = "24";

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
        CombatPayloads.register(registrar);
        WorldSimPayloads.register(registrar);
        LOGGER.info(
                "Payload handlers registered: flying-sword input, cultivation snapshots (time with week, profile v4)/intents/core-technique switch/status with study progress, combat intents/snapshots/actions, world-sim queries/snapshots, and the sect dialogue/intent (protocol {})",
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
