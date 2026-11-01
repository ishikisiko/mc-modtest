package com.example.myvillage.worldplan.roads;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** All tunable parameters and gate coordinates come from generated authored inputs. */
public final class RoadRules {
    public record LocalFloor(int x,int z,int feet2) {}
    public record Port(String region, String city, int x, int z, int feetY, int width, int depth, int gateWidth,RoadPortGeometry.Facing facing,List<LocalFloor> floors) {public Port {floors=List.copyOf(floors);}}
    public final int width, gridStep, maxExpansions, maxDistance, searchMargin, smoothRadius,
        maxEarthwork, maxWaterDepth, maxColumns, maxChunks, chunksPerTick, clearance, gateProbeRadius, foundationDepth, maxSampledTiles, obstacleMargin, maxObstacles, maxStructureStarts;
    public final RoadAStar.Cost cost;
    public final String surface, slab, foundation, fingerprint;
    public final Set<String> replaceable,vegetation;
    public final OfficialStyle style;
    public final RoadGuardrailRules guard;
    public final int maxRoutes,maxNetworkColumns,maxNetworkChunks;
    public final boolean automatic;
    public final int buildRadiusChunks;
    public record SlabSpec(String id,String name,String full,String texture) {}
    public record OfficialStyle(String id,String name,int curbWidth,int gradeWidth,int curbRise2,int carriageLift2,String edgeSurface,String edgeSlab,String curbSurface,String curbSlab,String gradeFoundation,Map<String,String> gradeSurfaces,float strength,float resistance,List<SlabSpec> slabs) {}
    public final Map<String,Port> ports;
    private static final String RESOURCE="/data/myvillage/roads/prototype.json";
    public static RoadRules load() {
        try(var input=RoadRules.class.getResourceAsStream(RESOURCE)) {
            if(input==null)throw new IllegalStateException("缺少正式驿道配置");
            return new RoadRules(JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject());
        } catch(IOException e){throw new UncheckedIOException(e);}
    }
    public RoadRules(JsonObject r) {
        if(n(r,"schema")!=4)throw new IllegalArgumentException("驿道配置格式");
        var network=r.getAsJsonObject("network");maxRoutes=n(network,"max_routes");maxNetworkColumns=n(network,"max_columns");maxNetworkChunks=n(network,"max_chunks");
        automatic=network.get("automatic").getAsBoolean();buildRadiusChunks=n(network,"build_radius_chunks");
        if(maxRoutes<2||maxRoutes>32||maxNetworkColumns<1||maxNetworkColumns>2_000_000||maxNetworkChunks<1||maxNetworkChunks>16384||buildRadiusChunks<1||buildRadiusChunks>16)throw new IllegalArgumentException("路网预算越界");
        width=n(r,"width");gridStep=n(r,"grid_step");maxExpansions=n(r,"max_expansions");
        maxDistance=n(r,"max_distance");searchMargin=n(r,"search_margin");smoothRadius=n(r,"smooth_radius");
        maxEarthwork=n(r,"max_earthwork");maxWaterDepth=n(r,"max_water_depth");maxColumns=n(r,"max_columns");
        maxChunks=n(r,"max_chunks");chunksPerTick=n(r,"chunks_per_tick");clearance=n(r,"clearance");gateProbeRadius=n(r,"gate_probe_radius");
        foundationDepth=n(r,"foundation_depth");
        maxSampledTiles=n(r,"max_sampled_tiles");
        obstacleMargin=n(r,"obstacle_margin");maxObstacles=n(r,"max_obstacles");
        maxStructureStarts=n(r,"max_structure_starts");
        if(width<3||width>9||width%2==0||gridStep<1||gridStep>16||maxExpansions<1||maxExpansions>1_000_000
            ||maxDistance<1||maxDistance>32768||searchMargin<width||searchMargin>4096||smoothRadius<0||smoothRadius>64
            ||maxEarthwork<1||maxEarthwork>32||maxWaterDepth<0||maxWaterDepth>maxEarthwork
            ||maxColumns<1||maxColumns>500_000||maxChunks<1||maxChunks>8192||chunksPerTick<1||chunksPerTick>4
            ||clearance<2||clearance>4||gateProbeRadius<1||gateProbeRadius>16||foundationDepth<1||foundationDepth>maxEarthwork||maxSampledTiles<1||maxSampledTiles>131072
            ||obstacleMargin<width||obstacleMargin>128||maxObstacles<1||maxObstacles>256||maxStructureStarts<1||maxStructureStarts>128)
            throw new IllegalArgumentException("驿道配置超出有界施工范围");
        var c=r.getAsJsonObject("cost");
        cost=new RoadAStar.Cost(d(c,"orthogonal"),d(c,"diagonal"),d(c,"elevation"),d(c,"water"),d(c,"deviation"),d(c,"heuristic_multiplier"));
        if(cost.orthogonal()<=0||cost.diagonal()<=0||cost.elevation()<0||cost.water()<0||cost.deviation()<0
            ||cost.heuristicMultiplier()<1||cost.heuristicMultiplier()>2)throw new IllegalArgumentException("驿道成本配置");
        surface=r.get("surface").getAsString();slab=r.get("slab").getAsString();foundation=r.get("foundation").getAsString();
        var st=r.getAsJsonObject("style");
        Map<String,String> grades=new LinkedHashMap<>();st.getAsJsonObject("grade_surface_by_region").entrySet().forEach(e->grades.put(e.getKey(),e.getValue().getAsString()));
        List<SlabSpec> slabs=new ArrayList<>();for(var v:st.getAsJsonArray("slabs")){var s=v.getAsJsonObject();slabs.add(new SlabSpec(s.get("id").getAsString(),s.get("name").getAsString(),s.get("full").getAsString(),s.get("texture").getAsString()));}
        guard=new RoadGuardrailRules(r.getAsJsonObject("guardrail"));
        style=new OfficialStyle(st.get("id").getAsString(),st.get("name").getAsString(),n(st,"curb_width"),n(st,"grade_width"),n(st,"curb_rise2"),n(st,"carriage_lift2"),st.get("edge_surface").getAsString(),st.get("edge_slab").getAsString(),st.get("curb_surface").getAsString(),st.get("curb_slab").getAsString(),st.get("grade_foundation").getAsString(),Map.copyOf(grades),(float)d(st,"strength"),(float)d(st,"resistance"),List.copyOf(slabs));
        if(!style.id.equals("official_stone")||style.curbWidth!=1||style.gradeWidth<1||style.gradeWidth>8||style.curbRise2!=1||style.carriageLift2<0||style.carriageLift2>1||slabs.size()!=3||style.strength<=0||style.resistance<0)throw new IllegalArgumentException("官道样式配置越界");
        Set<String> natural=new HashSet<>();r.getAsJsonArray("replaceable_blocks").forEach(v->natural.add(v.getAsString()));replaceable=Set.copyOf(natural);
        Set<String> plants=new HashSet<>();r.getAsJsonArray("vegetation_blocks").forEach(v->plants.add(v.getAsString()));vegetation=Set.copyOf(plants);
        Map<String,Port> bindings=new LinkedHashMap<>();
        for(var value:r.getAsJsonArray("ports")) {
            var p=value.getAsJsonObject();var size=p.getAsJsonArray("dimensions");
            List<LocalFloor> floors=new ArrayList<>();for(var valueFloor:p.getAsJsonArray("gate_cells")){var f=valueFloor.getAsJsonArray();if(f.size()!=3)throw new IllegalArgumentException("城门地坪格式");floors.add(new LocalFloor(f.get(0).getAsInt(),f.get(1).getAsInt(),f.get(2).getAsInt()));}
            var port=new Port(p.get("region").getAsString(),p.get("city").getAsString(),n(p,"x"),n(p,"z"),n(p,"feet_y"),size.get(0).getAsInt(),size.get(2).getAsInt(),n(p,"port_width"),RoadPortGeometry.Facing.parse(p.get("facing").getAsString()),floors);
            if(port.gateWidth()<3||port.gateWidth()>width||port.gateWidth()%2==0)throw new IllegalArgumentException("门洞净宽声明");
            if(port.x()<0||port.x()>=port.width()||port.z()<0||port.z()>=port.depth()||bindings.putIfAbsent(port.region(),port)!=null)throw new IllegalArgumentException("驿道出口绑定");
            if(floors.isEmpty()||floors.size()>port.width()*port.depth()||floors.stream().anyMatch(f->f.x()<0||f.x()>=port.width()||f.z()<0||f.z()>=port.depth()))throw new IllegalArgumentException("城门地坪范围");
        }
        ports=Map.copyOf(bindings);
        if(!style.gradeSurfaces.keySet().containsAll(ports.keySet()))throw new IllegalArgumentException("缺少地区缓坡地表");
        fingerprint=com.example.myvillage.worldplan.WorldPlanRules.sha256(r.toString());
    }
    private static int n(JsonObject r,String k){return r.get(k).getAsInt();}
    private static double d(JsonObject r,String k){double v=r.get(k).getAsDouble();if(!Double.isFinite(v))throw new IllegalArgumentException(k);return v;}
}
