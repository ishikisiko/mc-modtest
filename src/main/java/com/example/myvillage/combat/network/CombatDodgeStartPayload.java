package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Server to the dodging player and everyone tracking it: a dodge started. Presentation only (the
 * motion itself arrives as the vanilla entity-motion packet the server's impulse produces).
 *
 * @param entityId          the dodging player
 * @param startTick         server game time of the dodge's first tick
 * @param directionYaw      world yaw (degrees) the body travels along
 * @param distance          planned horizontal distance in blocks (after collision checks)
 * @param invulnerableTicks ticks from {@code startTick} during which incoming damage is cancelled
 * @param durationTicks     ticks the dodge presentation lasts (at least {@code invulnerableTicks})
 * @param cooldownTicks     ticks until the next dodge is accepted
 * @param techniqueId       the 身法 technique that was used
 */
public record CombatDodgeStartPayload(
        int entityId,
        long startTick,
        float directionYaw,
        float distance,
        int invulnerableTicks,
        int durationTicks,
        int cooldownTicks,
        ResourceLocation techniqueId) implements CustomPacketPayload {
    public static final Type<CombatDodgeStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_dodge_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatDodgeStartPayload> STREAM_CODEC =
            StreamCodec.of(CombatDodgeStartPayload::write, CombatDodgeStartPayload::read);

    private static void write(RegistryFriendlyByteBuf buffer, CombatDodgeStartPayload payload) {
        ByteBufCodecs.VAR_INT.encode(buffer, payload.entityId());
        ByteBufCodecs.VAR_LONG.encode(buffer, payload.startTick());
        buffer.writeFloat(payload.directionYaw());
        buffer.writeFloat(payload.distance());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.invulnerableTicks());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.durationTicks());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.cooldownTicks());
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.techniqueId());
    }

    private static CombatDodgeStartPayload read(RegistryFriendlyByteBuf buffer) {
        int entityId = ByteBufCodecs.VAR_INT.decode(buffer);
        long startTick = ByteBufCodecs.VAR_LONG.decode(buffer);
        float directionYaw = buffer.readFloat();
        float distance = buffer.readFloat();
        int invulnerableTicks = ByteBufCodecs.VAR_INT.decode(buffer);
        int durationTicks = ByteBufCodecs.VAR_INT.decode(buffer);
        int cooldownTicks = ByteBufCodecs.VAR_INT.decode(buffer);
        ResourceLocation techniqueId = ResourceLocation.STREAM_CODEC.decode(buffer);
        return new CombatDodgeStartPayload(
                entityId, startTick, directionYaw, distance, invulnerableTicks, durationTicks, cooldownTicks, techniqueId);
    }

    public CombatDodgeStartPayload {
        Objects.requireNonNull(techniqueId, "techniqueId");
        if (entityId < 0 || startTick < 0 || invulnerableTicks < 0 || cooldownTicks < 0) {
            throw new IllegalArgumentException("Dodge start fields must be non-negative");
        }
        if (!(distance > 0.0F) || !Float.isFinite(distance) || !Float.isFinite(directionYaw)) {
            throw new IllegalArgumentException("Dodge distance must be positive and finite");
        }
        if (durationTicks < Math.max(1, invulnerableTicks)) {
            throw new IllegalArgumentException("Dodge duration must cover the invulnerable window");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
