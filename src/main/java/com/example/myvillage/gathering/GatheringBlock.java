package com.example.myvillage.gathering;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class GatheringBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    private final boolean core;
    private final GatheringTier tier;
    public GatheringBlock(boolean core, Properties properties) { this(core, GatheringTier.LOW, properties); }
    public GatheringBlock(boolean core, GatheringTier tier, Properties properties) { super(properties); this.core = core; this.tier = tier; registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    public GatheringTier tier() { return tier; }
    public boolean isCore() { return core; }
    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(p -> new GatheringBlock(core, tier, p)); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return core ? Shapes.block() : GatheringConstruction.rotate(GatheringAppearance.get(tier).shape(),state.getValue(FACING));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return core ? new GatheringBlockEntity(pos, state) : null; }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return core && !level.isClientSide() && type == GatheringContent.ENTITY.get()
                ? (world, pos, blockState, entity) -> ((GatheringBlockEntity)entity).tick() : null;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (core && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof GatheringBlockEntity array)
            server.openMenu(array);
        return core ? InteractionResult.sidedSuccess(level.isClientSide()) : InteractionResult.PASS;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide() && level.getBlockEntity(pos) instanceof GatheringBlockEntity array) {
            Containers.dropContents(level, pos, array.dropSnapshot());
            array.clearContent();
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
