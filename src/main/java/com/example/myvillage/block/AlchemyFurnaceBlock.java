package com.example.myvillage.block;

import com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.level.pathfinder.PathComputationType;

/** Bronze three-legged furnace, with saved server inventory and a vanilla synchronized menu. */
public final class AlchemyFurnaceBlock extends Block implements EntityBlock {
    public static final MapCodec<AlchemyFurnaceBlock> CODEC = simpleCodec(AlchemyFurnaceBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape BODY = Shapes.or(box(2, 0, 2, 14, 14, 14), box(6.5, 14, 6.5, 9.5, 16, 9.5));
    private static final VoxelShape NS = Shapes.or(BODY, box(.5, 8, 5, 15.5, 12, 11));
    private static final VoxelShape EW = Shapes.or(BODY, box(5, 8, .5, 11, 12, 15.5));
    public AlchemyFurnaceBlock(BlockBehaviour.Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(FACING, Direction.NORTH));
    }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LIT, FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW; }
    // A non-full decorative collision shape is still solid equipment, not an open walking cell.
    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AlchemyFurnaceBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != ModBlockEntities.ALCHEMY_FURNACE.get() ? null
                : (world, pos, current, entity) -> AlchemyFurnaceBlockEntity.serverTick(world, pos, current, (AlchemyFurnaceBlockEntity) entity);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof AlchemyFurnaceBlockEntity furnace) server.openMenu(furnace);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide() && level.getBlockEntity(pos) instanceof AlchemyFurnaceBlockEntity furnace) {
            Containers.dropContents(level, pos, furnace); furnace.clearContent(); level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
