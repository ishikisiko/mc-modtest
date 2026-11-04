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
    static final class Samples {
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
        if(a.region.equals(b.region)||world.borders().stream().noneMatch(e->e.passable()&&Set.of(e.a(),e.b()).equals(Set.of(a.region,b.region))))
            throw new IllegalArgumentException("道路边必须连接两座具有合法州界的不同省城");
        if(blocked.size()>rules.maxObstacles)throw new IllegalArgumentException("驿道避让点超过配置上限");
        return plan(world,rules,a,b,blocked,locked,CityRoadAccess.plan(world,rules,a,blocked),CityRoadAccess.plan(world,rules,b,blocked));
    }
    static RoadPlan plan(WorldPlan world,RoadRules rules,Exit a,Exit b,List<Box> blocked,List<Cell> locked,CityRoadAccess accessA,CityRoadAccess accessB) {
        List<Box> obstacles=List.copyOf(blocked);
        if(obstacles.size()>rules.maxObstacles)throw new IllegalArgumentException("驿道避让点超过配置上限");
        if(a.region.equals(b.region))throw new IllegalArgumentException("请选择两座不同省城");
        var border=world.borders().stream().filter(e->Set.of(e.a(),e.b()).equals(Set.of(a.region,b.region))).findFirst()
            .orElseThrow(()->new IllegalArgumentException("道路边只能连接相邻省城"));
        if(!border.passable())throw new IllegalArgumentException("不能穿越封闭州界");
        Point start=accessA.hub(),end=accessB.hub();
        Point approachA=start,approachB=end;
        var gateStart=a.frame.outside(rules.width/2);var gateEnd=b.frame.outside(rules.width/2);
        if(Math.hypot(gateStart.x()-gateEnd.x(),gateStart.z()-gateEnd.z())>rules.maxDistance)throw new IllegalArgumentException("两城超出首版驿道距离预算");
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
        var fixed=new Long2ObjectOpenHashMap<Cell>();
        for(var group:List.of(locked,accessA.cells(),accessB.cells(),a.existing,b.existing))for(Cell c:group)if(c.existing()||c.band()<2){
            var old=fixed.putIfAbsent(c.key(),c);if(old!=null&&old.feet2()!=c.feet2())throw new IllegalStateException("只读出口与既有道路标高不一致："+c.x()+","+c.z());
        }
        int startFeet=accessA.feet2(),endFeet=accessB.feet2();
        List<Cell> fixedCarriage=new ArrayList<>(locked);fixedCarriage.addAll(accessA.cells());fixedCarriage.addAll(accessB.cells());
        Set<Long> protectedAccess=new HashSet<>(accessA.protectedBody(rules.width));protectedAccess.addAll(accessB.protectedBody(rules.width));
        final int[][] checkpoint={null};
        var field=new RoadTerrainField(world,rules,rules.width,fixed,Map.of(Cell.key(start.x(),start.z()),startFeet,Cell.key(end.x(),end.z()),endFeet)) {
            protected boolean permitsColumn(int x,int z,int ground) {
                return !protectedAccess.contains(Cell.key(x,z))&&avoids(obstacles,rules.obstacleMargin,x,z)
                    &&columnAllowed(world,rules,a.region,b.region,x,z,ground);
            }
            protected boolean inSearchArea(int x,int z) {
                if(x<minX||x>maxX||z<minZ||z>maxZ)return false;
                boolean ring=outsideRing(cityA,x,z,ringPadding)||outsideRing(cityB,x,z,ringPadding);
                return ring||checkpoint[0]==null||segmentDistance2(x,z,start.x(),start.z(),checkpoint[0][0],checkpoint[0][1])<=(double)rules.searchMargin*rules.searchMargin
                    ||segmentDistance2(x,z,checkpoint[0][0],checkpoint[0][1],end.x(),end.z())<=(double)rules.searchMargin*rules.searchMargin;
            }
        };
        int remaining=rules.maxExpansions-accessA.expansions()-accessB.expansions();
        int reroutes=rules.gradeReplans-accessA.replans()-accessB.replans();
        if(remaining<=0||reroutes<0)throw new IllegalStateException("两端公共接坡已用尽该路线的共享搜索预算");
        var budget=new RoadReplanner.Budget(remaining,reroutes);
        List<String> failures=new ArrayList<>();IllegalStateException last=null;
        for(Point pass:crossings){
            if(!field.allowed(pass.x(),pass.z()))continue;
            checkpoint[0]=new int[]{pass.x(),pass.z(),preferred(fixed,pass,field.height(pass.x(),pass.z())*2+rules.style.carriageLift2())};
            // Terrain bounds do not depend on the current waypoint's preferred height.
            List<Point> stops=List.of(start,pass,end);
            List<Cell> joins=new ArrayList<>();joins.add(waypointAnchor(fixed,pass,checkpoint[0][2]));
            for(Point p:List.of(approachA,approachB))joins.add(waypointAnchor(fixed,p,field.height(p.x(),p.z())*2+rules.style.carriageLift2()));
            joins=new ArrayList<>(hardGradeAnchors(a,b,joins));
            final List<Cell> anchors=joins;
            try {
                return RoadReplanner.findVia(a.region+"—"+b.region,stops,field,rules.gridStep,budget,rules.cost,raw->{
                    var geometry=RoadGeometry.build(raw,rules.width,rules.smoothRadius,startFeet,endFeet,
                        field::height,field::column,rules.maxEarthwork,rules.maxColumns,anchors,rules.style.carriageLift2());
                    var middle=RoadNetworkGeometry.fit(geometry.cells(),fixedCarriage,anchors,field::height,rules.maxEarthwork);
                    var body=new LinkedHashMap<Long,Cell>();
                    for(var group:List.of(accessA.cells(),middle,accessB.cells()))for(var c:group) {
                        var old=body.putIfAbsent(c.key(),c);
                        if(old!=null&&old.feet2()!=c.feet2())throw new IllegalStateException("官道没有保持公共接坡标高");
                    }
                    var line=new ArrayList<Point>(accessA.centerline());
                    for(var point:geometry.centerline())if(!line.getLast().equals(point))line.add(point);
                    var reversed=new ArrayList<Point>(accessB.centerline());Collections.reverse(reversed);
                    for(var point:reversed)if(!line.getLast().equals(point))line.add(point);
                    java.util.function.BiPredicate<Integer,Integer> styleAllowed=(x,z)->avoids(obstacles,rules.obstacleMargin,x,z)
                        &&columnAllowed(world,rules,a.region,b.region,x,z,field.ground(x,z))&&field.waterDepth(x,z)<=rules.bridgeMaxDepth;
                    for(var c:body.values())if(!styleAllowed.test(c.x(),c.z()))throw new IllegalStateException("完整公共接坡或官道触及禁止写入列："+c.x()+","+c.z());
                    var styled=RoadOfficialStyle.apply(List.copyOf(body.values()),rules,world.rules().seaLevel,field::ground,styleAllowed);
                    ArrayList<Cell> cells=new ArrayList<>(RoadGuardrailPlanner.apply(styled,line,rules.guard,
                        rules.style.curbRise2(),world.rules().seaLevel,field::ground));
                    cells.addAll(a.existing);cells.addAll(b.existing);
                    if(cells.size()>rules.maxColumns)throw new IllegalStateException("完整驿道超过路幅预算");
                    RoadPlan result=new RoadPlan(a.region,b.region,world.fingerprint(),rules.fingerprint,rules.width,
                        Map.of(a.region,rules.ports.get(a.region).gateWidth(),b.region,rules.ports.get(b.region).gateWidth()),obstacles,line,cells);
                    if(result.chunks().size()>rules.maxChunks)throw new IllegalStateException("驿道超过区块加载预算");
                    return result;
                });
            }catch(java.util.concurrent.CancellationException e){throw e;}
            catch(IllegalArgumentException|IllegalStateException e){
                failures.add(pass+"："+e.getMessage());last=new IllegalStateException(e.getMessage(),e);
                if(budget.remainingExpansions()==0)break;
            }
        }
        throw new IllegalStateException("合法过境点均未形成满足完整路幅与坡度的官道："+String.join("；",failures),last);
    }
    static Cell waypointAnchor(Map<Long,Cell> fixed,Point point,int sampledFeet2) {
        var old=fixed.get(Cell.key(point.x(),point.z()));
        return new Cell(point.x(),point.z(),old==null?sampledFeet2:old.feet2(),old!=null);
    }
    static List<Cell> hardGradeAnchors(Exit a,Exit b,List<Cell> sampled) {
        var anchors=new ArrayList<Cell>();
        sampled.stream().filter(Cell::existing).forEach(anchors::add);
        a.existing.stream().filter(c->(c.x()-a.frame.edgeX())*a.frame.dx()+(c.z()-a.frame.edgeZ())*a.frame.dz()==-1).forEach(anchors::add);
        b.existing.stream().filter(c->(c.x()-b.frame.edgeX())*b.frame.dx()+(c.z()-b.frame.edgeZ())*b.frame.dz()==-1).forEach(anchors::add);
        return List.copyOf(anchors);
    }
    static boolean avoids(List<Box> obstacles,int margin,int x,int z) {
        for(Box b:obstacles)if(x>=(long)b.minX-margin&&x<=(long)b.maxX+margin&&z>=(long)b.minZ-margin&&z<=(long)b.maxZ+margin)return false;
        return true;
    }
    private static int preferred(Map<Long,Cell> fixed,Point p,int fallback){Cell c=fixed.get(Cell.key(p.x(),p.z()));return c==null?fallback:c.feet2();}
    private static boolean outsideRing(CapitalCity city,int x,int z,int padding){return city.contains(x,z,city.margin()+padding)&&!city.contains(x,z,city.margin());}
    private static double segmentDistance2(int x,int z,int ax,int az,int bx,int bz){double dx=(double)bx-ax,dz=(double)bz-az,l=dx*dx+dz*dz,t=l==0?0:Math.clamp(((x-ax)*dx+(z-az)*dz)/l,0,1);return Math.pow(x-ax-t*dx,2)+Math.pow(z-az-t*dz,2);}
    public static boolean columnAllowed(WorldPlan world,RoadRules rules,String from,String to,int x,int z) {
        var sect=world.sectSite(from);
        if(sect.isPresent())return to.equals(sect.get().regionId())&&SectRoadPlanner.columnAllowed(world,rules,sect.get(),x,z);
        return columnAllowed(world,rules,from,to,x,z,world.surfaceY(x,z));
    }
    static boolean columnAllowed(WorldPlan world,RoadRules rules,String from,String to,int x,int z,int surface) {
        if(!legalBorderCrossing(world,rules,from,to,x,z))return false;
        String region=world.coreRegionAt(x,z).map(WorldPlan.Region::id).orElse("");
        if(!region.equals(from)&&!region.equals(to))return false;
        if(world.intersectsSettlements(x,z,x,z)||world.intersectsSects(x,z,x,z))return false;
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
