package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CombatChargeIntentPayload() implements CustomPacketPayload {
    public static final CombatChargeIntentPayload INSTANCE = new CombatChargeIntentPayload();
    public static final Type<CombatChargeIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_charge_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatChargeIntentPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
