package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tells the attacker's client that the server landed a hit during an action, so it
 * can play presentation-only hit feedback such as hit-stop.
 */
public record CombatHitConfirmPayload(
        int attackerEntityId,
        long revision,
        int hitCount) implements CustomPacketPayload {
    public static final Type<CombatHitConfirmPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_hit_confirm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatHitConfirmPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    CombatHitConfirmPayload::attackerEntityId,
                    ByteBufCodecs.VAR_LONG,
                    CombatHitConfirmPayload::revision,
                    ByteBufCodecs.VAR_INT,
                    CombatHitConfirmPayload::hitCount,
                    CombatHitConfirmPayload::new);

    public CombatHitConfirmPayload {
        if (attackerEntityId < 0 || revision <= 0 || hitCount <= 0) {
            throw new IllegalArgumentException("Hit confirm needs a valid attacker, positive revision, and hits");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
