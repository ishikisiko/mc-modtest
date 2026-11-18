package com.example.myvillage.wilderness.habitat;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.mine.StarterMineGeneration;
import com.example.myvillage.worldplan.roads.RoadLayout;
import com.example.myvillage.wilderness.WildernessRules;
import java.util.*;
import java.util.function.Predicate;

/** 同世界、同规则及同路网得到同一批据点；规划从不读取或加载区块。 */
public record HabitatLayout(HabitatRules rules,List<Site> sites,Map<String,String> shortfalls) {
    public record Site(String id,String kind,int x,int y,int z) {}
    public HabitatLayout {sites=List.copyOf(sites);shortfalls=Collections.unmodifiableMap(new LinkedHashMap<>(shortfalls));}
    public static HabitatLayout plan(WorldPlan world,RoadLayout roads,StarterMineGeneration mines){
        var rules=world.rules().habitats;
        int radius=rules.integer("site_radius"),foundation=rules.integer("maximum_slope")+2;
        return plan(world,roads,site->mines.intersects(site.x()-radius,site.y()-foundation,site.z()-radius,
            site.x()+radius,site.y()+5,site.z()+radius));
    }
    static HabitatLayout plan(WorldPlan world,RoadLayout roads,Predicate<Site> occupiesMine){
        var rules=world.rules().habitats;List<Site> result=new ArrayList<>();
        Map<String,String> shortfalls=new LinkedHashMap<>();
        var region=world.regions().stream().filter(r->r.id().equals(rules.region)).findFirst().orElseThrow();
        double[] center=world.geometry().unwarp(region.x(),region.z());int radius=rules.integer("site_radius");
        for(var kind:rules.kinds)for(int slot=0;slot<rules.integer("sites_per_kind");slot++){
            String id=kind.id()+"_"+slot;
            Site chosen=null;
            Site neighbour=null;
            if(!kind.nearKind().isEmpty()){
                String neighbourId=kind.nearKind()+"_"+slot;
                neighbour=result.stream().filter(v->v.id().equals(neighbourId)).findFirst().orElse(null);
                if(neighbour==null){
                    shortfalls.put(id,"相邻据点缺位："+neighbourId);
                    continue;
                }
            }
            for(int attempt=0;attempt<rules.integer("candidate_attempts");attempt++){
                long hash=WildernessRules.mix(world.seed()^WildernessRules.mix(kind.id().hashCode())^WildernessRules.mix((long)slot*4096+attempt));
                double angle=(hash>>>11)*0x1.0p-53*Math.PI*2;
                double distance=Math.sqrt((WildernessRules.mix(hash)>>>11)*0x1.0p-53)*rules.integer("region_radius");
                if(neighbour!=null)distance=rules.integer("paired_distance_min")+(rules.integer("paired_distance_max")-rules.integer("paired_distance_min"))*(WildernessRules.mix(hash)>>>11)*0x1.0p-53;
                int x=(int)Math.round((neighbour==null?center[0]:neighbour.x())+Math.cos(angle)*distance),z=(int)Math.round((neighbour==null?center[1]:neighbour.z())+Math.sin(angle)*distance),y=world.surfaceY(x,z);
                if(y<=world.rules().seaLevel+rules.integer("maximum_slope")||y>rules.integer("max_surface_y"))continue;
                if(Math.hypot(x-world.spawn().x(),z-world.spawn().z())<rules.integer("minimum_separation"))continue;
                if(result.stream().anyMatch(s->Math.hypot(s.x()-x,s.z()-z)<rules.integer("minimum_separation")))continue;
                int margin=radius+rules.integer("reservation_margin");
                if(world.graph().geometry().reservations().stream().anyMatch(r->x+margin>=r.minX()&&x-margin<=r.maxX()&&z+margin>=r.minZ()&&z-margin<=r.maxZ()))continue;
                var candidate=new Site(id,kind.id(),x,y,z);
                if(occupiesMine.test(candidate))continue;
                boolean valid=true;
                int roadMargin=radius+rules.integer("road_margin");
                for(int dx=-roadMargin;dx<=roadMargin&&valid;dx++)for(int dz=-roadMargin;dz<=roadMargin;dz++)if(roads.at(x+dx,z+dz)!=null){valid=false;break;}
                for(int dx=-radius;dx<=radius&&valid;dx++)for(int dz=-radius;dz<=radius;dz++){
                    if(!world.coreRegionAt(x+dx,z+dz).map(WorldPlan.Region::id).orElse("").equals(rules.region)||Math.abs(world.surfaceY(x+dx,z+dz)-y)>rules.integer("maximum_slope")){valid=false;break;}
                }
                if(valid){chosen=candidate;break;}
            }
            if(chosen==null)shortfalls.put(id,"候选耗尽");
            else result.add(chosen);
        }
        if(!shortfalls.isEmpty())org.slf4j.LoggerFactory.getLogger(HabitatLayout.class)
            .warn("东荒生态据点有缺位，主世界继续生成：seed={}，缺位={}",world.seed(),shortfalls);
        return new HabitatLayout(rules,result,shortfalls);
    }
    public boolean reserved(int x,int y,int z){int radius=rules.integer("site_radius");return sites.stream().anyMatch(s->Math.abs(x-s.x())<=radius&&Math.abs(z-s.z())<=radius&&y>=s.y()-rules.integer("maximum_slope")-1&&y<=s.y()+5);}
}
