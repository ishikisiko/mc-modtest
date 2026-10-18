package com.example.myvillage.sim.runtime.commerce;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Only a session-bound offer and bounded requested quantity; never client prices or items. */
public record CommerceIntentPayload(int menuId,long token,int sequence,int offer,int quantity) implements CustomPacketPayload {
    // Wire safety bound; the server further limits quantity by authored stock_limit.
    public static final int MAX_QUANTITY=20_000;
    public static final Type<CommerceIntentPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("myvillage","commerce_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CommerceIntentPayload> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,CommerceIntentPayload::menuId,ByteBufCodecs.VAR_LONG,CommerceIntentPayload::token,
            ByteBufCodecs.VAR_INT,CommerceIntentPayload::sequence,ByteBufCodecs.VAR_INT,CommerceIntentPayload::offer,
            ByteBufCodecs.VAR_INT,CommerceIntentPayload::quantity,
            CommerceIntentPayload::new);
    public CommerceIntentPayload {
        if(menuId<0||menuId>255||sequence<0||sequence>30_000||offer<0||offer>=32||quantity<1||quantity>MAX_QUANTITY)
            throw new IllegalArgumentException("Invalid commerce intent bounds");
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
