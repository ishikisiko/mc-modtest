package com.example.myvillage.dropprobe;

import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.resources.BuiltinResources;
import com.example.myvillage.sim.life.LifeRules;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

/** 无命簿的测试角色沿用正式生活规则及有实际进展的分段路径条件。 */
final class HousingNavigationFixture {
    static final LifeRules RULES=load();
    private static LifeRules load(){
        String source="data/yuanxian/world_sim/life_rules.json";
        try(var in=BuiltinResources.open(source)){
            return LifeRules.parse(source,JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject());
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    static Path search(NpcEntity npc,BlockPos target){npc.getNavigation().stop();return npc.getNavigation().createPath(target,0);}
    static boolean advances(NpcEntity npc,Path path){
        return path!=null&&(path.canReach()||path.getNodeCount()>1&&!path.getEndNode().asBlockPos().equals(npc.blockPosition()));
    }
    static boolean arrived(NpcEntity npc,BlockPos target){
        return npc.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(target))<RULES.arrivalDistance()*RULES.arrivalDistance();
    }
}
