package com.example.myvillage.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** No inventory, ticker, combat state or custom save data. The normal block state is authoritative. */
public final class SwordQiPedestalEntity extends BlockEntity {
    public SwordQiPedestalEntity(BlockPos pos, BlockState state) {
        super(SwordQiDecorationContent.ENTITY.get(), pos, state);
    }
}
