package com.example.myvillage.production;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Low-profile decorative connections use the physical neighboring pieces. */
final class CrystalChannelBlock extends ProductionBlock {
    private static final Map<Direction, BooleanProperty> CONNECTIONS = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH, Direction.EAST, BlockStateProperties.EAST,
            Direction.SOUTH, BlockStateProperties.SOUTH, Direction.WEST, BlockStateProperties.WEST);

    CrystalChannelBlock(Properties properties) {
        super(ProductionPart.CRYSTAL_CHANNEL, properties);
        var state = defaultBlockState();
        for (var property : CONNECTIONS.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(CrystalChannelBlock::new); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        CONNECTIONS.values().forEach(builder::add);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = super.getStateForPlacement(context);
        for (var entry : CONNECTIONS.entrySet()) {
            var neighbor = context.getLevel().getBlockState(context.getClickedPos().relative(entry.getKey()));
            state = state.setValue(entry.getValue(), connects(neighbor));
        }
        return state;
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                                LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        var property = CONNECTIONS.get(direction);
        return property != null ? state.setValue(property, connects(neighbor)) : state;
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        var rotated = super.rotate(state, rotation);
        for (var entry : CONNECTIONS.entrySet()) {
            rotated = rotated.setValue(CONNECTIONS.get(rotation.rotate(entry.getKey())), state.getValue(entry.getValue()));
        }
        return rotated;
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        var mirrored = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        for (var entry : CONNECTIONS.entrySet()) {
            mirrored = mirrored.setValue(CONNECTIONS.get(mirror.mirror(entry.getKey())), state.getValue(entry.getValue()));
        }
        return mirrored;
    }

    private static boolean connects(BlockState state) {
        if (!(state.getBlock() instanceof ProductionBlock block)) return false;
        return block.part() == ProductionPart.CRYSTAL_CHANNEL || block.part() == ProductionPart.GOLDEN_CRYSTAL_INTAKE
                || block.part() == ProductionPart.CRYSTAL_PRESS_BASE;
    }
}
