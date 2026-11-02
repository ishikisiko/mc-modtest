package com.example.myvillage.worldplan.travel;

import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.atlas.WorldAtlas;
import java.util.Optional;

/** 宗门标记与传送都从同一命簿及冻结地块解析，不接受客户端坐标。 */
public final class AtlasSectTargets {
    private AtlasSectTargets(){}
    public static Optional<WorldAtlas.Sect> resolve(WorldPlan plan,WorldSim sim,int sectId,String fingerprint){
        if(sim==null || sectId<0 || !plan.fingerprint().equals(fingerprint))return Optional.empty();
        return sim.sect(sectId).filter(s->s.state().equals("active")).flatMap(sect->
            plan.sectAtGate(sect.gateX(),sect.gateZ()).filter(site->site.regionId().equals(sect.regionId()))
                .map(site->new WorldAtlas.Sect(sect.id(),sect.name(),sect.regionId(),site.gateX(),site.gateY(),site.gateZ())));
    }
    public static WorldAtlas overlay(WorldPlan plan,WorldSim sim,WorldAtlas base){
        if(sim==null || base.status()!=WorldAtlas.Status.READY)return base;
        return base.withSects(sim.sects(false).stream()
            .flatMap(s->resolve(plan,sim,s.id(),base.fingerprint()).stream()).toList());
    }
}
