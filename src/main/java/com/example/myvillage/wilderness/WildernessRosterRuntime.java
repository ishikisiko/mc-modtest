package com.example.myvillage.wilderness;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.entity.beast.RosterBeastEntity;
import com.example.myvillage.entity.npc.RidgeReaverEntity;
import com.example.myvillage.wilderness.runtime.WildernessRuntime;
import com.example.myvillage.stats.runtime.StatsRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/** Native spawning and immutable birth identity; no player-level matching or remote chunk access. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class WildernessRosterRuntime {
    private WildernessRosterRuntime(){}
    public static <T extends Mob> boolean checkSpawn(EntityType<T> type,ServerLevelAccessor level,MobSpawnType reason,BlockPos pos,RandomSource random){
        return level.getLevel().dimension()==Level.OVERWORLD&&WildernessRuntime.regionAt(level.getLevel(),pos).isPresent()
                && level.getDifficulty()!=net.minecraft.world.Difficulty.PEACEFUL
                && (MobSpawnType.ignoresLightRequirements(reason)||Monster.isDarkEnoughToSpawn(level,pos,random))
                && Mob.checkMobSpawnRules(type,level,reason,pos,random);
    }
    @SubscribeEvent public static void birth(FinalizeSpawnEvent e){
        var mob=e.getEntity();if(!(mob instanceof RosterBeastEntity||mob instanceof RidgeReaverEntity))return;
        if(e.getSpawnType()!=MobSpawnType.NATURAL&&e.getSpawnType()!=MobSpawnType.CHUNK_GENERATION)return;
        var level=e.getLevel().getLevel();var pos=mob.blockPosition();var region=WildernessRuntime.regionAt(level,pos);
        if(region.isEmpty()){e.setSpawnCancelled(true);return;}
        if(mob.getPersistentData().contains("myvillage_roster_birth"))return;
        var realm=WildernessRules.bundled().region(region.get()).orElseThrow().beast().sample(level.getSeed(),region.get(),mob.getRandom().nextLong());
        if(mob instanceof RosterBeastEntity beast)beast.assignWildernessBirth(new BeastBirth(realm,region.get(),pos.getX(),pos.getY(),pos.getZ(),true));
        else {StatsRuntime.applyRealm(mob,realm.realmId(),realm.stage());mob.setHealth(mob.getMaxHealth());}
        var birth=new net.minecraft.nbt.CompoundTag();birth.putString("region",region.get());birth.putString("realm",realm.realmId());birth.putInt("stage",realm.stage());birth.putLong("pos",pos.asLong());
        mob.getPersistentData().put("myvillage_roster_birth",birth);mob.getPersistentData().putBoolean("myvillage_roster_natural",true);
    }
}
