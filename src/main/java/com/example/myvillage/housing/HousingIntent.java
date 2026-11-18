package com.example.myvillage.housing;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端只提交领取、模板、旋转和地块内位移。 */
public record HousingIntent(UUID nonce,boolean claim,String house,int turn,int x,int z) implements CustomPacketPayload {
    public static final Type<HousingIntent> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("yuanxian","housing_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf,HousingIntent> STREAM_CODEC=new StreamCodec<>(){
        public HousingIntent decode(RegistryFriendlyByteBuf b){return new HousingIntent(b.readUUID(),b.readBoolean(),b.readUtf(48),b.readVarInt(),b.readVarInt(),b.readVarInt());}
        public void encode(RegistryFriendlyByteBuf b,HousingIntent p){b.writeUUID(p.nonce);b.writeBoolean(p.claim);b.writeUtf(p.house,48);b.writeVarInt(p.turn);b.writeVarInt(p.x);b.writeVarInt(p.z);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
