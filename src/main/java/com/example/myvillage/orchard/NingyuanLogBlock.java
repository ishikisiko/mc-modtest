package com.example.myvillage.orchard;

import java.util.function.Supplier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.ItemAbilities;

public final class NingyuanLogBlock extends RotatedPillarBlock {
    private final Supplier<? extends RotatedPillarBlock> stripped;
    public NingyuanLogBlock(Properties properties,Supplier<? extends RotatedPillarBlock> stripped) { super(properties);this.stripped=stripped; }
    @Override public BlockState getToolModifiedState(BlockState state,UseOnContext context,ItemAbility action,boolean simulate) {
        if(action==ItemAbilities.AXE_STRIP && context.getItemInHand().canPerformAction(action))
            return stripped.get().defaultBlockState().setValue(AXIS,state.getValue(AXIS));
        return super.getToolModifiedState(state,context,action,simulate);
    }
}
