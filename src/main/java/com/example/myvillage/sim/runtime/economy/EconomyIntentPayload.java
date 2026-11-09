package com.example.myvillage.sim.runtime.economy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EconomyIntentPayload(int menuId, long token, int sequence, int row) implements CustomPacketPayload {
    public static final Type<EconomyIntentPayload> TYPE = new Type<>(ResourceLocation.parse("myvillage:economy_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EconomyIntentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EconomyIntentPayload::menuId, ByteBufCodecs.VAR_LONG, EconomyIntentPayload::token,
            ByteBufCodecs.VAR_INT, EconomyIntentPayload::sequence, ByteBufCodecs.VAR_INT, EconomyIntentPayload::row, EconomyIntentPayload::new);
    public EconomyIntentPayload { if (menuId < 0 || menuId > 255 || sequence < 0 || sequence > 30000 || row < 0 || row >= 32) throw new IllegalArgumentException("兑换意图越界"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
