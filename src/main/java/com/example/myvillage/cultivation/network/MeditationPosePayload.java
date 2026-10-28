package com.example.myvillage.cultivation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Presentation only. Does not reveal cultivation profile, resources or study targets to observers. */
public record MeditationPosePayload(int entityId, UUID playerId, ResourceLocation dimension,
        long revision, long startedAt, int enterTicks, boolean active, boolean graceful) implements CustomPacketPayload {
    public static final Type<MeditationPosePayload> TYPE = new Type<>(ResourceLocation.parse("myvillage:meditation_pose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeditationPosePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public MeditationPosePayload decode(RegistryFriendlyByteBuf b) {
            return new MeditationPosePayload(b.readVarInt(), b.readUUID(), b.readResourceLocation(),
                    b.readVarLong(), b.readLong(), b.readVarInt(), b.readBoolean(), b.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, MeditationPosePayload p) {
            b.writeVarInt(p.entityId); b.writeUUID(p.playerId); b.writeResourceLocation(p.dimension);
            b.writeVarLong(p.revision); b.writeLong(p.startedAt); b.writeVarInt(p.enterTicks);
            b.writeBoolean(p.active); b.writeBoolean(p.graceful);
        }
    };
    public MeditationPosePayload {
        java.util.Objects.requireNonNull(playerId); java.util.Objects.requireNonNull(dimension);
        if(entityId < 0 || revision < 1 || enterTicks < 1 || enterTicks > 200 || active && graceful)
            throw new IllegalArgumentException("Invalid meditation pose snapshot");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
