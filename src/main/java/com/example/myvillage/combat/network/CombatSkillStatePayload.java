package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;

/** Server presentation only. No client can supply a charge, aim, position, hit or cost. */
public record CombatSkillStatePayload(int attackerEntityId, ResourceLocation skillId, long revision,
        long serverTick, Phase phase, Vec3 origin, Vec3 direction, int chargeLevel) implements CustomPacketPayload {
    public enum Phase { CHARGING, RELEASED, WAVE, STOPPED, REJECTED, DERIVED_RELEASED, DERIVED_WAVE, DERIVED_STOPPED }
    public static final Type<CombatSkillStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_skill_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatSkillStatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.attackerEntityId()); buffer.writeResourceLocation(value.skillId());
                buffer.writeVarLong(value.revision()); buffer.writeVarLong(value.serverTick());
                buffer.writeByte(value.phase().ordinal());
                writeVector(buffer, value.origin()); writeVector(buffer, value.direction());
                buffer.writeByte(value.chargeLevel());
            }, buffer -> {
                int entity = buffer.readVarInt(); ResourceLocation skill = buffer.readResourceLocation();
                long revision = buffer.readVarLong(); long tick = buffer.readVarLong();
                int phase = buffer.readUnsignedByte();
                if (phase >= Phase.values().length) throw new IllegalArgumentException("Unknown skill phase");
                return new CombatSkillStatePayload(entity, skill, revision, tick, Phase.values()[phase],
                        readVector(buffer), readVector(buffer), buffer.readUnsignedByte());
            });
    public CombatSkillStatePayload {
        Objects.requireNonNull(skillId); Objects.requireNonNull(phase);
        Objects.requireNonNull(origin); Objects.requireNonNull(direction);
        if (attackerEntityId < 0 || revision < 1 || serverTick < 0 || chargeLevel < 0 || chargeLevel > 255
                || !finite(origin) || !finite(direction) || Math.abs(direction.lengthSqr() - 1) > .001) {
            throw new IllegalArgumentException("Invalid skill presentation state");
        }
    }
    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    private static void writeVector(RegistryFriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z);
    }
    private static Vec3 readVector(RegistryFriendlyByteBuf b) {
        return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
