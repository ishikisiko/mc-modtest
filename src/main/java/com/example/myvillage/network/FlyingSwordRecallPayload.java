package com.example.myvillage.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Empty request: the server derives the owner and vehicle. */
public record FlyingSwordRecallPayload() implements CustomPacketPayload {
    public static final Type<FlyingSwordRecallPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("myvillage", "flying_sword_recall"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlyingSwordRecallPayload> STREAM_CODEC = StreamCodec.unit(new FlyingSwordRecallPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
