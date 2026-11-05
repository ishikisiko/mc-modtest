package com.example.myvillage.worldplan.mine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;
import java.util.*;

/** 保存天然洞体入口和勘探到的落脚点；不存在任何强制洞穴生成参数。 */
public record RegionalMineSite(StarterMineLayout surface,int endZ,int floorY,List<BlockPos> naturalStances) {
    public static final Codec<RegionalMineSite> CODEC=RecordCodecBuilder.create(i->i.group(
        StarterMineLayout.CODEC.fieldOf("surface").forGetter(RegionalMineSite::surface),
        Codec.INT.fieldOf("end_z").forGetter(RegionalMineSite::endZ),Codec.INT.fieldOf("floor_y").forGetter(RegionalMineSite::floorY),
        BlockPos.CODEC.listOf().fieldOf("natural_stances").forGetter(RegionalMineSite::naturalStances)).apply(i,RegionalMineSite::new));
    public RegionalMineSite {naturalStances=List.copyOf(naturalStances);if(naturalStances.size()>1024)throw new IllegalArgumentException("天然洞体证据超出预算");}
    public List<StarterMineCavePlan.Access> access(StarterMineDefinition def){
        List<StarterMineCavePlan.Access> out=new ArrayList<>();
        for(int z=def.entry.z();z<=endZ;z++){
            int feet=z<=def.caves.flatEndZ?def.groundY*2:def.groundY*2+(int)Math.round((floorY-def.groundY)*2*(z-def.caves.flatEndZ)/(double)(endZ-def.caves.flatEndZ));
            for(int x=def.entry.x()-def.caves.connectionRadius;x<=def.entry.x()+def.caves.connectionRadius;x++)out.add(new StarterMineCavePlan.Access(x,z,feet));
        }
        return List.copyOf(out);
    }
    public boolean reserved(StarterMineDefinition def,int wx,int wy,int wz){
        int x=surface.localX(wx,wz),z=surface.localZ(wx,wz);
        if(Math.abs(x-def.entry.x())>def.caves.connectionRadius||z<def.entry.z()||z>endZ)return false;
        int feet=z<=def.caves.flatEndZ?def.groundY*2:def.groundY*2+(int)Math.round((floorY-def.groundY)*2*(z-def.caves.flatEndZ)/(double)(endZ-def.caves.flatEndZ));
        return wy>=surface.y+Math.floorDiv(feet-1,2)-def.caves.floorDepth+1&&wy<=surface.y+(int)Math.ceil(feet/2.0)+def.caves.headroom-1;
    }
    public void requireValid(StarterMineDefinition def){
        if(!def.regional.ends.contains(endZ)||def.groundY-floorY<def.regional.minDepth||def.groundY-floorY>def.regional.maxDepth
            ||(def.groundY-floorY)*2>endZ-def.caves.flatEndZ||naturalStances.size()<def.regional.stances||!surface.path.isEmpty())throw new IllegalArgumentException("普通矿口没有满足有界坡道与天然洞体条件");
        if(new HashSet<>(naturalStances).size()!=naturalStances.size()||naturalStances.stream().anyMatch(p->Math.abs(p.getX()-def.entry.x())>def.regional.scanRadius||Math.abs(p.getZ()-endZ)>def.regional.scanRadius||Math.abs(p.getY()-floorY)>def.regional.scanRadius))throw new IllegalArgumentException("天然洞体勘探坐标无效");
        if(!RegionalMinePlanner.sufficient(naturalStances,new BlockPos(def.entry.x(),floorY,endZ),def.regional))throw new IllegalArgumentException("天然洞体空间跨度不足");
        if(!naturalStances.contains(new BlockPos(def.entry.x(),floorY,endZ)))throw new IllegalArgumentException("普通矿口终点不在天然洞体内");
    }
    /** 仅在生成中的当前区块修入口坡道；终点之外不挖洞、不放矿石。 */
    public void finish(StarterMineDefinition def,ChunkAccess chunk){
        StarterMineCaveCarver.requireGenerating(chunk);var at=new BlockPos.MutableBlockPos();var cp=chunk.getPos();
        for(var p:access(def)){
            int x=surface.worldX(p.x(),p.z()),z=surface.worldZ(p.x(),p.z());
            if(x<cp.getMinBlockX()||x>cp.getMaxBlockX()||z<cp.getMinBlockZ()||z>cp.getMaxBlockZ())continue;
            int floor=surface.y+p.floorY(),head=surface.y+(int)Math.ceil(p.feet2()/2.0)+def.caves.headroom-1;
            for(int y=floor-def.caves.floorDepth+1;y<=head;y++){
                var state=y<=floor?Blocks.STONE.defaultBlockState():Blocks.CAVE_AIR.defaultBlockState();
                if(y==floor&&Math.floorMod(p.feet2(),2)==1)state=Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM);
                chunk.setBlockState(at.set(x,y,z),state,false);
            }
        }
    }
}
