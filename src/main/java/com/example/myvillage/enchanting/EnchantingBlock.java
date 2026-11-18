package com.example.myvillage.enchanting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

final class EnchantingBlock extends Block implements EntityBlock {
    static final BooleanProperty FORMED = BooleanProperty.create("formed");
    private final String kind;

    EnchantingBlock(String kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH).setValue(FORMED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, FORMED);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EnchantingDevice(pos, state); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != EnchantingContent.DEVICE.get() || !(level instanceof ServerLevel server) || kind.equals("enchanting_socket")) return null;
        return (world, pos, current, entity) -> ((EnchantingDevice) entity).tick(server);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind.equals("library_port") || EnchantingContent.stoneStrength(stack) == 0 || hand != InteractionHand.MAIN_HAND)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player instanceof ServerPlayer server && !server.isSpectator()
                && level.getBlockEntity(kind.equals("seal_table") ? pos.below() : pos) instanceof EnchantingDevice device
                && device.getBlockState().is(EnchantingContent.SOCKET.get())) device.exchangeStone(server);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && !server.isSpectator() && level.getBlockEntity(pos) instanceof EnchantingDevice device) {
            switch (kind) {
                case "enchanting_socket" -> device.exchangeStone(server);
                case "seal_table" -> {
                    if (device.formed()) {
                        if (device.verifyStructure()) EnchantingContent.open(server, EnchantingMenu.Mode.SEAL, pos, device);
                        else server.displayClientMessage(Component.literal("阵台无法核对全部方块。"), true);
                    } else device.form(server);
                }
                case "library_port" -> EnchantingContent.open(server, EnchantingMenu.Mode.LIBRARY, pos, device);
                default -> throw new IllegalStateException(kind);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof EnchantingDevice device) {
            if (kind.equals("seal_table")) {
                device.unform(pos);
                device.dropDiscs(server);
            } else if (kind.equals("enchanting_socket")) {
                if (level.getBlockEntity(pos.above()) instanceof EnchantingDevice seal && seal.seal()) seal.unform(pos);
                Block.popResource(level, pos, device.removeStone());
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
