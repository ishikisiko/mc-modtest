package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.sect.SectBlueprint;
import com.example.myvillage.sect.SectBlueprintPlacement;
import com.example.myvillage.worldplan.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** 在 FEATURES 阶段仅营造当前生成区块，不读取邻块、不触发已生成世界的二次施工。 */
public final class PlannedSectPlacement {
    private PlannedSectPlacement() {}
    public static void placeSlice(WorldPlan plan,WorldGenLevel level,ChunkAccess chunk){
        var cp=chunk.getPos();
        var clip=new BoundingBox(cp.getMinBlockX(),level.getMinBuildHeight(),cp.getMinBlockZ(),cp.getMaxBlockX(),level.getMaxBuildHeight()-1,cp.getMaxBlockZ());
        for(var site:plan.sectSites()){
            if(!site.intersects(clip.minX(),clip.minZ(),clip.maxX(),clip.maxZ(),0))continue;
            var data=SectBlueprint.current();
            if(!data.fingerprint.equals(site.blueprintHash()))throw new IllegalStateException("宗门规划与施工蓝图身份不一致");
            try(var scope=WorldPlanGuard.ownSlice(level,clip)){
                SectBlueprintPlacement.place(level.getLevel(),data,new BlockPos(site.x(),site.baseY(),site.z()),clip,
                    plan::naturalSurfaceY,new SectBlueprintPlacement.Sink(){
                        public BlockState get(BlockPos p){return chunk.getBlockState(p);}
                        public void set(BlockPos p,BlockState state,CompoundTag entity){
                            if(!clip.isInside(p))throw new IllegalStateException("宗门施工越过区块所有权");
                            level.setBlock(p,state,SectBlueprintPlacement.FLAGS);
                            if(entity!=null){var tag=entity.copy();tag.putInt("x",p.getX());tag.putInt("y",p.getY());tag.putInt("z",p.getZ());chunk.setBlockEntityNbt(tag);}
                        }
                    });
            }
        }
    }
}
