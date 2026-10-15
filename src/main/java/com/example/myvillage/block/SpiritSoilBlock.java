package com.example.myvillage.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.TriState;

/** Permanently fertile soil. No moisture, drying tick or farmland trampling transition. */
public final class SpiritSoilBlock extends Block {
    public static final MapCodec<SpiritSoilBlock> CODEC = simpleCodec(SpiritSoilBlock::new);
    public SpiritSoilBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override public boolean isFertile(BlockState state, BlockGetter level, BlockPos pos) { return true; }
    @Override public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos pos,
                                            Direction facing, BlockState plant) {
        return facing == Direction.UP && plant.getBlock() instanceof SpiritCropBlock ? TriState.TRUE : TriState.FALSE;
    }
}
