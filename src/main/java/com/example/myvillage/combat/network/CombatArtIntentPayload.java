package com.example.myvillage.combat.network;

import com.example.myvillage.combat.art.ArtMath;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Exactly one bounded slot. Never accepts a skill id, damage, victim, aim or position. */
public record CombatArtIntentPayload(int slot) implements CustomPacketPayload {
    public static final Type<CombatArtIntentPayload> TYPE = new Type<>(ResourceLocation.parse("myvillage:art_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatArtIntentPayload> STREAM_CODEC =
            StreamCodec.of((buf,p) -> buf.writeByte(p.slot), buf -> new CombatArtIntentPayload(buf.readUnsignedByte()));
    public CombatArtIntentPayload {
        if (!ArtMath.validSlot(slot)) throw new IllegalArgumentException("Art slot must be 0 or 1");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
