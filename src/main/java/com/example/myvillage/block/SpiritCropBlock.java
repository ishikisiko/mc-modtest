package com.example.myvillage.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One herb item is both planting material and harvest. Exactly seedling, growing and mature. */
public final class SpiritCropBlock extends CropBlock {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);
    public static final MapCodec<SpiritCropBlock> CODEC = simpleCodec(SpiritCropBlock::new);
    private static final VoxelShape[] SHAPES = {Block.box(2, 0, 2, 14, 4, 14),
            Block.box(1, 0, 1, 15, 9, 15), Block.box(1, 0, 1, 15, 15, 15)};
    public SpiritCropBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override public MapCodec<? extends CropBlock> codec() { return CODEC; }
    @Override public IntegerProperty getAgeProperty() { return AGE; }
    @Override public int getMaxAge() { return 2; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AGE); }
    @Override protected ItemLike getBaseSeedId() { return asItem(); }
    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getBlock() instanceof SpiritSoilBlock;
    }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return mayPlaceOn(level.getBlockState(pos.below()), level, pos.below());
    }
    @Override protected int getBonemealAgeIncrease(Level level) { return 1; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[getAge(state)];
    }
}
