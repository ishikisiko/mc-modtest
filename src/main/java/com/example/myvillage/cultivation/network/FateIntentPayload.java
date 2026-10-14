package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Action, seen draft revision and one bounded slot/mask. No gameplay value or authority. */
public record FateIntentPayload(byte action, long revision, byte value) implements CustomPacketPayload {
    public static final Type<FateIntentPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "fate_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FateIntentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, FateIntentPayload::action, ByteBufCodecs.VAR_LONG, FateIntentPayload::revision,
            ByteBufCodecs.BYTE, FateIntentPayload::value, FateIntentPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
