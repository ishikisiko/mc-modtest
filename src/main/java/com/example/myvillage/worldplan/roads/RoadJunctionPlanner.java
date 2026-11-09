package com.example.myvillage.worldplan.roads;

import java.util.*;
import java.util.function.ToIntBiFunction;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;
import static com.example.myvillage.worldplan.roads.RoadAStar.Point;

/** 新路网先统一枢纽，再拟合外围坡道；已有通行面与城门始终锁高。 */
public final class RoadJunctionPlanner {
    public record Hub(int x,int z,int feet2,int radius,int branches) {}
    public record Result(List<RoadPlan> routes,List<Hub> hubs) {
        public Result {routes=List.copyOf(routes);hubs=List.copyOf(hubs);}
    }
    private record Seed(int x,int z,int branches,int width) {}
    private record Diagonal(int slope,int width) {}
    /** 保留纯数据输入，调用方可以将失败路网写为可重放诊断，不必重跑整段寻路。 */
    public static final class Failure extends IllegalStateException {
        private final List<RoadPlan> routes;
        Failure(List<RoadPlan> routes,IllegalStateException cause){super(cause.getMessage(),cause);this.routes=List.copyOf(routes);}
        public List<RoadPlan> routes(){return routes;}
    }
    private RoadJunctionPlanner() {}

    public static Result prepare(List<RoadPlan> routes,List<Cell> previous,RoadRules rules,ToIntBiFunction<Integer,Integer> height) {
        try{return prepareChecked(routes,previous,rules,height);}
        catch(IllegalStateException error){throw new Failure(routes,error);}
    }
    private static Result prepareChecked(List<RoadPlan> routes,List<Cell> previous,RoadRules rules,ToIntBiFunction<Integer,Integer> height) {
        Map<Long,Cell> original=new LinkedHashMap<>(),gates=new LinkedHashMap<>();
        Map<Long,Integer> owners=new HashMap<>();
        for(int i=0;i<routes.size();i++)for(var c:routes.get(i).cells()) {
            owners.putIfAbsent(c.key(),i);
            if(c.existing())gates.putIfAbsent(c.key(),c);
            else if(c.band()<2)original.putIfAbsent(c.key(),new Cell(c.x(),c.z(),c.feet2(),false));
        }
        var settings=rules.junctions;
        List<Seed> seeds=seeds(routes,settings.mergeDistance());
        if(seeds.size()>settings.maxNodes())throw new IllegalStateException("道路枢纽超过声明预算");
        if(seeds.isEmpty())return new Result(routes,List.of());
        Map<Long,Cell> fixed=RoadNetworkGeometry.index(previous.stream().filter(c->c.existing()||c.band()<2).toList());
        gates.forEach(fixed::putIfAbsent);
        var hubs=new ArrayList<Hub>();
        for(var seed:seeds) {
            int radius=seed.branches>=4?settings.crossRadius():settings.teeRadius();
            hubs.add(select(seed,radius,original,owners,fixed,height,rules,hubs));
        }
        List<Cell> fitted=null;Map<Long,Cell> body=null;
        // 每次失败只收小发生约束冲突的枢纽，所有尝试都有固定上限。
        for(int attempt=0;attempt<=seeds.size()*(Math.max(settings.crossRadius(),settings.teeRadius())-settings.minimumRadius());attempt++) {
            body=new LinkedHashMap<>(original);
            for(var hub:hubs)for(var c:pad(hub,settings.cornerCut()))body.put(c.key(),c);
            if(body.size()>rules.maxNetworkColumns)throw new IllegalStateException("道路枢纽超过全网通行面预算");
            try {
                var grade=RoadJunctionGrades.fit(List.copyOf(body.values()),previous,gates.values(),hubs,rules,height);
                fitted=grade.cells();hubs=new ArrayList<>(grade.hubs());break;
            }
            catch(RoadJunctionGrades.Conflict conflict) {
                int target=-1;
                for(int i=0;i<hubs.size();i++) {
                    var h=hubs.get(i);
                    if(h.radius()>settings.minimumRadius()&&conflict.platforms.get(i)) {target=i;break;}
                }
                if(target<0)throw new IllegalStateException("道路枢纽无法在原有土方和只读通行面内接坡",conflict);
                var old=hubs.get(target);
                var occupied=new ArrayList<>(hubs);occupied.remove(target);
                hubs.set(target,select(seeds.get(target),old.radius-1,original,owners,fixed,height,rules,occupied));
            }
        }
        if(fitted==null)throw new IllegalStateException("道路枢纽已用尽有界标高求解次数");
        var by=RoadNetworkGeometry.index(fitted);var updated=new ArrayList<RoadPlan>();
        Map<Integer,List<Cell>> additions=new HashMap<>();
        for(var c:fitted)if(!original.containsKey(c.key()))additions.computeIfAbsent(owners.get(c.key()),k->new ArrayList<>()).add(c);
        for(int i=0;i<routes.size();i++) {
            var route=routes.get(i);var cells=new LinkedHashMap<Long,Cell>();
            for(var c:route.cells()) {
                var replacement=by.get(c.key());
                cells.put(c.key(),!c.existing()&&replacement!=null?new Cell(c.x(),c.z(),replacement.feet2(),false):c);
            }
            for(var c:additions.getOrDefault(i,List.of()))cells.put(c.key(),c);
            if(cells.size()>rules.maxColumns)throw new IllegalStateException("道路枢纽超过单路列预算");
            updated.add(new RoadPlan(route.from(),route.to(),route.worldHash(),route.rulesHash(),route.width(),route.gateWidths(),route.obstacles(),route.centerline(),List.copyOf(cells.values())));
        }
        return new Result(updated,hubs);
    }

    private static Hub select(Seed seed,int initial,Map<Long,Cell> original,Map<Long,Integer> owners,Map<Long,Cell> fixed,ToIntBiFunction<Integer,Integer> height,RoadRules rules,List<Hub> occupied) {
        Set<Long> used=new HashSet<>();for(var h:occupied)for(var c:pad(h,rules.junctions.cornerCut()))used.add(c.key());
        var offsets=new ArrayList<Point>();int search=rules.junctions.centerSearchRadius();
        for(int dx=-search;dx<=search;dx++)for(int dz=-search;dz<=search;dz++)offsets.add(new Point(dx,dz));
        offsets.sort(Comparator.comparingInt((Point p)->Math.abs(p.x())+Math.abs(p.z())).thenComparingInt(Point::x).thenComparingInt(Point::z));
        var core=new ArrayList<Cell>();for(int dx=-seed.width/2;dx<=seed.width/2;dx++)for(int dz=-seed.width/2;dz<=seed.width/2;dz++){var c=original.get(Cell.key(seed.x+dx,seed.z+dz));if(c!=null)core.add(c);}
        for(int radius=initial;radius>=rules.junctions.minimumRadius();radius--)for(var offset:offsets) {
            int cx=seed.x+offset.x(),cz=seed.z+offset.z();
            boolean covers=true;for(var c:core)if(Math.max(Math.abs(c.x()-cx),Math.abs(c.z()-cz))>radius||Math.abs(c.x()-cx)+Math.abs(c.z()-cz)>radius*2-rules.junctions.cornerCut()){covers=false;break;}
            if(!covers)continue;
            var shape=pad(new Hub(cx,cz,0,radius,seed.branches),rules.junctions.cornerCut());
            int low=Integer.MIN_VALUE,high=Integer.MAX_VALUE;var levels=new ArrayList<Integer>();boolean allowed=true;
            for(var c:shape) {
                if(!owners.containsKey(c.key())||used.contains(c.key())||gatesOrPermissionConflict(c,fixed)){allowed=false;break;}
                int ground=height.applyAsInt(c.x(),c.z())*2;
                low=Math.max(low,ground-rules.maxEarthwork*2);high=Math.min(high,ground+rules.maxEarthwork*2);
                var lock=fixed.get(c.key());if(lock!=null){low=Math.max(low,lock.feet2());high=Math.min(high,lock.feet2());}
                var existing=original.get(c.key());if(existing!=null)levels.add(existing.feet2());
            }
            if(!allowed||low>high||levels.isEmpty())continue;
            levels.sort(Integer::compareTo);
            return new Hub(cx,cz,Math.clamp(levels.get(levels.size()/2),low,high),radius,seed.branches);
        }
        throw new IllegalStateException("路口预留范围不足以容纳合法枢纽："+seed.x+","+seed.z);
    }
    private static boolean gatesOrPermissionConflict(Cell cell,Map<Long,Cell> fixed) {
        var old=fixed.get(cell.key());return old!=null&&old.existing();
    }
    static List<Cell> pad(Hub hub,int cornerCut) {
        var result=new ArrayList<Cell>();
        for(int dx=-hub.radius;dx<=hub.radius;dx++)for(int dz=-hub.radius;dz<=hub.radius;dz++)
            if(Math.abs(dx)+Math.abs(dz)<=hub.radius*2-cornerCut)result.add(new Cell(hub.x+dx,hub.z+dz,hub.feet2,false));
        return List.copyOf(result);
    }
    private static List<Seed> seeds(List<RoadPlan> routes,int mergeDistance) {
        Map<Long,Set<Long>> neighbors=new HashMap<>();Map<Long,Seed> raw=new HashMap<>();
        Map<Long,Diagonal> diagonals=new HashMap<>();Map<Long,Integer> widths=new HashMap<>();
        for(var route:routes){var line=route.centerline();for(int i=1;i<line.size();i++) {
            var a=line.get(i-1);var b=line.get(i);if(a.equals(b))continue;
            long ak=Cell.key(a.x(),a.z()),bk=Cell.key(b.x(),b.z());
            widths.merge(ak,route.width(),Math::max);widths.merge(bk,route.width(),Math::max);
            neighbors.computeIfAbsent(ak,k->new HashSet<>()).add(bk);neighbors.computeIfAbsent(bk,k->new HashSet<>()).add(ak);
            int dx=b.x()-a.x(),dz=b.z()-a.z();
            if(Math.abs(dx)==1&&Math.abs(dz)==1) {
                long middle=Cell.key(a.x()+b.x(),a.z()+b.z());int slope=dx*dz;
                var old=diagonals.putIfAbsent(middle,new Diagonal(slope,route.width()));
                if(old!=null&&old.slope!=slope){int x=Math.floorDiv(a.x()+b.x(),2),z=Math.floorDiv(a.z()+b.z(),2);raw.put(Cell.key(x,z),new Seed(x,z,4,Math.max(old.width,route.width())));}
            }
        }}
        neighbors.forEach((key,edges)->{if(edges.size()>=3)raw.put(key,new Seed((int)(key>>32),(int)(long)key,edges.size(),widths.get(key)));});
        var sorted=raw.values().stream().sorted(Comparator.comparingInt(Seed::branches).reversed().thenComparingInt(Seed::x).thenComparingInt(Seed::z)).toList();
        var selected=new ArrayList<Seed>();
        for(var seed:sorted)if(selected.stream().noneMatch(s->Math.max(Math.abs(s.x-seed.x),Math.abs(s.z-seed.z))<=mergeDistance))selected.add(seed);
        return selected.stream().sorted(Comparator.comparingInt(Seed::x).thenComparingInt(Seed::z)).toList();
    }
}
