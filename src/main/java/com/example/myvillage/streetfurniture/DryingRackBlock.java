package com.example.myvillage.streetfurniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class DryingRackBlock extends StreetFurnitureBlock {
    public static final MapCodec<DryingRackBlock> CODEC=simpleCodec(DryingRackBlock::new);
    public static final IntegerProperty PART=IntegerProperty.create("part",0,3);
    public DryingRackBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override public String furnitureId(){return "drying_rack";}
    @Override public IntegerProperty partProperty(){return PART;}
}
