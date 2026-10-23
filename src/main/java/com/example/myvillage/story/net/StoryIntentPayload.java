package com.example.myvillage.story.net;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The only client intent: choose an offered server option using its one-use nonce. */
public record StoryIntentPayload(int entityId, long nonce, String optionId) implements CustomPacketPayload {
    public static final int MAX_OPTION_ID = 64;
    public static final Type<StoryIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "story_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StoryIntentPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public StoryIntentPayload decode(RegistryFriendlyByteBuf b) {
            return StoryWire.decode(() -> new StoryIntentPayload(b.readVarInt(), b.readLong(), b.readUtf(MAX_OPTION_ID)));
        }
        @Override public void encode(RegistryFriendlyByteBuf b, StoryIntentPayload p) {
            b.writeVarInt(p.entityId()); b.writeLong(p.nonce()); b.writeUtf(p.optionId(), MAX_OPTION_ID);
        }
    };
    public StoryIntentPayload {
        if (entityId < 0) throw new IllegalArgumentException("negative story entity id");
        optionId = StoryWire.optionId(optionId);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
