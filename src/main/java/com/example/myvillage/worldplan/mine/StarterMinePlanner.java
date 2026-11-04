package com.example.myvillage.worldplan.mine;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.roads.RoadAStar;
import com.example.myvillage.worldplan.roads.RoadLayout;
import com.example.myvillage.worldplan.roads.RoadPlan;
import java.util.*;

/** 从出生省城已经可通行的官道旁选址；既有道路和城镇始终只读。 */
public final class StarterMinePlanner {
    private record Candidate(int x,int z,int dx,int dz,long distance) {}
    private StarterMinePlanner(){}

    public static StarterMineLayout plan(WorldPlan world,RoadLayout roads,StarterMineDefinition def){
        String region=world.spawn().regionId();var policy=def.placement;var candidates=new HashMap<Long,Candidate>();
        for(var route:roads.routes()){
            if(!route.sectSite.isEmpty()||!route.from.equals(region)&&!route.to.equals(region))continue;
            var points=route.points();
            for(int i=1;i<points.size()-1;i++){
                var p=points.get(i);long dx=(long)p.x()-world.spawn().x(),dz=(long)p.z()-world.spawn().z(),distance=dx*dx+dz*dz;
                if(distance<(long)policy.minimumRoadDistance()*policy.minimumRoadDistance()||distance>(long)policy.maximumRoadDistance()*policy.maximumRoadDistance())continue;
                var cell=roads.at(p.x(),p.z());if(cell==null||cell.band()!=0||cell.rail()>=0)continue;
                var before=points.get(Math.max(0,i-policy.sampleSpacing()/2));var after=points.get(Math.min(points.size()-1,i+policy.sampleSpacing()/2));
                candidates.putIfAbsent(RoadPlan.Cell.key(p.x(),p.z()),new Candidate(p.x(),p.z(),after.x()-before.x(),after.z()-before.z(),distance));
            }
        }
        var ordered=candidates.values().stream().sorted(Comparator.comparingLong(Candidate::distance).thenComparingInt(Candidate::x).thenComparingInt(Candidate::z)).toList();
        List<Candidate> sampled=new ArrayList<>();for(var candidate:ordered){
            if(sampled.stream().noneMatch(p->Math.abs(p.x-candidate.x)+Math.abs(p.z-candidate.z)<policy.sampleSpacing()))sampled.add(candidate);
        }
        int attempted=0;Map<String,Integer> failures=new LinkedHashMap<>();
        for(var candidate:sampled){
            int[] rotations=Math.abs(candidate.dx)>Math.abs(candidate.dz)?new int[]{0,2,1,3}:new int[]{1,3,0,2};
            for(int rotation:rotations)for(int length:policy.connectorLengths()){
                if(attempted++>=policy.attempts())throw failure(attempted-1,failures);
                int dx=switch(rotation){case 1->-1;case 3->1;default->0;};
                int dz=switch(rotation){case 0->1;case 2->-1;default->0;};
                int portX=candidate.x+dx*length,portZ=candidate.z+dz*length;
                int originX=portX-switch(rotation){case 0->def.port.x();case 1->-def.port.z();case 2->-def.port.x();default->def.port.z();};
                int originZ=portZ-switch(rotation){case 0->def.port.z();case 1->def.port.x();case 2->-def.port.z();default->-def.port.x();};
                int originY=world.surfaceY(portX,portZ)-def.groundY;
                try{
                    var path=connector(world,roads,def,candidate.x,candidate.z,dx,dz,length,originY+def.groundY);
                    var layout=new StarterMineLayout(world.fingerprint(),roads.hash(),def.hash,region,originX,originY,originZ,rotation,candidate.x,candidate.z,path);
                    validate(world,roads,def,layout);return layout;
                }catch(IllegalArgumentException|IllegalStateException ex){failures.merge(ex.getMessage(),1,Integer::sum);}
            }
        }
        throw failure(attempted,failures);
    }
    private static IllegalStateException failure(int attempted,Map<String,Integer> failures){return new IllegalStateException("初始城附近未找到可步行接通的陆地矿场，已检查 "+attempted+" 处："+failures);}

    private static List<StarterMineLayout.Path> connector(WorldPlan world,RoadLayout roads,StarterMineDefinition def,int ax,int az,int dx,int dz,int length,int endY){
        var first=roads.at(ax,az);if(first==null||first.band()!=0)throw new IllegalArgumentException("矿场接路点不在官道通行面");
        int start=first.feet2(),end=endY*2,radius=def.placement.pathWidth()/2;
        if(Math.abs(end-start)>length)throw new IllegalArgumentException("矿场接路坡度过大");
        List<StarterMineLayout.Path> out=new ArrayList<>();
        for(int step=0;step<=length;step++)for(int lateral=-radius;lateral<=radius;lateral++){
            int x=ax+dx*step-dz*lateral,z=az+dz*step+dx*lateral;
            int feet=start+(int)Math.round((end-start)*(step/(double)length));
            var road=roads.at(x,z);
            if(road!=null){if(road.rail()>=0)throw new IllegalArgumentException("矿场接路点被官道护栏封闭");feet=road.feet2();}
            out.add(new StarterMineLayout.Path(x,z,feet));
        }
        return List.copyOf(out);
    }
    static void validate(WorldPlan world,RoadLayout roads,StarterMineDefinition def,StarterMineLayout layout){
        var policy=def.placement;
        if(!layout.region.equals(world.spawn().regionId())||world.rules().spawnExcludedRegions.contains(layout.region))throw new IllegalArgumentException("矿场不属于当前初始城");
        if(layout.y+def.minBlockY<policy.minimumY()||layout.y+def.maxBlockY>=policy.maximumY())throw new IllegalArgumentException("矿场超过世界高度");
        int x0=layout.minX(def),x1=layout.maxX(def),z0=layout.minZ(def),z1=layout.maxZ(def);
        if(world.intersectsCity(x0,z0,x1,z1)||world.intersectsSettlements(x0,z0,x1,z1)||world.intersectsSects(x0,z0,x1,z1))throw new IllegalArgumentException("矿场与既有城镇或宗门预留地重叠");
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(int z=0;z<def.depth;z++)for(int x=0;x<def.width;x++){
            int wx=layout.worldX(x,z),wz=layout.worldZ(x,z),surface=world.surfaceY(wx,wz);
            if(!world.coreRegionAt(wx,wz).map(WorldPlan.Region::id).orElse("").equals(layout.region))throw new IllegalArgumentException("矿场越过初始城所在州域");
            if(roads.at(wx,wz)!=null)throw new IllegalArgumentException("矿场本体侵入已有官道");
            if(surface<Math.max(world.rules().seaLevel+1,policy.minimumLandY()))throw new IllegalArgumentException("矿场下方不是陆地");
            if(world.reserved(wx,surface,wz))throw new IllegalArgumentException("矿场覆盖世界保留区域");
            low=Math.min(low,surface);high=Math.max(high,surface);
            if(high-low>policy.surfaceSpread())throw new IllegalArgumentException("矿场天然地面起伏超过上限");
            if(Math.abs(def.surface(layout.y,x,z,surface)-surface)>policy.terrainAdjustment())throw new IllegalArgumentException("矿场需要过大地形改动");
        }
        var anchor=roads.at(layout.anchorX,layout.anchorZ);if(anchor==null||anchor.band()!=0||anchor.rail()>=0)throw new IllegalArgumentException("矿场接路点失效");
        long ax=(long)layout.anchorX-world.spawn().x(),az=(long)layout.anchorZ-world.spawn().z(),distance=ax*ax+az*az;
        if(distance<(long)policy.minimumRoadDistance()*policy.minimumRoadDistance()||distance>(long)policy.maximumRoadDistance()*policy.maximumRoadDistance())throw new IllegalArgumentException("矿场距离初始城过远或过近");
        var port=layout.position(def.port);int dx=Integer.compare(port.x(),layout.anchorX),dz=Integer.compare(port.z(),layout.anchorZ);
        int length=Math.abs(port.x()-layout.anchorX)+Math.abs(port.z()-layout.anchorZ);
        if(Math.abs(dx)+Math.abs(dz)!=1||!policy.connectorLengths().contains(length))throw new IllegalArgumentException("矿场接路方向或长度无效");
        if(layout.path.size()!=(length+1)*policy.pathWidth())throw new IllegalArgumentException("矿场接路缺少完整路幅");
        int radius=policy.pathWidth()/2;
        for(int step=0;step<=length;step++)for(int side=-radius;side<=radius;side++){
            int wx=layout.anchorX+dx*step-dz*side,wz=layout.anchorZ+dz*step+dx*side;var cell=layout.pathAt(wx,wz);
            if(cell==null)throw new IllegalArgumentException("矿场接路存在缺列");
            var road=roads.at(wx,wz);
            if(road!=null){if(road.feet2()!=cell.feet2()||road.rail()>=0)throw new IllegalArgumentException("矿场接路改变既有官道或穿过护栏");}
            else{
                int natural=world.surfaceY(wx,wz);
                if(!world.coreRegionAt(wx,wz).map(WorldPlan.Region::id).orElse("").equals(layout.region)||world.intersectsCity(wx,wz,wx,wz)
                    ||world.intersectsSettlements(wx,wz,wx,wz)||world.intersectsSects(wx,wz,wx,wz)||world.reserved(wx,natural,wz))throw new IllegalArgumentException("矿场接路触及保留区域");
                if(natural<Math.max(world.rules().seaLevel+1,policy.minimumLandY()))throw new IllegalArgumentException("矿场接路经过水域");
                if(Math.abs(cell.feet2()-natural*2)>policy.earthwork()*2)throw new IllegalArgumentException("矿场接路土方超过上限");
            }
            for(int[] dir:new int[][]{{1,0},{0,1}}){var other=layout.pathAt(wx+dir[0],wz+dir[1]);if(other!=null&&Math.abs(other.feet2()-cell.feet2())>1)throw new IllegalArgumentException("矿场接路没有连续半格台阶");}
            if(step==length&&cell.feet2()!=port.y()*2)throw new IllegalArgumentException("矿场接路与入口标高不一致");
        }
    }
}
