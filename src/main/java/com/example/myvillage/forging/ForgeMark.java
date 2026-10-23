package com.example.myvillage.forging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import java.util.List;

/** Stable string trait IDs and bounded quality travel with each weapon. */
public record ForgeMark(String trait, int quality) {
    public static final List<String> TRAITS=List.of("sharp","channeling","tough","breach","warding","riposte","carapace","frost_edge","soul_return");
    public static final Codec<ForgeMark> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.validate(s->TRAITS.contains(s)?com.mojang.serialization.DataResult.success(s):
                    com.mojang.serialization.DataResult.error(()->"Invalid forging trait")).fieldOf("trait").forGetter(ForgeMark::trait),
            Codec.intRange(1,4).fieldOf("quality").forGetter(ForgeMark::quality)).apply(i,ForgeMark::new));
    public static final StreamCodec<ByteBuf,ForgeMark> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,ForgeMark::trait,ByteBufCodecs.VAR_INT,ForgeMark::quality,ForgeMark::new);
    public ForgeMark {requireTrait(trait);requireQuality(quality);}
    public static String requireTrait(String s){if(!TRAITS.contains(s))throw new IllegalArgumentException("Invalid forging trait");return s;}
    public static int requireQuality(int q){if(q<1||q>4)throw new IllegalArgumentException("Invalid forging quality");return q;}
}
