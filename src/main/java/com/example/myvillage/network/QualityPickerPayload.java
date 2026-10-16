package com.example.myvillage.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded selection only; clients cannot submit arbitrary ItemStack components. */
public record QualityPickerPayload(ResourceLocation item, int quality, int trait, boolean fullStack) implements CustomPacketPayload {
    public static final Type<QualityPickerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("myvillage", "quality_picker"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QualityPickerPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, QualityPickerPayload::item,
            ByteBufCodecs.VAR_INT, QualityPickerPayload::quality,
            ByteBufCodecs.VAR_INT, QualityPickerPayload::trait,
            ByteBufCodecs.BOOL, QualityPickerPayload::fullStack, QualityPickerPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
