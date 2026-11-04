package com.example.myvillage.forging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;

/** Material and earned work follow the actual item; changing handles never earns work again. */
public record ForgeCraft(int manualPoints,long merit,String handle,String fire,int fireMaturity) {
    public static final ForgeCraft EMPTY=new ForgeCraft(0,0,"wood","",0);
    public static final Codec<ForgeCraft> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.intRange(0,1000000).fieldOf("manual_points").forGetter(ForgeCraft::manualPoints),
            Codec.LONG.validate(n->n>=0&&n<=10000000000L?com.mojang.serialization.DataResult.success(n):com.mojang.serialization.DataResult.error(()->"Invalid forge merit")).fieldOf("merit").forGetter(ForgeCraft::merit),
            Codec.STRING.fieldOf("handle").forGetter(ForgeCraft::handle),Codec.STRING.fieldOf("fire").forGetter(ForgeCraft::fire),
            Codec.intRange(0,10000).fieldOf("fire_maturity").forGetter(ForgeCraft::fireMaturity)).apply(i,ForgeCraft::new));
    public static final StreamCodec<ByteBuf,ForgeCraft> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,ForgeCraft::manualPoints,ByteBufCodecs.VAR_LONG,ForgeCraft::merit,
            ByteBufCodecs.STRING_UTF8,ForgeCraft::handle,ByteBufCodecs.STRING_UTF8,ForgeCraft::fire,
            ByteBufCodecs.VAR_INT,ForgeCraft::fireMaturity,ForgeCraft::new);
    public ForgeCraft {
        if(manualPoints<0||manualPoints>1000000||merit<0||merit>(long)manualPoints*10000
                ||fireMaturity<0||fireMaturity>10000||handle==null||!handle.matches("[a-z][a-z0-9_]{0,31}")
                ||fire==null||(!fire.isEmpty()&&!fire.matches("[a-z][a-z0-9_]{0,31}"))||fire.isEmpty()&&fireMaturity!=0)
            throw new IllegalArgumentException("Invalid earned forge work");
    }
    public ForgeCraft work(int points,int quality){
        if(points<1||quality<0||quality>10000)throw new IllegalArgumentException("Invalid work contribution");
        return new ForgeCraft(Math.addExact(manualPoints,points),Math.addExact(merit,(long)points*quality),handle,fire,fireMaturity);
    }
    public ForgeCraft withHandle(String id){FoundryRules.get().handle(id);return new ForgeCraft(manualPoints,merit,id,fire,fireMaturity);}
    public ForgeCraft withFire(String id,int maturity){
        if(!fire.isEmpty())throw new IllegalStateException("Auxiliary fire already committed");FoundryRules.get().flame(id);
        return new ForgeCraft(manualPoints,merit,handle,id,maturity);
    }
    public double manualStrength(int required){return Math.clamp(merit/(required*10000.0),0,1);}
}
