package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

/**
 * Broadcast to the attacker and everyone tracking it after the server lands a hit: which
 * entities were struck and where the blade first touched each one (index-aligned). Clients use
 * it only for presentation (target hit-stop, jitter, sparks); it carries no damage or health.
 */
public record CombatImpactPayload(
        int attackerEntityId,
        long revision,
        int moveIndex,
        List<Integer> struckEntityIds,
        List<Vec3> contactPoints) implements CustomPacketPayload {
    public static final int MAXIMUM_STRUCK_ENTITIES = 16;
    public static final Type<CombatImpactPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_impact"));
    static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            Vec3::x,
            ByteBufCodecs.DOUBLE,
            Vec3::y,
            ByteBufCodecs.DOUBLE,
            Vec3::z,
            Vec3::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatImpactPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    CombatImpactPayload::attackerEntityId,
                    ByteBufCodecs.VAR_LONG,
                    CombatImpactPayload::revision,
                    ByteBufCodecs.VAR_INT,
                    CombatImpactPayload::moveIndex,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAXIMUM_STRUCK_ENTITIES)),
                    CombatImpactPayload::struckEntityIds,
                    VEC3.apply(ByteBufCodecs.list(MAXIMUM_STRUCK_ENTITIES)),
                    CombatImpactPayload::contactPoints,
                    CombatImpactPayload::new);

    public CombatImpactPayload {
        struckEntityIds = List.copyOf(Objects.requireNonNull(struckEntityIds, "struckEntityIds"));
        contactPoints = List.copyOf(Objects.requireNonNull(contactPoints, "contactPoints"));
        if (attackerEntityId < 0 || revision <= 0 || moveIndex < 0) {
            throw new IllegalArgumentException("Impact needs a valid attacker, positive revision, and move index");
        }
        if (struckEntityIds.isEmpty() || struckEntityIds.size() > MAXIMUM_STRUCK_ENTITIES
                || struckEntityIds.size() != contactPoints.size()) {
            throw new IllegalArgumentException("Impact needs 1..16 struck entities with one contact point each");
        }
        for (Vec3 point : contactPoints) {
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
                throw new IllegalArgumentException("Impact contact points must be finite");
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
