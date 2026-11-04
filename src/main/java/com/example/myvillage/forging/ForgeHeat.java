package com.example.myvillage.forging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.world.item.ItemStack;
import com.example.myvillage.item.ModDataComponents;

/** Heat is a bounded snapshot on the item, so transfers and GUI closure preserve it. */
public record ForgeHeat(int amount,long time) {
    public static final Codec<ForgeHeat> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.intRange(0,30000).fieldOf("amount").forGetter(ForgeHeat::amount),
            Codec.LONG.validate(n->n>=0?com.mojang.serialization.DataResult.success(n):com.mojang.serialization.DataResult.error(()->"Negative heat time")).fieldOf("time").forGetter(ForgeHeat::time)).apply(i,ForgeHeat::new));
    public static final StreamCodec<ByteBuf,ForgeHeat> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,ForgeHeat::amount,ByteBufCodecs.VAR_LONG,ForgeHeat::time,ForgeHeat::new);
    public ForgeHeat{if(amount<0||amount>30000||time<0)throw new IllegalArgumentException("Invalid heat");}
    public int remaining(long now){return (int)Math.clamp(amount-Math.max(0,now-time)/FoundryRules.get().heat().passive_ticks_per_unit(),0,FoundryRules.get().heat().capacity());}
    public static int get(ItemStack stack,long now){var heat=stack.get(ModDataComponents.FORGE_HEAT.get());return heat==null?0:heat.remaining(now);}
    public static void set(ItemStack stack,int amount,long now){
        if(!stack.isEmpty())stack.set(ModDataComponents.FORGE_HEAT.get(),new ForgeHeat(Math.clamp(amount,0,FoundryRules.get().heat().capacity()),Math.max(0,now)));
    }
}
