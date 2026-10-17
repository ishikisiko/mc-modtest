package com.example.myvillage.combat.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Server-produced presentation cue. Ordinary attack packets still own animation playback. */
public record CombatArtStatePayload(int entityId, ResourceLocation artId, int kind, int phase,
        int age, long revision, long serverTick, Vec3 origin, float yaw, int cooldown) implements CustomPacketPayload {
    public static final int START=0, PULSE=1, STOP=2, WARD=3, COOLDOWN=4;
    public static final Type<CombatArtStatePayload> TYPE = new Type<>(ResourceLocation.parse("myvillage:art_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatArtStatePayload> STREAM_CODEC =
            StreamCodec.of((b,p) -> {
                b.writeVarInt(p.entityId); b.writeResourceLocation(p.artId); b.writeByte(p.kind);
                b.writeByte(p.phase); b.writeVarInt(p.age); b.writeVarLong(p.revision); b.writeLong(p.serverTick);
                b.writeDouble(p.origin.x); b.writeDouble(p.origin.y); b.writeDouble(p.origin.z);
                b.writeFloat(p.yaw); b.writeVarInt(p.cooldown);
            }, b -> new CombatArtStatePayload(b.readVarInt(),b.readResourceLocation(),b.readUnsignedByte(),
                    b.readUnsignedByte(),b.readVarInt(),b.readVarLong(),b.readLong(),
                    new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readVarInt()));
    public CombatArtStatePayload {
        java.util.Objects.requireNonNull(artId,"artId");
        java.util.Objects.requireNonNull(origin,"origin");
        if (entityId<0 || revision<0 || kind<START || kind>COOLDOWN || phase<0 || phase>5 || age<0 || age>1200
                || cooldown<0 || cooldown>1200 || !Float.isFinite(yaw)
                || !Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z))
            throw new IllegalArgumentException("Invalid art cue");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
