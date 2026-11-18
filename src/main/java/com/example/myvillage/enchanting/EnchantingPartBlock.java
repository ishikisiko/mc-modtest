package com.example.myvillage.enchanting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class EnchantingPartBlock extends Block {
    private final VoxelShape shape;

    EnchantingPartBlock(Properties properties, String part) {
        super(properties);
        shape = switch (part) {
            case "seal_pivot", "library_frame" -> box(2, 0, 2, 14, 16, 14);
            case "seal_rack" -> box(0, 0, 0, 16, 13, 16);
            // The moving ring segments have a stationary point where they can be selected and removed.
            case "library_outer", "library_equator", "library_oblique" -> box(3, 3, 3, 13, 13, 13);
            default -> box(0, 0, 0, 16, 16, 16);
        };
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
