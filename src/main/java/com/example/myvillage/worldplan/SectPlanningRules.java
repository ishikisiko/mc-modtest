package com.example.myvillage.worldplan;

import com.google.gson.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static com.example.myvillage.worldplan.WorldPlanRules.*;

/** 选址规则唯一；每块预留地同时冻结实际蓝图、地形和山门出口。 */
public final class SectPlanningRules {
    public final int slots,radius,attempts,buffer,spacing,minSurface,maxSurface,minY,maxY;
    public final List<Layout> layouts;
    private final Map<String,Layout> byId;
    public static final class Layout {
        public final String id,hash,archetype,scale;
        public final int width,height,depth,margin,maxBlockY,entryX,entryY,entryZ,portX,portY,portZ,portWidth,portDx,portDz;
        public final SectTerrain terrain;
        Layout(JsonObject r){
            id=text(r,"id");hash=text(r,"blueprint_sha256");archetype=text(r,"archetype");scale=text(r,"scale");
            if(!id.matches("myvillage:[a-z0-9_]+")||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("宗门蓝图身份无效");
            width=integer(r,"width",16,512);height=integer(r,"height",16,256);depth=integer(r,"depth",16,512);
            margin=integer(r,"blend_margin",1,128);maxBlockY=integer(r,"max_y",1,height-1);
            entryX=integer(r,"entry_x",0,width-1);entryY=integer(r,"entry_y",0,height-1);entryZ=integer(r,"entry_z",0,depth-1);
            var p=r.getAsJsonObject("road_port");var xyz=p.getAsJsonArray("position");
            portX=xyz.get(0).getAsInt();portY=xyz.get(1).getAsInt();portZ=xyz.get(2).getAsInt();portWidth=integer(p,"width",3,9);
            var facing=com.example.myvillage.worldplan.roads.RoadPortGeometry.Facing.parse(text(p,"facing"));portDx=facing.dx;portDz=facing.dz;
            if(portWidth%2==0||portX<0||portX>=width||portZ<0||portZ>=depth||portY<1||portY>=height
                ||!(portDx<0&&portX==0||portDx>0&&portX==width-1||portDz<0&&portZ==0||portDz>0&&portZ==depth-1))throw new IllegalArgumentException("宗门出口必须在蓝图边缘");
            try(var input=new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text(r,"terrain_gzip"))))){
                terrain=new SectTerrain(width,depth,margin,maxBlockY,input.readNBytes(width*depth*4+1));
            }catch(java.io.IOException e){throw new IllegalArgumentException("宗门地形场无效",e);}
            if(terrain.weight(portX,portZ)!=1||terrain.ground(portX,portZ)+1!=portY)throw new IllegalArgumentException("宗门出口没有确定地面");
        }
    }
    public SectPlanningRules(JsonObject r){
        slots=integer(r,"slots_per_region",1,32);radius=integer(r,"placement_radius",512,5000);
        attempts=integer(r,"placement_attempts",16,8192);buffer=integer(r,"structure_buffer",16,256);spacing=integer(r,"gate_spacing",128,2048);
        minSurface=integer(r,"minimum_surface",64,200);maxSurface=integer(r,"maximum_surface",minSurface,240);
        minY=integer(r,"minimum_y",-64,0);maxY=integer(r,"maximum_y_exclusive",256,320);
        if(!text(r,"on_candidate_exhaustion").equals("fail_whole_plan"))throw new IllegalArgumentException("宗门地块不足必须拒绝世界规划");
        var values=new LinkedHashMap<String,Layout>();
        for(var e:r.getAsJsonArray("layouts")){var v=new Layout(e.getAsJsonObject());if(values.putIfAbsent(v.id,v)!=null)throw new IllegalArgumentException("宗门蓝图重复");}
        if(values.isEmpty()||values.size()>16)throw new IllegalArgumentException("宗门蓝图数量越界");
        byId=Map.copyOf(values);layouts=List.copyOf(values.values());
    }
    public Layout layout(String id){var v=byId.get(id);if(v==null)throw new IllegalArgumentException("未知宗门蓝图："+id);return v;}
}
