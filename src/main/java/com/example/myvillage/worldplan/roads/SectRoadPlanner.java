package com.example.myvillage.worldplan.roads;

import com.example.myvillage.region.runtime.SectSite;
import com.example.myvillage.worldplan.WorldPlan;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.*;
import static com.example.myvillage.worldplan.roads.RoadAStar.Point;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

/** 官道只到山门外：宗门内部没有道路写权限；支路加入同一个锁高和路口合并过程。 */
public final class SectRoadPlanner {
    private SectRoadPlanner(){}
    public static WorldRoadPlanner.Exit exit(WorldPlan world,RoadRules rules,SectSite site){
        var layout=world.rules().sects.layout(site.blueprintId());
        int x=site.x()+layout.portX,z=site.z()+layout.portZ;
        var frame=new RoadPortGeometry.Frame(x,z,x+layout.portDx,z+layout.portDz,layout.portDx,layout.portDz,1,
            site.margin()+world.rules().sects.buffer+rules.gridStep*2);
        List<Cell> existing=new ArrayList<>();
        for(int i=-rules.sectSpurs.width()/2;i<=rules.sectSpurs.width()/2;i++)
            existing.add(new Cell(x+frame.tx()*i,z+frame.tz()*i,(site.baseY()+layout.portY)*2,true));
        return new WorldRoadPlanner.Exit(site.id(),frame,(site.baseY()+layout.portY)*2,existing);
    }
    public static boolean columnAllowed(WorldPlan world,RoadRules rules,SectSite site,int x,int z){
        if(!world.coreRegionAt(x,z).map(WorldPlan.Region::id).orElse("").equals(site.regionId()))return false;
        if(world.intersectsSettlements(x,z,x,z))return false;
        boolean collar=false;
        for(var other:world.sectSites())if(other.intersects(x,z,x,z,world.rules().sects.buffer)){
            if(!other.id().equals(site.id()))return false;
            if(x>=site.x()&&x<site.x()+site.width()&&z>=site.z()&&z<site.z()+site.depth())return false;
            var p=world.rules().sects.layout(site.blueprintId());
            int dx=x-(site.x()+p.portX+p.portDx),dz=z-(site.z()+p.portZ+p.portDz);
            int along=dx*p.portDx+dz*p.portDz,across=dx*p.portDz-dz*p.portDx;
            if(along<0||Math.abs(across)>rules.sectSpurs.approachHalfWidth())return false;
            collar=true;
        }
        for(var c:world.cities())if(c.contains(x,z,c.margin()))return false;
        var spawn=world.spawn();int pad=world.rules().spawnPadRadius;
        if(Math.abs((long)x-spawn.x())<=pad&&Math.abs((long)z-spawn.z())<=pad)return false;
        return collar||!world.reserved(x,world.surfaceY(x,z),z);
    }
    public static RoadPlan plan(WorldPlan world,RoadRules rules,SectSite site,List<RoadPlan> routes,List<Cell> locked){
        var sp=rules.sectSpurs;var exit=exit(world,rules,site);var start=exit.frame().outside(sp.width()/2);
        var fixed=new Long2ObjectOpenHashMap<Cell>();for(var c:locked)if(c.existing()||c.band()<2)fixed.put(c.key(),c);
        for(var c:exit.existing()){var old=fixed.putIfAbsent(c.key(),c);if(old!=null&&old.feet2()!=c.feet2())throw new IllegalStateException("宗门出口与既有道路标高不一致");}
        var possible=new LinkedHashMap<Long,Point>();
        for(var route:routes)for(int i=0;i<route.centerline().size();i+=sp.targetSpacing()){
            var p=route.centerline().get(i);var c=fixed.get(Cell.key(p.x(),p.z()));
            if(c!=null&&!c.existing()&&Math.hypot(p.x()-start.x(),p.z()-start.z())<=sp.maxDistance()
                &&columnAllowed(world,rules,site,p.x(),p.z()))possible.putIfAbsent(c.key(),p);
        }
        var sorted=possible.values().stream().sorted(Comparator.comparingDouble((Point p)->Math.hypot(p.x()-start.x(),p.z()-start.z())).thenComparingInt(Point::x).thenComparingInt(Point::z)).toList();
        List<Point> targets=new ArrayList<>();
        for(var p:sorted)if(targets.stream().noneMatch(q->Math.hypot(p.x()-q.x(),p.z()-q.z())<sp.targetSpacing())){targets.add(p);if(targets.size()==sp.candidates())break;}
        List<String> failures=new ArrayList<>();
        for(var end:targets){
            try{return connect(world,rules,site,exit,start,end,fixed,locked);}
            catch(java.util.concurrent.CancellationException e){throw e;}
            catch(IllegalArgumentException|IllegalStateException e){failures.add(end+"："+e.getMessage());}
        }
        throw new IllegalStateException("宗门支路无法安全接入官道："+site.id()+"；"+String.join("；",failures));
    }
    private static RoadPlan connect(WorldPlan world,RoadRules rules,SectSite site,WorldRoadPlanner.Exit exit,Point start,Point end,
                                    Map<Long,Cell> fixed,List<Cell> locked){
        var sp=rules.sectSpurs;int radius=sp.width()/2;
        int startFeet=exit.feet2(),endFeet=fixed.get(Cell.key(end.x(),end.z())).feet2();
        int loX=Math.min(start.x(),end.x())-sp.searchMargin(),hiX=Math.max(start.x(),end.x())+sp.searchMargin();
        int loZ=Math.min(start.z(),end.z())-sp.searchMargin(),hiZ=Math.max(start.z(),end.z())+sp.searchMargin();
        // 城市仍完全只读；搜索窗口必须容纳其外围，不能把大城当作横跨整个窗口的墙。
        int ring=rules.gridStep*2+radius;
        for(var city:world.cities())if(city.intersects(loX,loZ,hiX,hiZ,city.margin())){
            loX=Math.min(loX,city.x()-city.margin()-ring);hiX=Math.max(hiX,city.x()+city.bundle().width+city.margin()+ring);
            loZ=Math.min(loZ,city.z()-city.margin()-ring);hiZ=Math.max(hiZ,city.z()+city.bundle().depth+city.margin()+ring);
        }
        final int minX=loX,maxX=hiX,minZ=loZ,maxZ=hiZ;
        var field=new RoadTerrainField(world,rules,sp.width(),fixed,Map.of(Cell.key(start.x(),start.z()),startFeet,Cell.key(end.x(),end.z()),endFeet)) {
            protected boolean permitsColumn(int x,int z,int ground){return columnAllowed(world,rules,site,x,z);}
            protected boolean inSearchArea(int x,int z){return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
        };
        var joins=new ArrayList<>(exit.existing());joins.add(new Cell(end.x(),end.z(),endFeet,false));
        return RoadReplanner.findVia(site.id(),List.of(start,end),field,rules.gridStep,
                new RoadReplanner.Budget(sp.maxExpansions(),rules.gradeReplans),rules.cost,raw->{
            var geometry=RoadGeometry.build(raw,sp.width(),rules.smoothRadius,startFeet,endFeet,field::height,field::column,rules.maxEarthwork,rules.maxColumns,joins,rules.style.carriageLift2());
            var fitted=RoadNetworkGeometry.fit(geometry.cells(),locked,joins,field::height,rules.maxEarthwork);
            var styled=RoadOfficialStyle.apply(fitted,rules,world.rules().seaLevel,field::ground,field::column);
            var cells=new ArrayList<>(RoadGuardrailPlanner.apply(styled,geometry.centerline(),rules.guard,rules.style.curbRise2(),world.rules().seaLevel,field::ground));
            cells.addAll(exit.existing());
            var result=new RoadPlan(site.id(),site.regionId(),world.fingerprint(),rules.fingerprint,sp.width(),Map.of(site.id(),sp.width(),site.regionId(),rules.width),List.of(),geometry.centerline(),cells);
            if(result.chunks().size()>rules.maxChunks)throw new IllegalStateException("宗门支路超过区块预算");
            return result;
        });
    }
}
