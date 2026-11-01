package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.capital.CapitalCity;
import java.util.*;
import static com.example.myvillage.worldplan.roads.RoadAStar.Point;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

/** The world owns geography, closed borders, settlements and narrowly declared gate collars. */
public final class WorldRoadPlanner {
    public record Exit(String region,int x,int edgeZ,int escapeZ,int feet2,List<Cell> existing) {
        public Exit {existing=List.copyOf(existing);}
    }
    private WorldRoadPlanner() {}
    public static RoadPlan plan(WorldPlan world,RoadRules rules,Exit a,Exit b) {
        if(a.region.equals(b.region))throw new IllegalArgumentException("请选择两座不同省城");
        var border=world.borders().stream().filter(e->Set.of(e.a(),e.b()).equals(Set.of(a.region,b.region))).findFirst()
            .orElseThrow(()->new IllegalArgumentException("首版仅连接直接相邻的省城"));
        if(!border.passable()||border.gate())throw new IllegalArgumentException("首版不能穿越隔绝州界或代替关隘");
        Point start=new Point(a.x,a.escapeZ),end=new Point(b.x,b.escapeZ);
        if(Math.hypot(start.x()-end.x(),start.z()-end.z())>rules.maxDistance)throw new IllegalArgumentException("两城超出首版驿道距离预算");
        int minX=Math.min(start.x(),end.x())-rules.searchMargin,maxX=Math.max(start.x(),end.x())+rules.searchMargin;
        int minZ=Math.min(start.z(),end.z())-rules.searchMargin,maxZ=Math.max(start.z(),end.z())+rules.searchMargin;
        Map<Long,Integer> heights=new HashMap<>();Map<Long,Boolean> mask=new HashMap<>();
        RoadAStar.Field field=new RoadAStar.Field() {
            @Override public int height(int x,int z){return Math.max(world.rules().seaLevel+1,ground(x,z));}
            private int ground(int x,int z){return heights.computeIfAbsent(Cell.key(x,z),k->world.surfaceY(x,z));}
            @Override public int waterDepth(int x,int z){return Math.max(0,world.rules().seaLevel-ground(x,z));}
            @Override public boolean allowed(int x,int z) {
                if(x<minX||x>maxX||z<minZ||z>maxZ)return false;
                return mask.computeIfAbsent(Cell.key(x,z),k->{
                    int radius=rules.width/2;
                    for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)
                        if(!columnAllowed(world,rules,a.region,b.region,x+dx,z+dz)||waterDepth(x+dx,z+dz)>rules.maxWaterDepth)return false;
                    return true;
                });
            }
        };
        List<Point> raw=new ArrayList<>();
        raw.add(new Point(a.x,a.edgeZ+rules.width/2));raw.add(start);
        raw.addAll(RoadAStar.find(start,end,field,rules.gridStep,rules.maxExpansions,rules.cost));
        raw.add(new Point(b.x,b.edgeZ+rules.width/2));
        var geometry=RoadGeometry.build(raw,rules.width,rules.smoothRadius,a.feet2,b.feet2,
            field::height,(x,z)->columnAllowed(world,rules,a.region,b.region,x,z),rules.maxEarthwork,rules.maxColumns);
        ArrayList<Cell> cells=new ArrayList<>(geometry.cells());cells.addAll(a.existing);cells.addAll(b.existing);
        if(cells.size()>rules.maxColumns)throw new IllegalStateException("完整驿道超过路幅预算");
        RoadPlan result=new RoadPlan(a.region,b.region,world.fingerprint(),rules.fingerprint,rules.width,geometry.centerline(),cells);
        if(result.chunks().size()>rules.maxChunks)throw new IllegalStateException("驿道超过区块加载预算");
        return result;
    }
    public static boolean columnAllowed(WorldPlan world,RoadRules rules,String from,String to,int x,int z) {
        String region=world.coreRegionAt(x,z).map(WorldPlan.Region::id).orElse("");
        if(!region.equals(from)&&!region.equals(to))return false;
        if(world.intersectsSettlements(x,z,x,z))return false;
        for(CapitalCity city:world.cities()) {
            if(city.contains(x,z,0))return false; // Never grants any city-interior write permission.
            if(city.contains(x,z,city.margin())) {
                var p=rules.ports.get(city.regionId());
                if(p==null||(!city.regionId().equals(from)&&!city.regionId().equals(to)))return false;
                int center=city.x()+p.x(),edge=city.z()+city.bundle().depth;
                int escape=edge+city.margin()+rules.width/2+rules.gridStep*2;
                if(Math.abs(x-center)>rules.width/2||z<edge||z>escape+rules.width/2)return false;
                return true;
            }
        }
        return !world.reserved(x,Math.max(world.surfaceY(x,z),world.rules().seaLevel),z);
    }
}
