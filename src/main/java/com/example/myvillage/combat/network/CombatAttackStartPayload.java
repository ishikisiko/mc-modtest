package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record CombatAttackStartPayload(
        int attackerEntityId,
        ResourceLocation moveId,
        long serverStartTick,
        long revision) implements CustomPacketPayload {
    public static final Type<CombatAttackStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_attack_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatAttackStartPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    CombatAttackStartPayload::attackerEntityId,
                    ResourceLocation.STREAM_CODEC,
                    CombatAttackStartPayload::moveId,
                    ByteBufCodecs.VAR_LONG,
                    CombatAttackStartPayload::serverStartTick,
                    ByteBufCodecs.VAR_LONG,
                    CombatAttackStartPayload::revision,
                    CombatAttackStartPayload::new);

    public CombatAttackStartPayload {
        Objects.requireNonNull(moveId, "moveId");
        if (attackerEntityId < 0 || serverStartTick < 0 || revision <= 0) {
            throw new IllegalArgumentException("Attack start fields must be non-negative with positive revision");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
