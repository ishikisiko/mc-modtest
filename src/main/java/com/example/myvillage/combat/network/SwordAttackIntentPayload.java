package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SwordAttackIntentPayload() implements CustomPacketPayload {
    public static final SwordAttackIntentPayload INSTANCE = new SwordAttackIntentPayload();
    public static final Type<SwordAttackIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "sword_attack_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SwordAttackIntentPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
