package com.example.myvillage.worldplan.roads;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** One explicit prototype route per world. Progress is persisted, with no automatic restart writes. */
public final class RoadSavedData extends SavedData {
    RoadPlan plan;
    String phase="idle",failure="";
    int completedChunks;
    public RoadSavedData() {}
    public static RoadSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(RoadSavedData::new,RoadSavedData::load),"myvillage_roads");
    }
    static RoadSavedData load(CompoundTag tag,HolderLookup.Provider lookup) {
        RoadSavedData data=new RoadSavedData();
        if(tag.getInt("schema")!=1)throw new IllegalStateException("驿道存档格式不匹配");
        data.phase=tag.getString("phase");data.failure=tag.getString("failure");data.completedChunks=tag.getInt("completed_chunks");
        if(tag.contains("cells")) {
            int[] packed=tag.getIntArray("cells"),path=tag.getIntArray("path");
            if(packed.length%4!=0||packed.length>2_000_000||path.length%2!=0||path.length>200_000)throw new IllegalStateException("驿道存档范围不合法");
            List<RoadPlan.Cell> cells=new ArrayList<>();List<RoadAStar.Point> line=new ArrayList<>();
            for(int i=0;i<packed.length;i+=4)cells.add(new RoadPlan.Cell(packed[i],packed[i+1],packed[i+2],packed[i+3]!=0));
            for(int i=0;i<path.length;i+=2)line.add(new RoadAStar.Point(path[i],path[i+1]));
            data.plan=new RoadPlan(tag.getString("from"),tag.getString("to"),tag.getString("world_hash"),tag.getString("rules_hash"),tag.getInt("width"),line,cells);
            if(data.completedChunks<0||data.completedChunks>data.plan.chunks().size())throw new IllegalStateException("驿道进度越界");
        }
        if(Set.of("validating","building").contains(data.phase)){data.phase="paused";data.setDirty();}
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        tag.putInt("schema",1);tag.putString("phase",phase);tag.putString("failure",failure);tag.putInt("completed_chunks",completedChunks);
        if(plan!=null) {
            tag.putString("from",plan.from());tag.putString("to",plan.to());tag.putString("world_hash",plan.worldHash());tag.putString("rules_hash",plan.rulesHash());tag.putInt("width",plan.width());
            int[] packed=new int[plan.cells().size()*4],path=new int[plan.centerline().size()*2];
            for(int i=0;i<plan.cells().size();i++){var c=plan.cells().get(i);packed[i*4]=c.x();packed[i*4+1]=c.z();packed[i*4+2]=c.feet2();packed[i*4+3]=c.existing()?1:0;}
            for(int i=0;i<plan.centerline().size();i++){var p=plan.centerline().get(i);path[i*2]=p.x();path[i*2+1]=p.z();}
            tag.putIntArray("cells",packed);tag.putIntArray("path",path);
        }
        return tag;
    }
}
