package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The client supplies a technique id. Price and eligibility come from the server. */
public record HeartManualRequestPayload(ResourceLocation techniqueId) implements CustomPacketPayload {
    public static final Type<HeartManualRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "heart_manual_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HeartManualRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC, HeartManualRequestPayload::techniqueId,
                    HeartManualRequestPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
