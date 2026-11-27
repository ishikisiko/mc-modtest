package com.example.myvillage.production;

import com.example.myvillage.gathering.GatheringConstruction;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

class ProductionBlock extends Block implements EntityBlock {
    static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final ProductionPart part;
    private final VoxelShape[] shapes = new VoxelShape[4];

    ProductionBlock(ProductionPart part, Properties properties) {
        super(properties);
        this.part = part;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        for (var direction : Direction.Plane.HORIZONTAL) {
            shapes[direction.get2DDataValue()] = GatheringConstruction.rotate(part.northShape(), direction.getOpposite());
        }
    }

    ProductionPart part() { return part; }

    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(props -> new ProductionBlock(part, props)); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(FACING).get2DDataValue()];
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return part.controller() ? new ProductionBlockEntity(pos, state) : null;
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return part.controller() && !level.isClientSide() && type == ProductionContent.CONTROLLER.get()
                ? (world, pos, placed, entity) -> ((ProductionBlockEntity) entity).tick() : null;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (part.eye()) {
            if (player instanceof ServerPlayer server) server.displayClientMessage(Component.translatable("message.yuanxian.mine_eye"), true);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!part.controller()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof ProductionBlockEntity controller) {
            server.openMenu(controller);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide() && level.getBlockEntity(pos) instanceof ProductionBlockEntity root) {
            root.invalidateOutlet();
            net.minecraft.world.Containers.dropContents(level, pos, root);
            root.clearContent();
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
}
