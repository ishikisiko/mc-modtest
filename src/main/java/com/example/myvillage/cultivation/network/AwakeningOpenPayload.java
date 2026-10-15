package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Owning-client presentation request, emitted after the authoritative cultivation snapshot. */
public record AwakeningOpenPayload(BlockPos stelePos, long sessionToken) implements CustomPacketPayload {
    public static final Type<AwakeningOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "awakening_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AwakeningOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AwakeningOpenPayload::stelePos,
            ByteBufCodecs.VAR_LONG, AwakeningOpenPayload::sessionToken, AwakeningOpenPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
