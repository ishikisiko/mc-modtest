package com.example.myvillage.forging;

import com.example.myvillage.block.ModBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import java.util.ArrayList;
import java.util.List;

/** Small visible sediment patches on a bank or in one-block shallows; never deep river floors. */
public final class EdgeSandFeature extends Feature<EdgeSandFeature.Config> {
    public record Config(int radius,int waterRadius,int maxWaterDepth,int maxBlocks) implements FeatureConfiguration {
        public static final Codec<Config> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.intRange(1,5).fieldOf("radius").forGetter(Config::radius),
                Codec.intRange(1,8).fieldOf("water_radius").forGetter(Config::waterRadius),
                Codec.intRange(0,2).fieldOf("max_water_depth").forGetter(Config::maxWaterDepth),
                Codec.intRange(1,64).fieldOf("max_blocks").forGetter(Config::maxBlocks)).apply(i,Config::new));
    }
    private static final TagKey<Block> REPLACEABLE=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("myvillage","edge_sand_replaceables"));
    public EdgeSandFeature(){super(Config.CODEC);}
    static List<BlockPos> offsets(int radius){
        List<BlockPos> positions=new ArrayList<>();
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)if(x*x+z*z<=radius*radius)positions.add(new BlockPos(x,0,z));
        positions.sort(java.util.Comparator.comparingInt(p->p.getX()*p.getX()+p.getZ()*p.getZ()));
        return positions;
    }
    private static boolean surface(WorldGenLevel level,BlockPos pos,int depth){
        if(!level.getBlockState(pos).is(REPLACEABLE))return false;
        for(int y=1;y<=depth+1;y++){
            var above=level.getBlockState(pos.above(y));
            if(above.isAir())return true;
            if(!above.getFluidState().is(FluidTags.WATER))return false;
        }
        return false;
    }
    private static boolean nearWater(WorldGenLevel level,BlockPos pos,int radius){
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            if(x*x+z*z>radius*radius)continue;
            for(int y=0;y<=1;y++)if(level.getFluidState(pos.offset(x,y,z)).is(FluidTags.WATER))return true;
        }
        return false;
    }
    @Override public boolean place(FeaturePlaceContext<Config> context){
        var level=context.level();var c=context.config();var origin=context.origin();int placed=0;
        for(var offset:offsets(c.radius())){
            int x=origin.getX()+offset.getX(),z=origin.getZ()+offset.getZ();
            var pos=new BlockPos(x,level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)-1,z);
            if(surface(level,pos,c.maxWaterDepth())&&nearWater(level,pos,c.waterRadius())){
                level.setBlock(pos,ModBlocks.EDGE_TEMPERING_SAND_DEPOSIT.get().defaultBlockState(),2);
                if(++placed>=c.maxBlocks())break;
            }
        }
        return placed>0;
    }
}
