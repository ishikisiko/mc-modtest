package com.example.myvillage.forging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;

/** Captured fire can mature; its source is retained for stationary earth fire. */
public record FireSeed(String kind,int maturity,String dimension,long origin) {
    public static final Codec<FireSeed> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("kind").forGetter(FireSeed::kind),Codec.intRange(0,10000).fieldOf("maturity").forGetter(FireSeed::maturity),
            Codec.STRING.fieldOf("dimension").forGetter(FireSeed::dimension),Codec.LONG.fieldOf("origin").forGetter(FireSeed::origin)).apply(i,FireSeed::new));
    public static final StreamCodec<ByteBuf,FireSeed> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,FireSeed::kind,ByteBufCodecs.VAR_INT,FireSeed::maturity,
            ByteBufCodecs.STRING_UTF8,FireSeed::dimension,ByteBufCodecs.VAR_LONG,FireSeed::origin,FireSeed::new);
    public FireSeed{if(kind==null||!kind.matches("[a-z][a-z0-9_]{0,31}")||maturity<0||maturity>10000||dimension==null||dimension.length()>128)
        throw new IllegalArgumentException("Invalid captured fire");}
    public FireSeed grow(int amount){return new FireSeed(kind,(int)Math.clamp((long)maturity+amount,0,FoundryRules.get().fire().max_maturity()),dimension,origin);}
}
