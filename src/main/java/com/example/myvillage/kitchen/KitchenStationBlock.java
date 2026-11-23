package com.example.myvillage.kitchen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class KitchenStationBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape BOARD_SHAPE = Block.box(0, 0, 2, 16, 2.5, 14);
    private static final VoxelShape BOARD_TURNED = Block.box(2, 0, 0, 14, 2.5, 16);
    private static final VoxelShape MILL_SHAPE = Block.box(1, 0, 1, 15, 12, 15);
    private final KitchenRules.Station station;

    public KitchenStationBlock(KitchenRules.Station station, Properties properties) {
        super(properties);
        this.station = station;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public KitchenRules.Station station() { return station; }
    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(properties -> new KitchenStationBlock(station, properties)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, LIT); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (station) {
            case MILL -> MILL_SHAPE;
            case BOARD -> state.getValue(FACING).getAxis() == Direction.Axis.X ? BOARD_TURNED : BOARD_SHAPE;
            default -> Block.box(0, 0, 0, 16, 16, 16);
        };
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return station == KitchenRules.Station.STOVE ? new KitchenStoveBlockEntity(pos, state) : new KitchenPrepBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide() && type == KitchenContent.STOVE_ENTITY.get()
                ? (world, pos, current, entity) -> ((KitchenStoveBlockEntity) entity).tick() : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (station == KitchenRules.Station.STOVE)
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        boolean hasKnife = station == KitchenRules.Station.BOARD && stack.is(KitchenContent.KNIFE.get());
        if (!hasKnife && KitchenRules.get().prep(station, stack) == null)
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof KitchenPrepBlockEntity prep) {
            if (hasKnife) prep.process(server, hand);
            else prep.deposit(server, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server) {
            if (level.getBlockEntity(pos) instanceof KitchenStoveBlockEntity stove) server.openMenu(stove);
            if (level.getBlockEntity(pos) instanceof KitchenPrepBlockEntity prep) prep.useEmptyHand(server);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof KitchenStoveBlockEntity stove) {
                Containers.dropContents(level, pos, stove);
            } else if (level.getBlockEntity(pos) instanceof KitchenPrepBlockEntity prep) {
                Block.popResource(level, pos, prep.takeInput());
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
