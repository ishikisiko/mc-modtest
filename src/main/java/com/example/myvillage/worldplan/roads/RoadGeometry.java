package com.example.myvillage.worldplan.roads;

import java.util.*;
import java.util.function.*;
import static com.example.myvillage.worldplan.roads.RoadAStar.Point;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

/** Rasterizes the COMPLETE width and constrains ramps after all overlaps have been resolved. */
public final class RoadGeometry {
    public record Geometry(List<Point> centerline,List<Cell> cells) {}
    private RoadGeometry() {}
    public static Geometry build(List<Point> raw,int width,int smoothing,int startFeet2,int endFeet2,
                                 ToIntBiFunction<Integer,Integer> height,BiPredicate<Integer,Integer> allowed,int maxEarthwork,int maxColumns) {
        return build(raw,width,smoothing,startFeet2,endFeet2,height,allowed,maxEarthwork,maxColumns,List.of());
    }
    public static Geometry build(List<Point> raw,int width,int smoothing,int startFeet2,int endFeet2,
                                 ToIntBiFunction<Integer,Integer> height,BiPredicate<Integer,Integer> allowed,int maxEarthwork,int maxColumns,List<Cell> joins) {
        return build(raw,width,smoothing,startFeet2,endFeet2,height,allowed,maxEarthwork,maxColumns,joins,0);
    }
    public static Geometry build(List<Point> raw,int width,int smoothing,int startFeet2,int endFeet2,
                                 ToIntBiFunction<Integer,Integer> height,BiPredicate<Integer,Integer> allowed,int maxEarthwork,int maxColumns,List<Cell> joins,int lift2) {
        List<Point> line=densify(raw);int count=line.size();
        if(count<2||Math.abs(startFeet2-endFeet2)>count-1)throw new IllegalArgumentException("驿道坡长不足");
        int[] desired=new int[count],profile=new int[count];
        for(int i=0;i<count;i++)desired[i]=height.applyAsInt(line.get(i).x(),line.get(i).z())*2+lift2;
        for(int i=0;i<count;i++) {
            long sum=0;int n=0;
            for(int j=Math.max(0,i-smoothing);j<=Math.min(count-1,i+smoothing);j++){sum+=desired[j];n++;}
            profile[i]=(int)Math.round(sum/(double)n);
        }
        profile[0]=startFeet2;profile[count-1]=endFeet2;
        for(int i=0;i<Math.min(width,count);i++) {profile[i]=startFeet2;profile[count-1-i]=endFeet2;}
        for(int i=1;i<count;i++)profile[i]=Math.clamp(profile[i],profile[i-1]-1,profile[i-1]+1);
        profile[count-1]=endFeet2;
        for(int i=count-2;i>=0;i--)profile[i]=Math.clamp(profile[i],profile[i+1]-1,profile[i+1]+1);
        if(profile[0]!=startFeet2)throw new IllegalStateException("驿道无法平顺衔接门外地坪");
        Map<Long,Cell> cells=new LinkedHashMap<>();int radius=width/2;
        for(int i=0;i<count;i++) {
            Point p=line.get(i);
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
                int x=p.x()+dx,z=p.z()+dz;
                if(!allowed.test(x,z))throw new IllegalStateException("完整路幅触及保护区或禁止州界："+x+","+z);
                long k=Cell.key(x,z);var old=cells.get(k);
                if(old==null||old.feet2()<profile[i])cells.put(k,new Cell(x,z,profile[i],false));
            }
            if(cells.size()>maxColumns)throw new IllegalStateException("驿道路幅超过配置预算");
        }
        // Existing city exits can be stepped across their width. Intersect the half-block
        // slope cones of EVERY immutable exit column; a nonempty interval proves a join.
        // Clamping to those bounds before the global lowering pass retains all lower
        // bounds because the max-of-cones lower envelope is itself 1-Lipschitz.
        for(var entry:new ArrayList<>(cells.entrySet())) {
            Cell c=entry.getValue();int low=Integer.MIN_VALUE,high=Integer.MAX_VALUE;
            for(Cell anchor:joins) {
                int distance=Math.abs(c.x()-anchor.x())+Math.abs(c.z()-anchor.z());
                low=Math.max(low,anchor.feet2()-distance);high=Math.min(high,anchor.feet2()+distance);
            }
            if(low>high)throw new IllegalStateException("城门横断面无法在半砖坡度内衔接："+c.x()+","+c.z());
            int value=Math.clamp(c.feet2(),low,high);if(value!=c.feet2())cells.put(c.key(),new Cell(c.x(),c.z(),value,false));
        }
        // The union of adjacent squares can expose lateral one-block cliffs. Lower those
        // columns until every cardinal edge is at most half a block; never hide missing cells.
        PriorityQueue<Cell> queue=new PriorityQueue<>(Comparator.comparingInt(Cell::feet2));queue.addAll(cells.values());
        int[][] offsets={{1,0},{-1,0},{0,1},{0,-1}};
        while(!queue.isEmpty()) {
            Cell c=queue.remove();if(cells.get(c.key())!=c)continue;
            for(int[] off:offsets) {
                long key=Cell.key(c.x()+off[0],c.z()+off[1]);Cell n=cells.get(key);
                if(n!=null&&n.feet2()>c.feet2()+1){Cell lowered=new Cell(n.x(),n.z(),c.feet2()+1,false);cells.put(key,lowered);queue.add(lowered);}
            }
        }
        // The complete two end caps must match the gate exit, including overlap pixels.
        if(joins.isEmpty())for(var cap:List.of(Map.entry(line.getFirst(),startFeet2),Map.entry(line.getLast(),endFeet2)))
            for(int dx=-radius;dx<=radius;dx++) {
                Cell c=cells.get(Cell.key(cap.getKey().x()+dx,cap.getKey().z()));
                if(c==null||c.feet2()!=cap.getValue())throw new IllegalStateException("驿道路口横断面与城门不齐");
            }
        for(Cell anchor:joins)for(int[] off:offsets) {
            Cell c=cells.get(Cell.key(anchor.x()+off[0],anchor.z()+off[1]));
            if(c!=null&&Math.abs(c.feet2()-anchor.feet2())>1)throw new IllegalStateException("城门逐列衔接存在陡坎");
        }
        for(Cell c:cells.values())if(Math.abs(c.feet2()-height.applyAsInt(c.x(),c.z())*2)>maxEarthwork*2)
            throw new IllegalStateException("驿道土方超出限额："+c.x()+","+c.z());
        return new Geometry(List.copyOf(line),List.copyOf(cells.values()));
    }
    public static List<Point> densify(List<Point> raw) {
        ArrayList<Point> result=new ArrayList<>();
        for(int n=0;n<raw.size()-1;n++) {
            Point a=raw.get(n),b=raw.get(n+1);int length=Math.max(Math.abs(a.x()-b.x()),Math.abs(a.z()-b.z()));
            for(int i=0;i<=length;i++) {
                double t=length==0?0:i/(double)length;
                Point p=new Point((int)Math.round(a.x()+(b.x()-a.x())*t),(int)Math.round(a.z()+(b.z()-a.z())*t));
                if(result.isEmpty()||!result.getLast().equals(p))result.add(p);
            }
        }
        return result;
    }
}
