package com.example.myvillage.arrival;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Owner-only server notice; the client never submits a place, name or replay request. */
public record ArrivalPayload(ResourceLocation dimension, ArrivalLocation location, boolean showRegion,
        boolean showTown) implements CustomPacketPayload {
    public static final Type<ArrivalPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("myvillage", "arrival"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArrivalPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public ArrivalPayload decode(RegistryFriendlyByteBuf b) {
            return new ArrivalPayload(b.readResourceLocation(), new ArrivalLocation(b.readUtf(ArrivalLocation.MAX_ID),
                    b.readUtf(ArrivalLocation.MAX_TEXT), b.readUtf(ArrivalLocation.MAX_ID), b.readUtf(ArrivalLocation.MAX_TEXT)),
                    b.readBoolean(), b.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, ArrivalPayload p) {
            b.writeResourceLocation(p.dimension());
            b.writeUtf(p.location().regionId(), ArrivalLocation.MAX_ID); b.writeUtf(p.location().regionName(), ArrivalLocation.MAX_TEXT);
            b.writeUtf(p.location().townId(), ArrivalLocation.MAX_ID); b.writeUtf(p.location().townName(), ArrivalLocation.MAX_TEXT);
            b.writeBoolean(p.showRegion()); b.writeBoolean(p.showTown());
        }
    };
    private static volatile Consumer<ArrivalPayload> receiver = ignored -> { };
    public ArrivalPayload {
        Objects.requireNonNull(dimension); Objects.requireNonNull(location);
        if (showRegion && location.regionId().isEmpty() || showTown && location.townId().isEmpty())
            throw new IllegalArgumentException("Arrival without a destination");
    }
    public static void installReceiver(Consumer<ArrivalPayload> next) { receiver = Objects.requireNonNull(next); }
    public static void receive(ArrivalPayload payload) { receiver.accept(payload); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
