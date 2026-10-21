package com.example.myvillage.orchard;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class NingyuanSaplingBlock extends SaplingBlock {
    public NingyuanSaplingBlock(Properties properties) { super(OrchardContent.TREE_GROWER,properties); }
    @Override protected boolean mayPlaceOn(BlockState state,BlockGetter level,BlockPos pos) {
        return state.getBlock() instanceof com.example.myvillage.block.SpiritSoilBlock || super.mayPlaceOn(state,level,pos);
    }
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        var rules=OrchardRules.get();
        if(!level.isAreaLoaded(pos,6) || level.getMaxLocalRawBrightness(pos.above())<rules.minimumLight()) return;
        int chance=level.getBlockState(pos.below()).getBlock() instanceof com.example.myvillage.block.SpiritSoilBlock
                ?rules.fertileSaplingChance():rules.saplingChance();
        if(random.nextInt(chance)==0) advanceTree(level,pos,state,random);
    }
    @Override public boolean isBonemealSuccess(Level level,RandomSource random,BlockPos pos,BlockState state) {
        return random.nextFloat()<OrchardRules.get().bonemealChance();
    }
    @Override public void advanceTree(ServerLevel level,BlockPos pos,BlockState state,RandomSource random) {
        if(level.isAreaLoaded(pos,6)) super.advanceTree(level,pos,state,random);
    }
}
