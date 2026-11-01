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
    final List<com.example.myvillage.worldplan.WorldPlan.Position> blocked=new ArrayList<>();
    public RoadSavedData() {}
    public static RoadSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(RoadSavedData::new,RoadSavedData::load),"myvillage_roads");
    }
    static RoadSavedData load(CompoundTag tag,HolderLookup.Provider lookup) {
        RoadSavedData data=new RoadSavedData();
        if(tag.getInt("schema")!=1)throw new IllegalStateException("驿道存档格式不匹配");
        data.phase=tag.getString("phase");data.failure=tag.getString("failure");data.completedChunks=tag.getInt("completed_chunks");
        int[] blocked=tag.getIntArray("blocked");if(blocked.length%3!=0||blocked.length>768)throw new IllegalStateException("驿道避让存档越界");
        for(int i=0;i<blocked.length;i+=3)data.blocked.add(new com.example.myvillage.worldplan.WorldPlan.Position(blocked[i],blocked[i+1],blocked[i+2]));
        if(tag.contains("cells")) {
            int[] packed=tag.getIntArray("cells"),path=tag.getIntArray("path");
            if(packed.length%4!=0||packed.length>2_000_000||path.length%2!=0||path.length>200_000)throw new IllegalStateException("驿道存档范围不合法");
            List<RoadPlan.Cell> cells=new ArrayList<>();List<RoadAStar.Point> line=new ArrayList<>();
            for(int i=0;i<packed.length;i+=4)cells.add(new RoadPlan.Cell(packed[i],packed[i+1],packed[i+2],packed[i+3]!=0));
            for(int i=0;i<path.length;i+=2)line.add(new RoadAStar.Point(path[i],path[i+1]));
            String from=tag.getString("from"),to=tag.getString("to");
            int[] avoided=tag.getIntArray("avoided");if(avoided.length%4!=0||avoided.length>1024)throw new IllegalStateException("驿道路线避让范围越界");
            List<WorldRoadPlanner.Box> obstacles=new ArrayList<>();for(int i=0;i<avoided.length;i+=4)obstacles.add(new WorldRoadPlanner.Box(avoided[i],avoided[i+1],avoided[i+2],avoided[i+3]));
            data.plan=new RoadPlan(from,to,tag.getString("world_hash"),tag.getString("rules_hash"),tag.getInt("width"),
                Map.of(from,tag.getInt("from_gate_width"),to,tag.getInt("to_gate_width")),obstacles,line,cells);
            if(data.completedChunks<0||data.completedChunks>data.plan.chunks().size())throw new IllegalStateException("驿道进度越界");
        }
        if(Set.of("validating","building").contains(data.phase)){data.phase="paused";data.setDirty();}
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        tag.putInt("schema",1);tag.putString("phase",phase);tag.putString("failure",failure);tag.putInt("completed_chunks",completedChunks);
        int[] blocked=new int[this.blocked.size()*3];for(int i=0;i<this.blocked.size();i++){var p=this.blocked.get(i);blocked[i*3]=p.x();blocked[i*3+1]=p.y();blocked[i*3+2]=p.z();}tag.putIntArray("blocked",blocked);
        if(plan!=null) {
            tag.putString("from",plan.from());tag.putString("to",plan.to());tag.putString("world_hash",plan.worldHash());tag.putString("rules_hash",plan.rulesHash());tag.putInt("width",plan.width());
            tag.putInt("from_gate_width",plan.gateWidths().get(plan.from()));tag.putInt("to_gate_width",plan.gateWidths().get(plan.to()));
            int[] avoided=new int[plan.obstacles().size()*4];for(int i=0;i<plan.obstacles().size();i++){var p=plan.obstacles().get(i);avoided[i*4]=p.minX();avoided[i*4+1]=p.minZ();avoided[i*4+2]=p.maxX();avoided[i*4+3]=p.maxZ();}tag.putIntArray("avoided",avoided);
            int[] packed=new int[plan.cells().size()*4],path=new int[plan.centerline().size()*2];
            for(int i=0;i<plan.cells().size();i++){var c=plan.cells().get(i);packed[i*4]=c.x();packed[i*4+1]=c.z();packed[i*4+2]=c.feet2();packed[i*4+3]=c.existing()?1:0;}
            for(int i=0;i<plan.centerline().size();i++){var p=plan.centerline().get(i);path[i*2]=p.x();path[i*2+1]=p.z();}
            tag.putIntArray("cells",packed);tag.putIntArray("path",path);
        }
        return tag;
    }
}
