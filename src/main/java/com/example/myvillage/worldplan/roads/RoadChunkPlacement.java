package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.EnumSet;

/** Before-feature writer, after carvers. Uses only its target ProtoChunk, never ServerLevel#setBlock. */
public final class RoadChunkPlacement {
    private RoadChunkPlacement(){}
    public static void place(WorldPlan world,RoadGeneration generation,ChunkAccess chunk){
        RoadLayout layout=generation.await();RoadLayout.Slice slice=layout.slice(chunk.getPos().toLong());if(slice==null)return;
        if(!(chunk instanceof ProtoChunk)||chunk instanceof ImposterProtoChunk||chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL))throw new IllegalStateException("官道仅允许在本区块生成阶段写入，不覆盖已生成区块");
        var rules=generation.rules();var cells=slice.cells();
        for(var c:cells)if(c.chunk()!=chunk.getPos().toLong()||!layout.permits(world,rules,c)||RoadPaver.low(world,rules,c)<chunk.getMinBuildHeight()||RoadPaver.high(world,rules,c)>=chunk.getMaxBuildHeight())throw new IllegalStateException("官道生成列越过当前区块、保护范围或世界高度："+c.x()+","+c.z());
        var at=new BlockPos.MutableBlockPos();
        try(var placement=RoadGuardrailBlock.placing()){
            for(var c:cells){BlockState foundation=block(c.band()==3?rules.style.gradeFoundation():rules.foundation),surface=RoadPaver.surface(world,rules,c);
                int low=RoadPaver.low(world,rules,c),high=RoadPaver.high(world,rules,c);
                for(int y=low;y<=high;y++){
                    at.set(c.x(),y,c.z());BlockState value=y<c.floorY()?foundation:y==c.floorY()?surface:Blocks.AIR.defaultBlockState();
                    BlockState before=chunk.setBlockState(at,value,false);if(before!=null&&before.hasBlockEntity())chunk.removeBlockEntity(at);
                }
            }
            for(var c:cells)if(c.rail()>=0){at.set(c.x(),c.floorY()+1,c.z());chunk.setBlockState(at,RoadPaver.guardState(c),false);}
        }
        Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
    }
    private static BlockState block(String id){var key=ResourceLocation.parse(id);if(!BuiltInRegistries.BLOCK.containsKey(key))throw new IllegalStateException("官道材料不存在："+id);return BuiltInRegistries.BLOCK.get(key).defaultBlockState();}
}
