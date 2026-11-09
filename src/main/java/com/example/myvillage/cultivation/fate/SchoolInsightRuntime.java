package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.*;
import com.example.myvillage.cultivation.data.*;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRules;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

public final class SchoolInsightRuntime {
    private SchoolInsightRuntime() { }
    public static SchoolInsightState state(ServerPlayer p) { return p.getData(CultivationAttachments.SCHOOL_INSIGHT.get()); }
    public static Map<String,SchoolDefinition> schools(RegistryAccess access) {
        var out=new TreeMap<String,SchoolDefinition>();var used=new HashSet<ResourceLocation>();
        access.registry(ModCultivationRegistries.TECHNIQUES).ifPresent(r -> r.forEach(t -> t.school().ifPresent(used::add)));
        access.registry(ModCultivationRegistries.SCHOOLS).ifPresent(r -> r.keySet().stream().filter(used::contains)
                .forEach(id -> out.put(id.toString(),r.get(id))));
        return Collections.unmodifiableMap(out);
    }
    public static int bonus(ServerPlayer player, TechniqueDefinition technique, String effect) {
        return SchoolInsight.matches(state(player),technique) ? (int)Math.round(FateService.effect(player,effect)*SchoolInsight.UNIT) : 0;
    }
    /** 仅在正式成长奖励的提交前调用；行政设值和传承初始熟练不经过此处。 */
    public static CultivationProfile boost(ServerPlayer p, CultivationProfile before, CultivationProfile planned) {
        var result=planned;
        for (var e:planned.learnedTechniques().entrySet()) {
            var old=before.learnedTechniques().get(e.getKey());
            if (old==null || e.getValue().masteryPoints()<=old.masteryPoints()) continue;
            var definition=ModCultivationRegistries.technique(p.registryAccess(),e.getKey()).orElse(null);
            int bp=bonus(p,definition,"school_mastery");if(bp<=0)continue;
            long cap=TechniqueGrowthRules.bundled().find(e.getKey()).map(t -> (long)t.cap()).orElse(Long.MAX_VALUE);
            var next=SchoolInsight.gain(old,e.getValue().masteryPoints()-old.masteryPoints(),bp,cap);
            result=result.withTechniqueProgress(e.getKey(),next);
        }
        return result;
    }
}
