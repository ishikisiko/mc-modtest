package com.example.myvillage.combat.session;

import com.example.myvillage.combat.art.*;
import com.example.myvillage.combat.elemental.*;
import com.example.myvillage.combat.definition.*;
import com.example.myvillage.combat.network.CombatArtStatePayload;
import com.example.myvillage.combat.runtime.*;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.technique.TechniqueRequirementEvaluator;
import com.example.myvillage.forging.ForgedEquipment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Metadata subordinate to CombatSessionManager. Never ticks/moves an independent attack session. */
public final class CombatArtActions {
    private static final Logger LOG = LoggerFactory.getLogger(CombatArtActions.class);
    private static final Map<UUID,Entry> ENTRIES = new HashMap<>();
    private static final ArtCooldowns COOLDOWNS = new ArtCooldowns();
    private static final Map<UUID,Long> LAST_REQUEST = new HashMap<>();
    private CombatArtActions() {}
    private static final class Entry {
        final ArtDefinition art;
        final ItemStack held;
        final int slot;
        final ResourceLocation dimension;
        final CombatSession session;
        int phase;
        boolean counter;
        double disciplineMultiplier = 1;
        net.minecraft.world.damagesource.DamageSource guardedSource;
        long guardedTick;
        net.minecraft.world.entity.Entity lockTarget;
        Vec3 lockAnchor;
        int nextStrike;
        ElementalActions.Cast elementalCast;
        Entry(ServerPlayer player,ArtDefinition art,CombatSession session) {
            this.art=art;this.held=player.getMainHandItem();this.slot=player.getInventory().selected;
            this.dimension=player.level().dimension().location();this.session=session;
        }
    }
    private static long clock(ServerPlayer player) { return player.serverLevel().getServer().overworld().getGameTime(); }
    public static boolean has(ServerPlayer player) { return ENTRIES.containsKey(player.getUUID()); }
    public static boolean elemental(ServerPlayer player) {
        Entry e=ENTRIES.get(player.getUUID());return e!=null && e.art.elemental().isPresent();
    }
    public static boolean locked(ServerPlayer player) {
        Entry e=ENTRIES.get(player.getUUID());return e!=null && e.art.lock().isPresent();
    }
    public static boolean intentDue(ServerPlayer player,int slot) {
        if (!ArtMath.validSlot(slot)) return false;
        long now=clock(player);
        Long last=LAST_REQUEST.put(player.getUUID(),now);
        return last==null || now-last>=2;
    }
    static Optional<CombatSession.StartEvent> start(ServerPlayer player,CombatSession session,int slot) {
        Optional<String> family=CombatStyles.bundled().weapon(player.getMainHandItem()).flatMap(WeaponDefinition::family);
        Optional<ArtDefinition> found=family.flatMap(f -> ArtDefinition.forSlot(f,slot));
        if (found.isEmpty()) return Optional.empty();
        return start(player,session,found.get());
    }
    static Optional<CombatSession.StartEvent> start(ServerPlayer player,CombatSession session,ArtDefinition art) {
        if (!authorized(player,art)) {
            refuse(player,"unlearned",Component.translatable(art.translationKey()));return Optional.empty();
        }
        if (player.containerMenu!=player.inventoryMenu) return Optional.empty();
        if (art.requiresGround() && !player.onGround()) {
            refuse(player,"ground",Component.empty());return Optional.empty();
        }
        int remaining=cooldownRemaining(player,art);
        if (remaining>0) {
            sendCooldown(player,art,remaining);
            refuse(player,"cooldown",Component.literal(String.format(java.util.Locale.ROOT,"%.1f",remaining/20.0)));
            return Optional.empty();
        }
        net.minecraft.world.entity.Entity target=null;
        if(art.lock().isPresent()) {
            target=LockedSwordTargets.acquire(player,session,art.lock().get()).orElse(null);
            if(target==null) {refuse(player,"no_target",Component.empty());return Optional.empty();}
        }
        long cost=cost(player,art);
        // All validation precedes the single atomic spend. Interrupted casts are not refunded.
        if (!CultivationService.spendSpiritualPower(player,cost)) {
            refuse(player,"power",Component.literal(Long.toString(cost)));return Optional.empty();
        }
        COOLDOWNS.start(player.getUUID(),art.id().toString(),clock(player),art.cooldownTicks());
        Entry e=new Entry(player,art,session);ENTRIES.put(player.getUUID(),e);
        if(art.elemental().isPresent()) {
            var spell=art.elemental().get();
            e.elementalCast=ElementalActions.begin(player,session,spell);e.lockAnchor=e.elementalCast.anchor;
            if(spell.role().equals("ward"))COOLDOWNS.start(player.getUUID(),"elemental_ward",clock(player),ElementalRules.integer("ward_shared_cooldown_ticks",20,1200));
        }
        if(target!=null) {
            e.lockTarget=target;e.lockAnchor=target.position();
            if(target instanceof ServerPlayer victim) victim.displayClientMessage(
                    Component.translatable("combat.myvillage.art.threat",Component.translatable(art.translationKey())),true);
            LOG.info("LOCKED_SWORD player={} t={} result=locked art={} target={} x={} y={} z={}",
                    player.getGameProfile().getName(),player.serverLevel().getGameTime(),art.id(),target.getId(),
                    target.getX(),target.getY(),target.getZ());
        }
        player.displayClientMessage(Component.translatable(art.translationKey()),true);
        if (art.family().equals("fist") && art.slot()==0) e.disciplineMultiplier=CombatDisciplines.consume(player,"fist");
        LOG.info("ADVANCED_ART player={} result=start art={} cost={}",player.getGameProfile().getName(),art.id(),cost);
        return Optional.of(beginPhase(player,e));
    }
    private static CombatSession.StartEvent beginPhase(ServerPlayer player,Entry e) {
        AttackMoveDefinition move=e.art.move(e.phase,1);
        var start=e.session.startExternal(move,player.serverLevel().getGameTime(),
                BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()),e.dimension,player.getYRot());
        send(player,e,CombatArtStatePayload.START,e.lockAnchor==null?player.position():e.lockAnchor);
        return start;
    }
    /** Recheck current registry facts and realm after reload/admin changes, not just learned IDs. */
    public static boolean authorized(ServerPlayer player,ArtDefinition art) {
        var profile=CultivationService.getProfile(player);
        var technique=ModCultivationRegistries.technique(player.registryAccess(),art.id());
        var family=CombatStyles.bundled().weapon(player.getMainHandItem()).flatMap(WeaponDefinition::family);
        return profile.learnedTechniques().containsKey(art.id()) && technique.isPresent()
                && technique.get().category()==TechniqueCategory.ACTIVE
                && technique.get().school().equals(Optional.of(art.school()))
                && (art.elemental().isPresent()
                    ? ModCultivationRegistries.school(player.registryAccess(),art.school())
                        .filter(s -> s.runtime().equals(Optional.of(com.example.myvillage.cultivation.data.SchoolRuntime.SPELL))
                            && s.elementLean().contains(ResourceLocation.parse("myvillage:"+art.elemental().get().element().id()))).isPresent()
                    : family.equals(Optional.of(art.family())) && ModCultivationRegistries.school(player.registryAccess(),art.school())
                        .flatMap(s -> s.weaponFamily()).equals(family))
                && technique.get().effects().flatMap(e -> e.active())
                    .filter(a -> a.skill().equals(art.id()) && a.qiCost()==art.qiCost()).isPresent()
                && TechniqueRequirementEvaluator.evaluate(player.registryAccess(),profile,technique.get()).satisfied();
    }
    static boolean valid(ServerPlayer player) {
        Entry e=ENTRIES.get(player.getUUID());
        return e==null || (e.held==player.getMainHandItem() && e.slot==player.getInventory().selected
                && e.dimension.equals(player.level().dimension().location())
                && player.containerMenu==player.inventoryMenu && authorized(player,e.art));
    }
    static void resolve(ServerPlayer player,CombatSession session,Optional<CombatHitResolver.StepSweep> step) {
        Entry e=ENTRIES.get(player.getUUID());if(e==null)return;
        if(e.art.elemental().isPresent()) {
            int age=session.actionTick(player.serverLevel().getGameTime());
            var spell=e.art.elemental().get();
            if(age<spell.lastStrike() && age%ElementalRules.integer("telegraph_interval_ticks",1,20)==0)
                send(player,e,CombatArtStatePayload.WARD,ElementalActions.origin(player,spell,e.elementalCast));
            if(ElementalActions.resolve(player,session,e.art,e.elementalCast,age))
                send(player,e,CombatArtStatePayload.PULSE,ElementalActions.origin(player,spell,e.elementalCast));
            return;
        }
        if(e.art.lock().isPresent()) {resolveLocked(player,e);return;}
        int age=session.actionTick(player.serverLevel().getGameTime());
        ArtDefinition.Phase phase=e.art.phases().get(e.phase);
        Vec3 origin=step.map(s -> s.originAt(age)).orElseGet(player::position);
        if (ArtMath.guarding(e.phase,age,session.currentMove().activeStartTick(),e.art.guardReduction()) && age%2==0)
            send(player,e,CombatArtStatePayload.WARD,origin);
        if (!session.currentMove().isActiveTick(age)) return;
        AttackMoveDefinition damageMove=phase.move(CombatStyles.bundled(),e.disciplineMultiplier*(e.counter?e.art.counterMultiplier():1));
        List<CombatHitResolver.TargetContact> successful=new ArrayList<>();
        for (var contact:ArtHitResolver.resolve(player,session,phase,origin)) {
            if (!session.markAttempted(CombatTargets.hitId(contact.target()))) continue;
            // Ring reactions radiate away from the caster; a rear target must not be pulled inward.
            Vec3 radial=contact.target().position().subtract(origin);
            float reactionYaw=phase.shape().equals("ring") && radial.horizontalDistanceSqr()>1e-8
                    ? (float)Math.toDegrees(Math.atan2(-radial.x,radial.z)) : session.facingYaw();
            var damage=CombatDamageService.apply(player,contact.target(),damageMove,session,reactionYaw);
            // Thorns and other synchronous callbacks can remove this entry during hurt().
            if (ENTRIES.get(player.getUUID())!=e || !session.hasActiveAction()) return;
            if (damage.successful()) successful.add(contact);
        }
        CombatFeedbackService.hit(player,damageMove,session.revision(),successful);
        send(player,e,CombatArtStatePayload.PULSE,origin);
    }

    private static void resolveLocked(ServerPlayer player,Entry e) {
        var lock=e.art.lock().orElseThrow();
        int age=e.session.actionTick(player.serverLevel().getGameTime());
        var target=e.lockTarget;
        // Losing visibility, range, identity or permissions ends the lock; never snap to a new victim.
        boolean array=lock.mode().equals("array");
        if(array && player.position().distanceToSqr(e.lockAnchor)>lock.range()*lock.range()) {
            e.nextStrike=lock.strikes();return;
        }
        if(!array && (target==null || !CombatHitResolver.legalTarget(player,target,e.session)
                || !LockedSwordTargets.within(player,target,lock.range()) || !LockedSwordTargets.visible(player,target))) {
            if(e.nextStrike<lock.strikes()) LOG.info("LOCKED_SWORD player={} result=lost art={}",player.getGameProfile().getName(),e.art.id());
            e.nextStrike=lock.strikes();return;
        }
        Vec3 origin=array?e.lockAnchor:target.position();
        if(e.nextStrike<lock.strikes() && age%lock.telegraphInterval()==0) {
            send(player,e,CombatArtStatePayload.WARD,origin);
            if(array && lock.slowTicks()>0) for(var candidate:LockedSwordTargets.inArray(player,e.session,origin,
                    lock.radius(),e.art.phases().getFirst().targets())) {
                var owner=CombatTargets.owner(candidate);
                if(owner instanceof net.minecraft.world.entity.LivingEntity living
                        && !(owner instanceof ServerPlayer defender && CombatDodgeService.isDodging(defender)))
                    living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,lock.slowTicks(),lock.slowAmplifier(),false,false,true),player);
            }
        }
        if(!lock.strikeDue(age,e.nextStrike))return;
        int pulse=e.nextStrike++;
        var phase=e.art.phases().getFirst();
        List<net.minecraft.world.entity.Entity> targets=array
                ?LockedSwordTargets.inArray(player,e.session,origin,lock.radius(),phase.targets())
                :lock.hasSplash()?LockedSwordTargets.impact(player,e.session,target,origin,lock.radius(),phase.targets()):List.of(target);
        var move=e.art.move(0,lock.pulseMultiplier(pulse));
        List<CombatHitResolver.TargetContact> successes=new ArrayList<>();
        for(var victim:targets) {
            var owner=CombatTargets.owner(victim);
            double fraction=owner instanceof ServerPlayer defender?CombatDodgeService.lockedArtDamageFraction(defender):1;
            // An invulnerable hit goes through the existing cancellation hook for dodge/fate receipts.
            double impactScale=lock.targetMultiplier(CombatTargets.hitId(victim)==CombatTargets.hitId(target));
            var victimMove=impactScale==1?move:e.art.move(0,lock.pulseMultiplier(pulse)*impactScale);
            var result=CombatDamageService.applyMitigated(player,victim,victimMove,e.session,fraction==0?1:fraction);
            if(ENTRIES.get(player.getUUID())!=e || !e.session.hasActiveAction())return;
            if(result.successful() && result.actualDamage()>0) {
                successes.add(new CombatHitResolver.TargetContact(victim,victim.distanceToSqr(player),victim.position()));
                if(owner instanceof net.minecraft.world.entity.LivingEntity living)
                    SwordArmorBreak.apply(living,lock.armorBreakFraction()*fraction,lock.armorBreakTicks());
            }
            if(owner instanceof ServerPlayer defender && fraction<1) defender.displayClientMessage(
                    Component.translatable(fraction==0?"combat.myvillage.art.evaded":"combat.myvillage.art.partial"),true);
            LOG.info("LOCKED_SWORD player={} t={} result=strike art={} pulse={} target={} fraction={} success={} damage={}",
                    player.getGameProfile().getName(),player.serverLevel().getGameTime(),e.art.id(),pulse,victim.getId(),
                    fraction,result.successful(),result.actualDamage());
        }
        CombatFeedbackService.hit(player,move,e.session.revision(),successes);
        send(player,e,CombatArtStatePayload.PULSE,origin);
    }
    static Optional<CombatSession.StartEvent> advance(ServerPlayer player) {
        Entry e=ENTRIES.get(player.getUUID());if(e==null)return Optional.empty();
        if (e.phase+1>=e.art.phases().size()) { stop(player,"COMPLETED");return Optional.empty(); }
        e.phase++;e.guardedSource=null;
        return Optional.of(beginPhase(player,e));
    }
    /** Called for every incoming hit, before the existing positive-damage interruption hook. */
    public static void guard(LivingIncomingDamageEvent event) {
        ElementalEffects.guard(event);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entry e=ENTRIES.get(player.getUUID());if(e==null)return;
        e.guardedSource=null;
        if (!player.isAlive() || !valid(player) || !e.session.hasActiveAction()) return;
        int age=e.session.actionTick(player.serverLevel().getGameTime());
        if (!ArtMath.guarding(e.phase,age,e.session.currentMove().activeStartTick(),e.art.guardReduction())) return;
        var source=event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.getEntity()==null || source.getSourcePosition()==null || !(event.getAmount()>0)) return;
        Vec3 delta=source.getEntity().position().subtract(player.position());
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getDeltaMovement().horizontalDistanceSqr()>.0001)
            delta=projectile.getDeltaMovement().scale(-1);
        if (!ArtMath.inArc(delta.x,delta.z,e.session.facingYaw(),e.art.guardArc())) return;
        event.setAmount((float)(event.getAmount()*(1-e.art.guardReduction())));
        e.guardedSource=source;e.guardedTick=player.serverLevel().getGameTime();
    }
    /** Match the exact source and tick, so another simultaneous hit cannot borrow a guard. */
    static boolean damageInterrupts(ServerPlayer player,net.minecraft.world.damagesource.DamageSource source) {
        Entry e=ENTRIES.get(player.getUUID());
        if(e==null) return false;
        if(source!=null && e.guardedSource==source && e.guardedTick==player.serverLevel().getGameTime()) {
            e.guardedSource=null;e.counter=true;
            player.displayClientMessage(Component.translatable("combat.myvillage.art.counter"),true);
            CombatDisciplines.guarded(player,e.session.revision());
            com.example.myvillage.wilderness.ExpeditionCombat.guarded(player);
            return false;
        }
        return e.art.interruptOnDamage();
    }
    static void stop(ServerPlayer player,String reason) {
        Entry e=ENTRIES.remove(player.getUUID());if(e==null)return;
        send(player,e,CombatArtStatePayload.STOP,player.position());
        LOG.info("ADVANCED_ART player={} result=stop art={} reason={}",player.getGameProfile().getName(),e.art.id(),reason);
    }
    static void remove(UUID player) { ENTRIES.remove(player);LAST_REQUEST.remove(player); }
    static void clear() { ENTRIES.clear();LAST_REQUEST.clear();COOLDOWNS.clear(); }
    static void prune(long now) { COOLDOWNS.prune(now); }
    public static int cooldownRemaining(ServerPlayer player,ArtDefinition art) {
        int own=COOLDOWNS.remaining(player.getUUID(),art.id().toString(),clock(player));
        return art.elemental().filter(e->e.role().equals("ward")).isPresent()
                ?Math.max(own,COOLDOWNS.remaining(player.getUUID(),"elemental_ward",clock(player))):own;
    }
    public static long cost(ServerPlayer player,ArtDefinition art) {
        return art.elemental().isPresent()?art.qiCost():ForgedEquipment.qiCost(player.getMainHandItem(),art.qiCost());
    }
    public static void syncCooldowns(ServerPlayer player) {
        for (var art:ArtDefinition.all()) sendCooldown(player,art,COOLDOWNS.remaining(player.getUUID(),art.id().toString(),clock(player)));
    }
    private static void sendCooldown(ServerPlayer player,ArtDefinition art,int remaining) {
        PacketDistributor.sendToPlayer(player,new CombatArtStatePayload(player.getId(),art.id(),
                CombatArtStatePayload.COOLDOWN,0,0,0,player.serverLevel().getGameTime(),player.position(),player.getYRot(),remaining));
    }
    private static void send(ServerPlayer player,Entry e,int kind,Vec3 origin) {
        int age=Math.max(0,e.session.actionTick(player.serverLevel().getGameTime()));
        float yaw=e.art.elemental().map(spell->ElementalActions.facingYaw(spell,e.elementalCast,e.session.facingYaw()))
                .orElse(e.session.facingYaw());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,new CombatArtStatePayload(player.getId(),e.art.id(),
                kind,e.phase,age,e.session.revision(),player.serverLevel().getGameTime(),origin,yaw,
                COOLDOWNS.remaining(player.getUUID(),e.art.id().toString(),clock(player))));
    }
    private static void refuse(ServerPlayer player,String why,Component detail) {
        LOG.info("ADVANCED_ART player={} result=rejected reason={}",player.getGameProfile().getName(),why);
        player.displayClientMessage(Component.translatable("combat.myvillage.art."+why,detail),true);
    }
}
