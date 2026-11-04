package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import java.util.*;
import java.util.function.ToIntBiFunction;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

/** 已建通行面锁高；路口由所有路线的通行面并集统一生成。 */
public final class RoadNetworkGeometry {
    private static final int[][] CARDINAL={{1,0},{-1,0},{0,1},{0,-1}};
    public record Change(Cell before,Cell after) {}
    public record Candidate(RoadPlan route,List<Cell> cells) {public Candidate{cells=List.copyOf(cells);}}
    public record GradeLimit(int x,int z,int feet2) {}
    /** The two constraints that made this footprint infeasible are retained for replanning. */
    public static final class GradeConflict extends IllegalStateException {
        private final Cell cell;
        private final GradeLimit lower,upper;
        GradeConflict(Bounds bounds){
            super("新旧道路不能在土方和半砖坡度内衔接："+bounds.cell.x()+","+bounds.cell.z()
                +"；下限="+bounds.low+"，上限="+bounds.high
                +"；下限来源="+bounds.lowSource.x()+","+bounds.lowSource.z()+"@"+bounds.lowOrigin
                +"，上限来源="+bounds.highSource.x()+","+bounds.highSource.z()+"@"+bounds.highOrigin);
            cell=bounds.cell;
            lower=new GradeLimit(bounds.lowSource.x(),bounds.lowSource.z(),bounds.lowOrigin);
            upper=new GradeLimit(bounds.highSource.x(),bounds.highSource.z(),bounds.highOrigin);
        }
        public Cell cell(){return cell;}
        public GradeLimit lower(){return lower;}
        public GradeLimit upper(){return upper;}
    }
    private record Mark(long key,int value) {}
    private static final class Bounds {
        final Cell cell;int low,high,lowOrigin,highOrigin;Cell lowSource,highSource;
        Bounds(Cell c,int l,int h){cell=c;low=lowOrigin=l;high=highOrigin=h;lowSource=highSource=c;}
        void minimum(int value,Cell source){if(value>low){low=lowOrigin=value;lowSource=source;}}
        void maximum(int value,Cell source){if(value<high){high=highOrigin=value;highSource=source;}}
    }
    private RoadNetworkGeometry() {}
    public static Map<Long,Cell> index(List<Cell> cells) {
        Map<Long,Cell> result=new LinkedHashMap<>();for(Cell c:cells)if(result.putIfAbsent(c.key(),c)!=null)throw new IllegalArgumentException("道路列重复："+c.x()+","+c.z());return result;
    }
    public static List<Cell> fit(List<Cell> cells,List<Cell> locked,List<Cell> gates,ToIntBiFunction<Integer,Integer> height,int earthwork) {
        Map<Long,Cell> fixed=new HashMap<>();for(Cell c:locked)if(c.existing()||c.band()<2)fixed.put(c.key(),c);
        Map<Long,Cell> anchors=new HashMap<>();for(Cell c:gates)anchors.put(c.key(),c);
        Map<Long,Bounds> bounds=new HashMap<>();
        for(Cell c:cells) {
            int ground=height.applyAsInt(c.x(),c.z())*2;var b=new Bounds(c,ground-earthwork*2,ground+earthwork*2);
            Cell exact=anchors.get(c.key());if(exact!=null){b.minimum(exact.feet2(),exact);b.maximum(exact.feet2(),exact);}
            for(int[] d:CARDINAL){Cell g=anchors.get(Cell.key(c.x()+d[0],c.z()+d[1]));if(g!=null){b.minimum(g.feet2()-1,g);b.maximum(g.feet2()+1,g);}}
            Cell old=fixed.get(c.key());if(old!=null){b.minimum(old.feet2(),old);b.maximum(old.feet2(),old);}
            for(int[] d:CARDINAL){Cell n=fixed.get(Cell.key(c.x()+d[0],c.z()+d[1]));if(n!=null){b.minimum(n.feet2()-1,n);b.maximum(n.feet2()+1,n);}}
            bounds.put(c.key(),b);
        }
        propagate(bounds,true);propagate(bounds,false);
        List<Cell> result=new ArrayList<>();
        for(Cell c:cells){Bounds b=bounds.get(c.key());if(b.low>b.high)throw new GradeConflict(b);result.add(new Cell(c.x(),c.z(),Math.clamp(c.feet2(),b.low,b.high),false));}
        Map<Long,Cell> after=index(result);
        for(Cell c:result)for(int[] d:CARDINAL){Cell n=after.get(Cell.key(c.x()+d[0],c.z()+d[1]));if(n!=null&&Math.abs(n.feet2()-c.feet2())>1)throw new IllegalStateException("共享路口存在陡坎");}
        return List.copyOf(result);
    }
    private static void propagate(Map<Long,Bounds> bounds,boolean lower) {
        Comparator<Mark> comparator=Comparator.comparingInt(Mark::value);if(lower)comparator=comparator.reversed();
        var queue=new PriorityQueue<>(comparator);bounds.forEach((k,b)->queue.add(new Mark(k,lower?b.low:b.high)));
        while(!queue.isEmpty()) {
            var m=queue.remove();var b=bounds.get(m.key);if(m.value!=(lower?b.low:b.high))continue;
            for(int[] d:CARDINAL){long k=Cell.key(b.cell.x()+d[0],b.cell.z()+d[1]);var n=bounds.get(k);if(n==null)continue;int value=lower?m.value-1:m.value+1;
                if(lower&&n.low<value){n.low=value;n.lowSource=b.lowSource;n.lowOrigin=b.lowOrigin;queue.add(new Mark(k,value));}
                else if(!lower&&n.high>value){n.high=value;n.highSource=b.highSource;n.highOrigin=b.highOrigin;queue.add(new Mark(k,value));}}
        }
    }
    public static List<Cell> merge(WorldPlan world,RoadRules rules,List<RoadPlan> routes,List<Cell> previous) {
        Set<Long> allowed=new HashSet<>();Map<Long,Cell> body=new LinkedHashMap<>(),gates=new LinkedHashMap<>();
        for(var route:routes)for(Cell c:route.cells()) {
            if(c.existing()){Cell old=gates.putIfAbsent(c.key(),c);if(old!=null&&old.feet2()!=c.feet2())throw new IllegalStateException("共享城门的只读地坪不一致");continue;}
            allowed.add(c.key());
            if(c.band()<2){Cell old=body.putIfAbsent(c.key(),new Cell(c.x(),c.z(),c.feet2(),false));if(old!=null&&old.feet2()!=c.feet2())throw new IllegalStateException("交汇通行面的高度未统一");}
        }
        if(body.size()>rules.maxNetworkColumns)throw new IllegalStateException("路网通行面超过预算");
        List<Cell> styled=RoadOfficialStyle.apply(List.copyOf(body.values()),rules,world.rules().seaLevel,world::surfaceY,(x,z)->allowed.contains(Cell.key(x,z)),rules.maxNetworkColumns);
        Map<Long,Cell> merged=index(styled);
        for(Cell old:previous){if(!allowed.contains(old.key())&&!old.existing())throw new IllegalStateException("路网丢失原有列的权限来源");merged.putIfAbsent(old.key(),old);}
        for(Cell g:gates.values()){Cell old=merged.putIfAbsent(g.key(),g);if(old!=null&&!old.existing())throw new IllegalStateException("路网触及城内只读列");}
        var withGuards=RoadGuardrailPlanner.applyNetwork(List.copyOf(merged.values()),routes.stream().map(RoadPlan::centerline).toList(),rules.guard,rules.style.curbRise2(),world.rules().seaLevel,world::surfaceY);
        Map<Long,Cell> after=index(withGuards);
        for(Cell old:previous)if(old.existing()||old.band()<2){Cell c=after.get(old.key());if(c==null||c.feet2()!=old.feet2()||c.existing()!=old.existing()||!c.existing()&&c.band()>=2)throw new IllegalStateException("原有通行面或只读城门被改变");}
        if(after.size()>rules.maxNetworkColumns||RoadPlan.chunks(withGuards).size()>rules.maxNetworkChunks)throw new IllegalStateException("完整路网超过预算");
        return withGuards.stream().sorted(Comparator.comparingLong(Cell::chunk).thenComparingInt(Cell::x).thenComparingInt(Cell::z)).toList();
    }
    public static List<Change> changes(List<Cell> before,List<Cell> after) {
        Map<Long,Cell> old=index(before);List<Change> result=new ArrayList<>();
        for(Cell c:after)if(!c.existing()&&!c.equals(old.get(c.key())))result.add(new Change(old.get(c.key()),c));
        return List.copyOf(result);
    }
    public static String id(String a,String b){if(a.equals(b))throw new IllegalArgumentException("请选择两座不同省城");return a.compareTo(b)<0?a+"--"+b:b+"--"+a;}
}
