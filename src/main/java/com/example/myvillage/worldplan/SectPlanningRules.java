package com.example.myvillage.worldplan;

import com.google.gson.*;
import com.example.myvillage.region.runtime.RegionGeometry;
import com.example.myvillage.region.runtime.SectSite;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.example.myvillage.worldplan.WorldPlanRules.*;

/** 选址规则唯一；每块预留地同时冻结实际蓝图、地形和山门出口。 */
public final class SectPlanningRules {
    public final int slots,radius,attempts,buffer,spacing,minSurface,maxSurface,minY,maxY;
    public final List<Layout> layouts;
    private final Map<String,List<Layout>> byTier;
    private final int entryStates, regionalHubs;
    private final Map<String,Layout> byId;
    public static final class Layout {
        public final String id,hash,archetype,scale,tier;
        public final int dryGateMargin,width,height,depth,margin,maxBlockY,entryX,entryY,entryZ,portX,portY,portZ,portWidth,portDx,portDz;
        public final SectTerrain terrain;
        Layout(JsonObject r){
            id=text(r,"id");hash=text(r,"blueprint_sha256");
            if(!id.matches("yuanxian:[a-z0-9_]+")||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("宗门蓝图身份无效");
            byte[] raw=SectTerrain.verifiedResource(text(r,"resource"),hash,8*1024*1024);
            var data=JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!id.equals(text(data,"id")))throw new IllegalArgumentException("宗门规划与资源身份不一致："+id);
            archetype=text(data,"archetype");scale=text(data,"scale");tier=data.has("tier")?text(data,"tier"):"";
            if(!com.example.myvillage.sim.data.SectProgressionRules.IDS.contains(tier))throw new IllegalArgumentException("未知营造等级："+tier);
            dryGateMargin=integer(data,"dry_gate_margin",0,128);
            var size=data.getAsJsonArray("size");
            width=size.get(0).getAsInt();height=size.get(1).getAsInt();depth=size.get(2).getAsInt();
            if(width<16||depth<16||width>512||depth>512||height<16||height>384)throw new IllegalArgumentException("宗门规划尺寸越界");
            margin=integer(data,"blend_margin",1,64);maxBlockY=integer(data,"max_y",1,height-1);
            var entry=data.getAsJsonArray("entry_anchor");
            entryX=entry.get(0).getAsInt();entryY=entry.get(1).getAsInt();entryZ=entry.get(2).getAsInt();
            if(entryX<0||entryX>=width||entryY<0||entryY>=height||entryZ<0||entryZ>=depth)
                throw new IllegalArgumentException("宗门规划入口越界");
            var port=data.getAsJsonObject("road_port");var xyz=port.getAsJsonArray("position");
            portX=xyz.get(0).getAsInt();portY=xyz.get(1).getAsInt();portZ=xyz.get(2).getAsInt();portWidth=integer(port,"width",3,9);
            var facing=com.example.myvillage.worldplan.roads.RoadPortGeometry.Facing.parse(text(port,"facing"));
            portDx=facing.dx;portDz=facing.dz;
            if(portWidth%2==0||portX<0||portX>=width||portZ<0||portZ>=depth||portY<1||portY>=height
                ||!(portDx<0&&portX==0||portDx>0&&portX==width-1||portDz<0&&portZ==0||portDz>0&&portZ==depth-1))throw new IllegalArgumentException("宗门出口必须在蓝图边缘");
            terrain=SectTerrain.read(width,depth,margin,maxBlockY,data);
            if(terrain.weight(portX,portZ)!=1||terrain.ground(portX,portZ)+1!=portY)throw new IllegalArgumentException("宗门出口没有确定地面");
        }
    }
    public SectPlanningRules(JsonObject r){
        entryStates=integer(r,"entry_states",1,3);regionalHubs=integer(r,"regional_hubs",1,2);
        slots=integer(r,"slots_per_region",1,32);radius=integer(r,"placement_radius",512,5000);
        attempts=integer(r,"placement_attempts",16,8192);buffer=integer(r,"structure_buffer",16,256);spacing=integer(r,"gate_spacing",128,2048);
        minSurface=integer(r,"minimum_surface",64,200);maxSurface=integer(r,"maximum_surface",minSurface,240);
        minY=integer(r,"minimum_y",-64,0);maxY=integer(r,"maximum_y_exclusive",256,320);
        if(!text(r,"on_candidate_exhaustion").equals("fail_whole_plan"))throw new IllegalArgumentException("宗门地块不足必须拒绝世界规划");
        var values=new LinkedHashMap<String,Layout>();
        for(var e:r.getAsJsonArray("layouts")){var v=new Layout(e.getAsJsonObject());if(values.putIfAbsent(v.id,v)!=null)throw new IllegalArgumentException("宗门蓝图重复");}
        if(values.isEmpty()||values.size()>32)throw new IllegalArgumentException("宗门蓝图数量越界");
        byId=Map.copyOf(values);layouts=List.copyOf(values.values());
        byTier=layouts.stream().collect(java.util.stream.Collectors.groupingBy(l->l.tier));
        for(String tier:com.example.myvillage.sim.data.SectProgressionRules.IDS)
            if(!byTier.containsKey(tier)||byTier.get(tier).size()>slots)throw new IllegalArgumentException("宗门等级布局数量无效："+tier);
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
    public String tier(com.example.myvillage.region.runtime.RegionGraph graph,String region) {
        return com.example.myvillage.sim.data.SectProgressionRules.forRegion(graph,region,entryStates,regionalHubs);
    }
    public Layout choose(int regionHash,int seedSalt,int slot,String tier) {
        var family=byTier.get(tier);
        if(family==null)throw new IllegalArgumentException("未知宗门等级："+tier);
        return family.get(Math.floorMod((long)regionHash+seedSalt+slot,family.size()));
    }
}
