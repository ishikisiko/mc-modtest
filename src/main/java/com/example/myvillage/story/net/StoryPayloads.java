package com.example.myvillage.story.net;

import com.example.myvillage.story.StoryRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registered by the common mod entry; receivers are installed by the independent client entry. */
public final class StoryPayloads {
    private StoryPayloads() { }
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(StorySnapshotPayload.TYPE, StorySnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> StorySnapshotPayload.receive(payload)));
        registrar.playToClient(StoryDialoguePayload.TYPE, StoryDialoguePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> StoryDialoguePayload.receive(payload)));
        registrar.playToServer(StoryIntentPayload.TYPE, StoryIntentPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) StoryRuntime.choose(player, payload);
                }));
    }
}
