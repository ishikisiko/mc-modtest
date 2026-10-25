package com.example.myvillage.npc;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.story.net.StoryDialoguePayload;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Reuses the bounded paper-page format; its channel and authority are independent of story. */
public record NpcDialoguePayload(StoryDialoguePayload page) implements CustomPacketPayload {
    public static final Type<NpcDialoguePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "npc_dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDialoguePayload> STREAM_CODEC =
            StoryDialoguePayload.STREAM_CODEC.map(NpcDialoguePayload::new, NpcDialoguePayload::page);
    private static Consumer<NpcDialoguePayload> receiver = ignored -> { };
    public NpcDialoguePayload { Objects.requireNonNull(page); }
    public static void installReceiver(Consumer<NpcDialoguePayload> next) { receiver = Objects.requireNonNull(next); }
    public static void receive(NpcDialoguePayload page) { receiver.accept(page); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
