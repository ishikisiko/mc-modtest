package com.example.myvillage.forging;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Paid qi PER INGOT: splitting/merging a stack conserves its total investment. */
public record CopperInfusion(int paidQi) {
    public static final Codec<CopperInfusion> CODEC=Codec.intRange(0,10000).xmap(CopperInfusion::new,CopperInfusion::paidQi);
    public static final StreamCodec<ByteBuf,CopperInfusion> STREAM_CODEC=ByteBufCodecs.VAR_INT.map(CopperInfusion::new,CopperInfusion::paidQi);
    public CopperInfusion { if(paidQi<0||paidQi>10000)throw new IllegalArgumentException("Invalid copper infusion"); }
}
