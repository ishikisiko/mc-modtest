package com.example.myvillage.cultivation.network;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpiritTrialStatusPayload(UUID attempt, int strike, Action action) implements CustomPacketPayload {
    public enum Action { NONE, SCATTER, BLOCK, UNITE }

    public static final Type<SpiritTrialStatusPayload> TYPE =
            new Type<>(ResourceLocation.parse("yuanxian:spirit_trial_status"));
    private static final UUID INACTIVE = new UUID(0, 0);
    public static final SpiritTrialStatusPayload IDLE = new SpiritTrialStatusPayload(INACTIVE, 0, Action.NONE);
    public static final StreamCodec<FriendlyByteBuf, SpiritTrialStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeUUID(packet.attempt());
                buffer.writeByte(packet.strike());
                buffer.writeEnum(packet.action());
            },
            buffer -> new SpiritTrialStatusPayload(buffer.readUUID(), buffer.readUnsignedByte(), buffer.readEnum(Action.class)));

    private static Consumer<SpiritTrialStatusPayload> receiver = ignored -> {};
    public static void installReceiver(Consumer<SpiritTrialStatusPayload> value) { receiver = value; }
    public static void receive(SpiritTrialStatusPayload payload) { receiver.accept(payload); }
    public boolean active() { return !attempt.equals(INACTIVE); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
