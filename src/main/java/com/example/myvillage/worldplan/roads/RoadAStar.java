package com.example.myvillage.worldplan.roads;

import java.util.*;

/**
 * WorldPlan adapter derived from RoadWeaver BasicAStarPathfinder (MIT, shiroha-233).
 * Upstream: 9b76a41d9abefb969aa9950ef6b502c0c0dc7e19. See META-INF/LICENSE-RoadWeaver.txt.
 * Eight-neighbour weighted A*, with bounded grade labels and complete transition checks.
 * Planning is pure: no server access, chunk reads or world writes.
 */
public final class RoadAStar {
    public record Point(int x,int z) {}
    public record Cost(double orthogonal,double diagonal,double elevation,double water,double deviation,double heuristicMultiplier) {}
    public interface Field {
        int height(int x,int z);
        int waterDepth(int x,int z);
        default int waterCostDepth(int x,int z){return waterDepth(x,z);}
        boolean allowed(int x,int z);
        default int minimumFeet2(int x,int z){return -1_000_000;}
        default int maximumFeet2(int x,int z){return 1_000_000;}
        default int corridorWidth(){return 1;}
    }
    /** Binary ancestor ranges only skip boxes disjoint from a candidate; geometric tests stay exact. */
    private record History(Node[] jumps,int[] boxes,int distance,int depth) {
        static History append(Point p,Node parent){
            if(parent==null)return new History(new Node[0],new int[0],0,0);
            int depth=parent.history.depth+1,levels=32-Integer.numberOfLeadingZeros(depth);Node[] jumps=new Node[levels];int[] boxes=new int[levels*4];
            jumps[0]=parent;boxes[0]=Math.min(p.x,parent.p.x);boxes[1]=Math.min(p.z,parent.p.z);boxes[2]=Math.max(p.x,parent.p.x);boxes[3]=Math.max(p.z,parent.p.z);
            for(int k=1;k<levels;k++){Node mid=jumps[k-1];jumps[k]=mid.history.jumps[k-1];int at=k*4,prior=at-4;for(int j=0;j<4;j++)boxes[at+j]=j<2?Math.min(boxes[prior+j],mid.history.boxes[prior+j]):Math.max(boxes[prior+j],mid.history.boxes[prior+j]);}
            return new History(jumps,boxes,parent.history.distance+Math.max(Math.abs(p.x-parent.p.x),Math.abs(p.z-parent.p.z)),depth);
        }
    }
    private record Node(Point p,Node parent,double g,double f,long serial,int low,int high,int minX,int minZ,int maxX,int maxZ,History history) {
        Node(Point p,Node parent,double g,double f,long serial,int low,int high){this(p,parent,g,f,serial,low,high,parent==null?p.x:Math.min(p.x,parent.minX),parent==null?p.z:Math.min(p.z,parent.minZ),parent==null?p.x:Math.max(p.x,parent.maxX),parent==null?p.z:Math.max(p.z,parent.maxZ),History.append(p,parent));}
    }
    public static final class SearchBudget {
        private int left;
        public SearchBudget(int value){if(value<1)throw new IllegalArgumentException("寻路预算必须为正数");left=value;}
        public int remaining(){return left;}
    }
    private RoadAStar() {}

    public static List<Point> find(Point start,Point end,Field field,int step,int budget,Cost cost){return findVia(List.of(start,end),field,step,budget,cost);}
    /** 各段共享总预算、已有路径与可达标高，不能通过走回头路虚增坡长。 */
    public static List<Point> findVia(List<Point> stops,Field field,int step,int budget,Cost cost) {
        return findVia(stops,field,step,new SearchBudget(budget),cost);
    }
    /** A route's retries and alternate crossings consume this same expansion budget. */
    public static List<Point> findVia(List<Point> stops,Field field,int step,SearchBudget remaining,Cost cost) {
        if(step<1||remaining==null||stops.size()<2)throw new IllegalArgumentException("驿道寻路参数不合法");
        if(remaining.left==0)throw new IllegalStateException("驿道寻路总预算已耗尽");
        Node tail=null;
        for(int i=0;i<stops.size()-1;i++) {
            try {tail=findLeg(stops.get(i),stops.get(i+1),tail,field,step,remaining,cost);}
            catch(java.util.concurrent.CancellationException e){throw e;}
            catch(IllegalStateException e){throw new IllegalStateException("驿道第 "+(i+1)+"/"+(stops.size()-1)+" 段 "+stops.get(i)+" → "+stops.get(i+1)+"："+e.getMessage(),e);}
        }
        List<Point> result=new ArrayList<>();for(Node n=tail;n!=null;n=n.parent)result.add(n.p);Collections.reverse(result);return List.copyOf(result);
    }
    private static Node findLeg(Point start,Point end,Node prefix,Field field,int step,SearchBudget budget,Cost cost) {
        if(!field.allowed(start.x,start.z)||!field.allowed(end.x,end.z))throw new IllegalArgumentException("驿道寻路端点不合法："+start+" → "+end);
        if(field.minimumFeet2(end.x,end.z)>field.maximumFeet2(end.x,end.z))throw new IllegalArgumentException("驿道终点没有满足完整路幅和固定地坪的标高："+end);
        var open=new PriorityQueue<Node>(Comparator.comparingDouble(Node::f).thenComparingLong(Node::serial));
        Map<Point,List<Node>> labels=new HashMap<>();long serial=0;int expanded=0;
        int low=Math.max(field.minimumFeet2(start.x,start.z),prefix==null?-1_000_000:prefix.low),high=Math.min(field.maximumFeet2(start.x,start.z),prefix==null?1_000_000:prefix.high);
        if(low>high)throw new IllegalArgumentException("驿道起点标高不合法");
        Node first=new Node(start,prefix==null?null:prefix.parent,0,heuristic(start,end,step,cost),serial++,low,high);
        open.add(first);labels.put(start,new ArrayList<>(List.of(first)));
        int[][] offsets={{step,0},{-step,0},{0,step},{0,-step},{step,step},{step,-step},{-step,step},{-step,-step}};
        while(!open.isEmpty()&&budget.left>0) {
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            Node current=open.remove();if(labels.get(current.p).stream().noneMatch(n->n==current))continue;
            budget.left--;expanded++;
            if(Math.max(Math.abs(current.p.x-end.x),Math.abs(current.p.z-end.z))<=step&&clear(current.p,end,field)&&simple(current,end,field.corridorWidth())) {
                int[] reach=advance(current,end,field);
                if(reach!=null)return current.p.equals(end)?current:new Node(end,current,current.g,0,serial++,reach[0],reach[1]);
            }
            for(int[] off:offsets) {
                Point next=new Point(current.p.x+off[0],current.p.z+off[1]);
                if(!clear(current.p,next,field))continue;
                int[] reach=advance(current,next,field);if(reach==null)continue;
                double move=(off[0]!=0&&off[1]!=0?cost.diagonal:cost.orthogonal)
                    +Math.abs(field.height(next.x,next.z)-field.height(current.p.x,current.p.z))*cost.elevation
                    +field.waterCostDepth(next.x,next.z)*cost.water+deviation(next,start,end)*cost.deviation/step;
                double g=current.g+move;var previous=labels.computeIfAbsent(next,k->new ArrayList<>());
                if(previous.stream().anyMatch(n->n.g<=g&&n.low<=reach[0]&&n.high>=reach[1]))continue;
                if(!simple(current,next,field.corridorWidth()))continue;
                previous.removeIf(n->g<=n.g&&reach[0]<=n.low&&reach[1]>=n.high);
                Node n=new Node(next,current,g,g+heuristic(next,end,step,cost),serial++,reach[0],reach[1]);previous.add(n);open.add(n);
            }
        }
        throw new IllegalStateException("驿道寻路未找到合法路线：展开="+expanded+"，剩余候选="+open.size()+"，总预算耗尽="+(budget.left==0));
    }
    /** 非相邻路径段保持完整路幅间距，避免自交后让同一地面承担两种高度。 */
    private static boolean simple(Node from,Point to,int width) {
        Node older=from.parent;if(older==null)return true;
        int margin=width>1?width:0,minX=Math.min(from.p.x,to.x)-margin,maxX=Math.max(from.p.x,to.x)+margin,minZ=Math.min(from.p.z,to.z)-margin,maxZ=Math.max(from.p.z,to.z)+margin;
        if(maxX<older.minX||minX>older.maxX||maxZ<older.minZ||minZ>older.maxZ)return true;
        for(Node n=older;n!=null;) {
            if(n.p.equals(to))return false;
            Node skip=null;
            for(int k=n.history.jumps.length-1;k>=0;k--){int at=k*4;var b=n.history.boxes;if(b[at+2]<minX||b[at]>maxX||b[at+3]<minZ||b[at+1]>maxZ){skip=n.history.jumps[k];break;}}
            if(skip!=null){n=skip;continue;}
            int along=from.history.distance-n.history.distance;
            Node current=n;n=n.parent;
            if(width<=1||current.parent==null)continue;
            Point a=current.p,b=current.parent.p;
            if(Math.max(a.x,b.x)<minX||Math.min(a.x,b.x)>maxX||Math.max(a.z,b.z)<minZ||Math.min(a.z,b.z)>maxZ)continue;
            int first=Math.max(Math.abs(to.x-from.p.x),Math.abs(to.z-from.p.z)),second=Math.max(Math.abs(b.x-a.x),Math.abs(b.z-a.z));
            for(int i=0;i<=first;i++) {
                double t=first==0?0:i/(double)first;int x=(int)Math.round(from.p.x+(to.x-from.p.x)*t),z=(int)Math.round(from.p.z+(to.z-from.p.z)*t);
                for(int j=0;j<=second;j++){if(along+i+j<=width*2)continue;double u=second==0?0:j/(double)second;int ox=(int)Math.round(a.x+(b.x-a.x)*u),oz=(int)Math.round(a.z+(b.z-a.z)*u);if(Math.max(Math.abs(x-ox),Math.abs(z-oz))<=width)return false;}
            }
        }
        return true;
    }
    private static int[] advance(Node from,Point to,Field field) {
        int count=Math.max(Math.abs(to.x-from.p.x),Math.abs(to.z-from.p.z)),low=from.low,high=from.high;
        for(int i=0;i<=count;i++){double t=count==0?0:i/(double)count;int x=(int)Math.round(from.p.x+(to.x-from.p.x)*t),z=(int)Math.round(from.p.z+(to.z-from.p.z)*t);low=Math.max(low-(i==0?0:1),field.minimumFeet2(x,z));high=Math.min(high+(i==0?0:1),field.maximumFeet2(x,z));if(low>high)return null;}
        return new int[]{low,high};
    }
    static boolean clear(Point a,Point b,Field field) {
        int count=Math.max(Math.abs(b.x-a.x),Math.abs(b.z-a.z));
        for(int i=0;i<=count;i++) {
            double t=count==0?0:i/(double)count;int x=(int)Math.round(a.x+(b.x-a.x)*t),z=(int)Math.round(a.z+(b.z-a.z)*t);if(!field.allowed(x,z))return false;
            if(i>0){double previous=(i-1)/(double)count;int px=(int)Math.round(a.x+(b.x-a.x)*previous),pz=(int)Math.round(a.z+(b.z-a.z)*previous);if(x!=px&&z!=pz&&(!field.allowed(px,z)||!field.allowed(x,pz)))return false;}
        }
        return true;
    }
    private static double heuristic(Point a,Point b,int step,Cost c){double dx=Math.abs(a.x-b.x)/(double)step,dz=Math.abs(a.z-b.z)/(double)step;return(Math.min(dx,dz)*c.diagonal+Math.abs(dx-dz)*c.orthogonal)*c.heuristicMultiplier;}
    private static double deviation(Point p,Point a,Point b){double length=Math.hypot(b.x-a.x,b.z-a.z);return length==0?0:Math.abs((b.z-a.z)*(double)p.x-(b.x-a.x)*(double)p.z+(double)b.x*a.z-(double)b.z*a.x)/length;}
}
