package com.example.myvillage.forging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Completed work travels with the item. Anvil heat is saved separately; unpaid actions are never saved. */
public record Workpiece(String recipe,int stage,int blows,int treatment) {
    public static final Codec<Workpiece> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("recipe").forGetter(Workpiece::recipe),
            Codec.intRange(0,4).fieldOf("stage").forGetter(Workpiece::stage),
            Codec.intRange(0,64).fieldOf("blows").forGetter(Workpiece::blows),
            Codec.intRange(0,2).fieldOf("treatment").forGetter(Workpiece::treatment)).apply(i,Workpiece::new));
    public static final StreamCodec<ByteBuf,Workpiece> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,Workpiece::recipe,ByteBufCodecs.VAR_INT,Workpiece::stage,
            ByteBufCodecs.VAR_INT,Workpiece::blows,ByteBufCodecs.VAR_INT,Workpiece::treatment,Workpiece::new);
    public Workpiece{if(recipe==null||!recipe.matches("[a-z0-9_]{1,64}")||stage<0||stage>4||blows<0||blows>64||treatment<0||treatment>2)
        throw new IllegalArgumentException("Invalid workpiece");}
    public Workpiece strike(WorkshopRules rules){
        if(stage>1||blows>=rules.forming_strikes())throw new IllegalStateException("Already formed");
        int n=blows+1;return new Workpiece(recipe,n>=rules.shape_at()?1:0,n,treatment);
    }
    public Workpiece next(int route){if(stage<1||stage>=4)throw new IllegalStateException("Wrong workpiece stage");return new Workpiece(recipe,stage+1,blows,route);}
}
