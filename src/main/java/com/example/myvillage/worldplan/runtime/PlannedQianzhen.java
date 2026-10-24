package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.regiongen.QianzhenRegionLayout;
import com.example.myvillage.worldplan.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.security.MessageDigest;
import java.util.HexFormat;

/** The Chapter 1 buildings enter the regular plan's chunk stage without deploying a story scene. */
public final class PlannedQianzhen {
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE;
    private PlannedQianzhen() {}
    public static void placeSlice(WorldPlan plan,WorldGenLevel level,ChunkAccess chunk,ChunkGenerator generator){
        var site=plan.qianzhen();if(site==null||!plan.intersectsQianzhen(chunk.getPos().getMinBlockX(),chunk.getPos().getMinBlockZ(),chunk.getPos().getMaxBlockX(),chunk.getPos().getMaxBlockZ(),false))return;
        var layout=QianzhenRegionLayout.current();var rules=plan.rules().qianzhen;
        if(!layout.resourceHash.equals(rules.resourceHash)||!layout.layoutHash.equals(site.layoutHash()))throw new IllegalStateException("Saved Chapter 1 settlement resources changed; restore matching assets");
        var cp=chunk.getPos();var clip=new BoundingBox(cp.getMinBlockX(),level.getMinBuildHeight(),cp.getMinBlockZ(),cp.getMaxBlockX(),level.getMaxBuildHeight()-1,cp.getMaxBlockZ());
        var top=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(layout.surface)).defaultBlockState();
        var fill=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(layout.foundation)).defaultBlockState();
        var air=Blocks.AIR.defaultBlockState();var water=Blocks.WATER.defaultBlockState();
        var position=new BlockPos.MutableBlockPos();var noise=level.getLevel().getChunkSource().randomState();
        try(var scope=WorldPlanGuard.ownSlice(level,clip)){
            for(int z=cp.getMinBlockZ();z<=cp.getMaxBlockZ();z++)for(int x=cp.getMinBlockX();x<=cp.getMaxBlockX();x++){
                double weight=layout.weight(x-site.x(),z-site.z());if(weight==0)continue;
                int natural=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,noise);
                int target=(int)Math.round(natural*(1-weight)+(site.groundY()+1)*weight);
                int low=Math.max(level.getMinBuildHeight(),Math.min(natural,target)-layout.soilDepth);
                int ceiling=Math.min(level.getMaxBuildHeight()-1,Math.max(natural,site.groundY()+layout.height));
                for(int y=low;y<=ceiling;y++){
                    position.set(x,y,z);
                    level.setBlock(position,y<target-1?fill:y==target-1?top:y<generator.getSeaLevel()?water:air,FLAGS);
                }
            }
            var local=layout.inverse(cp.getMinBlockX()-site.x(),cp.getMinBlockZ()-site.z(),site.rotation());
            int tx=Math.floorDiv(local.x(),16)*16,tz=Math.floorDiv(local.z(),16)*16;var tile=layout.tile(tx,tz);if(tile==null)return;
            ResourceLocation id=ResourceLocation.parse(tile.template());
            var file=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"structure/"+id.getPath()+".nbt");
            try(var input=level.getLevel().getServer().getResourceManager().getResourceOrThrow(file).open()){
                String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                if(!actual.equals(tile.sha256()))throw new IllegalStateException("Chapter 1 tile identity differs: "+id);
            }catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException("Cannot verify Chapter 1 tile",e);}
            var template=level.getLevel().getStructureManager().get(id).orElseThrow();
            if(template.getSize().getX()!=16||template.getSize().getY()!=layout.height||template.getSize().getZ()!=16)throw new IllegalStateException("Chapter 1 tile bounds differ");
            var rotated=layout.rotate(tx,tz,site.rotation());var origin=new BlockPos(site.x()+rotated.x(),site.groundY(),site.z()+rotated.z());
            Rotation facing=switch(site.rotation()){case 1->Rotation.CLOCKWISE_90;case 2->Rotation.CLOCKWISE_180;case 3->Rotation.COUNTERCLOCKWISE_90;default->Rotation.NONE;};
            var settings=new StructurePlaceSettings().setBoundingBox(clip).setRotation(facing).setKnownShape(true).setIgnoreEntities(true);
            if(!template.placeInWorld(level,origin,origin,settings,RandomSource.create(site.siteSeed()^((long)tx<<32)^tz),FLAGS))throw new IllegalStateException("Cannot place Chapter 1 tile");
        }
    }
}
