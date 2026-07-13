package com.example.myvillage.cultivation.network;

import com.example.myvillage.cultivation.time.CultivationTimeRuntime;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.meditation.MeditationMode;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CultivationPayloads {
    private CultivationPayloads() {
    }

    public static void register(PayloadRegistrar registrar) {
        CultivationTimeRuntime.installStatusListener((player, status) ->
                PacketDistributor.sendToPlayer(player, CultivationTimeSnapshotPayload.fromStatus(status)));
        MeditationManager.installStatusListener((player, status) ->
                PacketDistributor.sendToPlayer(player, MeditationStatusPayload.fromStatus(status)));
        registrar.playToClient(
                CultivationSnapshotPayload.TYPE,
                CultivationSnapshotPayload.STREAM_CODEC,
                CultivationPayloads::handleSnapshot);
        registrar.playToClient(
                CultivationTimeSnapshotPayload.TYPE,
                CultivationTimeSnapshotPayload.STREAM_CODEC,
                CultivationPayloads::handleTimeSnapshot);
        registrar.playToClient(
                MeditationStatusPayload.TYPE,
                MeditationStatusPayload.STREAM_CODEC,
                CultivationPayloads::handleMeditationStatus);
        registrar.playToServer(
                MeditationIntentPayload.TYPE,
                MeditationIntentPayload.STREAM_CODEC,
                CultivationPayloads::handleMeditationIntent);
    }

    private static void handleSnapshot(
            CultivationSnapshotPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> CultivationSnapshotReceiver.receive(payload.profile()));
    }

    private static void handleTimeSnapshot(
            CultivationTimeSnapshotPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> CultivationTimeSnapshotReceiver.receive(payload));
    }

    private static void handleMeditationStatus(
            MeditationStatusPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> MeditationStatusReceiver.receive(payload.status()));
    }

    private static void handleMeditationIntent(
            MeditationIntentPayload payload,
            IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            switch (payload.action()) {
                case START_NORMAL -> MeditationManager.requestStart(player, MeditationMode.NORMAL);
                case START_SPIRIT -> MeditationManager.requestStart(player, MeditationMode.SPIRIT);
                case STOP -> MeditationManager.requestStop(player, MeditationStopReason.REQUESTED);
                case START_BREAKTHROUGH -> MeditationManager.requestAdvancement(player);
            }
        });
    }
}
