package com.example.myvillage.worldplan.townmap;

import com.example.myvillage.MyVillageMod;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Location and identity of the capital in the player's current surroundings. */
public record TownMapLocation(String city, String bundle, BlockPos origin) implements CustomPacketPayload {
    public static final TownMapLocation EMPTY = new TownMapLocation("", "", BlockPos.ZERO);
    public static final Type<TownMapLocation> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "town_map_location"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TownMapLocation> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TownMapLocation decode(RegistryFriendlyByteBuf buffer) {
            if (!buffer.readBoolean()) return EMPTY;
            return new TownMapLocation(buffer.readUtf(32), buffer.readUtf(64), buffer.readBlockPos());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, TownMapLocation location) {
            buffer.writeBoolean(location.available());
            if (!location.available()) return;
            buffer.writeUtf(location.city(), 32);
            buffer.writeUtf(location.bundle(), 64);
            buffer.writeBlockPos(location.origin());
        }
    };

    private static volatile Consumer<TownMapLocation> receiver = ignored -> { };

    public TownMapLocation {
        origin = Objects.requireNonNull(origin).immutable();
        if (city.isEmpty()) {
            if (!bundle.isEmpty() || !origin.equals(BlockPos.ZERO)) throw new IllegalArgumentException("Empty town map location");
        } else if (!city.matches("capital_[a-z_]{1,24}") || !bundle.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid town map identity");
        }
    }

    public boolean available() { return !city.isEmpty(); }
    public static void installReceiver(Consumer<TownMapLocation> next) { receiver = Objects.requireNonNull(next); }
    public static void receive(TownMapLocation location) { receiver.accept(location); }
    @Override public Type<TownMapLocation> type() { return TYPE; }
}
