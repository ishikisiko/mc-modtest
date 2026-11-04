package com.example.myvillage.worldplan.mine;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.roads.RoadLayout;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

/** 只写当前正在生成的区块；地形、空腔、真矿石和完整原版树均由作者资源提供。 */
public final class StarterMinePlacement {
    private StarterMinePlacement(){}
    public static void place(WorldPlan world,StarterMineGeneration generation,RoadLayout roads,WorldGenLevel level,ChunkAccess chunk){
        var def=generation.definition();var layout=generation.layout();var cp=chunk.getPos();
        int x0=cp.getMinBlockX(),z0=cp.getMinBlockZ(),x1=cp.getMaxBlockX(),z1=cp.getMaxBlockZ();
        if(!generation.intersects(x0,chunk.getMinBuildHeight(),z0,x1,chunk.getMaxBuildHeight()-1,z1))return;
        if(!(chunk instanceof ProtoChunk)||chunk instanceof ImposterProtoChunk||chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL))throw new IllegalStateException("矿场只能放置到当前生成中的区块，不能回填已生成区域");
        Rotation rotation=switch(layout.rotation){case 0->Rotation.NONE;case 1->Rotation.CLOCKWISE_90;case 2->Rotation.CLOCKWISE_180;default->Rotation.COUNTERCLOCKWISE_90;};
        var palette=def.palette.stream().map(v->parse(level,v).rotate(rotation)).toList();
        BlockState stone=parse(level,def.surfaces.get("stone")),soil=parse(level,def.surfaces.get("dirt")),grass=parse(level,def.surfaces.get("grass")),path=parse(level,def.surfaces.get("path"));
        var at=new BlockPos.MutableBlockPos();
        for(int wz=z0;wz<=z1;wz++)for(int wx=x0;wx<=x1;wx++){
            if(!layout.inside(def,wx,wz))continue;
            if(roads.at(wx,wz)!=null)throw new IllegalStateException("矿场本体侵入已规划道路");
            int lx=layout.localX(wx,wz),lz=layout.localZ(wx,wz);if(def.weight(lx,lz)==0)continue;
            int natural=world.surfaceY(wx,wz),top=layout.surface(world,def,wx,wz);
            int low=Math.max(chunk.getMinBuildHeight()+5,Math.min(natural,top)-def.foundationDepth);
            int high=Math.min(chunk.getMaxBuildHeight()-1,Math.max(Math.max(natural,top+def.clearance),chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,wx&15,wz&15)+1));
            for(int y=low;y<=high;y++){
                BlockState value=y>=top?Blocks.AIR.defaultBlockState():y==top-1?grass:y>=top-def.soilDepth?soil:stone;
                write(chunk,at.set(wx,y,wz),value,null);
            }
        }
        for(var cell:def.cells){
            int wx=layout.worldX(cell.x(),cell.z()),wz=layout.worldZ(cell.x(),cell.z());
            if(wx<x0||wx>x1||wz<z0||wz>z1)continue;
            int wy=layout.y+cell.y();CompoundTag entity=null;
            String nbt=def.entities.get(def.key(cell.x(),cell.y(),cell.z()));
            if(nbt!=null)try{entity=TagParser.parseTag(nbt);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalStateException("矿场方块实体不能解码",e);}
            write(chunk,at.set(wx,wy,wz),palette.get(cell.palette()),entity);
        }
        for(var cell:layout.path){
            if(cell.x()<x0||cell.x()>x1||cell.z()<z0||cell.z()>z1||roads.at(cell.x(),cell.z())!=null)continue;
            int natural=world.surfaceY(cell.x(),cell.z()),floor=cell.floorY();
            int low=Math.max(chunk.getMinBuildHeight()+5,Math.min(natural,floor)-def.foundationDepth);
            int high=Math.min(chunk.getMaxBuildHeight()-1,Math.max(Math.max(natural,floor+def.clearance),chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,cell.x()&15,cell.z()&15)+1));
            var surface=Math.floorMod(cell.feet2(),2)==0?path:Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM);
            for(int y=low;y<=high;y++)write(chunk,at.set(cell.x(),y,cell.z()),y<floor?stone:y==floor?surface:Blocks.AIR.defaultBlockState(),null);
        }
        Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
    }
    private static BlockState parse(WorldGenLevel level,String state){
        try{return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK),state,false).blockState();}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalStateException("矿场材料不存在或状态非法："+state,e);}
    }
    private static void write(ChunkAccess chunk,BlockPos at,BlockState state,CompoundTag entity){
        if(at.getX()<chunk.getPos().getMinBlockX()||at.getX()>chunk.getPos().getMaxBlockX()||at.getZ()<chunk.getPos().getMinBlockZ()||at.getZ()>chunk.getPos().getMaxBlockZ()||at.getY()<chunk.getMinBuildHeight()||at.getY()>=chunk.getMaxBuildHeight())throw new IllegalStateException("矿场施工越过区块所有权");
        var previous=chunk.setBlockState(at,state,false);
        if(previous!=null&&previous.hasBlockEntity())chunk.removeBlockEntity(at);
        if(entity!=null){if(!state.hasBlockEntity())throw new IllegalStateException("矿场方块实体缺少对应方块");var tag=entity.copy();tag.putInt("x",at.getX());tag.putInt("y",at.getY());tag.putInt("z",at.getZ());chunk.setBlockEntityNbt(tag);}
    }
}
