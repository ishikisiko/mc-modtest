package com.example.myvillage.streetfurniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class RuneLampBlock extends StreetFurnitureBlock {
    public static final MapCodec<RuneLampBlock> CODEC=simpleCodec(RuneLampBlock::new);
    public static final IntegerProperty PART=IntegerProperty.create("part",0,1);
    public RuneLampBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override public String furnitureId(){return "rune_lamp";}
    @Override public IntegerProperty partProperty(){return PART;}
}
