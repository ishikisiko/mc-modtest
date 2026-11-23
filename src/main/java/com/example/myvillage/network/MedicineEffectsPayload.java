package com.example.myvillage.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Owner-only list of active pill icons. Server medicine state remains authoritative. */
public record MedicineEffectsPayload(List<ResourceLocation> pills) implements CustomPacketPayload {
    private static final int MAX_PILLS = 16;
    public static final Type<MedicineEffectsPayload> TYPE = new Type<>(ResourceLocation.parse("yuanxian:medicine_effects"));
    public static final StreamCodec<ByteBuf, MedicineEffectsPayload> STREAM_CODEC = ResourceLocation.STREAM_CODEC
            .apply(ByteBufCodecs.list(MAX_PILLS)).map(MedicineEffectsPayload::new, MedicineEffectsPayload::pills);

    private static Consumer<MedicineEffectsPayload> receiver = ignored -> {};

    public MedicineEffectsPayload {
        pills = List.copyOf(pills);
        if (pills.size() > MAX_PILLS) throw new IllegalArgumentException("Too many active medicines");
    }

    public static void installReceiver(Consumer<MedicineEffectsPayload> handler) {
        receiver = Objects.requireNonNull(handler);
    }

    public static void receive(MedicineEffectsPayload payload) {
        receiver.accept(payload);
    }

    @Override public Type<MedicineEffectsPayload> type() { return TYPE; }
}
