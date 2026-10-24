package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.regiongen.BayTerracesLayout;
import com.example.myvillage.regiongen.BayTerracesPiece;
import com.example.myvillage.worldplan.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** The main world plan owns positions; the existing settlement realizer owns its blocks. */
public final class PlannedSettlement {
    private PlannedSettlement() {}
    public static void placeSlice(WorldPlan plan,WorldGenLevel level,ChunkAccess chunk,
                                  StructureManager structures,ChunkGenerator generator) {
        var site=plan.settlementChunk(chunk.getPos().x,chunk.getPos().z).orElse(null);
        if(site==null)return;
        var layout=BayTerracesLayout.current();var frozen=plan.rules().settlements;
        if(!site.layout().equals(layout.id)||!site.layoutHash().equals(layout.layoutHash)
                ||frozen.size!=layout.size||frozen.margin!=layout.margin||frozen.inset!=layout.blendWidth-layout.margin
                ||frozen.baseY!=layout.baseY||frozen.height!=layout.height)
            throw new IllegalStateException("Frozen settlement asset differs from saved worldplan; restore matching resources");
        var origin=new BlockPos(site.x(),site.offsetY(),site.z());
        var piece=new BayTerracesPiece(origin,site.siteSeed(),site.rotation(),level.getMinBuildHeight(),level.getMaxBuildHeight()-1);
        var at=chunk.getPos();
        var clip=new BoundingBox(at.getMinBlockX(),level.getMinBuildHeight(),at.getMinBlockZ(),
                at.getMaxBlockX(),level.getMaxBuildHeight()-1,at.getMaxBlockZ());
        try(var scope=WorldPlanGuard.ownSlice(level,clip)) {
            piece.postProcess(level,structures,generator,RandomSource.create(site.siteSeed()),clip,at,origin);
        }
    }
}
