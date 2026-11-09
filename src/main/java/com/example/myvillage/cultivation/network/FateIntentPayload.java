package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Action, seen draft revision, bounded slot/mask and the server-issued stele session. */
public record FateIntentPayload(byte action, long revision, byte value, long sessionToken, String school) implements CustomPacketPayload {
    public static final Type<FateIntentPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "fate_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FateIntentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, FateIntentPayload::action, ByteBufCodecs.VAR_LONG, FateIntentPayload::revision,
            ByteBufCodecs.BYTE, FateIntentPayload::value,
            ByteBufCodecs.VAR_LONG, FateIntentPayload::sessionToken,
            ByteBufCodecs.stringUtf8(128), FateIntentPayload::school, FateIntentPayload::new);
    public FateIntentPayload(byte action,long revision,byte value,long sessionToken) { this(action,revision,value,sessionToken,""); }
    public FateIntentPayload { if(school==null || school.length()>128) throw new IllegalArgumentException("无效流派意图"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
