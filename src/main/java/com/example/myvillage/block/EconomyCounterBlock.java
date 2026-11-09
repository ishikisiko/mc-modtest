package com.example.myvillage.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** 现场交互柜台；归属读取作者声明位置，手放复制品不会取得宗门或城镇权限。 */
public final class EconomyCounterBlock extends Block {
    public static final MapCodec<EconomyCounterBlock> CODEC = simpleCodec(EconomyCounterBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public EconomyCounterBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.is(ModBlocks.JIANGHU_BOARD.get())) return state.getValue(FACING).getAxis() == Direction.Axis.Z
                ? Shapes.or(Block.box(1, 0, 4, 4, 16, 12), Block.box(12, 0, 4, 15, 16, 12), Block.box(2, 4, 5, 14, 16, 11))
                : Shapes.or(Block.box(4, 0, 1, 12, 16, 4), Block.box(4, 0, 12, 12, 16, 15), Block.box(5, 4, 2, 11, 16, 14));
        return Shapes.or(Block.box(1, 0, 2, 15, 12, 14), Block.box(0, 12, 0, 16, 15, 16));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server) com.example.myvillage.sim.runtime.economy.EconomyRuntime.open(server, pos);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
