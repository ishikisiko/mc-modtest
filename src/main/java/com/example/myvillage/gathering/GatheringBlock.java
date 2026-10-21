package com.example.myvillage.gathering;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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
    private final boolean core;
    public GatheringBlock(boolean core, Properties properties) { super(properties); this.core = core; }
    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(p -> new GatheringBlock(core, p)); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return core ? Shapes.block() : Shapes.or(box(1, 0, 1, 15, 3, 15), box(3, 3, 3, 13, 12, 13), box(2, 12, 2, 14, 15, 14));
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
