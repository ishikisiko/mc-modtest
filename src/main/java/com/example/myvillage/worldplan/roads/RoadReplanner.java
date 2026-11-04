package com.example.myvillage.worldplan.roads;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import static com.example.myvillage.worldplan.roads.RoadAStar.Point;

/** A full-footprint grade failure becomes an exclusion for the next bounded search. */
public final class RoadReplanner {
    public static final class Budget {
        private final RoadAStar.SearchBudget search;
        private final int maxReplans;
        private int replans;
        public Budget(int expansions,int maxReplans) {
            if(maxReplans<0)throw new IllegalArgumentException("道路改道次数不能为负数");
            search=new RoadAStar.SearchBudget(expansions);this.maxReplans=maxReplans;
        }
        public int remainingExpansions(){return search.remaining();}
        public int replans(){return replans;}
    }
    private RoadReplanner() {}

    public static <T> T findVia(String label,List<Point> stops,RoadAStar.Field source,int step,
                               Budget budget,RoadAStar.Cost cost,Function<List<Point>,T> realize) {
        int radius=source.corridorWidth()/2;
        var avoided=new ArrayList<Point>();
        var field=new RoadAStar.Field() {
            public int height(int x,int z){return source.height(x,z);}
            public int waterDepth(int x,int z){return source.waterDepth(x,z);}
            public int waterCostDepth(int x,int z){return source.waterCostDepth(x,z);}
            public int minimumFeet2(int x,int z){return source.minimumFeet2(x,z);}
            public int maximumFeet2(int x,int z){return source.maximumFeet2(x,z);}
            public int corridorWidth(){return source.corridorWidth();}
            public boolean allowed(int x,int z){
                for(var point:avoided)if(Math.abs((long)x-point.x())<=radius&&Math.abs((long)z-point.z())<=radius)return false;
                return source.allowed(x,z);
            }
        };
        while(true) {
            var path=RoadAStar.findVia(stops,field,step,budget.search,cost);
            try{return realize.apply(path);}
            catch(RoadNetworkGeometry.GradeConflict failure) {
                if(budget.replans>=budget.maxReplans||budget.search.remaining()==0)
                    throw new IllegalStateException("道路改道预算已耗尽："+label+"；"+failure.getMessage(),failure);
                var point=exclusion(path,stops,failure,Math.max(source.corridorWidth(),step));
                if(point==null)throw new IllegalStateException("道路必经出口附近没有可改道的位置："+label+"；"+failure.getMessage(),failure);
                avoided.add(point);budget.replans++;
                org.slf4j.LoggerFactory.getLogger(RoadReplanner.class).info(
                        "道路 {} 的完整路幅存在高度冲突，第 {} 次改道避开 {},{}；{}",
                        label,budget.replans,point.x(),point.z(),failure.getMessage());
            }
        }
    }
    private static Point exclusion(List<Point> path,List<Point> stops,RoadNetworkGeometry.GradeConflict failure,int guard) {
        var targets=List.of(new Point(failure.cell().x(),failure.cell().z()),
                new Point(failure.lower().x(),failure.lower().z()),new Point(failure.upper().x(),failure.upper().z()));
        Point best=null;long distance=Long.MAX_VALUE;
        for(var candidate:RoadGeometry.densify(path)) {
            if(stops.stream().anyMatch(p->Math.abs((long)p.x()-candidate.x())<=guard&&Math.abs((long)p.z()-candidate.z())<=guard))continue;
            long score=Long.MAX_VALUE;
            for(var target:targets){long dx=(long)candidate.x()-target.x(),dz=(long)candidate.z()-target.z();score=Math.min(score,dx*dx+dz*dz);}
            if(score<distance){best=candidate;distance=score;}
        }
        return best;
    }
}
