package com.example.myvillage.legacy;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.wilderness.*;
import com.example.myvillage.wilderness.runtime.WildernessRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.*;

@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class LegacyEvents {
    private LegacyEvents(){}
    public static boolean checkSpawn(EntityType<LegacyBeastEntity> type,ServerLevelAccessor l,MobSpawnType reason,BlockPos p,RandomSource random) {
        return l.getLevel().dimension().equals(Level.OVERWORLD)&&WildernessRuntime.regionAt(l.getLevel(),p).isPresent()
                &&Monster.checkMonsterSpawnRules(type,l,reason,p,random);
    }
    public static boolean assignBirth(ServerLevel level,LegacyBeastEntity mob,BlockPos p,boolean natural) {
        if(mob.wildernessBirth().isPresent())return true;
        var region=WildernessRuntime.regionAt(level,p);if(region.isEmpty())return false;
        var context=com.example.myvillage.worldplan.runtime.WorldPlanRuntime.context(level,p.getX(),p.getZ());
        String actual=context.map(c->c.regionId()).orElse(region.get());
        var rule=WildernessRules.bundled().region(region.get());if(rule.isEmpty())return false;
        var realm=rule.get().beast().sample(level.getSeed(),actual,mob.getRandom().nextLong());
        return mob.assignWildernessBirth(new BeastBirth(realm,actual,p.getX(),p.getY(),p.getZ(),natural));
    }
    @SubscribeEvent public static void birth(FinalizeSpawnEvent e) {
        if(!(e.getEntity() instanceof LegacyBeastEntity mob))return;
        boolean natural=e.getSpawnType()==MobSpawnType.NATURAL||e.getSpawnType()==MobSpawnType.CHUNK_GENERATION;
        if(natural&&!assignBirth(e.getLevel().getLevel(),mob,mob.blockPosition(),true))e.setSpawnCancelled(true);
    }
    @SubscribeEvent public static void attack(AttackEntityEvent e) {
        if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p&&LegacyRuntime.weaponAway(p)) {e.setCanceled(true);LegacyRuntime.message(p,"deployed");}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {LegacyRuntime.forget(e.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {LegacyRuntime.forget(e.getEntity().getUUID());}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {LegacyRuntime.forget(e.getEntity().getUUID());}
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("myvillage").then(Commands.literal("legacy").requires(s->s.hasPermission(2))
                .then(Commands.literal("status").then(Commands.argument("player",EntityArgument.player()).executes(c->{
                    var p=EntityArgument.getPlayer(c,"player");c.getSource().sendSuccess(()->Component.literal(LegacyRuntime.status(p)),false);return 1;
                })))));
    }
}
