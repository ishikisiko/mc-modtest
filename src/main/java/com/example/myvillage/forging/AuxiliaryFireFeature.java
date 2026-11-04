package com.example.myvillage.forging;

import com.example.myvillage.block.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.tags.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Finds a natural cave wall; earth sources require exposed lava, cold sources use cold-biome placement. */
public final class AuxiliaryFireFeature extends Feature<NoneFeatureConfiguration> {
    private final boolean cold;
    public AuxiliaryFireFeature(boolean cold){super(NoneFeatureConfiguration.CODEC);this.cold=cold;}
    static boolean replaceable(net.minecraft.world.level.block.state.BlockState state){return state.is(BlockTags.STONE_ORE_REPLACEABLES)||state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);}
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context){
        var level=context.level();var cfg=FoundryRules.get().worldgen();int radius=cfg.search_radius();
        for(var pos:BlockPos.betweenClosed(context.origin().offset(-radius,-radius,-radius),context.origin().offset(radius,radius,radius))){
            if(!replaceable(level.getBlockState(pos)))continue;
            boolean exposed=false;
            for(var d:Direction.values()){
                var next=pos.relative(d);
                if(cold?level.isEmptyBlock(next):level.getFluidState(next).is(FluidTags.LAVA)){exposed=true;break;}
            }
            if(!exposed)continue;
            level.setBlock(pos,(cold?ModBlocks.COLD_FIRE_VENT:ModBlocks.EARTH_FIRE_VENT).get().defaultBlockState(),2);return true;
        }
        return false;
    }
}
