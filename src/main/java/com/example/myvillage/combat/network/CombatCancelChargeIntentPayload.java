package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CombatCancelChargeIntentPayload() implements CustomPacketPayload {
    public static final CombatCancelChargeIntentPayload INSTANCE = new CombatCancelChargeIntentPayload();
    public static final Type<CombatCancelChargeIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_cancel_charge_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatCancelChargeIntentPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
