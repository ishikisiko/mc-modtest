package com.example.myvillage.block;

import com.example.myvillage.ruins.RuinCoreBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Structure-only residual array. Mineable, no item/drop and cannot be moved by pistons. */
public final class RuinCoreBlock extends Block implements EntityBlock {
    public static final MapCodec<RuinCoreBlock> CODEC = simpleCodec(RuinCoreBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public RuinCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING,LIT); }
    @Override protected BlockState rotate(BlockState state, Rotation r) { return state.setValue(FACING,r.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror m) { return state.rotate(m.getRotation(state.getValue(FACING))); }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Block.box(0,0,0,16,14,16);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RuinCoreBlockEntity(pos,state); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof RuinCoreBlockEntity core)
            core.use(server);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
