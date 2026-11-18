package com.example.myvillage.worldplan.mine;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.roads.RoadAStar.Point;
import com.example.myvillage.worldplan.roads.RoadLayout;
import com.example.myvillage.worldplan.roads.RoadNetworkGeometry;
import com.example.myvillage.worldplan.roads.RoadNetworkGeometry.GradeRange;
import com.example.myvillage.worldplan.roads.RoadPlan.Cell;
import java.util.*;

final class StarterMineAccess {
    private static final int[][] CARDINAL = {{1,0}, {-1,0}, {0,1}, {0,-1}};

    record Join(int x,int z,int rotation,int overlap) {
        int dx(){return switch(rotation){case 1 -> -1;case 3 -> 1;default -> 0;};}
        int dz(){return switch(rotation){case 0 -> 1;case 2 -> -1;default -> 0;};}
        Point at(int forward,int lateral){return new Point(x+dx()*forward-dz()*lateral,z+dz()*forward+dx()*lateral);}
        int forward(Point p){return (p.x()-x)*dx()+(p.z()-z)*dz();}
        int lateral(Point p){return (p.z()-z)*dx()-(p.x()-x)*dz();}
    }

    record Corridor(List<Point> columns,List<Point> entrance) {}
    record Profile(Corridor corridor,Map<Long,GradeRange> limits,GradeRange port) {
        List<StarterMineLayout.Path> fit(int portY){
            int target=portY*2;
            var locked=new HashMap<>(limits);
            for(var p:corridor.entrance)locked.put(key(p),new GradeRange(target,target));
            // A common target preserves the half-block grade after clamping to the propagated bounds.
            var desired=corridor.columns.stream().map(p->new Cell(p.x(),p.z(),target,false)).toList();
            try {
                return RoadNetworkGeometry.fitWithin(desired,locked).stream()
                    .map(c->new StarterMineLayout.Path(c.x(),c.z(),c.feet2())).toList();
            } catch(RoadNetworkGeometry.GradeConflict conflict) {
                throw new StarterMinePlanner.Rejected("矿场接路的完整路幅无法形成连续半格台阶",conflict);
            }
        }
    }

    private StarterMineAccess(){}

    static Optional<Join> join(RoadLayout roads,StarterMineDefinition.Placement policy,int x,int z,int rotation){
        var axis=new Join(x,z,rotation,0);
        var anchor=roads.at(x,z);
        if(anchor==null||anchor.band()>=2)return Optional.empty();
        int radius=policy.pathWidth()/2;
        Map<Long,Cell> lead=new HashMap<>();
        for(int forward=0;forward<=policy.roadOverlap();forward++){
            boolean hasRoad=false;
            for(int side=-radius;side<=radius;side++){
                var p=axis.at(forward,side);
                var c=roads.at(p.x(),p.z());
                if(c==null)continue;
                if(c.rail()>=0)return Optional.empty();
                hasRoad=true;
                for(var d:CARDINAL){
                    var neighbor=lead.get(Cell.key(c.x()+d[0],c.z()+d[1]));
                    if(neighbor!=null&&Math.abs(c.feet2()-neighbor.feet2())>1)return Optional.empty();
                }
                lead.put(c.key(),c);
            }
            if(!hasRoad)return Optional.of(new Join(x,z,rotation,forward));
        }
        return Optional.empty();
    }

    static Corridor corridor(Join join,StarterMineDefinition.Placement policy,int length,int offset){
        if(!policy.connectorLengths().contains(length)||!policy.lateralOffsets().contains(offset)||length<=join.overlap)
            throw new StarterMinePlanner.Rejected("矿场接路超出配置范围");
        int radius=policy.pathWidth()/2;
        int bend=(join.overlap+length)/2;
        if(offset!=0&&(bend-radius<join.overlap||bend+radius>=length))
            throw new StarterMinePlanner.Rejected("矿场接路没有足够空间转弯");
        Set<Point> columns=new LinkedHashSet<>();
        for(int forward=0;forward<=length;forward++){
            int center=forward<=bend?0:offset;
            for(int side=-radius;side<=radius;side++)columns.add(join.at(forward,center+side));
        }
        if(offset!=0)for(int forward=bend-radius;forward<=bend+radius;forward++)
            for(int side=Math.min(0,offset)-radius;side<=Math.max(0,offset)+radius;side++)
                columns.add(join.at(forward,side));
        List<Point> entrance=new ArrayList<>();
        for(int side=-radius;side<=radius;side++)entrance.add(join.at(length,offset+side));
        return new Corridor(List.copyOf(columns),List.copyOf(entrance));
    }

    static Profile profile(WorldPlan world,RoadLayout roads,StarterMineDefinition def,Join join,Corridor corridor){
        Map<Long,GradeRange> limits=new HashMap<>();
        for(var p:corridor.columns)limits.put(key(p),columnLimit(world,roads,def,join,p));

        // A flat port spans the full width. Its distance to a constraint is the shortest walk
        // within the complete footprint from any port cell, including shortcuts across turns.
        Map<Long,Integer> distance=new HashMap<>();
        Deque<Point> frontier=new ArrayDeque<>();
        for(var p:corridor.entrance){
            distance.put(key(p),0);
            frontier.addLast(p);
        }
        int low=Integer.MIN_VALUE,high=Integer.MAX_VALUE;
        while(!frontier.isEmpty()){
            var p=frontier.removeFirst();
            var limit=limits.get(key(p));
            int steps=distance.get(key(p));
            low=Math.max(low,limit.low()-steps);
            high=Math.min(high,limit.high()+steps);
            for(var d:CARDINAL){
                int x=p.x()+d[0],z=p.z()+d[1];
                long key=Cell.key(x,z);
                if(limits.containsKey(key)&&distance.putIfAbsent(key,steps+1)==null)frontier.addLast(new Point(x,z));
            }
        }
        if(low>high)throw new StarterMinePlanner.Rejected("矿场入口没有与支路相容的标高");
        return new Profile(corridor,limits,new GradeRange(low,high));
    }

    static void validate(WorldPlan world,RoadLayout roads,StarterMineDefinition def,StarterMineLayout layout,Join join,Corridor corridor){
        if(layout.path.size()!=corridor.columns.size())throw new StarterMinePlanner.Rejected("矿场接路缺少完整路幅");
        int portFeet=layout.position(def.port).y()*2;
        for(var p:corridor.entrance){
            var cell=layout.pathAt(p.x(),p.z());
            if(cell==null||cell.feet2()!=portFeet)throw new StarterMinePlanner.Rejected("矿场接路与入口标高不一致");
        }
        for(var p:corridor.columns){
            var cell=layout.pathAt(p.x(),p.z());
            if(cell==null)throw new StarterMinePlanner.Rejected("矿场接路存在缺列");
            var limit=columnLimit(world,roads,def,join,p);
            if(cell.feet2()<limit.low()||cell.feet2()>limit.high())
                throw new StarterMinePlanner.Rejected("矿场接路改变既有官道或超出施工标高");
            for(var d:CARDINAL){
                var other=layout.pathAt(p.x()+d[0],p.z()+d[1]);
                if(other!=null&&Math.abs(other.feet2()-cell.feet2())>1)
                    throw new StarterMinePlanner.Rejected("矿场接路没有连续半格台阶");
            }
        }
    }

    private static GradeRange columnLimit(WorldPlan world,RoadLayout roads,StarterMineDefinition def,Join join,Point p){
        int x=p.x(),z=p.z();
        var road=roads.at(x,z);
        if(road!=null){
            if(join.forward(p)>=join.overlap||road.rail()>=0)
                throw new StarterMinePlanner.Rejected("矿场支路重新穿过官道或触及护栏");
            return new GradeRange(road.feet2(),road.feet2());
        }
        int natural=world.surfaceY(x,z),dryLevel=world.rules().seaLevel+1;
        if(!world.coreRegionAt(x,z).map(WorldPlan.Region::id).orElse("").equals(world.spawn().regionId())
            ||world.intersectsCity(x,z,x,z)||world.intersectsSettlements(x,z,x,z)||world.intersectsSects(x,z,x,z)
            ||world.reserved(x,natural,z))throw new StarterMinePlanner.Rejected("矿场接路触及保留区域或越过州域");
        if(natural<Math.max(dryLevel,def.placement.minimumLandY()))
            throw new StarterMinePlanner.Rejected("矿场接路经过水域");
        int low=Math.max(dryLevel,natural-def.placement.earthwork())*2;
        int high=Math.min(def.placement.maximumY()-def.clearance,natural+def.placement.earthwork())*2;
        if(low>high)throw new StarterMinePlanner.Rejected("矿场接路超过世界高度");
        return new GradeRange(low,high);
    }

    private static long key(Point p){return Cell.key(p.x(),p.z());}
}
