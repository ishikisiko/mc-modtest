package com.example.myvillage.forging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Completed work travels with the item. Anvil heat is saved separately; unpaid actions are never saved. */
public record Workpiece(String recipe,int stage,int points,int treatment) {
    public static final Codec<Workpiece> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("recipe").forGetter(Workpiece::recipe),
            Codec.intRange(0,4).fieldOf("stage").forGetter(Workpiece::stage),
            Codec.intRange(0,1000000).fieldOf("points").forGetter(Workpiece::points),
            Codec.intRange(0,2).fieldOf("treatment").forGetter(Workpiece::treatment)).apply(i,Workpiece::new));
    public static final StreamCodec<ByteBuf,Workpiece> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,Workpiece::recipe,ByteBufCodecs.VAR_INT,Workpiece::stage,
            ByteBufCodecs.VAR_INT,Workpiece::points,ByteBufCodecs.VAR_INT,Workpiece::treatment,Workpiece::new);
    public Workpiece{if(recipe==null||!recipe.matches("[a-z0-9_]{1,64}")||stage<0||stage>4||points<0||points>1000000||treatment<0||treatment>2)
        throw new IllegalArgumentException("Invalid workpiece");}
    public Workpiece work(int amount,WorkshopRules rules){
        if(amount<1)throw new IllegalArgumentException("No forming work");
        if(stage>1||points>=rules.formingPoints(recipe))throw new IllegalStateException("Already formed");
        int n=(int)Math.min((long)points+amount,rules.formingPoints(recipe));
        return new Workpiece(recipe,n>=rules.shapeAt(recipe)?1:0,n,treatment);
    }
    public Workpiece next(int route){if(stage<1||stage>=4)throw new IllegalStateException("Wrong workpiece stage");return new Workpiece(recipe,stage+1,points,route);}
}
