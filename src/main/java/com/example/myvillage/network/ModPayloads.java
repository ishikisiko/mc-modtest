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
    /** Protocol 43 adds fate-granted ability IDs to existing binding/hotbar semantics without changing packet fields. Protocol 40 adds the owner-only four-slot combat hotbar readout. Protocol 37 adds workpiece/temper components and the forging workshop menu. Protocol 36 adds bounded held-building variant selection. Protocol 35 adds the bounded frozen-plan atlas to world-sim snapshots. Protocol 20 adds saved crafting aptitudes and server-derived quality caps to the owning snapshot (19: stele sessions). Protocol 19 adds stele screen open/close and a session token to bounded fate intents (18: fate snapshot/budget; 17: empty charging-only cancellation intent; 16: qi status and charge/release intents; 15: sect tasks; 14: scripture hall; 13: sect dialogue; 11: study progress; 10: core-technique switch). */
    // Protocol 44 adds owner-only story snapshots, NPC dialogue pages and bounded session intents.
    // Protocol 48 adds flight boost/safe landing, talisman aptitude, and formal tribulation snapshots.
    // Protocol 50 adds the handheld forging hammer and persistent shared anvil inventory.
    // Protocol 54 adds at most eight server-authored capital entries to the existing atlas section.
    public static final String PROTOCOL_VERSION = "54";

    private ModPayloads() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModPayloads::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(com.example.myvillage.cultivation.tribulation.TribulationStatusPayload.TYPE,
                com.example.myvillage.cultivation.tribulation.TribulationStatusPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.example.myvillage.cultivation.tribulation.TribulationStatusPayload.receive(payload)));
        registrar.playToClient(com.example.myvillage.npc.NpcDialoguePayload.TYPE,
                com.example.myvillage.npc.NpcDialoguePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.example.myvillage.npc.NpcDialoguePayload.receive(payload)));
        registrar.playToServer(com.example.myvillage.npc.NpcDialogueIntent.TYPE,
                com.example.myvillage.npc.NpcDialogueIntent.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer p) com.example.myvillage.npc.NpcDialogue.choose(p, payload);
                }));
        registrar.playToServer(CourtyardVariantPayload.TYPE,CourtyardVariantPayload.STREAM_CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player)context.enqueueWork(()->
                    com.example.myvillage.courtyard.CourtyardAssemblyItem.select(player,
                            payload.offhand()?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND,
                            payload.item(),payload.variant()));
        });
        registrar.playToServer(QualityPickerPayload.TYPE, QualityPickerPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) context.enqueueWork(() ->
                    com.example.myvillage.item.QualityPicker.give(player, payload.item(), payload.quality(), payload.trait(), payload.fullStack()));
        });
        registrar.playToServer(
                FlyingSwordInputPayload.TYPE,
                FlyingSwordInputPayload.STREAM_CODEC,
                ModPayloads::handleFlyingSwordInput);
        registrar.playToServer(com.example.myvillage.sim.runtime.commerce.CommerceIntentPayload.TYPE,
                com.example.myvillage.sim.runtime.commerce.CommerceIntentPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) context.enqueueWork(() -> {
                        if (player.containerMenu instanceof com.example.myvillage.sim.runtime.commerce.CommerceMenu menu)
                            menu.act(player, payload);
                    });
                });
        registrar.playToServer(FlyingSwordRecallPayload.TYPE, FlyingSwordRecallPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer p
                            && p.isAlive() && !p.isRemoved()
                            && p.getVehicle() instanceof RideableFlyingSwordEntity sword && sword.isOwnedBy(p)
                            && sword.hasPassenger(p) && sword.level() == p.level())
                        sword.requestLanding();
                }));
        CultivationPayloads.register(registrar);
        CombatPayloads.register(registrar);
        WorldSimPayloads.register(registrar);
        com.example.myvillage.story.net.StoryPayloads.register(registrar);
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

        sword.acceptInput(player, payload.flags(), player.serverLevel().getGameTime());
    }
}
