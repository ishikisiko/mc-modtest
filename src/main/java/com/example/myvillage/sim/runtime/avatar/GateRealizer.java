package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.region.runtime.SectSite;
import com.example.myvillage.sect.SectBlueprint;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.LoggerFactory;

/** 宗门几何只由区块生成营造；此处仅观察已加载山门和设施并绑定命簿归属。 */
public final class GateRealizer {
    private GateRealizer() {}
    public static void register(){NeoForge.EVENT_BUS.addListener(GateRealizer::tick);}
    private static void tick(ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%20!=0)return;
        var active=WorldSimRuntime.sim();if(active.isEmpty())return;
        bindLoaded(event.getServer().overworld(),active.get());
    }
    public static int bindLoaded(ServerLevel level,WorldSim sim){
        if(!level.getServer().getWorldData().worldGenOptions().generateStructures())return 0;
        var generator=WorldPlanRuntime.generator(level);if(generator.isEmpty())return 0;
        int bound=0;
        for(var sect:sim.sects(false)){
            if(!sect.state().equals("active"))continue;
            var site=generator.get().plan().sectAtGate(sect.gateX(),sect.gateZ());
            if(site.isEmpty())continue;
            var plot=site.get();var data=SectBlueprint.get(plot.blueprintId());
            boolean shelvesLoaded=data.shelves.stream().allMatch(s->{var p=new BlockPos(plot.x(),plot.baseY(),plot.z()).offset(s.position());return level.getChunkSource().getChunkNow(Math.floorDiv(p.getX(),16),Math.floorDiv(p.getZ(),16))!=null;});
            if(shelvesLoaded)ScriptureShelves.bindExisting(level,sect.id(),new BlockPos(plot.gateX(),plot.gateY(),plot.gateZ()));
            if(sect.gateRealized()||!gateLoaded(level,plot))continue;
            if(!data.fingerprint.equals(plot.blueprintHash()))throw new IllegalStateException("宗门规划蓝图身份不一致");
            var anchor=new BlockPos(plot.gateX(),plot.gateY(),plot.gateZ());
            GateRealizations.get(level).put(new GateRealizations.Gate(sect.id(),anchor,GateBuilder.seed(level.getSeed(),sect.id()),data.id.toString(),sim.day(),data.fingerprint));
            sim.markGateRealized(sect.id(),true);WorldSimSavedData.get(level).setDirty();WorldSimAvatars.gateChanged(sect.id());
            LoggerFactory.getLogger(GateRealizer.class).info("SECT_WORLDGEN_BOUND sect={} site={} anchor={} blueprint={}",sect.id(),plot.id(),anchor.toShortString(),data.fingerprint);
            bound++;
        }
        return bound;
    }
    /** FULL 区块已经经过 FEATURES；只检查山门，远处楼阁随各自区块正常生成。 */
    static boolean gateLoaded(ServerLevel level,SectSite site){
        return level.getChunkSource().getChunkNow(Math.floorDiv(site.gateX(),16),Math.floorDiv(site.gateZ(),16))!=null;
    }
}
