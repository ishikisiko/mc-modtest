package com.example.myvillage.orchard;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class NingyuanLeavesBlock extends LeavesBlock {
    public NingyuanLeavesBlock(Properties properties) { super(properties); }
    @Override public boolean isRandomlyTicking(BlockState state) { return !state.getValue(PERSISTENT); }
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(decaying(state)) { super.randomTick(state,level,pos,random); return; }
        var rules=OrchardRules.get(); BlockPos below=pos.below();
        if(level.isAreaLoaded(pos,1) && !state.getValue(WATERLOGGED) && level.isEmptyBlock(below)
                && level.getMaxLocalRawBrightness(below)>=rules.minimumLight()
                && random.nextInt(rules.newClusterChance())==0)
            level.setBlock(below,OrchardContent.FRUIT.get().defaultBlockState(),3);
    }
}
