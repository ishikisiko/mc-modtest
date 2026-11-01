package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.capital.CapitalCity;
import java.util.*;
import static com.example.myvillage.worldplan.roads.RoadAStar.Point;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

/** The world owns geography, closed borders, settlements and narrowly declared gate collars. */
public final class WorldRoadPlanner {
    private static final class Samples {
        final short[] heights=new short[256];
        final byte[] columns=new byte[256],centers=new byte[256];
        Samples(){Arrays.fill(heights,Short.MIN_VALUE);}
    }
    public record Exit(String region,int x,int edgeZ,int escapeZ,int feet2,List<Cell> existing) {
        public Exit {existing=List.copyOf(existing);}
    }
    private WorldRoadPlanner() {}
    public static RoadPlan plan(WorldPlan world,RoadRules rules,Exit a,Exit b) {
        return plan(world,rules,a,b,List.of());
    }
    public static RoadPlan plan(WorldPlan world,RoadRules rules,Exit a,Exit b,List<Point> blocked) {
        List<Point> obstacles=List.copyOf(blocked);
        if(obstacles.size()>rules.maxObstacles)throw new IllegalArgumentException("驿道避让点超过配置上限");
        if(a.region.equals(b.region))throw new IllegalArgumentException("请选择两座不同省城");
        var border=world.borders().stream().filter(e->Set.of(e.a(),e.b()).equals(Set.of(a.region,b.region))).findFirst()
            .orElseThrow(()->new IllegalArgumentException("首版仅连接直接相邻的省城"));
        if(!border.passable()||border.gate())throw new IllegalArgumentException("首版不能穿越隔绝州界或代替关隘");
        Point start=new Point(a.x,a.escapeZ),end=new Point(b.x,b.escapeZ);
        if(Math.hypot(start.x()-end.x(),start.z()-end.z())>rules.maxDistance)throw new IllegalArgumentException("两城超出首版驿道距离预算");
        List<Point> crossings=new ArrayList<>();double length=Math.hypot(border.q().x()-border.p().x(),border.q().z()-border.p().z());
        int attempts=obstacles.isEmpty()?0:obstacles.size()+1;
        for(int i=0;i<=attempts;i++)for(int sign:i==0?new int[]{0}:new int[]{1,-1}) {
            double t=.5+sign*i*(rules.obstacleMargin*2.0+rules.gridStep)/length;if(t<0||t>1)continue;
            double[] actual=world.geometry().unwarp(border.p().x()+t*(border.q().x()-border.p().x()),border.p().z()+t*(border.q().z()-border.p().z()));
            Point p=new Point(start.x()+(int)Math.round((actual[0]-start.x())/rules.gridStep)*rules.gridStep,
                start.z()+(int)Math.round((actual[1]-start.z())/rules.gridStep)*rules.gridStep);
            if(!crossings.contains(p))crossings.add(p);
        }
        int minX=Math.min(start.x(),Math.min(end.x(),crossings.stream().mapToInt(Point::x).min().orElseThrow()))-rules.searchMargin;
        int maxX=Math.max(start.x(),Math.max(end.x(),crossings.stream().mapToInt(Point::x).max().orElseThrow()))+rules.searchMargin;
        int minZ=Math.min(start.z(),Math.min(end.z(),crossings.stream().mapToInt(Point::z).min().orElseThrow()))-rules.searchMargin;
        int maxZ=Math.max(start.z(),Math.max(end.z(),crossings.stream().mapToInt(Point::z).max().orElseThrow()))+rules.searchMargin;
        Map<Long,Samples> samples=new HashMap<>();
        RoadAStar.Field field=new RoadAStar.Field() {
            private Samples tile(int x,int z) {
                long key=Cell.key(x>>4,z>>4);Samples tile=samples.get(key);
                if(tile==null){if(samples.size()>=rules.maxSampledTiles)throw new IllegalStateException("驿道地形缓存达到配置上限");tile=new Samples();samples.put(key,tile);}
                return tile;
            }
            private int index(int x,int z){return (z&15)*16+(x&15);}
            @Override public int height(int x,int z){return Math.max(world.rules().seaLevel+1,ground(x,z));}
            private int ground(int x,int z){var tile=tile(x,z);int i=index(x,z);if(tile.heights[i]==Short.MIN_VALUE)tile.heights[i]=(short)world.surfaceY(x,z);return tile.heights[i];}
            @Override public int waterDepth(int x,int z){return Math.max(0,world.rules().seaLevel-ground(x,z));}
            private boolean column(int x,int z) {
                var tile=tile(x,z);int i=index(x,z);
                if(tile.columns[i]==0)tile.columns[i]=(byte)(avoids(obstacles,rules.obstacleMargin,x,z)&&columnAllowed(world,rules,a.region,b.region,x,z,ground(x,z))&&waterDepth(x,z)<=rules.maxWaterDepth?1:2);
                return tile.columns[i]==1;
            }
            @Override public boolean allowed(int x,int z) {
                if(x<minX||x>maxX||z<minZ||z>maxZ)return false;
                var tile=tile(x,z);int i=index(x,z);if(tile.centers[i]!=0)return tile.centers[i]==1;
                int radius=rules.width/2;
                for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)
                    if(!column(x+dx,z+dz)){tile.centers[i]=2;return false;}
                tile.centers[i]=1;return true;
            }
        };
        Point pass=crossings.stream().filter(p->field.allowed(p.x(),p.z())).findFirst()
            .orElseThrow(()->new IllegalStateException("可连州界内没有满足保护与水深限制的过境点"));
        List<Point> raw=new ArrayList<>();
        raw.add(new Point(a.x,a.edgeZ+rules.width/2));raw.add(start);
        // Shared border coordinates live in the warped grammar plane. Route through
        // the real crossing, rather than exhausting a straight-line search behind a
        // third state's forbidden territory. Both searches share the declared budget.
        raw.addAll(RoadAStar.find(start,pass,field,rules.gridStep,rules.maxExpansions/2,rules.cost));
        raw.addAll(RoadAStar.find(pass,end,field,rules.gridStep,rules.maxExpansions/2,rules.cost));
        raw.add(new Point(b.x,b.edgeZ+rules.width/2));
        List<Cell> joins=new ArrayList<>();
        a.existing.stream().filter(c->c.z()==a.edgeZ-1).forEach(joins::add);
        b.existing.stream().filter(c->c.z()==b.edgeZ-1).forEach(joins::add);
        var geometry=RoadGeometry.build(raw,rules.width,rules.smoothRadius,a.feet2,b.feet2,
            field::height,(x,z)->avoids(obstacles,rules.obstacleMargin,x,z)&&columnAllowed(world,rules,a.region,b.region,x,z),rules.maxEarthwork,rules.maxColumns,joins);
        ArrayList<Cell> cells=new ArrayList<>(geometry.cells());cells.addAll(a.existing);cells.addAll(b.existing);
        if(cells.size()>rules.maxColumns)throw new IllegalStateException("完整驿道超过路幅预算");
        RoadPlan result=new RoadPlan(a.region,b.region,world.fingerprint(),rules.fingerprint,rules.width,
            Map.of(a.region,rules.ports.get(a.region).gateWidth(),b.region,rules.ports.get(b.region).gateWidth()),obstacles,geometry.centerline(),cells);
        if(result.chunks().size()>rules.maxChunks)throw new IllegalStateException("驿道超过区块加载预算");
        return result;
    }
    static boolean avoids(List<Point> obstacles,int margin,int x,int z) {
        return obstacles.stream().noneMatch(p->Math.abs((long)x-p.x())<=margin&&Math.abs((long)z-p.z())<=margin);
    }
    public static boolean columnAllowed(WorldPlan world,RoadRules rules,String from,String to,int x,int z) {
        return columnAllowed(world,rules,from,to,x,z,world.surfaceY(x,z));
    }
    private static boolean columnAllowed(WorldPlan world,RoadRules rules,String from,String to,int x,int z,int surface) {
        String region=world.coreRegionAt(x,z).map(WorldPlan.Region::id).orElse("");
        if(!region.equals(from)&&!region.equals(to))return false;
        if(world.intersectsSettlements(x,z,x,z))return false;
        var spawn=world.spawn();int pad=world.rules().spawnPadRadius;
        if(Math.abs((long)x-spawn.x())<=pad&&Math.abs((long)z-spawn.z())<=pad)return false;
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
        return !world.reserved(x,Math.max(surface,world.rules().seaLevel),z);
    }
}
