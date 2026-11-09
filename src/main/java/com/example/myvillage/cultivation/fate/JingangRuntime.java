package com.example.myvillage.cultivation.fate;

import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.session.CombatSession;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.CultivationStats;
import com.example.myvillage.cultivation.tribulation.TribulationDamage;
import com.example.myvillage.stats.runtime.RealmArmorRuntime;
import com.example.myvillage.stats.runtime.StatsRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** 适应仅改变下一次共享攻防计算的防御输入，不再执行另一层伤害减免。 */
public final class JingangRuntime {
    private record Attack(String token, String style, boolean trains) { }
    private record Frame(double health, double maximum, AdaptationCategory category, String action) { }
    private record Spent(double points,long seen) { }
    private static final class Tracked {
        JingangAdaptation adaptation;
        final Map<String,Spent> actions=new HashMap<>();
        Tracked(long now){adaptation=JingangAdaptation.empty(now);}
    }
    private static final Map<UUID,Tracked> STATES=new HashMap<>();
    private static final Map<DamageContainer,Frame> RECEIPTS=new WeakHashMap<>();
    private static final Map<CombatSession,Long> SESSION_IDS=new WeakHashMap<>();
    private static final Map<net.minecraft.world.entity.projectile.Projectile,String> PROJECTILES=new WeakHashMap<>();
    private static final ThreadLocal<Attack> ATTACK=new ThreadLocal<>();
    private static long receiptSequence;
    private JingangRuntime() { }
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,JingangRuntime::beforeDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,JingangRuntime::afterDamage);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e)->forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e)->{if(FateActors.supported(e.getEntity()))forget(e.getEntity().getUUID());});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e)->{
            if(!e.getLevel().isClientSide()&&e.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile p&&p.getOwner()!=null)
                PROJECTILES.put(p,"volley:"+e.getLevel().dimension().location()+":"+p.getOwner().getUUID()+":"+e.getLevel().getGameTime());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e)->{STATES.clear();RECEIPTS.clear();SESSION_IDS.clear();PROJECTILES.clear();ATTACK.remove();});
    }
    public static final class Scope implements AutoCloseable {
        private final Attack previous;
        private Scope(Attack next){previous=ATTACK.get();ATTACK.set(next);}
        @Override public void close(){if(previous==null)ATTACK.remove();else ATTACK.set(previous);}
    }
    public static Scope attack(LivingEntity attacker,CombatSession session) {
        return new Scope(new Attack(attacker.getUUID()+":"+SESSION_IDS.computeIfAbsent(session,ignored->++receiptSequence)+":"+session.revision(),session.style().id().toString(),true));
    }
    public static Scope attack(LivingEntity attacker,long sequence) {
        return new Scope(new Attack("beast:"+attacker.getUUID()+":"+sequence,"",true));
    }
    /** 持续领域保留原伤害链，仅禁止反复训练适应。 */
    public static Scope periodic() { return new Scope(new Attack("periodic","",false)); }
    public static boolean enabled(LivingEntity player){return FateService.effect(player,"jingang_adaptation")>0;}
    public static JingangAdaptation state(LivingEntity player) {
        long now=player.getServer().overworld().getGameTime();
        var tracked=STATES.get(player.getUUID());
        if(tracked==null)return JingangAdaptation.empty(now);
        tracked.adaptation=tracked.adaptation.tick(now,SpiritBodyRules.get().adaptation());return tracked.adaptation;
    }
    public static double defense(LivingEntity target,DamageSource source) {
        LivingEntity player=target;if(!FateActors.supported(player)||!enabled(player))return 0;
        return state(player).defense(category(source),FateActors.body(player).map(b->b.defense()).orElse(0.0),SpiritBodyRules.get().adaptation());
    }
    public static void forget(UUID id){STATES.remove(id);}
    private static boolean trains(LivingEntity target,DamageSource source) {
        if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)||CombatDamageService.isDerivedHit(target)||ATTACK.get()!=null&&!ATTACK.get().trains())return false;
        if(TribulationDamage.tribulation(source))return true;
        return source.getEntity() instanceof LivingEntity attacker && attacker!=target
                && CombatActors.mayHarm(attacker,target) && StatsRuntime.mappedType(source).isPresent();
    }
    private static void beforeDamage(LivingDamageEvent.Pre event) {
        LivingEntity player=event.getEntity();if(!FateActors.supported(player)||!enabled(player)||!trains(player,event.getSource())||event.getNewDamage()<=0)return;
        var attack=ATTACK.get(); var direct=event.getSource().getDirectEntity();
        String token=attack!=null?attack.token():direct instanceof net.minecraft.world.entity.projectile.Projectile
                ?PROJECTILES.getOrDefault((net.minecraft.world.entity.projectile.Projectile)direct,"projectile:"+direct.getUUID()):"hit:"+(++receiptSequence);
        RECEIPTS.put(event.getContainer(),new Frame(player.getHealth(),player.getMaxHealth(),category(event.getSource()),token));
    }
    private static void afterDamage(LivingDamageEvent.Post event) {
        if(event.getEntity().level().isClientSide())return;
        var container=RealmArmorRuntime.currentContainer(event.getEntity());var frame=RECEIPTS.remove(container);
        if(event.getSource().getEntity() instanceof LivingEntity attacker && FateActors.supported(attacker) && enabled(attacker)
                && event.getNewDamage()>0 && event.getEntity()!=attacker && !CombatDamageService.isDerivedHit(event.getEntity())
                && (ATTACK.get()==null||ATTACK.get().trains())
                && !attacker.isAlliedTo(event.getEntity()) && !event.getEntity().isAlliedTo(attacker)) {
            long now=attacker.getServer().overworld().getGameTime();
            var active=STATES.computeIfAbsent(attacker.getUUID(),ignored->new Tracked(now));
            active.adaptation=active.adaptation.tick(now,SpiritBodyRules.get().adaptation()).touch(now);
        }
        LivingEntity player=event.getEntity();if(frame==null||!FateActors.valid(player)||!enabled(player))return;
        double loss=Math.min(Math.max(0,event.getNewDamage()),Math.max(0,frame.health()-player.getHealth()))
                *TemperedBonesRuntime.primaryFraction(container);
        if(loss<=0||frame.maximum()<=0)return;
        long now=player.getServer().overworld().getGameTime();var rules=SpiritBodyRules.get().adaptation();
        var tracked=STATES.computeIfAbsent(player.getUUID(),ignored->new Tracked(now));
        tracked.actions.entrySet().removeIf(e->now-e.getValue().seen()>rules.actionInactivityTicks());
        var old=tracked.actions.get(frame.action());
        if(old==null&&tracked.actions.size()>=rules.maximumTrackedActions())return;
        double spent=old==null?0:old.points();
        double gain=Math.min(Math.max(0,rules.maximumPointsPerAction()-spent),loss/frame.maximum()/rules.healthFractionPerPoint());
        tracked.actions.put(frame.action(),new Spent(spent+gain,now));
        tracked.adaptation=tracked.adaptation.grow(frame.category(),gain,now,rules);
    }
    public static AdaptationCategory category(DamageSource source) {
        if(TribulationDamage.tribulation(source)||source.is(DamageTypes.LIGHTNING_BOLT))return AdaptationCategory.THUNDER;
        var type=StatsRuntime.mappedType(source);
        if(type.isPresent()&&type.get()!=com.example.myvillage.stats.DamageType.PHYSICAL)return AdaptationCategory.fromId(type.get().id());
        if(source.is(DamageTypeTags.IS_EXPLOSION))return AdaptationCategory.IMPACT;
        if(source.is(DamageTypeTags.IS_PROJECTILE))return AdaptationCategory.PIERCE;
        var rules=SpiritBodyRules.get();var scope=ATTACK.get();
        if(scope!=null&&rules.styleCategories().containsKey(scope.style()))return AdaptationCategory.fromId(rules.styleCategories().get(scope.style()));
        if(source.getEntity() instanceof LivingEntity attacker) {
            if(attacker.getMainHandItem().is(ItemTags.SWORDS)||attacker.getMainHandItem().is(ItemTags.AXES))return AdaptationCategory.SLASH;
            var category=rules.entityCategories().get(BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString());
            if(category!=null)return AdaptationCategory.fromId(category);
            if(FateActors.supported(attacker)&&attacker.getMainHandItem().isEmpty())return AdaptationCategory.IMPACT;
        }
        return AdaptationCategory.PHYSICAL;
    }
}
