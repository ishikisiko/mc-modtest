package com.example.myvillage.worldplan.roads;

import java.util.*;

/**
 * WorldPlan adapter derived from RoadWeaver BasicAStarPathfinder (MIT, shiroha-233).
 * Upstream: 9b76a41d9abefb969aa9950ef6b502c0c0dc7e19. See META-INF/LICENSE-RoadWeaver.txt.
 * Retains the eight-neighbour weighted A*; validates every transition rather than only its endpoint.
 * Pure terrain input: no server, chunk reads, background world edits or unbounded global caches.
 */
public final class RoadAStar {
    public record Point(int x, int z) {}
    public record Cost(double orthogonal, double diagonal, double elevation, double water,
                       double deviation, double heuristicMultiplier) {}
    public interface Field {
        int height(int x, int z);
        int waterDepth(int x, int z);
        boolean allowed(int x, int z);
    }
    private record Node(Point p, Node parent, double g, double f, long serial) {}
    private RoadAStar() {}

    public static List<Point> find(Point start, Point end, Field field, int step, int budget, Cost cost) {
        if (step < 1 || budget < 1 || !field.allowed(start.x, start.z) || !field.allowed(end.x, end.z))
            throw new IllegalArgumentException("驿道寻路端点不合法");
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f).thenComparingLong(Node::serial));
        Map<Point, Node> active = new HashMap<>();
        Map<Point, Double> closed = new HashMap<>();
        long serial = 0;
        Node first = new Node(start, null, 0, heuristic(start, end, step, cost), serial++);
        open.add(first); active.put(start, first);
        int[][] offsets = {{step,0},{-step,0},{0,step},{0,-step},{step,step},{step,-step},{-step,step},{-step,-step}};
        while (!open.isEmpty() && budget > 0) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            Node current = open.remove();
            if (active.get(current.p) != current) continue;
            active.remove(current.p); budget--;
            if (Math.max(Math.abs(current.p.x-end.x), Math.abs(current.p.z-end.z)) <= step && clear(current.p,end,field)) {
                ArrayList<Point> result = new ArrayList<>();
                for (Node n=current;n!=null;n=n.parent) result.add(n.p);
                Collections.reverse(result);
                if (!result.getLast().equals(end)) result.add(end);
                return List.copyOf(result);
            }
            closed.put(current.p, current.g);
            for (int[] off: offsets) {
                Point next = new Point(current.p.x+off[0], current.p.z+off[1]);
                if (!clear(current.p,next,field)) continue;
                double move = (off[0]!=0 && off[1]!=0 ? cost.diagonal : cost.orthogonal)
                    + Math.abs(field.height(next.x,next.z)-field.height(current.p.x,current.p.z))*cost.elevation
                    + field.waterDepth(next.x,next.z)*cost.water
                    + deviation(next,start,end)*cost.deviation/step;
                double g=current.g+move;
                if (closed.getOrDefault(next,Double.POSITIVE_INFINITY)<=g) continue;
                Node previous=active.get(next);
                if (previous==null || g<previous.g) {
                    Node n=new Node(next,current,g,g+heuristic(next,end,step,cost),serial++);
                    active.put(next,n);open.add(n);
                }
            }
        }
        throw new IllegalStateException("驿道寻路未找到合法路线（搜索预算已用尽或通道不连通）");
    }
    static boolean clear(Point a, Point b, Field field) {
        int count=Math.max(Math.abs(b.x-a.x),Math.abs(b.z-a.z));
        for(int i=0;i<=count;i++) {
            double t=count==0?0:i/(double)count;
            int x=(int)Math.round(a.x+(b.x-a.x)*t),z=(int)Math.round(a.z+(b.z-a.z)*t);
            if(!field.allowed(x,z))return false;
            // A diagonal must not squeeze through two forbidden cardinal neighbours.
            if(i>0) {
                double previous=(i-1)/(double)count;
                int px=(int)Math.round(a.x+(b.x-a.x)*previous),pz=(int)Math.round(a.z+(b.z-a.z)*previous);
                if(x!=px&&z!=pz&&(!field.allowed(px,z)||!field.allowed(x,pz)))return false;
            }
        }
        return true;
    }
    private static double heuristic(Point a,Point b,int step,Cost c) {
        double dx=Math.abs(a.x-b.x)/(double)step,dz=Math.abs(a.z-b.z)/(double)step;
        return (Math.min(dx,dz)*c.diagonal+Math.abs(dx-dz)*c.orthogonal)*c.heuristicMultiplier;
    }
    private static double deviation(Point p,Point a,Point b) {
        double length=Math.hypot(b.x-a.x,b.z-a.z);
        return length==0?0:Math.abs((b.z-a.z)*(double)p.x-(b.x-a.x)*(double)p.z+(double)b.x*a.z-(double)b.z*a.x)/length;
    }
}
