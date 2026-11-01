package com.example.myvillage.worldplan.roads;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** All tunable parameters and gate coordinates come from generated authored inputs. */
public final class RoadRules {
    public record Port(String region, String city, int x, int z, int feetY, int width, int depth, int gateWidth) {}
    public final int width, gridStep, maxExpansions, maxDistance, searchMargin, smoothRadius,
        maxEarthwork, maxWaterDepth, maxColumns, maxChunks, chunksPerTick, clearance, gateProbeRadius;
    public final RoadAStar.Cost cost;
    public final String surface, slab, foundation, fingerprint;
    public final Set<String> replaceable;
    public final Map<String,Port> ports;
    private static final String RESOURCE="/data/myvillage/roads/prototype.json";
    public static RoadRules load() {
        try(var input=RoadRules.class.getResourceAsStream(RESOURCE)) {
            if(input==null)throw new IllegalStateException("缺少正式驿道配置");
            return new RoadRules(JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject());
        } catch(IOException e){throw new UncheckedIOException(e);}
    }
    public RoadRules(JsonObject r) {
        if(n(r,"schema")!=1)throw new IllegalArgumentException("驿道配置格式");
        width=n(r,"width");gridStep=n(r,"grid_step");maxExpansions=n(r,"max_expansions");
        maxDistance=n(r,"max_distance");searchMargin=n(r,"search_margin");smoothRadius=n(r,"smooth_radius");
        maxEarthwork=n(r,"max_earthwork");maxWaterDepth=n(r,"max_water_depth");maxColumns=n(r,"max_columns");
        maxChunks=n(r,"max_chunks");chunksPerTick=n(r,"chunks_per_tick");clearance=n(r,"clearance");gateProbeRadius=n(r,"gate_probe_radius");
        if(width<3||width>9||width%2==0||gridStep<1||gridStep>16||maxExpansions<1||maxExpansions>1_000_000
            ||maxDistance<1||maxDistance>32768||searchMargin<width||searchMargin>4096||smoothRadius<0||smoothRadius>64
            ||maxEarthwork<1||maxEarthwork>32||maxWaterDepth<0||maxWaterDepth>maxEarthwork
            ||maxColumns<1||maxColumns>500_000||maxChunks<1||maxChunks>8192||chunksPerTick<1||chunksPerTick>4
            ||clearance<2||clearance>4||gateProbeRadius<1||gateProbeRadius>16)
            throw new IllegalArgumentException("驿道配置超出有界施工范围");
        var c=r.getAsJsonObject("cost");
        cost=new RoadAStar.Cost(d(c,"orthogonal"),d(c,"diagonal"),d(c,"elevation"),d(c,"water"),d(c,"deviation"),d(c,"heuristic_multiplier"));
        if(cost.orthogonal()<=0||cost.diagonal()<=0||cost.elevation()<0||cost.water()<0||cost.deviation()<0
            ||cost.heuristicMultiplier()<1||cost.heuristicMultiplier()>2)throw new IllegalArgumentException("驿道成本配置");
        surface=r.get("surface").getAsString();slab=r.get("slab").getAsString();foundation=r.get("foundation").getAsString();
        Set<String> natural=new HashSet<>();r.getAsJsonArray("replaceable_blocks").forEach(v->natural.add(v.getAsString()));replaceable=Set.copyOf(natural);
        Map<String,Port> bindings=new LinkedHashMap<>();
        for(var value:r.getAsJsonArray("ports")) {
            var p=value.getAsJsonObject();var size=p.getAsJsonArray("dimensions");
            var port=new Port(p.get("region").getAsString(),p.get("city").getAsString(),n(p,"x"),n(p,"z"),n(p,"feet_y"),size.get(0).getAsInt(),size.get(2).getAsInt(),n(p,"port_width"));
            if(port.gateWidth()<3||port.gateWidth()>width||port.gateWidth()%2==0)throw new IllegalArgumentException("门洞净宽声明");
            if(!p.get("facing").getAsString().equals("south")||bindings.putIfAbsent(port.region(),port)!=null)throw new IllegalArgumentException("驿道出口绑定");
        }
        ports=Map.copyOf(bindings);
        fingerprint=com.example.myvillage.worldplan.WorldPlanRules.sha256(r.toString());
    }
    private static int n(JsonObject r,String k){return r.get(k).getAsInt();}
    private static double d(JsonObject r,String k){double v=r.get(k).getAsDouble();if(!Double.isFinite(v))throw new IllegalArgumentException(k);return v;}
}
