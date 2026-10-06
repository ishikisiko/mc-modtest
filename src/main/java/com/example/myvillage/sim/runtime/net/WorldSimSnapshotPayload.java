package com.example.myvillage.sim.runtime.net;

import com.example.myvillage.MyVillageMod;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Clientbound: the server's {@link WorldSimSnapshot} answering one query (codec in {@link WorldSimSnapshotCodec}). */
public record WorldSimSnapshotPayload(WorldSimSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<WorldSimSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "world_sim_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, WorldSimSnapshotPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WorldSimSnapshotPayload decode(FriendlyByteBuf buffer) {
                    return new WorldSimSnapshotPayload(WorldSimSnapshotCodec.read(buffer));
                }

                @Override
                public void encode(FriendlyByteBuf buffer, WorldSimSnapshotPayload payload) {
                    WorldSimSnapshotCodec.write(buffer, payload.snapshot());
                }
            };

    public WorldSimSnapshotPayload {
        Objects.requireNonNull(snapshot, "snapshot");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
