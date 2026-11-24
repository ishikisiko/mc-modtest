package com.example.myvillage.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.neoforged.neoforge.common.CommonHooks;

/** One herb item is both planting material and harvest. Exactly seedling, growing and mature. */
public final class SpiritCropBlock extends CropBlock {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);
    public static final MapCodec<SpiritCropBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("required_soil").forGetter(SpiritCropBlock::requiredSoil)
    ).apply(instance, (properties, soil) -> new SpiritCropBlock(properties, soil.orElse(null))));
    private static final VoxelShape[] SHAPES = {Block.box(2, 0, 2, 14, 4, 14),
            Block.box(1, 0, 1, 15, 9, 15), Block.box(1, 0, 1, 15, 15, 15)};
    private final Block requiredSoil;

    public SpiritCropBlock(BlockBehaviour.Properties properties) { this(properties, null); }

    public SpiritCropBlock(BlockBehaviour.Properties properties, Block requiredSoil) {
        super(properties);
        this.requiredSoil = requiredSoil;
    }
    @Override public MapCodec<? extends CropBlock> codec() { return CODEC; }
    @Override public IntegerProperty getAgeProperty() { return AGE; }
    @Override public int getMaxAge() { return 2; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AGE); }
    @Override protected ItemLike getBaseSeedId() { return asItem(); }
    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return acceptsSoil(state);
    }
    public Optional<Block> requiredSoil() { return Optional.ofNullable(requiredSoil); }
    public boolean acceptsSoil(BlockState soil) {
        return requiredSoil == null ? soil.getBlock() instanceof SpiritSoilBlock : soil.is(requiredSoil);
    }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return mayPlaceOn(level.getBlockState(pos.below()), level, pos.below());
    }
    @Override protected int getBonemealAgeIncrease(Level level) { return 1; }
    @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return canSurvive(state, level, pos) && super.isValidBonemealTarget(level, pos, state);
    }

    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (requiredSoil == null) {
            super.randomTick(state, level, pos, random);
            return;
        }
        // In the Nether and End, dedicated soil supplies the natural growing conditions.
        if (isMaxAge(state) || !level.isAreaLoaded(pos, 1) || !canSurvive(state, level, pos)) return;
        float speed = getGrowthSpeed(state, level, pos);
        if (CommonHooks.canCropGrow(level, pos, state, random.nextInt((int) (25.0F / speed) + 1) == 0)
                && level.setBlock(pos, getStateForAge(getAge(state) + 1), UPDATE_CLIENTS)) {
            CommonHooks.fireCropGrowPost(level, pos, state);
        }
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[getAge(state)];
    }
}
