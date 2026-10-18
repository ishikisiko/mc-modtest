package com.example.myvillage.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Only a held-item identity, hand and bounded index; never arbitrary components or block data. */
public record CourtyardVariantPayload(ResourceLocation item,boolean offhand,int variant) implements CustomPacketPayload {
    public static final Type<CourtyardVariantPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("myvillage","courtyard_variant"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CourtyardVariantPayload> STREAM_CODEC=StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,CourtyardVariantPayload::item,ByteBufCodecs.BOOL,CourtyardVariantPayload::offhand,
            ByteBufCodecs.VAR_INT,CourtyardVariantPayload::variant,CourtyardVariantPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
