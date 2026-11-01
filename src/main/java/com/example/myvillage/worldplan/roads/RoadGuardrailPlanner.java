package com.example.myvillage.worldplan.roads;

import java.util.*;
import java.util.function.*;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;
import static com.example.myvillage.worldplan.roads.RoadAStar.Point;

/** 仅选择现有石缘上的危险路段，中央五格与城门端部始终不放护栏。 */
public final class RoadGuardrailPlanner {
    private static final int[][] DIRECTIONS={{0,-1},{1,0},{0,1},{-1,0}};
    private RoadGuardrailPlanner() {}
    public static List<Cell> apply(List<Cell> cells,List<Point> line,RoadGuardrailRules rules,int curbRise2,int seaLevel,ToIntBiFunction<Integer,Integer> ground) {
        Map<Long,Cell> all=new HashMap<>();cells.forEach(c->all.put(c.key(),c));
        Map<Long,Integer> indices=new HashMap<>();for(int i=0;i<line.size();i++)indices.putIfAbsent(Cell.key(line.get(i).x(),line.get(i).z()),i);
        Map<Long,Cell> eligible=new HashMap<>();Set<Long> selected=new HashSet<>();
        for(Cell c:cells) {
            if(c.existing()||c.band()!=2)continue;
            int nearest=-1,best=Integer.MAX_VALUE;
            for(int dx=-8;dx<=8;dx++)for(int dz=-8;dz<=8;dz++) {
                Integer index=indices.get(Cell.key(c.x()+dx,c.z()+dz));int d=dx*dx+dz*dz;
                if(index!=null&&(d<best||d==best&&index<nearest)){nearest=index;best=d;}
            }
            if(nearest<rules.endGap||nearest>=line.size()-rules.endGap)continue;
            boolean exterior=false;for(int[] d:DIRECTIONS){Cell n=all.get(Cell.key(c.x()+d[0],c.z()+d[1]));if(n==null||n.band()==3){exterior=true;break;}}
            if(!exterior)continue;eligible.put(c.key(),c);
            Point p=line.get(nearest);int dx=c.x()-p.x(),dz=c.z()-p.z();
            int sx=Math.abs(dx)>=Math.abs(dz)?Integer.signum(dx):0,sz=sx==0?Integer.signum(dz):0;
            int y=ground.applyAsInt(c.x()+sx*rules.probeDistance,c.z()+sz*rules.probeDistance);
            if(y<seaLevel||c.feet2()-curbRise2-y*2>=rules.minDrop2)selected.add(c.key());
        }
        for(int step=0;step<rules.runPadding;step++) {
            Set<Long> extra=new HashSet<>();for(long k:selected){Cell c=all.get(k);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){long n=Cell.key(c.x()+dx,c.z()+dz);if(eligible.containsKey(n))extra.add(n);}}selected.addAll(extra);
        }
        // 方块弯角用现有石缘内的一个角点接成直角，不跨到通行面上。
        Set<Long> corners=new HashSet<>();for(long key:selected){Cell c=all.get(key);for(int dx:new int[]{-1,1})for(int dz:new int[]{-1,1}) {
            if(!selected.contains(Cell.key(c.x()+dx,c.z()+dz)))continue;
            Cell a=all.get(Cell.key(c.x()+dx,c.z())),b=all.get(Cell.key(c.x(),c.z()+dz));
            Cell corner=a!=null&&a.band()==2?a:b;if(corner!=null&&corner.band()==2)corners.add(corner.key());
        }}selected.addAll(corners);
        List<Cell> result=new ArrayList<>();for(Cell c:cells) {
            int mask=-1;if(selected.contains(c.key())) {
                mask=0;for(int i=0;i<4;i++){int[] d=DIRECTIONS[i];Cell n=all.get(Cell.key(c.x()+d[0],c.z()+d[1]));
                    if(n!=null&&selected.contains(n.key())&&Math.abs(n.feet2()-c.feet2())<=rules.maxJoinStep2){mask|=1<<i;if(n.feet2()-c.feet2()>1)mask|=16<<i;}}
            }
            result.add(new Cell(c.x(),c.z(),c.feet2(),c.existing(),c.band(),mask));
        }return List.copyOf(result);
    }
}
