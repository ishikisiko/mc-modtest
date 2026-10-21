package com.example.myvillage.orchard;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class NingyuanLanternBlock extends LanternBlock {
    public static final MapCodec<LanternBlock> CODEC=simpleCodec(NingyuanLanternBlock::new);
    public NingyuanLanternBlock(Properties properties) { super(properties); }
    @Override public MapCodec<LanternBlock> codec() { return CODEC; }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return OrchardGeometry.lantern(state.getValue(HANGING));
    }
}
