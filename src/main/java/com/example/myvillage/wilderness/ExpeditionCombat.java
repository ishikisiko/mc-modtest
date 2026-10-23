package com.example.myvillage.wilderness;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.alchemy.*;
import com.example.myvillage.forging.ForgeMark;
import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.combat.session.CombatSession;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.runtime.*;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Expedition effects: transient benefits, persistent online cooldowns, exact direct-hit receipts. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class ExpeditionCombat {
    private static final String TAG="myvillage_expedition_cooldowns";
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final ThreadLocal<ServerPlayer> DIRECT=new ThreadLocal<>();
    private static final ResourceLocation MED=ResourceLocation.parse("myvillage:expedition_medicine"),WARD=ResourceLocation.parse("myvillage:expedition_ward");
    private static final class State {
        ItemStack held;int slot;ResourceLocation dimension;long revision=-1,lastGain=-1;int ready,ward,quiet;
        double activePierce,activeBoost;String trait="";
    }
    private ExpeditionCombat(){}
    private static State state(ServerPlayer p){
        var mark=p.getMainHandItem().get(ModDataComponents.FORGE_MARK.get());String trait=mark==null?"":mark.trait();
        var old=STATES.get(p.getUUID());
        if(old==null||old.held!=p.getMainHandItem()||old.slot!=p.getInventory().selected||!old.trait.equals(trait)||!old.dimension.equals(p.level().dimension().location())){
            old=new State();old.held=p.getMainHandItem();old.slot=p.getInventory().selected;old.dimension=p.level().dimension().location();old.trait=trait;STATES.put(p.getUUID(),old);
        }
        return old;
    }
    private static ForgeMark mark(ServerPlayer p){return p.getMainHandItem().get(ModDataComponents.FORGE_MARK.get());}
    private static double value(ServerPlayer p,String trait){var m=mark(p);var r=ExpeditionRules.get().trait(trait);return m!=null&&m.trait().equals(trait)&&r!=null?r.value(m.quality()):0;}
    private static CompoundTag cooldowns(ServerPlayer p){return p.getPersistentData().getCompound(TAG);}
    private static boolean ready(ServerPlayer p,String trait){return cooldowns(p).getInt(trait)<=0;}
    private static void cooldown(ServerPlayer p,String trait){var c=cooldowns(p);c.putInt(trait,ExpeditionRules.get().trait(trait).cooldown());p.getPersistentData().put(TAG,c);}
    public static boolean front(LivingEntity defender,LivingEntity attacker){Vec3 delta=attacker.position().subtract(defender.position()).multiply(1,0,1).normalize();return defender.getLookAngle().multiply(1,0,1).normalize().dot(delta)>.35;}
    public static double meleeMultiplier(ServerPlayer p,CombatSession session){
        if(session.isExternal())return 1;var s=state(p);
        if(s.revision!=session.revision()){
            s.revision=session.revision();s.activePierce=0;s.activeBoost=0;
            if(s.ready>0){s.activePierce=value(p,"breach");s.activeBoost=value(p,"riposte");s.ready=0;}
        }
        return 1+s.activeBoost;
    }
    public static Scope scope(LivingEntity actor,CombatSession session,boolean direct){return new Scope(direct&&!session.isExternal()&&actor instanceof ServerPlayer p?p:null);}
    public static final class Scope implements AutoCloseable {
        private final ServerPlayer old;
        Scope(ServerPlayer p){old=DIRECT.get();if(p==null)DIRECT.remove();else DIRECT.set(p);}
        public void close(){if(old==null)DIRECT.remove();else DIRECT.set(old);}
    }
    /** Armor is adjusted inside the original armor calculation, never by a second hurt. */
    public static double armorPierce(LivingEntity attacker){
        if(!(attacker instanceof ServerPlayer p))return 0;
        double pill=medicine(p,"pozhang");var s=STATES.get(p.getUUID());
        return Math.min(.65,pill+(DIRECT.get()==p&&s!=null?s.activePierce:0));
    }
    public static void meleeAccepted(ServerPlayer p,CombatSession session,LivingEntity victim,double damage){
        if(damage<=0||session.isExternal())return;var s=state(p);
        if(value(p,"breach")>0&&session.isFinisher()&&s.lastGain!=session.revision()&&ready(p,"breach")){
            s.ready=ExpeditionRules.get().trait("breach").duration();s.lastGain=session.revision();cooldown(p,"breach");feedback(p,"破锋已蓄：下一次近战穿甲");
        }
        if(value(p,"frost_edge")>0&&ready(p,"frost_edge")){
            var r=ExpeditionRules.get().trait("frost_edge");victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,r.duration(),0),p);cooldown(p,"frost_edge");feedback(p,"寒锋：迟滞");
        }
    }
    public static void guarded(ServerPlayer p){if(value(p,"riposte")<=0||!ready(p,"riposte"))return;state(p).ready=ExpeditionRules.get().trait("riposte").duration();cooldown(p,"riposte");feedback(p,"荆棘已蓄：下一次近战反击");}
    public static void cast(ServerPlayer p){if(value(p,"warding")<=0||!ready(p,"warding"))return;state(p).ward=ExpeditionRules.get().trait("warding").duration();cooldown(p,"warding");refresh(p);feedback(p,"护符：元素防护");}
    private static void feedback(ServerPlayer p,String text){p.displayClientMessage(net.minecraft.network.chat.Component.literal(text),true);}
    private static double medicine(ServerPlayer p,String id){var m=MedicineRuntime.state(p);var r=ExpeditionRules.get().medicine(id);return r!=null&&m.effect().equals(id)&&m.effectTicks()>0?r.strength()*AlchemyRules.get().qualityMultiplier(m.quality()):0;}
    private static void modifier(ServerPlayer p,Holder<Attribute> attr,ResourceLocation id,double amount,AttributeModifier.Operation op){var a=p.getAttribute(attr);if(a==null)return;if(amount==0){a.removeModifier(id);return;}var old=a.getModifier(id);if(old==null||old.amount()!=amount||old.operation()!=op)a.addOrUpdateTransientModifier(new AttributeModifier(id,amount,op));}
    public static void refresh(ServerPlayer p){
        var m=MedicineRuntime.state(p);var spec=ExpeditionRules.get().medicine(m.effect());double f=AlchemyRules.get().qualityMultiplier(m.quality());
        modifier(p,Attributes.MOVEMENT_SPEED,MED,medicine(p,"qingshen"),AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(p,Attributes.KNOCKBACK_RESISTANCE,MED,m.effect().equals("humai")&&spec!=null?Math.min(.8,spec.secondary()*f):0,AttributeModifier.Operation.ADD_VALUE);
        State s=STATES.get(p.getUUID());
        for(var type:DamageType.values()){
            double amount=type==DamageType.PHYSICAL?medicine(p,"humai"):medicine(p,"dingshen");
            if(type==DamageType.FIRE)amount+=medicine(p,"yuyan");
            modifier(p,StatsRuntime.resistance(type),MED,amount,AttributeModifier.Operation.ADD_VALUE);
            modifier(p,StatsRuntime.resistance(type),WARD,type!=DamageType.PHYSICAL&&s!=null&&s.ward>0?value(p,"warding"):0,AttributeModifier.Operation.ADD_VALUE);
        }
        if(medicine(p,"qingzhang")>0)p.removeEffect(MobEffects.POISON);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!p.isAlive())return;
        var c=cooldowns(p);for(var id:ExpeditionRules.get().traitIds())if(c.getInt(id)>0)c.putInt(id,Math.min(c.getInt(id),ExpeditionRules.get().trait(id).cooldown())-1);
        if(!c.isEmpty())p.getPersistentData().put(TAG,c);
        var s=state(p);if(s.ready>0)s.ready--;if(s.ward>0)s.ward--;
        if(value(p,"carapace")>0)s.quiet=Math.min(s.quiet+1,ExpeditionRules.get().trait("carapace").cooldown());else s.quiet=0;
        refresh(p);
    }
    @SubscribeEvent public static void incoming(LivingDamageEvent.Pre e){
        if(!(e.getEntity() instanceof ServerPlayer p)||e.getNewDamage()<=0||e.getSource().getEntity()==null||e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var s=state(p);double v=value(p,"carapace");
        if(v>0&&s.quiet>=ExpeditionRules.get().trait("carapace").cooldown()&&ready(p,"carapace")){e.setNewDamage((float)(e.getNewDamage()*(1-v)));cooldown(p,"carapace");feedback(p,"赤甲：抵御来袭");}
        s.quiet=0;
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Post e){if(e.getEntity() instanceof ServerPlayer p&&e.getNewDamage()>0)state(p).quiet=0;}
    @SubscribeEvent public static void fall(LivingFallEvent e){if(e.getEntity() instanceof ServerPlayer p&&medicine(p,"qingshen")>0){var r=ExpeditionRules.get().medicine("qingshen");e.setDamageMultiplier((float)(e.getDamageMultiplier()*(1-Math.min(.7,r.secondary()*AlchemyRules.get().qualityMultiplier(MedicineRuntime.state(p).quality())))));}}
    @SubscribeEvent public static void died(LivingDeathEvent e){
        if(e.getEntity() instanceof ServerPlayer p)STATES.remove(p.getUUID());
        if(!(e.getSource().getEntity() instanceof ServerPlayer p)||value(p,"soul_return")<=0||!ready(p,"soul_return")||!e.getEntity().getType().getCategory().equals(MobCategory.MONSTER))return;
        var victim=RealmArmorRuntime.defenderScale(e.getEntity());var owner=RealmArmorRuntime.defenderScale(p);
        if(!victim.status().equals("valid")||victim.factor()<owner.factor())return;
        long amount=Math.max(1,(long)(CultivationService.spiritualPowerCap(p)*value(p,"soul_return")));
        if(CultivationService.recoverSpiritualPower(p,amount).success()){cooldown(p,"soul_return");feedback(p,"归息：灵力回转");}
    }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone e){if(e.getEntity() instanceof ServerPlayer p){p.getPersistentData().put(TAG,e.getOriginal().getPersistentData().getCompound(TAG).copy());STATES.remove(p.getUUID());}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){STATES.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){STATES.remove(e.getEntity().getUUID());if(e.getEntity() instanceof ServerPlayer p)refresh(p);}
    @SubscribeEvent public static void stop(net.neoforged.neoforge.event.server.ServerStoppingEvent e){STATES.clear();DIRECT.remove();}
}
