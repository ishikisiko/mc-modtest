package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CombatReleaseIntentPayload() implements CustomPacketPayload {
    public static final CombatReleaseIntentPayload INSTANCE = new CombatReleaseIntentPayload();
    public static final Type<CombatReleaseIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_release_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatReleaseIntentPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
