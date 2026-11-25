package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.sect.SectBlueprint;
import com.example.myvillage.sect.SectBlueprintPlacement;
import com.example.myvillage.worldplan.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.EnumSet;

/** 在 FEATURES 阶段仅营造当前生成区块，不读取邻块、不触发已生成世界的二次施工。 */
public final class PlannedSectPlacement {
    private PlannedSectPlacement() {}
    /** 洞室先于植被开挖。山体地表由基础地形与原生表层规则覆盖。 */
    public static void carve(WorldPlan plan,ChunkAccess chunk){
        var cp=chunk.getPos();
        var air=Blocks.CAVE_AIR.defaultBlockState();
        var pos=new BlockPos.MutableBlockPos();
        for(var site:plan.sectSites()){
            if(!site.intersects(cp.getMinBlockX(),cp.getMinBlockZ(),cp.getMaxBlockX(),cp.getMaxBlockZ(),0))continue;
            var terrain=plan.rules().sects.layout(site.blueprintId()).terrain;
            if(!terrain.hasCavern())continue;
            int low=Math.max(chunk.getMinBuildHeight(),site.baseY()+terrain.carveBottom());
            int high=Math.min(chunk.getMaxBuildHeight()-1,site.baseY()+terrain.carveTop());
            for(int z=cp.getMinBlockZ();z<=cp.getMaxBlockZ();z++)for(int x=cp.getMinBlockX();x<=cp.getMaxBlockX();x++){
                int top=chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x&15,z&15);
                for(int y=low;y<=Math.min(top,high);y++)
                    if(terrain.carves(x-site.x(),y-site.baseY(),z-site.z()))
                        chunk.setBlockState(pos.set(x,y,z),air,false);
            }
            Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
        }
    }
    public static void placeSlice(WorldPlan plan,WorldGenLevel level,ChunkAccess chunk){
        var cp=chunk.getPos();
        var clip=new BoundingBox(cp.getMinBlockX(),level.getMinBuildHeight(),cp.getMinBlockZ(),cp.getMaxBlockX(),level.getMaxBuildHeight()-1,cp.getMaxBlockZ());
        var roads=WorldPlanRuntime.generator(level.getLevel()).map(g->g.roads().await()).orElse(null);
        for(var site:plan.sectSites()){
            if(!site.intersects(clip.minX(),clip.minZ(),clip.maxX(),clip.maxZ(),0))continue;
            var data=SectBlueprint.get(site.blueprintId());
            if(!data.fingerprint.equals(site.blueprintHash()))throw new IllegalStateException("宗门规划与施工蓝图身份不一致");
            try(var scope=WorldPlanGuard.ownSlice(level,clip)){
                SectBlueprintPlacement.place(level.getLevel(),data,new BlockPos(site.x(),site.baseY(),site.z()),clip,
                    plan::naturalSurfaceY,new SectBlueprintPlacement.Sink(){
                        public BlockState get(BlockPos p){return chunk.getBlockState(p);}
                        public void set(BlockPos p,BlockState state,CompoundTag entity){
                            if(!clip.isInside(p))throw new IllegalStateException("宗门施工越过区块所有权");
                            if(roads!=null&&roads.at(p.getX(),p.getZ())!=null){
                                if(p.getX()>=site.x()&&p.getX()<site.x()+site.width()&&p.getZ()>=site.z()&&p.getZ()<site.z()+site.depth())throw new IllegalStateException("道路侵入宗门内部");
                                return; // 外部支路已在本区块前一阶段完成，宗门过渡地形不得覆盖。
                            }
                            level.setBlock(p,state,SectBlueprintPlacement.FLAGS);
                            if(entity!=null){var tag=entity.copy();tag.putInt("x",p.getX());tag.putInt("y",p.getY());tag.putInt("z",p.getZ());chunk.setBlockEntityNbt(tag);}
                        }
                    },site.regionId(),data.terrain.hasCavern());
            }
        }
    }
}
