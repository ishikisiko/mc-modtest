package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CombatModeTogglePayload() implements CustomPacketPayload {
    public static final CombatModeTogglePayload INSTANCE = new CombatModeTogglePayload();
    public static final Type<CombatModeTogglePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_mode_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatModeTogglePayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
