package com.example.myvillage.worldplan.roads;

import java.util.*;
import java.util.function.ToIntBiFunction;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

/** 平台内各列是同一标高变量，相邻平台与引道共同传播上下限。 */
final class RoadJunctionGrades {
    record Result(List<Cell> cells,List<RoadJunctionPlanner.Hub> hubs) {}
    static final class Conflict extends IllegalStateException {
        final BitSet platforms;
        Conflict(Node node){
            super("枢纽平台标高无解："+(int)(node.key>>32)+","+(int)node.key+"；下限="+node.low+"，上限="+node.high);
            platforms=(BitSet)node.lowVia.clone();platforms.or(node.highVia);
        }
    }
    private static final int[][] CARDINAL={{1,0},{-1,0},{0,1},{0,-1}};
    private static final class Node {
        final long key;final int platform,preferred;final Set<Node> edges=new HashSet<>();
        int low=Integer.MIN_VALUE,high=Integer.MAX_VALUE;
        BitSet lowVia=new BitSet(),highVia=new BitSet();
        Node(long key,int platform,int preferred){this.key=key;this.platform=platform;this.preferred=preferred;if(platform>=0){lowVia.set(platform);highVia.set(platform);}}
    }
    private record Mark(Node node,int value) {}
    private RoadJunctionGrades() {}
    static Result fit(List<Cell> body,List<Cell> previous,Collection<Cell> gates,List<RoadJunctionPlanner.Hub> hubs,RoadRules rules,ToIntBiFunction<Integer,Integer> height) {
        Map<Long,Node> at=new HashMap<>();var platforms=new ArrayList<Node>();var nodes=new LinkedHashSet<Node>();
        for(int i=0;i<hubs.size();i++) {
            var h=hubs.get(i);var node=new Node(Cell.key(h.x(),h.z()),i,h.feet2());platforms.add(node);nodes.add(node);
            for(var c:RoadJunctionPlanner.pad(h,rules.junctions.cornerCut()))if(at.putIfAbsent(c.key(),node)!=null)throw new IllegalStateException("枢纽平台重叠");
        }
        Map<Long,Cell> fixed=new HashMap<>();
        for(var c:previous)if(c.existing()||c.band()<2)fixed.put(c.key(),c);
        for(var c:gates)fixed.put(c.key(),c);
        for(var c:body) {
            Node node=at.computeIfAbsent(c.key(),k->new Node(k,-1,c.feet2()));nodes.add(node);
            int ground=height.applyAsInt(c.x(),c.z())*2;
            node.low=Math.max(node.low,ground-rules.maxEarthwork*2);node.high=Math.min(node.high,ground+rules.maxEarthwork*2);
            var exact=fixed.get(c.key());if(exact!=null){node.low=Math.max(node.low,exact.feet2());node.high=Math.min(node.high,exact.feet2());}
            for(var d:CARDINAL){var old=fixed.get(Cell.key(c.x()+d[0],c.z()+d[1]));if(old!=null){node.low=Math.max(node.low,old.feet2()-1);node.high=Math.min(node.high,old.feet2()+1);}}
        }
        for(var c:body)for(var d:CARDINAL){var a=at.get(c.key());var b=at.get(Cell.key(c.x()+d[0],c.z()+d[1]));if(b!=null&&a!=b)a.edges.add(b);}
        propagate(nodes,true);propagate(nodes,false);
        for(var node:nodes)if(node.low>node.high)throw new Conflict(node);
        // 在已求得的共同可行区间内依次选高程；后续平台继续接受前一平台的约束。
        var resolved=new ArrayList<RoadJunctionPlanner.Hub>();
        for(int i=0;i<hubs.size();i++) {
            var node=platforms.get(i);int value=Math.clamp(node.preferred,node.low,node.high);
            node.low=node.high=value;node.lowVia=new BitSet();node.highVia=new BitSet();node.lowVia.set(i);node.highVia.set(i);
            propagate(List.of(node),true);propagate(List.of(node),false);
            var h=hubs.get(i);resolved.add(new RoadJunctionPlanner.Hub(h.x(),h.z(),value,h.radius(),h.branches()));
        }
        var result=new ArrayList<Cell>();Map<Long,Cell> indexed=new HashMap<>();
        for(var c:body){var node=at.get(c.key());if(node.low>node.high)throw new Conflict(node);int value=Math.clamp(c.feet2(),node.low,node.high);var next=new Cell(c.x(),c.z(),value,false);result.add(next);indexed.put(next.key(),next);}
        for(var c:result)for(var d:CARDINAL){var n=indexed.get(Cell.key(c.x()+d[0],c.z()+d[1]));if(n!=null&&Math.abs(n.feet2()-c.feet2())>1)throw new IllegalStateException("枢纽引道存在未处理的半格高差");}
        return new Result(List.copyOf(result),List.copyOf(resolved));
    }
    private static void propagate(Collection<Node> seeds,boolean lower) {
        Comparator<Mark> order=Comparator.comparingInt(Mark::value);if(lower)order=order.reversed();order=order.thenComparingLong(m->m.node.key);
        var queue=new PriorityQueue<Mark>(order);for(var node:seeds)queue.add(new Mark(node,lower?node.low:node.high));
        while(!queue.isEmpty()) {
            var mark=queue.remove();var node=mark.node;if(mark.value!=(lower?node.low:node.high))continue;
            if(node.low>node.high)throw new Conflict(node);
            for(var next:node.edges) {
                int value=lower?mark.value-1:mark.value+1;
                if(lower&&value>next.low){next.low=value;next.lowVia=(BitSet)node.lowVia.clone();if(next.platform>=0)next.lowVia.set(next.platform);queue.add(new Mark(next,value));}
                if(!lower&&value<next.high){next.high=value;next.highVia=(BitSet)node.highVia.clone();if(next.platform>=0)next.highVia.set(next.platform);queue.add(new Mark(next,value));}
            }
        }
    }
}
