package com.example.myvillage.cultivation.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpiritTrialIntentPayload(UUID attempt, int strike, SpiritTrialStatusPayload.Action action)
        implements CustomPacketPayload {
    public static final Type<SpiritTrialIntentPayload> TYPE = new Type<>(ResourceLocation.parse("yuanxian:spirit_trial_intent"));
    public static final StreamCodec<FriendlyByteBuf, SpiritTrialIntentPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeUUID(packet.attempt());
                buffer.writeByte(packet.strike());
                buffer.writeEnum(packet.action());
            },
            buffer -> new SpiritTrialIntentPayload(buffer.readUUID(), buffer.readUnsignedByte(),
                    buffer.readEnum(SpiritTrialStatusPayload.Action.class)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
