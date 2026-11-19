package com.example.myvillage.streetfurniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class TallStreetLampBlock extends StreetFurnitureBlock {
    public static final MapCodec<TallStreetLampBlock> CODEC=simpleCodec(TallStreetLampBlock::new);
    public static final IntegerProperty PART=IntegerProperty.create("part",0,2);
    public TallStreetLampBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override public String furnitureId(){return "tall_street_lamp";}
    @Override public IntegerProperty partProperty(){return PART;}
}
