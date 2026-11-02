package com.example.myvillage.network;

import com.example.myvillage.worldplan.travel.AtlasCityTravelTarget;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 只传宗门身份和舆图摘要，目的地和落点由服务器解析。 */
public record AtlasSectTravelPayload(int sectId,String atlasFingerprint) implements CustomPacketPayload {
    public static final Type<AtlasSectTravelPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("myvillage","atlas_sect_travel"));
    public static final StreamCodec<FriendlyByteBuf,AtlasSectTravelPayload> STREAM_CODEC=new StreamCodec<>(){
        @Override public AtlasSectTravelPayload decode(FriendlyByteBuf buffer){return new AtlasSectTravelPayload(buffer.readVarInt(),buffer.readUtf(64));}
        @Override public void encode(FriendlyByteBuf buffer,AtlasSectTravelPayload value){buffer.writeVarInt(value.sectId());buffer.writeUtf(value.atlasFingerprint(),64);}
    };
    public AtlasSectTravelPayload {
        if(sectId<0 || !AtlasCityTravelTarget.validFingerprint(atlasFingerprint))throw new IllegalArgumentException("非法宗门舆图传送请求");
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
