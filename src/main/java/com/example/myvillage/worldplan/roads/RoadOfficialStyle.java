package com.example.myvillage.worldplan.roads;

import java.util.*;
import java.util.function.*;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

/** A 官道：通行面完整保留，石缘和缓坡仅在允许的外侧增加。 */
public final class RoadOfficialStyle {
    private record Reach(int distance,int feet2,int x,int z) {}
    private RoadOfficialStyle() {}
    public static List<Cell> apply(List<Cell> carriage,RoadRules rules,int seaLevel,ToIntBiFunction<Integer,Integer> ground,BiPredicate<Integer,Integer> allowed) {
        Map<Long,Cell> body=new LinkedHashMap<>();for(Cell c:carriage)body.put(c.key(),c);
        List<Cell> result=new ArrayList<>();int[][] cardinal={{1,0},{-1,0},{0,1},{0,-1}};
        for(Cell c:carriage) {
            boolean edge=false;for(int[] d:cardinal)if(!body.containsKey(Cell.key(c.x()+d[0],c.z()+d[1]))){edge=true;break;}
            result.add(new Cell(c.x(),c.z(),c.feet2(),false,edge?1:0));
        }
        var queue=new PriorityQueue<Reach>(Comparator.comparingInt(Reach::distance).thenComparingInt(Reach::feet2).thenComparingInt(Reach::x).thenComparingInt(Reach::z));
        Map<Long,Reach> best=new HashMap<>();
        for(Cell c:carriage){var r=new Reach(0,c.feet2(),c.x(),c.z());best.put(c.key(),r);queue.add(r);}
        int extent=rules.style.curbWidth()+rules.style.gradeWidth();
        while(!queue.isEmpty()) {
            Reach r=queue.remove();long key=Cell.key(r.x,r.z);if(best.get(key)!=r)continue;
            if(r.distance>0&&allowed.test(r.x,r.z)) {
                int groundY=ground.applyAsInt(r.x,r.z),ground2=groundY*2,curb=r.feet2+rules.style.curbRise2();
                boolean isCurb=r.distance<=rules.style.curbWidth();
                int feet=isCurb?curb:
                    (int)Math.round((curb+(ground2-curb)*(r.distance-rules.style.curbWidth())/(double)rules.style.gradeWidth())/2)*2;
                if((isCurb||groundY>seaLevel)&&(!isCurb||seaLevel-groundY<=rules.maxWaterDepth)&&Math.abs(feet-ground2)<=rules.maxEarthwork*2)
                    result.add(new Cell(r.x,r.z,feet,false,isCurb?2:3));
            }
            if(r.distance==extent)continue;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                if(dx==0&&dz==0)continue;int x=r.x+dx,z=r.z+dz;long k=Cell.key(x,z);
                if(body.containsKey(k))continue;
                Reach old=best.get(k),next=new Reach(r.distance+1,r.feet2,x,z);
                if(old==null||old.distance>next.distance||old.distance==next.distance&&old.feet2>next.feet2){best.put(k,next);queue.add(next);}
            }
        }
        if(result.size()>rules.maxColumns)throw new IllegalStateException("官道完整施工范围超过配置预算");
        return List.copyOf(result);
    }
}
