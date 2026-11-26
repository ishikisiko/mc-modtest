package com.example.myvillage.worldplan.roads;

import com.example.myvillage.region.runtime.SectSite;
import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.WorldPlan.RejectedSectSite;
import com.example.myvillage.worldplan.WorldPlanRules;
import com.example.myvillage.worldplan.mine.StarterMinePlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 在生成区块前用实际支路检查宗门落点，重选时重新校验全网。 */
public final class CompactWorldPlanner {
    private static final Logger LOGGER=LoggerFactory.getLogger(CompactWorldPlanner.class);

    public record Selection(WorldPlan world,RoadLayout roads,List<RejectedSectSite> rejectedSites) {
        public Selection {rejectedSites=List.copyOf(rejectedSites);}
    }

    private CompactWorldPlanner(){}

    public static Selection plan(long seed,WorldPlanRules rules,RoadRules roads,Consumer<RoadNetworkPlanner.Progress> progress){
        var policy=rules.sectPlacement;
        if(policy==null)throw new IllegalArgumentException("缺少宗门选址规则");
        if(!roads.automatic){
            var world=new WorldPlan(seed,rules);
            return new Selection(world,RoadLayout.empty(world,roads),List.of());
        }

        List<RejectedSectSite> rejected=new ArrayList<>();
        Selection best=null;
        double bestLength=Double.POSITIVE_INFINITY;
        boolean bestHasMine=false;
        RuntimeException lastFailure=null;
        for(int attempt=0;attempt<=policy.siteRelocations();attempt++){
            SectSite retrySite;
            try{
                var world=new WorldPlan(seed,rules,rejected);
                var network=RoadNetworkPlanner.plan(world,roads,List.of(),List.of(),progress);
                var layout=RoadLayout.from(world,roads,network);
                layout.requireIdentity(world,roads);

                RoadPlan longest=null;
                double length=0;
                for(var route:java.util.stream.Stream.concat(network.routes().stream(),network.foundingRoutes().stream()).toList()){
                    if(world.sectSite(route.from()).isEmpty())continue;
                    double actual=route.horizontalLength();
                    if(actual>length){length=actual;longest=route;}
                }
                boolean hasMine=StarterMinePlanner.search(world,layout,rules.starterMine).layout().isPresent();
                if(best==null||(hasMine&&!bestHasMine)||(hasMine==bestHasMine&&length<bestLength)){
                    best=new Selection(world,layout,rejected);
                    bestLength=length;
                    bestHasMine=hasMine;
                }
                if(longest==null||length<=policy.preferredSpurLength())break;
                retrySite=world.sectSite(longest.from()).orElseThrow();
                LOGGER.info("宗门支路 {} 长 {} 格，目标 {} 格",retrySite.id(),Math.round(length),policy.preferredSpurLength());
            }catch(SectRoadPlanner.UnroutableSect failure){
                lastFailure=failure;
                retrySite=failure.site;
                LOGGER.info("宗门接路失败，seed={}，{}",seed,failure.getMessage());
            }catch(WorldPlan.SectPlacementExhausted|RoadNetworkPlanner.UnconnectedCities|RoadJunctionPlanner.Failure failure){
                if(best==null)throw failure;
                LOGGER.info("宗门改选未通过全网校验，seed={}，{}",seed,failure.getMessage());
                break;
            }
            if(attempt==policy.siteRelocations())break;
            rejected.add(new RejectedSectSite(retrySite));
            LOGGER.info("重选宗门位置，seed={}，地块={}，原点=({}, {})",seed,retrySite.id(),retrySite.x(),retrySite.z());
        }
        if(best==null)throw new IllegalStateException("宗门选址用尽，无法生成完整路网",lastFailure);
        LOGGER.info("选址完成，seed={}，重选记录={}，最长宗门支路={} 格，开局矿场={}",
            seed,best.rejectedSites().size(),Math.round(bestLength),bestHasMine);
        return best;
    }
}
