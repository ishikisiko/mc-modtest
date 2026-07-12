package com.example.myvillage.cultivation.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CultivationPayloads {
    private CultivationPayloads() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                CultivationSnapshotPayload.TYPE,
                CultivationSnapshotPayload.STREAM_CODEC,
                CultivationPayloads::handleSnapshot);
    }

    private static void handleSnapshot(
            CultivationSnapshotPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> CultivationSnapshotReceiver.receive(payload.profile()));
    }
}
