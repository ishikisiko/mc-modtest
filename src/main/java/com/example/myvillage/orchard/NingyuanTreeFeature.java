package com.example.myvillage.orchard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class NingyuanTreeFeature extends Feature<NoneFeatureConfiguration> {
    public NingyuanTreeFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level=context.level(); BlockPos origin=context.origin();
        BlockState soil=level.getBlockState(origin.below());
        if(!soil.is(BlockTags.DIRT) && !soil.is(Blocks.FARMLAND) && !(soil.getBlock() instanceof com.example.myvillage.block.SpiritSoilBlock)) return false;
        var plan=NingyuanTreePlan.create(context.random().nextLong(),OrchardRules.get());
        for(var entry:plan.entrySet()) {
            var c=entry.getKey(); BlockPos pos=origin.offset(c.x(),c.y(),c.z());
            if(level.isOutsideBuildHeight(pos) || !level.ensureCanWrite(pos) || !level.hasChunk(pos.getX()>>4,pos.getZ()>>4)) return false;
            BlockState old=level.getBlockState(pos);
            if(old.getBlock() instanceof LeavesBlock && old.getValue(LeavesBlock.PERSISTENT)) return false;
            if(old.is(OrchardContent.FRUIT.get()) && old.getValue(NingyuanFruitBlock.ENRICHED)) return false;
            if(!(old.isAir() || old.is(OrchardContent.SAPLING.get()) || old.is(BlockTags.REPLACEABLE_BY_TREES))) return false;
            if(!old.getFluidState().isEmpty()) return false;
        }
        // No dirt conversion: spirit soil and its existing crop behaviour are preserved.
        for(var entry:plan.entrySet()) {
            var c=entry.getKey();var piece=entry.getValue();
            BlockState state=switch(piece.kind()) {
                case "log" -> OrchardContent.LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.valueOf(piece.axis().toUpperCase(java.util.Locale.ROOT)));
                case "leaves" -> OrchardContent.LEAVES.get().defaultBlockState().setValue(LeavesBlock.DISTANCE,piece.distance());
                default -> OrchardContent.FRUIT.get().defaultBlockState();
            };
            level.setBlock(origin.offset(c.x(),c.y(),c.z()),state,3);
        }
        return true;
    }
}
