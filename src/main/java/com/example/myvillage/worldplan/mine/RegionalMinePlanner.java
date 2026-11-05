package com.example.myvillage.worldplan.mine;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.roads.RoadLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.BiFunction;

/** 在真实噪声地形中寻找已有干燥洞体，不调用雕刻器，也不加载或改写区块。 */
public final class RegionalMinePlanner {
    private RegionalMinePlanner(){}
    public static List<RegionalMineSite> plan(WorldPlan world,RoadLayout roads,StarterMineLayout starter,BiFunction<Integer,Integer,NoiseColumn> columns){
        var def=world.rules().starterMine;var rules=def.regional;List<RegionalMineSite> sites=new ArrayList<>();
        for(var region:world.regions()){
            var capital=world.capitalOf(region.id()).orElseThrow();var center=capital.entrance();
            var random=new Random(StarterMineCavePlan.seed(world.seed(),region.x(),region.z(),rules.salt));
            int found=0,attempted=0;Map<String,Integer> failures=new TreeMap<>();
            for(;attempted<rules.attempts&&found<rules.count;attempted++){
                double angle=random.nextDouble()*Math.PI*2,distance=Math.sqrt(random.nextDouble())*rules.radius;
                int x=(int)Math.round(center.x()+Math.cos(angle)*distance),z=(int)Math.round(center.z()+Math.sin(angle)*distance),rotation=random.nextInt(4);
                int y=world.surfaceY(x,z)-def.groundY;
                var layout=new StarterMineLayout(world.fingerprint(),roads.hash(),def.hash,region.id(),x,y,z,rotation,x,z,List.of(),StarterMineCavePlan.seed(world.seed(),x,z,def.caves.salt));
                try{
                    requireFootprint(world,roads,layout,starter,sites,false);
                    // 同一候选的列缓存严格有界，候选结束即释放。
                    Map<Long,NoiseColumn> cache=new HashMap<>();
                    java.util.function.Function<BlockPos,BlockState> blocks=p->{
                        int wx=layout.worldX(p.getX(),p.getZ()),wz=layout.worldZ(p.getX(),p.getZ());long key=((long)wx<<32)^(wz&0xffffffffL);
                        return cache.computeIfAbsent(key,k->columns.apply(wx,wz)).getBlock(layout.y+p.getY());
                    };
                    RegionalMineSite chosen=null;
                    for(int end:rules.ends){
                        int maxDrop=Math.min(rules.maxDepth,(end-def.caves.flatEndZ)/2);
                        for(int drop=rules.minDepth;drop<=maxDrop;drop++){
                            var at=new BlockPos(def.entry.x(),def.groundY-drop,end);
                            if(layout.y+at.getY()>world.surfaceY(layout.worldX(at.getX(),at.getZ()),layout.worldZ(at.getX(),at.getZ()))-rules.minDepth||!stance(blocks,at))continue;
                            var evidence=explore(blocks,at,rules);
                            if(!sufficient(evidence,at,rules)||evidence.stream().anyMatch(p->layout.y+p.getY()>world.surfaceY(layout.worldX(p.getX(),p.getZ()),layout.worldZ(p.getX(),p.getZ()))-def.caves.cover-def.foundationDepth-2))continue;
                            var site=new RegionalMineSite(layout,end,at.getY(),evidence);site.requireValid(def);
                            if(!dryAccess(site,def,blocks))continue;
                            chosen=site;break;
                        }
                        if(chosen!=null)break;
                    }
                    if(chosen==null)throw new IllegalArgumentException("未找到可接入的干燥天然洞体");
                    requireFootprint(world,roads,layout,starter,sites,true);sites.add(chosen);found++;
                }catch(IllegalArgumentException ex){failures.merge(ex.getMessage(),1,Integer::sum);}
            }
            org.slf4j.LoggerFactory.getLogger(RegionalMinePlanner.class).info("普通矿口 region={}，找到={}/{}，候选={}，未采用原因={}",region.id(),found,rules.count,attempted,failures);
        }
        return List.copyOf(sites);
    }
    public static boolean stance(java.util.function.Function<BlockPos,BlockState> blocks,BlockPos p){
        var floor=blocks.apply(p.below());return !floor.isAir()&&floor.getFluidState().isEmpty()&&floor.blocksMotion()
            &&blocks.apply(p).isAir()&&blocks.apply(p.above()).isAir();
    }
    public static List<BlockPos> explore(java.util.function.Function<BlockPos,BlockState> blocks,BlockPos start,RegionalMineRules rules){
        Set<BlockPos> seen=new LinkedHashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();seen.add(start);queue.add(start);
        while(!queue.isEmpty()&&seen.size()<rules.scanLimit){
            var p=queue.removeFirst();
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy:new int[]{0,1,-1}){
                var q=p.offset(d[0],dy,d[1]);
                if(Math.abs(q.getX()-start.getX())>rules.scanRadius||Math.abs(q.getZ()-start.getZ())>rules.scanRadius||Math.abs(q.getY()-start.getY())>rules.scanRadius)continue;
                if(seen.contains(q))break;
                if(!stance(blocks,q)||dy>0&&!blocks.apply(p.above(2)).isAir()||dy<0&&!blocks.apply(q.above(2)).isAir())continue;
                seen.add(q);queue.add(q);break;
            }
        }
        return List.copyOf(seen);
    }
    public static boolean sufficient(List<BlockPos> points,BlockPos start,RegionalMineRules rules){
        if(points.size()<rules.stances)return false;
        int minX=start.getX(),maxX=minX,minZ=start.getZ(),maxZ=minZ;
        for(var p:points){minX=Math.min(minX,p.getX());maxX=Math.max(maxX,p.getX());minZ=Math.min(minZ,p.getZ());maxZ=Math.max(maxZ,p.getZ());}
        return Math.max(maxX-minX,maxZ-minZ)>=rules.span;
    }
    private static boolean dryAccess(RegionalMineSite site,StarterMineDefinition def,java.util.function.Function<BlockPos,BlockState> blocks){
        for(var p:site.access(def))for(int y=p.floorY()-def.caves.floorDepth;y<=Math.ceil(p.feet2()/2.0)+def.caves.headroom;y++)
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!blocks.apply(new BlockPos(p.x()+dx,y,p.z()+dz)).getFluidState().isEmpty())return false;
        return true;
    }
    public static void requireFootprint(WorldPlan world,RoadLayout roads,StarterMineLayout layout,StarterMineLayout starter,List<RegionalMineSite> others,boolean full){
        var def=world.rules().starterMine;var rules=def.regional;int x0=layout.minX(def),x1=layout.maxX(def),z0=layout.minZ(def),z1=layout.maxZ(def);
        if(world.intersectsCity(x0,z0,x1,z1)||world.intersectsSettlements(x0,z0,x1,z1)||world.intersectsSects(x0,z0,x1,z1))throw new IllegalArgumentException("与城镇或宗门重叠");
        if(distance(layout,starter)<(long)rules.spacing*rules.spacing||others.stream().anyMatch(s->distance(layout,s.surface())<(long)rules.spacing*rules.spacing))throw new IllegalArgumentException("矿口间距不足");
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE,step=full?1:8;
        for(int z=0;z<def.depth;z+=step)for(int x=0;x<def.width;x+=step){
            int wx=layout.worldX(x,z),wz=layout.worldZ(x,z),surface=world.surfaceY(wx,wz);
            if(!world.coreRegionAt(wx,wz).map(WorldPlan.Region::id).orElse("").equals(layout.region))throw new IllegalArgumentException("越过州域");
            if(surface<Math.max(world.rules().seaLevel+1,def.placement.minimumLandY()))throw new IllegalArgumentException("地面包含水域");
            if(roads.at(wx,wz)!=null||world.reserved(wx,surface,wz))throw new IllegalArgumentException("覆盖道路或保留区域");
            low=Math.min(low,surface);high=Math.max(high,surface);
            if(high-low>def.placement.surfaceSpread()||Math.abs(layout.surface(world,def,wx,wz)-surface)>def.placement.terrainAdjustment())throw new IllegalArgumentException("地形改动过大");
        }
    }
    private static long distance(StarterMineLayout a,StarterMineLayout b){long x=(long)a.x-b.x,z=(long)a.z-b.z;return x*x+z*z;}
}
