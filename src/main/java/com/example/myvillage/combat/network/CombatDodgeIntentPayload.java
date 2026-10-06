package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.DodgeDirection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Client to server: the player pressed the dodge key with this movement input. One byte of input
 * and no authority: timing, distance, protection, cooldown and cost are all decided by the server.
 */
public record CombatDodgeIntentPayload(DodgeDirection direction) implements CustomPacketPayload {
    public static final Type<CombatDodgeIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_dodge_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatDodgeIntentPayload> STREAM_CODEC =
            CombatNetworkCodecs.DODGE_DIRECTION
                    .map(CombatDodgeIntentPayload::new, CombatDodgeIntentPayload::direction)
                    .cast();

    public CombatDodgeIntentPayload {
        Objects.requireNonNull(direction, "direction");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
