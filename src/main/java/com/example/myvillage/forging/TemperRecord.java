package com.example.myvillage.forging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Permanent per-item experience and paid strikes, independently allocated to three directions. */
public record TemperRecord(int experience,int edge,int endurance,int channeling,int treatment) {
    public static final TemperRecord EMPTY=new TemperRecord(0,0,0,0,0);
    public static final Codec<TemperRecord> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.intRange(0,1000000).fieldOf("experience").forGetter(TemperRecord::experience),
            Codec.intRange(0,1000).fieldOf("edge").forGetter(TemperRecord::edge),
            Codec.intRange(0,1000).fieldOf("endurance").forGetter(TemperRecord::endurance),
            Codec.intRange(0,1000).fieldOf("channeling").forGetter(TemperRecord::channeling),
            Codec.intRange(0,2).optionalFieldOf("treatment",0).forGetter(TemperRecord::treatment)).apply(i,TemperRecord::new));
    public static final StreamCodec<ByteBuf,TemperRecord> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,TemperRecord::experience,ByteBufCodecs.VAR_INT,TemperRecord::edge,
            ByteBufCodecs.VAR_INT,TemperRecord::endurance,ByteBufCodecs.VAR_INT,TemperRecord::channeling,
            ByteBufCodecs.VAR_INT,TemperRecord::treatment,TemperRecord::new);
    public TemperRecord{if(experience<0||experience>1000000||edge<0||endurance<0||channeling<0
            ||(long)edge+endurance+channeling>1000||treatment<0||treatment>2)throw new IllegalArgumentException("Invalid temper record");}
    public int strikes(){return edge+endurance+channeling;}
    public TemperRecord earn(int amount,WorkshopRules rules){
        if(amount<0)throw new IllegalArgumentException("Negative experience");
        return new TemperRecord((int)Math.min(rules.experience_limit(),(long)experience+amount),edge,endurance,channeling,treatment);
    }
    public TemperRecord strike(int choice,WorkshopRules.Craft craft,WorkshopRules rules){
        if(!craft.training(choice)||strikes()>=craft.max_strikes()||experience<rules.experienceCost(strikes()))
            throw new IllegalArgumentException("Unavailable training strike");
        return new TemperRecord(experience-rules.experienceCost(strikes()),edge+(choice==1?1:0),
                endurance+(choice==2?1:0),channeling+(choice==3?1:0),treatment);
    }
}
