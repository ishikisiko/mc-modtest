package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Closing an older screen cannot close the session granted by a more recent stele click. */
public record AwakeningClosePayload(long sessionToken) implements CustomPacketPayload {
    public static final Type<AwakeningClosePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "awakening_close"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AwakeningClosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, AwakeningClosePayload::sessionToken, AwakeningClosePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
