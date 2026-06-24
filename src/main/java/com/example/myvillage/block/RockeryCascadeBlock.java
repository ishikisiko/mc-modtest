package com.example.myvillage.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 细瀑 (rockery cascade) — a translucent, water-textured decorative block for the
 * one visible 泉水细瀑 of the hero 假山 (add-hero-rockery Decision 6 / task 2.6).
 *
 * <p>Minecraft fluids cannot be voxel-shaped, so a thin sub-block trickle is
 * realized as a baked translucent block: the model JSON carries the water
 * geometry and sets {@code render_type=minecraft:translucent}, and a client
 * {@code BlockColor} tints the grayscale {@code water_still} texture (see
 * {@code com.example.myvillage.client.MyVillageClient}). It is visual-only — an
 * empty {@link VoxelShape} makes it passable and non-colliding (it ships placed
 * only in AIR cells), so it reads as a thin trickle without flooding or blocking
 * the climbable 假山 summit/path.
 */
public class RockeryCascadeBlock extends Block {
    public static final MapCodec<RockeryCascadeBlock> CODEC = simpleCodec(RockeryCascadeBlock::new);

    public RockeryCascadeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
