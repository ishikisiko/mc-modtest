package com.example.myvillage.npc;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.story.net.StoryIntentPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Entity id, one-use server nonce, and offered option only; no client-supplied identity or result. */
public record NpcDialogueIntent(StoryIntentPayload choice) implements CustomPacketPayload {
    public static final Type<NpcDialogueIntent> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "npc_dialogue_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDialogueIntent> STREAM_CODEC =
            StoryIntentPayload.STREAM_CODEC.map(NpcDialogueIntent::new, NpcDialogueIntent::choice);
    public NpcDialogueIntent { java.util.Objects.requireNonNull(choice); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
