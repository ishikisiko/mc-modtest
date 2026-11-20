package com.example.myvillage.cultivation.network;

import com.example.myvillage.cultivation.nascent.NascentTrial;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public record NascentStatusPayload(NascentTrial.Phase phase, int remaining) implements CustomPacketPayload {
    public static final Type<NascentStatusPayload> TYPE = new Type<>(ResourceLocation.parse("yuanxian:nascent_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NascentStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeEnum(value.phase);
                buffer.writeVarInt(value.remaining);
            },
            buffer -> new NascentStatusPayload(buffer.readEnum(NascentTrial.Phase.class), buffer.readVarInt()));

    private static Consumer<NascentStatusPayload> receiver = value -> { };

    public NascentStatusPayload {
        if (remaining < 0) throw new IllegalArgumentException("Negative nascent time");
    }

    public static void installReceiver(Consumer<NascentStatusPayload> value) { receiver = value; }
    public static void receive(NascentStatusPayload value) { receiver.accept(value); }

    @Override public Type<NascentStatusPayload> type() { return TYPE; }
}
