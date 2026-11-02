package com.example.myvillage.entity.undead;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.wilderness.BeastBirth;
import com.example.myvillage.wilderness.WildernessRules;
import com.example.myvillage.wilderness.runtime.WildernessRuntime;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/** Region selection reads the existing plan; birth realm and actual outer region are immutable. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class UndeadSpawns {
    private UndeadSpawns(){}
    public static boolean check(EntityType<UndeadEntity> type,ServerLevelAccessor level,MobSpawnType reason,BlockPos pos,RandomSource random){
        var region=WildernessRuntime.regionAt(level.getLevel(),pos);
        var variant=UndeadRules.get().require(EntityType.getKey(type).getPath());
        return level.getLevel().dimension().equals(Level.OVERWORLD)&&region.isPresent()&&variant.acceptsRegion(region.get())
                &&Monster.checkMonsterSpawnRules(type,level,reason,pos,random);
    }
    @SubscribeEvent public static void birth(FinalizeSpawnEvent event){
        if(!(event.getEntity() instanceof UndeadEntity mob))return;
        if(event.getSpawnType()!=MobSpawnType.NATURAL&&event.getSpawnType()!=MobSpawnType.CHUNK_GENERATION)return;
        var level=event.getLevel().getLevel();var pos=mob.blockPosition();var profile=WildernessRuntime.regionAt(level,pos);
        if(profile.isEmpty()||!mob.variant().acceptsRegion(profile.get())){event.setSpawnCancelled(true);return;}
        if(mob.wildernessBirth().isPresent())return;
        String actual=WorldPlanRuntime.context(level,pos.getX(),pos.getZ()).map(c->c.regionId()).orElse(profile.get());
        var realm=WildernessRules.bundled().region(profile.get()).orElseThrow().beast().sample(level.getSeed(),actual,mob.getRandom().nextLong());
        mob.assignWildernessBirth(new BeastBirth(realm,actual,pos.getX(),pos.getY(),pos.getZ(),true));
    }
}
