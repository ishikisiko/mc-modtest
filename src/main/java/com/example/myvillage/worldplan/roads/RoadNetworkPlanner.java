package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import java.util.*;

/** 以合法州界为候选边，逐条落地并锁定交汇标高，直至全部省城处于同一连通分量。 */
public final class RoadNetworkPlanner {
    public record Result(List<RoadPlan> routes,List<RoadPlan.Cell> cells) {
        public Result {routes=List.copyOf(routes);cells=List.copyOf(cells);}
    }
    private RoadNetworkPlanner() {}
    public static Result plan(WorldPlan world,RoadRules rules,List<RoadPlan> completed,List<RoadPlan.Cell> previous) {
        var regions=world.cities().stream().map(c->c.regionId()).sorted().toList();
        if(regions.size()!=world.regions().size()||!rules.ports.keySet().containsAll(regions))throw new IllegalStateException("全网缺少正式省城出口");
        Map<String,String> groups=new HashMap<>();regions.forEach(r->groups.put(r,r));
        List<RoadPlan> routes=new ArrayList<>(completed);List<RoadPlan.Cell> locked=new ArrayList<>(previous);
        for(var p:completed)join(groups,p.from(),p.to());
        var candidates=world.borders().stream().filter(WorldPlan.Border::passable)
            .sorted(Comparator.comparingDouble((WorldPlan.Border b)->distance(world,b)).thenComparing(b->RoadNetworkGeometry.id(b.a(),b.b()))).toList();
        List<String> failures=new ArrayList<>();
        for(var edge:candidates) {
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            if(root(groups,edge.a()).equals(root(groups,edge.b())))continue;
            try {
                var a=RoadPaver.exit(world,rules,edge.a(),RoadPaver.declaredGate(world,rules,edge.a()));
                var b=RoadPaver.exit(world,rules,edge.b(),RoadPaver.declaredGate(world,rules,edge.b()));
                var road=WorldRoadPlanner.plan(world,rules,a,b,List.of(),locked);
                routes.add(road);locked.addAll(road.cells());join(groups,edge.a(),edge.b());
                if(routes.size()>rules.maxRoutes)throw new IllegalStateException("全网路线数超出预算");
            } catch(java.util.concurrent.CancellationException e){throw e;}
            catch(IllegalArgumentException|IllegalStateException e){failures.add(edge.a()+"—"+edge.b()+"："+e.getMessage());}
            if(regions.stream().map(r->root(groups,r)).distinct().count()==1)break;
        }
        if(regions.stream().map(r->root(groups,r)).distinct().count()!=1)throw new IllegalStateException("八城未全通；"+String.join("；",failures));
        return new Result(routes,RoadNetworkGeometry.merge(world,rules,routes,previous));
    }
    private static double distance(WorldPlan world,WorldPlan.Border b){var a=world.capitalOf(b.a()).orElseThrow();var c=world.capitalOf(b.b()).orElseThrow();return Math.hypot(a.x()-c.x(),a.z()-c.z());}
    private static String root(Map<String,String> groups,String r){String p=groups.get(r);if(p==null)throw new IllegalArgumentException("未知省城："+r);while(!p.equals(groups.get(p)))p=groups.get(p);return p;}
    private static void join(Map<String,String> groups,String a,String b){groups.put(root(groups,a),root(groups,b));}
}
