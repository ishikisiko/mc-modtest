package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.capital.CapitalCity;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.*;
import static com.example.myvillage.worldplan.roads.RoadAStar.Point;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

/** The world owns geography, closed borders, settlements and narrowly declared gate collars. */
public final class WorldRoadPlanner {
    public record Box(int minX,int minZ,int maxX,int maxZ) {
        public Box {if(minX>maxX||minZ>maxZ)throw new IllegalArgumentException("无效避让范围");}
    }
    private static final class Samples {
        final short[] heights=new short[256];
        final byte[] columns=new byte[256],centers=new byte[256],grades=new byte[256];
        final short[] low=new short[256],high=new short[256];
        Samples(){Arrays.fill(heights,Short.MIN_VALUE);}
    }
    public record Exit(String region,RoadPortGeometry.Frame frame,int feet2,List<Cell> existing) {
        public Exit {existing=List.copyOf(existing);}
    }
    private WorldRoadPlanner() {}
    /** 当前城市声明的只读出口；纯数据入口，不加载 Minecraft 世界或区块。 */
    public static Exit exit(WorldPlan world,RoadRules rules,String region) {
        var p=rules.ports.get(region);if(p==null)throw new IllegalArgumentException("该城尚未配置正式公共出口："+region);
        var c=world.capitalOf(region).orElseThrow();
        if(c.bundle().width!=p.width()||c.bundle().depth!=p.depth())throw new IllegalStateException("城门声明与实际城市尺寸不一致");
        var f=RoadPortGeometry.frame(p,c.x(),c.z(),c.margin(),rules.width,rules.gridStep);
        var cells=p.floors().stream().map(v->new Cell(c.x()+v.x(),c.z()+v.z(),c.originY()*2+v.feet2(),true)).toList();
        int feet=cells.stream().filter(v->v.x()==f.edgeX()-f.dx()&&v.z()==f.edgeZ()-f.dz()).mapToInt(Cell::feet2).findFirst().orElseThrow(()->new IllegalStateException("缺少城门出口断面"));
        return new Exit(region,f,feet,cells);
    }
    public static RoadPlan plan(WorldPlan world,RoadRules rules,Exit a,Exit b) {
        return plan(world,rules,a,b,List.of());
    }
    public static RoadPlan plan(WorldPlan world,RoadRules rules,Exit a,Exit b,List<Box> blocked) {
        return plan(world,rules,a,b,blocked,List.of());
    }
    public static RoadPlan plan(WorldPlan world,RoadRules rules,Exit a,Exit b,List<Box> blocked,List<Cell> locked) {
        List<Box> obstacles=List.copyOf(blocked);
        if(obstacles.size()>rules.maxObstacles)throw new IllegalArgumentException("驿道避让点超过配置上限");
        if(a.region.equals(b.region))throw new IllegalArgumentException("请选择两座不同省城");
        var border=world.borders().stream().filter(e->Set.of(e.a(),e.b()).equals(Set.of(a.region,b.region))).findFirst()
            .orElseThrow(()->new IllegalArgumentException("道路边只能连接相邻省城"));
        if(!border.passable())throw new IllegalArgumentException("不能穿越封闭州界");
        Point start=a.frame.outside(rules.width/2),end=b.frame.outside(rules.width/2);
        Point approachA=a.frame.outside(a.frame.escapeDistance()),approachB=b.frame.outside(b.frame.escapeDistance());
        if(Math.hypot(start.x()-end.x(),start.z()-end.z())>rules.maxDistance)throw new IllegalArgumentException("两城超出首版驿道距离预算");
        List<Point> crossings=new ArrayList<>();double length=Math.hypot(border.q().x()-border.p().x(),border.q().z()-border.p().z());
        int attempts=border.gate()?0:rules.maxObstacles;
        for(int i=0;i<=attempts;i++)for(int sign:i==0?new int[]{0}:new int[]{1,-1}) {
            double t=.5+sign*i*(rules.obstacleMargin*2.0+rules.gridStep)/length;if(t<0||t>1)continue;
            double[] actual=world.geometry().unwarp(border.p().x()+t*(border.q().x()-border.p().x()),border.p().z()+t*(border.q().z()-border.p().z()));
            Point p=new Point(start.x()+(int)Math.round((actual[0]-start.x())/rules.gridStep)*rules.gridStep,
                start.z()+(int)Math.round((actual[1]-start.z())/rules.gridStep)*rules.gridStep);
            if(!crossings.contains(p))crossings.add(p);
        }
        var cityA=world.capitalOf(a.region).orElseThrow();var cityB=world.capitalOf(b.region).orElseThrow();
        int ringPadding=rules.gridStep*2+rules.width/2;
        int minX=Math.min(Math.min(start.x(),Math.min(end.x(),crossings.stream().mapToInt(Point::x).min().orElseThrow()))-rules.searchMargin,Math.min(cityA.x()-cityA.margin()-ringPadding,cityB.x()-cityB.margin()-ringPadding));
        int maxX=Math.max(Math.max(start.x(),Math.max(end.x(),crossings.stream().mapToInt(Point::x).max().orElseThrow()))+rules.searchMargin,Math.max(cityA.x()+cityA.bundle().width+cityA.margin()+ringPadding,cityB.x()+cityB.bundle().width+cityB.margin()+ringPadding));
        int minZ=Math.min(Math.min(start.z(),Math.min(end.z(),crossings.stream().mapToInt(Point::z).min().orElseThrow()))-rules.searchMargin,Math.min(cityA.z()-cityA.margin()-ringPadding,cityB.z()-cityB.margin()-ringPadding));
        int maxZ=Math.max(Math.max(start.z(),Math.max(end.z(),crossings.stream().mapToInt(Point::z).max().orElseThrow()))+rules.searchMargin,Math.max(cityA.z()+cityA.bundle().depth+cityA.margin()+ringPadding,cityB.z()+cityB.bundle().depth+cityB.margin()+ringPadding));
        var fixed=new Long2ObjectOpenHashMap<Cell>();for(Cell c:locked)if(c.existing()||c.band()<2)fixed.put(c.key(),c);
        int startFeet=preferred(fixed,start,a.feet2),endFeet=preferred(fixed,end,b.feet2);
        // Packed grid keys collide heavily under Long.hashCode (x xor z).
        // Primitive-key mixing changes only lookup cost, never sampling or route order.
        var samples=new Long2ObjectOpenHashMap<Samples>();final int[][] checkpoint={null};
        var field=new RoadAStar.Field() {
            private Samples tile(int x,int z) {
                long key=Cell.key(x>>4,z>>4);Samples tile=samples.get(key);
                if(tile==null){if(samples.size()>=rules.maxSampledTiles)throw new IllegalStateException("驿道地形缓存达到配置上限");tile=new Samples();samples.put(key,tile);}
                return tile;
            }
            private int index(int x,int z){return (z&15)*16+(x&15);}
            @Override public int corridorWidth(){return rules.width;}
            @Override public int height(int x,int z){return Math.max(world.rules().seaLevel+1,ground(x,z));}
            private int ground(int x,int z){var tile=tile(x,z);int i=index(x,z);if(tile.heights[i]==Short.MIN_VALUE)tile.heights[i]=(short)world.surfaceY(x,z);return tile.heights[i];}
            @Override public int waterDepth(int x,int z){return Math.max(0,world.rules().seaLevel-ground(x,z));}
            @Override public int waterCostDepth(int x,int z){return Math.min(waterDepth(x,z),rules.bridgeCostDepth);}
            private boolean column(int x,int z) {
                var tile=tile(x,z);int i=index(x,z);
                if(tile.columns[i]==0)tile.columns[i]=(byte)(avoids(obstacles,rules.obstacleMargin,x,z)&&columnAllowed(world,rules,a.region,b.region,x,z,ground(x,z))&&waterDepth(x,z)<=rules.bridgeMaxDepth?1:2);
                return tile.columns[i]==1;
            }
            private Samples grade(int x,int z){
                var t=tile(x,z);int i=index(x,z);if(t.grades[i]==0){int lo=Integer.MIN_VALUE,hi=Integer.MAX_VALUE;
                    for(int dx=-rules.width/2;dx<=rules.width/2;dx++)for(int dz=-rules.width/2;dz<=rules.width/2;dz++){int h=height(x+dx,z+dz)*2;lo=Math.max(lo,h-rules.maxEarthwork*2);hi=Math.min(hi,h+rules.maxEarthwork*2);}
                    // Old carriage and read-only exits are grade constraints during
                    // search, rather than a conflict discovered after the path is chosen.
                    for(int dx=-rules.width/2-1;dx<=rules.width/2+1;dx++)for(int dz=-rules.width/2-1;dz<=rules.width/2+1;dz++){
                        var old=fixed.get(Cell.key(x+dx,z+dz));if(old!=null){int d=Math.abs(dx)+Math.abs(dz);lo=Math.max(lo,old.feet2()-d);hi=Math.min(hi,old.feet2()+d);}
                    }
                    if(x==start.x()&&z==start.z()){lo=Math.max(lo,startFeet);hi=Math.min(hi,startFeet);}
                    if(x==end.x()&&z==end.z()){lo=Math.max(lo,endFeet);hi=Math.min(hi,endFeet);}
                    if(checkpoint[0]!=null&&x==checkpoint[0][0]&&z==checkpoint[0][1]){lo=Math.max(lo,checkpoint[0][2]);hi=Math.min(hi,checkpoint[0][2]);}
                    if((x==approachA.x()&&z==approachA.z())||(x==approachB.x()&&z==approachB.z())){int h=preferred(fixed,new Point(x,z),height(x,z)*2+rules.style.carriageLift2());lo=Math.max(lo,h);hi=Math.min(hi,h);}
                    t.low[i]=(short)lo;t.high[i]=(short)hi;t.grades[i]=1;}
                return t;
            }
            @Override public int minimumFeet2(int x,int z){return grade(x,z).low[index(x,z)];}
            @Override public int maximumFeet2(int x,int z){return grade(x,z).high[index(x,z)];}
            @Override public boolean allowed(int x,int z) {
                if(x<minX||x>maxX||z<minZ||z>maxZ)return false;
                // Search around the OUTSIDE of an endpoint reservation. Column permissions
                // below remain authoritative; this never opens the city or its buffer.
                boolean ring=outsideRing(cityA,x,z,ringPadding)||outsideRing(cityB,x,z,ringPadding);
                if(!ring&&checkpoint[0]!=null&&segmentDistance2(x,z,start.x(),start.z(),approachA.x(),approachA.z())>(double)rules.searchMargin*rules.searchMargin
                    &&segmentDistance2(x,z,approachA.x(),approachA.z(),checkpoint[0][0],checkpoint[0][1])>(double)rules.searchMargin*rules.searchMargin
                    &&segmentDistance2(x,z,checkpoint[0][0],checkpoint[0][1],approachB.x(),approachB.z())>(double)rules.searchMargin*rules.searchMargin
                    &&segmentDistance2(x,z,approachB.x(),approachB.z(),end.x(),end.z())>(double)rules.searchMargin*rules.searchMargin)return false;
                var tile=tile(x,z);int i=index(x,z);if(tile.centers[i]!=0)return tile.centers[i]==1;
                int radius=rules.width/2;
                for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)
                    if(!column(x+dx,z+dz)){tile.centers[i]=2;return false;}
                tile.centers[i]=1;return true;
            }
        };
        Point pass=crossings.stream().filter(p->field.allowed(p.x(),p.z())).findFirst()
            .orElseThrow(()->new IllegalStateException("可连州界内没有满足保护与水深限制的过境点"));
        checkpoint[0]=new int[]{pass.x(),pass.z(),preferred(fixed,pass,field.height(pass.x(),pass.z())*2+rules.style.carriageLift2())};
        List<Point> raw=new ArrayList<>();
        raw.add(a.frame.outside(rules.width/2));raw.add(start);
        // Shared borders live in the warped plane. Every leg retains the prefix
        // footprint and reachable grade interval and draws from one route budget.
        List<Point> stops=List.of(start,approachA,pass,approachB,end);
        raw.addAll(RoadAStar.findVia(stops,field,rules.gridStep,rules.maxExpansions,rules.cost));
        List<Cell> joins=new ArrayList<>();joins.add(new Cell(pass.x(),pass.z(),checkpoint[0][2],false));
        for(Point p:List.of(approachA,approachB))joins.add(new Cell(p.x(),p.z(),preferred(fixed,p,field.height(p.x(),p.z())*2+rules.style.carriageLift2()),false));
        a.existing.stream().filter(c->(c.x()-a.frame.edgeX())*a.frame.dx()+(c.z()-a.frame.edgeZ())*a.frame.dz()==-1).forEach(joins::add);
        b.existing.stream().filter(c->(c.x()-b.frame.edgeX())*b.frame.dx()+(c.z()-b.frame.edgeZ())*b.frame.dz()==-1).forEach(joins::add);
        var geometry=RoadGeometry.build(raw,rules.width,rules.smoothRadius,startFeet,endFeet,
            field::height,field::column,rules.maxEarthwork,rules.maxColumns,joins,rules.style.carriageLift2());
        var joined=RoadNetworkGeometry.fit(geometry.cells(),locked,joins,(x,z)->field.height(x,z),rules.maxEarthwork);
        var styled=RoadOfficialStyle.apply(joined,rules,world.rules().seaLevel,field::ground,field::column);
        ArrayList<Cell> cells=new ArrayList<>(RoadGuardrailPlanner.apply(styled,geometry.centerline(),rules.guard,
            rules.style.curbRise2(),world.rules().seaLevel,field::ground));
        cells.addAll(a.existing);cells.addAll(b.existing);
        if(cells.size()>rules.maxColumns)throw new IllegalStateException("完整驿道超过路幅预算");
        RoadPlan result=new RoadPlan(a.region,b.region,world.fingerprint(),rules.fingerprint,rules.width,
            Map.of(a.region,rules.ports.get(a.region).gateWidth(),b.region,rules.ports.get(b.region).gateWidth()),obstacles,geometry.centerline(),cells);
        if(result.chunks().size()>rules.maxChunks)throw new IllegalStateException("驿道超过区块加载预算");
        return result;
    }
    static boolean avoids(List<Box> obstacles,int margin,int x,int z) {
        for(Box b:obstacles)if(x>=(long)b.minX-margin&&x<=(long)b.maxX+margin&&z>=(long)b.minZ-margin&&z<=(long)b.maxZ+margin)return false;
        return true;
    }
    private static int preferred(Map<Long,Cell> fixed,Point p,int fallback){Cell c=fixed.get(Cell.key(p.x(),p.z()));return c==null?fallback:c.feet2();}
    private static boolean outsideRing(CapitalCity city,int x,int z,int padding){return city.contains(x,z,city.margin()+padding)&&!city.contains(x,z,city.margin());}
    private static double segmentDistance2(int x,int z,int ax,int az,int bx,int bz){double dx=(double)bx-ax,dz=(double)bz-az,l=dx*dx+dz*dz,t=l==0?0:Math.clamp(((x-ax)*dx+(z-az)*dz)/l,0,1);return Math.pow(x-ax-t*dx,2)+Math.pow(z-az-t*dz,2);}
    public static boolean columnAllowed(WorldPlan world,RoadRules rules,String from,String to,int x,int z) {
        return columnAllowed(world,rules,from,to,x,z,world.surfaceY(x,z));
    }
    private static boolean columnAllowed(WorldPlan world,RoadRules rules,String from,String to,int x,int z,int surface) {
        if(!legalBorderCrossing(world,rules,from,to,x,z))return false;
        String region=world.coreRegionAt(x,z).map(WorldPlan.Region::id).orElse("");
        if(!region.equals(from)&&!region.equals(to))return false;
        if(world.intersectsSettlements(x,z,x,z))return false;
        for(CapitalCity city:world.cities()) {
            if(city.contains(x,z,0))return false; // Never grants any city-interior write permission.
            if(city.contains(x,z,city.margin())) {
                var p=rules.ports.get(city.regionId());
                if(p==null||(!city.regionId().equals(from)&&!city.regionId().equals(to)))return false;
                return RoadPortGeometry.frame(p,city.x(),city.z(),city.margin(),rules.width,rules.gridStep).permits(x,z,rules.gateApproachHalfWidth);
            }
        }
        // The real capital spawn remains inside the read-only city. Its legacy
        // square must not cut the explicitly authored exterior public exit.
        var spawn=world.spawn();int pad=world.rules().spawnPadRadius;
        if(Math.abs((long)x-spawn.x())<=pad&&Math.abs((long)z-spawn.z())<=pad)return false;
        return !world.reserved(x,Math.max(surface,world.rules().seaLevel),z);
    }
    static boolean legalBorderCrossing(WorldPlan world,RoadRules rules,String from,String to,int x,int z) {
        for(var b:world.borders())if(b.gate()&&((b.a().equals(from)&&b.b().equals(to))||(b.a().equals(to)&&b.b().equals(from)))) {
            double[] q=world.geometry().warp(x,z);double dx=b.q().x()-b.p().x(),dz=b.q().z()-b.p().z();
            double t=((q[0]-b.p().x())*dx+(q[1]-b.p().z())*dz)/(dx*dx+dz*dz);
            if(t<0||t>1)return true;
            double distance=Math.hypot(q[0]-b.p().x()-t*dx,q[1]-b.p().z()-t*dz);
            return distance>rules.width||Math.hypot(q[0]-b.crossing().x(),q[1]-b.crossing().z())<=world.rules().passWidth;
        }
        return true;
    }
}
