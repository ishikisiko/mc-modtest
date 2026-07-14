package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.session.CombatStopReason;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record CombatAttackStopPayload(
        int attackerEntityId,
        long revision,
        CombatStopReason reason) implements CustomPacketPayload {
    public static final Type<CombatAttackStopPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_attack_stop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatAttackStopPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    CombatAttackStopPayload::attackerEntityId,
                    ByteBufCodecs.VAR_LONG,
                    CombatAttackStopPayload::revision,
                    CombatNetworkCodecs.STOP_REASON,
                    CombatAttackStopPayload::reason,
                    CombatAttackStopPayload::new);

    public CombatAttackStopPayload {
        Objects.requireNonNull(reason, "reason");
        if (attackerEntityId < 0 || revision < 0) {
            throw new IllegalArgumentException("Attack stop fields must be non-negative");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
