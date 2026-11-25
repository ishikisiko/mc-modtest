package com.example.myvillage.worldplan;

import com.google.gson.*;
import com.example.myvillage.region.runtime.RegionGeometry;
import com.example.myvillage.region.runtime.SectSite;
import com.example.myvillage.sect.SectBlueprint;
import java.util.*;
import static com.example.myvillage.worldplan.WorldPlanRules.*;

/** 选址规则唯一；每块预留地同时冻结实际蓝图、地形和山门出口。 */
public final class SectPlanningRules {
    public final int slots,radius,attempts,buffer,spacing,minSurface,maxSurface,minY,maxY;
    public final List<Layout> layouts;
    private final Map<String,Layout> byId;
    public static final class Layout {
        public final String id,hash,archetype,scale,tier;
        public final int width,height,depth,margin,maxBlockY,entryX,entryY,entryZ,portX,portY,portZ,portWidth,portDx,portDz;
        public final SectTerrain terrain;
        Layout(JsonObject r){
            id=text(r,"id");hash=text(r,"blueprint_sha256");
            if(!id.matches("yuanxian:[a-z0-9_]+")||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("宗门蓝图身份无效");
            var data=SectBlueprint.get(id);
            if(!hash.equals(data.fingerprint))throw new IllegalArgumentException("宗门规划与资源身份不一致："+id);
            archetype=data.archetype;scale=data.scale;tier=data.tier;
            width=data.width;height=data.height;depth=data.depth;margin=data.margin;maxBlockY=data.maxY;
            if(width>512||depth>512||height>256||maxBlockY<1)throw new IllegalArgumentException("宗门规划尺寸越界");
            entryX=data.entry.getX();entryY=data.entry.getY();entryZ=data.entry.getZ();
            var port=data.roadPort;
            portX=port.position().getX();portY=port.position().getY();portZ=port.position().getZ();portWidth=port.width();
            portDx=port.facing().getStepX();portDz=port.facing().getStepZ();
            if(portWidth%2==0||portX<0||portX>=width||portZ<0||portZ>=depth||portY<1||portY>=height
                ||!(portDx<0&&portX==0||portDx>0&&portX==width-1||portDz<0&&portZ==0||portDz>0&&portZ==depth-1))throw new IllegalArgumentException("宗门出口必须在蓝图边缘");
            terrain=data.terrain;
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
    RegionGeometry.Reservation footprint(SectSite site){
        int m=site.margin()+buffer;
        return new RegionGeometry.Reservation(site.id(),site.x()-m,site.z()-m,
                site.x()+site.width()+m-1,site.z()+site.depth()+m-1);
    }
    /** Keep a buffer-sized turning area outside the narrow, protected gate collar. */
    RegionGeometry.Reservation exitClearance(SectSite site){
        var p=layout(site.blueprintId());int distance=site.margin()+buffer*2;
        int x=site.x()+p.portX+p.portDx*distance,z=site.z()+p.portZ+p.portDz*distance;
        return new RegionGeometry.Reservation(site.id()+"/road_exit",x-buffer,z-buffer,x+buffer,z+buffer);
    }
    public Layout choose(int regionHash,int seedSalt,int slot,boolean anchor){
        var main=layouts.stream().filter(l->l.tier.equals("main")).toList();
        if(anchor&&slot<main.size())return main.get(slot);
        var shared=layouts.stream().filter(l->l.tier.isEmpty()).toList();
        return shared.get(Math.floorMod((long)regionHash+seedSalt+slot,shared.size()));
    }
}
