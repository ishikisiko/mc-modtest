package com.example.myvillage.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QualityPickerArrayPayload(byte selection, long ticket) implements CustomPacketPayload {
    public static final Type<QualityPickerArrayPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "quality_picker_array"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QualityPickerArrayPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BYTE, QualityPickerArrayPayload::selection,
                    ByteBufCodecs.VAR_LONG, QualityPickerArrayPayload::ticket, QualityPickerArrayPayload::new);

    @Override public Type<QualityPickerArrayPayload> type() { return TYPE; }
}
