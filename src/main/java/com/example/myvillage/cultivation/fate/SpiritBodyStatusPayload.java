package com.example.myvillage.cultivation.fate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 单向、仅所有者的灵体快照；不能作为客户端伤害、火种、位移或复生请求。 */
public record SpiritBodyStatusPayload(int mode,List<Double> adaptation,double bodyDefense,double defensePerPoint,
        double adaptationCapacity,double reserve,double reserveCapacity,double rebirthThreshold,
        double emberHealth,double emberMaximum,int remaining,int duration) implements CustomPacketPayload {
    public static final Type<SpiritBodyStatusPayload> TYPE=new Type<>(ResourceLocation.parse("myvillage:spirit_body_status"));
    private static Consumer<SpiritBodyStatusPayload> receiver=p->{};
    public static void installReceiver(Consumer<SpiritBodyStatusPayload> consumer){receiver=java.util.Objects.requireNonNull(consumer);}
    public static void receive(SpiritBodyStatusPayload payload){receiver.accept(payload);}
    public SpiritBodyStatusPayload {
        adaptation=List.copyOf(adaptation);
        if(mode<0||mode>3||adaptation.size()!=AdaptationCategory.values().length||adaptation.stream().anyMatch(v->!finite(v)||v<0||v>1000)
                ||!finite(bodyDefense)||bodyDefense<0||!finite(defensePerPoint)||defensePerPoint<0||!finite(adaptationCapacity)||adaptationCapacity<=0
                ||!finite(reserve)||reserve<0||!finite(reserveCapacity)||reserveCapacity<=0||reserve>reserveCapacity+1e-6
                ||!finite(rebirthThreshold)||rebirthThreshold<=0||rebirthThreshold>reserveCapacity
                ||!finite(emberHealth)||!finite(emberMaximum)||emberHealth<0||emberMaximum<0||emberHealth>emberMaximum+1e-4
                ||remaining<0||duration<1||remaining>duration)throw new IllegalArgumentException("非法灵体显示数据");
    }
    private static boolean finite(double n){return Double.isFinite(n);}
    public static final StreamCodec<RegistryFriendlyByteBuf,SpiritBodyStatusPayload> STREAM_CODEC=new StreamCodec<>() {
        @Override public SpiritBodyStatusPayload decode(RegistryFriendlyByteBuf b){
            int mode=b.readUnsignedByte();var points=new ArrayList<Double>();for(var ignored:AdaptationCategory.values())points.add(b.readDouble());
            return new SpiritBodyStatusPayload(mode,points,b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt(),b.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf b,SpiritBodyStatusPayload p){
            b.writeByte(p.mode);p.adaptation.forEach(b::writeDouble);b.writeDouble(p.bodyDefense);b.writeDouble(p.defensePerPoint);b.writeDouble(p.adaptationCapacity);
            b.writeDouble(p.reserve);b.writeDouble(p.reserveCapacity);b.writeDouble(p.rebirthThreshold);b.writeDouble(p.emberHealth);b.writeDouble(p.emberMaximum);b.writeVarInt(p.remaining);b.writeVarInt(p.duration);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
