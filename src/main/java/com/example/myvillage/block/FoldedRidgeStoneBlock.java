package com.example.myvillage.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** The strata share block behaviour while retaining the profile's individual surface materials. */
public final class FoldedRidgeStoneBlock extends Block {
    public static final IntegerProperty STRATUM = IntegerProperty.create("stratum", 1, 12);

    public FoldedRidgeStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STRATUM, 3));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STRATUM);
    }
}
