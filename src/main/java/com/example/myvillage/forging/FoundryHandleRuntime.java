package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.session.CombatSession;
import com.example.myvillage.cultivation.CultivationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import java.util.*;

/** One action can consume one handle proc. Timers are temporary, bound to the actual held stack. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class FoundryHandleRuntime {
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final class State {
        ItemStack weapon=ItemStack.EMPTY;
        long combat,expires,cooldown,bonusRevision=Long.MIN_VALUE,nurtureTick=Long.MIN_VALUE;
        double armed,applied;
    }
    private FoundryHandleRuntime(){}
    private static State state(ServerPlayer player){
        var state=STATES.computeIfAbsent(player.getUUID(),id->new State());var held=player.getMainHandItem();
        if(state.weapon!=held){state.weapon=held;state.armed=state.applied=0;state.expires=0;state.bonusRevision=Long.MIN_VALUE;}
        return state;
    }
    private static FoundryRules.Handle handle(ItemStack stack){
        var craft=WorkshopEquipment.craft(stack);
        return craft.isPresent()&&!craft.get().armor()&&FoundryEquipment.valid(stack)?FoundryEquipment.handle(stack):null;
    }
    public static double multiplier(ServerPlayer player,CombatSession session){
        if(session.isExternal())return 1;
        var handle=handle(player.getMainHandItem());if(handle==null)return 1;
        var state=state(player);long now=player.level().getGameTime();
        if(state.bonusRevision==session.revision())return 1+state.applied;
        if(now>state.expires||state.armed<=0)return 1;
        if(handle.effect().equals("followup")&&session.isFinisher())return 1;
        return 1+state.armed;
    }
    public static void accepted(ServerPlayer player,CombatSession session){
        var handle=handle(player.getMainHandItem());if(handle==null)return;
        var state=state(player);long now=player.level().getGameTime();state.combat=now;
        if(!session.isExternal()&&state.armed>0&&now<=state.expires&&state.bonusRevision!=session.revision()
                &&(!handle.effect().equals("followup")||!session.isFinisher())){
            state.applied=state.armed;state.armed=0;state.bonusRevision=session.revision();
        }
        boolean trigger=handle.effect().equals("followup")&&session.isFinisher()
                ||handle.effect().equals("spell_followup")&&session.isExternal();
        if(trigger&&now>=state.cooldown){
            state.armed=handle.effect_value();state.expires=now+handle.effect_ticks();
            state.cooldown=now+FoundryRules.get().assembly().handle_cooldown_ticks();
        }
    }
    @SubscribeEvent public static void hurt(LivingDamageEvent.Post event){
        if(event.getNewDamage()>0&&event.getEntity() instanceof ServerPlayer player)state(player).combat=player.level().getGameTime();
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!player.isAlive()||player.isCreative()||player.isSpectator())return;
        var held=player.getMainHandItem();var handle=handle(held);if(handle==null||!handle.effect().equals("nurture")||!held.isDamaged())return;
        long now=player.level().getGameTime();var state=state(player);var cfg=FoundryRules.get().handle_effects();
        if(state.nurtureTick==now||now%handle.effect_ticks()!=0||now-state.combat<cfg.nurture_idle_ticks())return;
        long cap=CultivationService.spiritualPowerCap(player);
        if(CultivationService.getProfile(player).currentSpiritualPower()<Math.ceil(cap*cfg.nurture_min_power_fraction()))return;
        if(CultivationService.spendSpiritualPower(player,(long)handle.effect_value())){
            state.nurtureTick=now;held.setDamageValue(Math.max(0,held.getDamageValue()-cfg.nurture_repair()));player.getInventory().setChanged();
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){STATES.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){STATES.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){STATES.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){STATES.clear();}
}
