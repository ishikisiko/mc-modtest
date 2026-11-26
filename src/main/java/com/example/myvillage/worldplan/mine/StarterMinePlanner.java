package com.example.myvillage.worldplan.mine;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.roads.RoadAStar.Point;
import com.example.myvillage.worldplan.roads.RoadLayout;
import java.util.*;

/** 在完成的官道上寻找实际可通行的接出点，再共同确定支路与矿场的标高。 */
public final class StarterMinePlanner {
    public record Search(Optional<StarterMineLayout> layout,int approaches,int attempted,Map<String,Integer> rejections) {
        public Search {rejections=Map.copyOf(rejections);}
    }

    static final class Rejected extends IllegalArgumentException {
        Rejected(String reason){super(reason);}
        Rejected(String reason,Throwable cause){super(reason,cause);}
    }

    private record Candidate(int x,int z,int dx,int dz,long distance) {}
    private record Altitude(int low,int high) {}

    private StarterMinePlanner(){}

    public static Search search(WorldPlan world,RoadLayout roads,StarterMineDefinition def){
        var alongTrunk=search(world,roads,def,false);
        if(alongTrunk.layout().isPresent()||!world.rules().compactCapitals)return alongTrunk;
        return search(world,roads,def,true);
    }

    private static Search search(WorldPlan world,RoadLayout roads,StarterMineDefinition def,boolean sectSpurs){
        var policy=def.placement;
        var approaches=approaches(world,roads,policy,sectSpurs);
        int variants=policy.connectorLengths().size()*policy.lateralOffsets().size();
        int budget=policy.attempts()/variants;
        var sampled=spread(approaches,budget);
        int attempted=0;
        Map<String,Integer> rejections=new TreeMap<>();
        for(int offset:policy.lateralOffsets())for(var join:sampled)for(int length:policy.connectorLengths()){
            attempted++;
            try{
                var layout=planSite(world,roads,def,join,length,offset);
                return new Search(Optional.of(layout),approaches.size(),attempted,rejections);
            }catch(Rejected rejected){
                rejections.merge(rejected.getMessage(),1,Integer::sum);
            }
        }
        return new Search(Optional.empty(),approaches.size(),attempted,rejections);
    }

    private static List<StarterMineAccess.Join> approaches(WorldPlan world,RoadLayout roads,StarterMineDefinition.Placement policy,boolean sectSpurs){
        String region=world.spawn().regionId();
        Map<Point,Candidate> candidates=new HashMap<>();
        for(var route:roads.routes()){
            if((!route.sectSite.isEmpty())!=sectSpurs||(!route.from.equals(region)&&!route.to.equals(region)))continue;
            var points=route.points();
            for(int i=1;i<points.size()-1;i++){
                var p=points.get(i);
                long distance=distance(world,p.x(),p.z());
                if(!inRange(policy,distance))continue;
                var before=points.get(Math.max(0,i-policy.sampleSpacing()/2));
                var after=points.get(Math.min(points.size()-1,i+policy.sampleSpacing()/2));
                candidates.putIfAbsent(p,new Candidate(p.x(),p.z(),after.x()-before.x(),after.z()-before.z(),distance));
            }
        }
        var ordered=candidates.values().stream()
            .sorted(Comparator.comparingLong(Candidate::distance).thenComparingInt(Candidate::x).thenComparingInt(Candidate::z)).toList();
        List<StarterMineAccess.Join> joins=new ArrayList<>();
        for(var candidate:ordered){
            int[] rotations=Math.abs(candidate.dx)>Math.abs(candidate.dz)?new int[]{0,2,1,3}:new int[]{1,3,0,2};
            for(int rotation:rotations){
                // Each side is sampled independently so a blocked shoulder cannot discard a nearby usable one.
                if(joins.stream().anyMatch(join->join.rotation()==rotation&&
                    Math.abs(join.x()-candidate.x)+Math.abs(join.z()-candidate.z)<policy.sampleSpacing()))continue;
                StarterMineAccess.join(roads,policy,candidate.x,candidate.z,rotation).ifPresent(joins::add);
            }
        }
        return joins;
    }

    private static <T> List<T> spread(List<T> ordered,int count){
        if(ordered.size()<=count)return ordered;
        if(count==1)return List.of(ordered.getFirst());
        List<T> selected=new ArrayList<>(count);
        for(int i=0;i<count;i++)selected.add(ordered.get((int)Math.round(i*(ordered.size()-1.0)/(count-1))));
        return selected;
    }

    private static StarterMineLayout planSite(WorldPlan world,RoadLayout roads,StarterMineDefinition def,
                                               StarterMineAccess.Join join,int length,int offset){
        var front=join.at(length,offset);
        var origin=join.at(length,offset+def.port.x());
        int naturalY=world.surfaceY(front.x(),front.z());
        long seed=StarterMineCavePlan.seed(world.seed(),origin.x(),origin.z(),def.caves.salt);
        var footprint=new StarterMineLayout(world.fingerprint(),roads.hash(),def.hash,world.spawn().regionId(),
            origin.x(),naturalY-def.groundY,origin.z(),join.rotation(),join.x(),join.z(),List.of(),seed);
        requireUnoccupied(world,def,footprint);
        var corridor=StarterMineAccess.corridor(join,def.placement,length,offset);
        var profile=StarterMineAccess.profile(world,roads,def,join,corridor);
        var port=profile.port();
        var feasible=altitude(world,roads,def,footprint,Math.ceilDiv(port.low(),2)-def.groundY,Math.floorDiv(port.high(),2)-def.groundY);
        int baseY=Math.clamp(footprint.y,feasible.low,feasible.high);
        var layout=new StarterMineLayout(footprint.worldHash,footprint.roadsHash,footprint.definitionHash,footprint.region,
            footprint.x,baseY,footprint.z,footprint.rotation,footprint.anchorX,footprint.anchorZ,profile.fit(baseY+def.groundY),seed);
        return layout;
    }

    static void validate(WorldPlan world,RoadLayout roads,StarterMineDefinition def,StarterMineLayout layout){
        var policy=def.placement;
        if(!layout.region.equals(world.spawn().regionId())||world.rules().spawnExcludedRegions.contains(layout.region))
            throw new Rejected("矿场不属于当前初始城");
        requireUnoccupied(world,def,layout);
        altitude(world,roads,def,layout,layout.y,layout.y);
        if(!inRange(policy,distance(world,layout.anchorX,layout.anchorZ)))throw new Rejected("矿场距离初始城过远或过近");
        var join=StarterMineAccess.join(roads,policy,layout.anchorX,layout.anchorZ,layout.rotation)
            .orElseThrow(()->new Rejected("矿场接路点失效"));
        var front=layout.position(def.port);
        var port=new Point(front.x(),front.z());
        var corridor=StarterMineAccess.corridor(join,policy,join.forward(port),join.lateral(port));
        StarterMineAccess.validate(world,roads,def,layout,join,corridor);
    }

    private static void requireUnoccupied(WorldPlan world,StarterMineDefinition def,StarterMineLayout layout){
        int x0=layout.minX(def),x1=layout.maxX(def),z0=layout.minZ(def),z1=layout.maxZ(def);
        if(world.intersectsCity(x0,z0,x1,z1)||world.intersectsSettlements(x0,z0,x1,z1)||world.intersectsSects(x0,z0,x1,z1))
            throw new Rejected("矿场与既有城镇或宗门预留地重叠");
    }

    private static Altitude altitude(WorldPlan world,RoadLayout roads,StarterMineDefinition def,StarterMineLayout layout,int low,int high){
        var policy=def.placement;
        low=Math.max(low,policy.minimumY()-Math.min(def.minBlockY,def.caves.minY-def.caves.seal));
        high=Math.min(high,policy.maximumY()-1-def.maxBlockY);
        if(low>high)throw new Rejected("矿场没有合适的世界高度");
        int landY=Math.max(world.rules().seaLevel+1,policy.minimumLandY());
        int lowest=Integer.MAX_VALUE,highest=Integer.MIN_VALUE;
        for(int z=0;z<def.depth;z++)for(int x=0;x<def.width;x++){
            int wx=layout.worldX(x,z),wz=layout.worldZ(x,z);
            if(roads.at(wx,wz)!=null)throw new Rejected("矿场本体侵入已有官道");
            if(!world.coreRegionAt(wx,wz).map(WorldPlan.Region::id).orElse("").equals(layout.region))
                throw new Rejected("矿场越过初始城所在州域");
            int natural=world.surfaceY(wx,wz);
            if(natural<landY)throw new Rejected("矿场下方不是陆地");
            if(world.reserved(wx,natural,wz))throw new Rejected("矿场覆盖世界保留区域");
            lowest=Math.min(lowest,natural);
            highest=Math.max(highest,natural);
            if(highest-lowest>policy.surfaceSpread())throw new Rejected("矿场天然地面起伏超过上限");

            int weight=def.weight(x,z);
            if(weight==0)continue;
            // Blend uses round(delta * weight / 255), then applies the authoring earthwork limit.
            int cut=Math.min(policy.terrainAdjustment(),natural-world.rules().seaLevel-1);
            int keep=natural-def.top(x,z);
            low=Math.max(low,keep-(cut*255+127)/weight);
            high=Math.min(high,keep+(policy.terrainAdjustment()*255+127)/weight);
            if(low>high)throw new Rejected("矿场地面与支路没有共同的合法标高");
        }
        return new Altitude(low,high);
    }

    private static long distance(WorldPlan world,int x,int z){
        long dx=(long)x-world.spawn().x(),dz=(long)z-world.spawn().z();
        return dx*dx+dz*dz;
    }

    private static boolean inRange(StarterMineDefinition.Placement policy,long distance){
        return distance>=(long)policy.minimumRoadDistance()*policy.minimumRoadDistance()
            &&distance<=(long)policy.maximumRoadDistance()*policy.maximumRoadDistance();
    }
}
