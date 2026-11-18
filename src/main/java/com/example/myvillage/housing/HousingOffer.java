package com.example.myvillage.housing;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HousingOffer(UUID nonce,String site,String name,BlockPos origin,int width,int depth,String status,
        boolean owner,int claimCost,int buildCost,String currency,String message,String currentHouse,int currentTurn,int currentX,int currentZ) implements CustomPacketPayload {
    public static final Type<HousingOffer> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("yuanxian","housing_offer"));
    public static final StreamCodec<RegistryFriendlyByteBuf,HousingOffer> STREAM_CODEC=new StreamCodec<>(){
        public HousingOffer decode(RegistryFriendlyByteBuf b){return new HousingOffer(b.readUUID(),b.readUtf(96),b.readUtf(64),b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readUtf(32),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readUtf(64),b.readUtf(512),b.readUtf(64),b.readVarInt(),b.readVarInt(),b.readVarInt());}
        public void encode(RegistryFriendlyByteBuf b,HousingOffer p){b.writeUUID(p.nonce);b.writeUtf(p.site,96);b.writeUtf(p.name,64);b.writeBlockPos(p.origin);b.writeVarInt(p.width);b.writeVarInt(p.depth);b.writeUtf(p.status,32);b.writeBoolean(p.owner);b.writeVarInt(p.claimCost);b.writeVarInt(p.buildCost);b.writeUtf(p.currency,64);b.writeUtf(p.message,512);b.writeUtf(p.currentHouse,64);b.writeVarInt(p.currentTurn);b.writeVarInt(p.currentX);b.writeVarInt(p.currentZ);}
    };
    private static Consumer<HousingOffer> receiver=p->{};
    public static void installReceiver(Consumer<HousingOffer> value){receiver=value;}
    public static void receive(HousingOffer p){receiver.accept(p);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
