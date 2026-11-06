package com.example.myvillage.legacy;

import com.example.myvillage.combat.*;
import com.example.myvillage.combat.elemental.*;
import com.example.myvillage.combat.runtime.*;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.runtime.StatsRuntime;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.slf4j.LoggerFactory;

/** Persistent items retain only bounded charges. All active effects belong to the combat manager. */
public final class LegacyRuntime {
    private static final Map<UUID,List<State>> STATES=new HashMap<>();
    private static final Map<UUID,Mirror> MIRRORS=new HashMap<>();
    private static final Map<UUID,Map<String,Long>> ITEM_COOLDOWNS=new HashMap<>();
    private LegacyRuntime() {}
    private static LegacyRules rules() {return LegacyRules.get();}
    private static long now(ServerPlayer p) {return p.serverLevel().getServer().overworld().getGameTime();}
    private static final class State {
        final LegacyRules.Art art;
        final ItemStack held;
        final int slot;
        final ResourceLocation dimension;
        final LegacyProjectionEntity view;
        final long until;
        final double damage;
        final Set<Integer> hit=new HashSet<>();
        Vec3 target;
        boolean returning;
        State(ServerPlayer p,LegacyRules.Art art,LegacyProjectionEntity view,Vec3 target) {
            this.art=art;this.view=view;this.target=target;held=p.getMainHandItem();slot=p.getInventory().selected;
            dimension=p.level().dimension().location();until=now(p)+art.duration();
            damage=ElementalEffects.base(p,element(art.mode()));
        }
        boolean valid(ServerPlayer p) {
            return alive(p)&&p.containerMenu==p.inventoryMenu&&p.getMainHandItem()==held&&p.getInventory().selected==slot
                    &&p.level().dimension().location().equals(dimension)&&!view.isRemoved()&&now(p)<until
                    &&CultivationService.getProfile(p).learnedTechniques().containsKey(art.id());
        }
    }
    private record Mirror(ItemStack stack,int slot,ResourceLocation dimension,long until,LegacyProjectionEntity view) {}
    public record Plan(LegacyRules.Art art,boolean recast,Vec3 destination,Vec3 origin) {}
    public static boolean legacy(ResourceLocation id) {return rules().art(id).isPresent();}
    public static boolean allowedItem(ServerPlayer p,ResourceLocation id) {
        return rules().art(id).filter(a->a.item().isEmpty()||a.item().get().equals(BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()))).isPresent();
    }
    public static boolean recast(ServerPlayer p,ResourceLocation id) {
        var art=rules().art(id).orElse(null);if(art==null)return false;
        return (!art.mode().equals("fire")||p.isShiftKeyDown())&&states(p).stream().anyMatch(s->s.art.id().equals(id)&&s.valid(p)&&!s.returning);
    }
    public static boolean weaponAway(ServerPlayer p) {
        return states(p).stream().anyMatch(s->Set.of("sword","ruler").contains(s.art.mode())&&s.valid(p));
    }
    public static Optional<Plan> prepare(ServerPlayer p,ResourceLocation id) {
        var art=rules().art(id).orElse(null);
        if(art==null||!allowedItem(p,id)||!alive(p)||p.containerMenu!=p.inventoryMenu)return Optional.empty();
        var own=states(p).stream().filter(s->s.art.id().equals(id)&&s.valid(p)).toList();
        if(own.stream().anyMatch(s->s.returning))return refused(p,"blocked");
        boolean recast=(!art.mode().equals("fire")||p.isShiftKeyDown())&&!own.isEmpty();
        if(recast) {
            var point=own.getFirst().view.position();
            if(art.mode().equals("shadow")&&!safeReturn(p,point))return refused(p,"blocked");
            return Optional.of(new Plan(art,true,point,p.position()));
        }
        if(STATES.size()>=rules().ticks("max_active_players")&&!STATES.containsKey(p.getUUID()))return refused(p,"blocked");
        if(art.mode().equals("fire")&&(p.isShiftKeyDown()||own.size()>=rules().ticks("max_fire_seeds")))return refused(p,"blocked");
        Vec3 origin=p.position(),destination;
        switch(art.mode()) {
            case "shadow" -> {
                if(com.example.myvillage.combat.foundation.ControlService.isRooted(p))return refused(p,"blocked");
                Vec3 dir=p.getLookAngle().multiply(1,0,1).normalize();
                double distance=CombatStepService.safeDistance(p,dir,art.range(),.35);
                if(distance<1)return refused(p,"blocked");
                destination=origin.add(dir.scale(distance));
            }
            case "sword" -> {
                Vec3 start=p.getEyePosition().add(0,-.25,0);
                destination=clipped(p,start,start.add(p.getLookAngle().scale(art.range())));
                if(destination.distanceToSqr(start)<1)return refused(p,"blocked");
            }
            default -> {
                var hit=p.level().clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(art.range())),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=net.minecraft.core.Direction.UP)return refused(p,"blocked");
                destination=hit.getLocation().add(0,.05,0);
                if(!p.serverLevel().hasChunkAt(BlockPos.containing(destination))||!p.level().getWorldBorder().isWithinBounds(BlockPos.containing(destination)))return refused(p,"blocked");
                if(art.mode().equals("ruler")) {
                    AABB bounds=LegacyEffects.rulerBounds(destination);
                    if(!p.serverLevel().hasChunksAt(BlockPos.containing(bounds.minX,bounds.minY,bounds.minZ),BlockPos.containing(bounds.maxX,bounds.maxY,bounds.maxZ))||!p.level().noCollision(null,bounds))return refused(p,"blocked");
                }
            }
        }
        return Optional.of(new Plan(art,false,destination,origin));
    }
    private static Optional<Plan> refused(ServerPlayer p,String key) {message(p,key);return Optional.empty();}
    /** Called synchronously only after prepare and payment; false is refunded before cooldown starts. */
    public static boolean activate(ServerPlayer p,Plan plan) {
        var art=plan.art();
        if(plan.recast()) {
            var own=List.copyOf(states(p)).stream().filter(s->s.art.id().equals(art.id())&&s.valid(p)).toList();
            if(own.isEmpty())return false;
            switch(art.mode()) {
                case "sword" -> {var s=own.getFirst();s.returning=true;s.view.phase(1);s.hit.clear();}
                case "shadow" -> {if(!safeReturn(p,plan.destination()))return false;Vec3 from=p.position();p.teleportTo(plan.destination().x,plan.destination().y,plan.destination().z);p.fallDistance=0;LegacyEffects.emit(p,LegacyProjectionEntity.SHADOW_PATH,from,plan.destination(),p.getBbWidth(),(int)rules().visual("shadow_trace_ticks"));remove(p,own.getFirst());}
                case "ruler" -> remove(p,own.getFirst());
                case "fire" -> {for(var s:own){area(p,s.view.position(),s.art.radius(),DamageType.FIRE,s.damage,s.art.damage(),true);remove(p,s);}}
                default -> {return false;}
            }
            log(p,"recast",art.mode());return true;
        }
        int kind=switch(art.mode()){case "sword"->LegacyProjectionEntity.SWORD;case "shadow"->LegacyProjectionEntity.SHADOW;case "fire"->LegacyProjectionEntity.FIRE;default->LegacyProjectionEntity.RULER;};
        ItemStack visual=kind==LegacyProjectionEntity.FIRE?new ItemStack(LegacyContent.item("ember_cinnabar")):p.getMainHandItem();
        var view=new LegacyProjectionEntity(LegacyContent.PROJECTION.get(),p.level());
        Vec3 at=kind==LegacyProjectionEntity.SWORD?p.getEyePosition().add(0,-.25,0):kind==LegacyProjectionEntity.SHADOW?plan.origin():plan.destination();
        view.setPos(at);view.setYRot(p.getYRot());view.initialize(kind,p,visual,art.duration());
        view.visual(Vec3.ZERO,art.radius());
        if(!p.serverLevel().addFreshEntity(view))return false;
        if(kind==LegacyProjectionEntity.SHADOW) {
            if(!safePath(p,plan.destination(),false)){view.discard();return false;}
            p.teleportTo(plan.destination().x,plan.destination().y,plan.destination().z);p.fallDistance=0;
            LegacyEffects.emit(p,LegacyProjectionEntity.SHADOW_PATH,plan.origin(),plan.destination(),p.getBbWidth(),(int)rules().visual("shadow_trace_ticks"));
        }
        if(kind==LegacyProjectionEntity.RULER)LegacyEffects.emit(p,LegacyProjectionEntity.EARTH_IMPACT,at,at,art.radius(),(int)rules().visual("impact_ticks"));
        STATES.computeIfAbsent(p.getUUID(),id->new ArrayList<>()).add(new State(p,art,view,plan.destination()));
        p.playSound(kind==LegacyProjectionEntity.FIRE?SoundEvents.FIRECHARGE_USE:kind==LegacyProjectionEntity.RULER?SoundEvents.STONE_PLACE:SoundEvents.AMETHYST_BLOCK_CHIME,.7F,1.1F);
        log(p,"cast",art.mode());return true;
    }
    private static List<State> states(ServerPlayer p) {return STATES.getOrDefault(p.getUUID(),List.of());}
    private static boolean alive(ServerPlayer p) {return p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&!p.isPassenger()&&CombatService.getMode(p)==CombatMode.CULTIVATION;}
    private static DamageType element(String mode) {return switch(mode){case "sword"->DamageType.METAL;case "shadow"->DamageType.WATER;case "fire"->DamageType.FIRE;default->DamageType.EARTH;};}
    private static boolean safeReturn(ServerPlayer p,Vec3 at) {return !com.example.myvillage.combat.foundation.ControlService.isRooted(p)&&safePath(p,at,true);}
    private static boolean safePath(ServerPlayer p,Vec3 at,boolean bounded) {
        Vec3 delta=at.subtract(p.position());double length=delta.length();
        if(bounded&&length>rules().number("projection_max_distance"))return false;
        if(!p.level().getWorldBorder().isWithinBounds(BlockPos.containing(at)))return false;
        int count=Math.max(1,(int)Math.ceil(length/.25));
        for(int i=1;i<=count;i++) {
            Vec3 d=delta.scale(i/(double)count);AABB box=p.getBoundingBox().move(d);
            if(!p.serverLevel().hasChunksAt(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))||!p.level().noCollision(p,box))return false;
        }
        AABB feet=p.getBoundingBox().move(delta).move(0,-.35,0);
        return !p.level().noCollision(p,feet)&&p.level().getFluidState(BlockPos.containing(at)).isEmpty();
    }
    /** Residual effects are advanced by CombatSessionManager, never a second player attack scheduler. */
    public static void tick(MinecraftServer server) {
        for(var entry:List.copyOf(STATES.entrySet())) {
            var p=server.getPlayerList().getPlayer(entry.getKey());
            if(p==null){forget(entry.getKey());continue;}
            for(var s:List.copyOf(entry.getValue())) {
                if(!s.valid(p)){remove(p,s);continue;}
                switch(s.art.mode()) {
                    case "sword" -> tickSword(p,s);
                    case "ruler" -> {
                        if(now(p)%rules().ticks("ruler_interval_ticks")==0) {
                            for(var target:targets(p,s.view.position(),s.art.radius())) {
                                var owner=CombatTargets.owner(target);
                                if(owner instanceof LivingEntity living&&!StaggerResistant.resists(living))living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,rules().ticks("ruler_slow_ticks"),rules().ticks("ruler_slow_amplifier"),false,false,true),p);
                                hit(p,target,DamageType.EARTH,s.damage,s.art.damage());
                            }
                        }
                    }
                    case "fire","shadow" -> {}
                }
            }
        }
        tickMirrors(server);
        long now=server.overworld().getGameTime();
        ITEM_COOLDOWNS.values().forEach(m->m.values().removeIf(t->t<=now));ITEM_COOLDOWNS.values().removeIf(Map::isEmpty);
    }
    private static void tickSword(ServerPlayer p,State s) {
        Vec3 from=s.view.position();
        if(from.distanceToSqr(p.position())>Math.pow(rules().number("projection_max_distance"),2)){remove(p,s);return;}
        Vec3 target=s.returning?p.getEyePosition().add(0,-.25,0):s.target;
        double distance=target.distanceTo(from),speed=s.returning?s.art.returnSpeed():s.art.speed();
        if(distance<.35){if(s.returning)remove(p,s);return;}
        Vec3 next=from.add(target.subtract(from).normalize().scale(Math.min(speed,distance)));
        if(!p.serverLevel().hasChunkAt(BlockPos.containing(next))){remove(p,s);return;}
        Vec3 end=clipped(p,from,next);boolean wall=end.distanceToSqr(next)>.0001;
        final Vec3 segmentEnd=end;
        var contacts=p.serverLevel().getEntities(p,new AABB(from,end).inflate(s.art.radius()),e->legal(p,e)&&!s.hit.contains(CombatTargets.hitId(e))).stream()
                .filter(e->e.getBoundingBox().inflate(s.art.radius()).contains(from)||e.getBoundingBox().inflate(s.art.radius()).clip(from,segmentEnd).isPresent())
                .sorted(Comparator.comparingDouble((Entity e)->e.getBoundingBox().inflate(s.art.radius()).clip(from,segmentEnd).map(from::distanceToSqr).orElse(0.))).toList();
        for(var e:contacts) {
            if(s.hit.size()>=rules().ticks("projection_targets"))break;
            if(!s.hit.add(CombatTargets.hitId(e)))continue;
            hit(p,e,DamageType.METAL,s.damage,s.art.damage());
        }
        Vec3 direction=end.subtract(from);s.view.setPos(end);s.view.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
        s.view.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance())));
        if(wall){if(s.returning)remove(p,s);else s.target=end;}
    }
    public static boolean legal(LivingEntity p,Entity e) {
        Entity owner=CombatTargets.owner(e);
        return CombatTargets.physicalTarget(e)&&owner instanceof LivingEntity&&e.isAlive()&&!e.isRemoved()&&e.isAttackable()&&!e.skipAttackInteraction(p)&&CombatActors.mayHarm(p,owner);
    }
    public static List<Entity> targets(LivingEntity p,Vec3 point,double radius) {
        var found=p.level().getEntities(p,new AABB(point,point).inflate(radius,2.5,radius),e->legal(p,e)).stream()
                .filter(e->e.getBoundingBox().getCenter().multiply(1,0,1).distanceToSqr(point.multiply(1,0,1))<=radius*radius)
                .filter(e->clear(p,point.add(0,.3,0),e.getBoundingBox().getCenter()))
                .sorted(Comparator.comparingDouble((Entity e)->e.distanceToSqr(point))).toList();
        Set<Integer> seen=new HashSet<>();return found.stream().filter(e->seen.add(CombatTargets.hitId(e))).limit(rules().ticks("max_targets")).toList();
    }
    public static boolean clear(Entity actor,Vec3 from,Vec3 to) {
        if(actor.level() instanceof ServerLevel level&&!loaded(level,from,to))return false;
        return actor.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor)).getType()==HitResult.Type.MISS;
    }
    private static boolean loaded(ServerLevel level,Vec3 a,Vec3 b) {
        AABB box=new AABB(a,b);return level.hasChunksAt(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ));
    }
    private static Vec3 clipped(Entity p,Vec3 a,Vec3 b) {
        if(p.level() instanceof ServerLevel level&&!loaded(level,a,b))return a;
        var h=p.level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));return h.getType()==HitResult.Type.MISS?b:h.getLocation();
    }
    private static void hit(ServerPlayer p,Entity e,DamageType type,double base,double factor) {
        if(!legal(p,e))return;
        boolean hit=StatsRuntime.hurt(p,e,ElementalRules.source(p,type),StatsRuntime.outgoing(p,base,factor));
        if(hit)log(p,"hit",type.id()+" target="+e.getId());
    }
    private static void area(ServerPlayer p,Vec3 at,double radius,DamageType type,double base,double factor,boolean burst) {
        for(var e:targets(p,at,radius))hit(p,e,type,base,factor);
        LegacyEffects.emit(p,type==DamageType.FIRE?LegacyProjectionEntity.FIRE_BURST:LegacyProjectionEntity.EARTH_IMPACT,at,at,radius,(int)rules().visual(type==DamageType.FIRE?"fire_burst_ticks":"impact_ticks"));
        if(burst)p.serverLevel().playSound(null,BlockPos.containing(at),SoundEvents.FIRECHARGE_USE,net.minecraft.sounds.SoundSource.PLAYERS,.7F,1.1F);
    }
    public static void particles(ServerLevel level,Vec3 at,net.minecraft.core.particles.SimpleParticleType type,int count,double spread) {level.sendParticles(type,at.x,at.y,at.z,count,spread,spread,spread,0);}
    public static void ring(ServerLevel level,Vec3 at,double radius,net.minecraft.core.particles.SimpleParticleType type) {
        for(int i=0;i<24;i++){double a=i*Math.PI/12;particles(level,at.add(Math.cos(a)*radius,.12,Math.sin(a)*radius),type,1,0);}
    }
    private static void remove(ServerPlayer p,State s) {s.view.discard();var list=STATES.get(p.getUUID());if(list!=null){list.remove(s);if(list.isEmpty())STATES.remove(p.getUUID());}}
    public static void forget(UUID id) {var states=STATES.remove(id);if(states!=null)states.forEach(s->s.view.discard());var mirror=MIRRORS.remove(id);if(mirror!=null&&mirror.view()!=null)mirror.view().discard();}
    public static void clearAll() {List.copyOf(STATES.keySet()).forEach(LegacyRuntime::forget);MIRRORS.clear();ITEM_COOLDOWNS.clear();}
    public static int charges(ItemStack stack,int maximum) {return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("myvillage_legacy_charge"),0,maximum);}
    private static void setCharges(ItemStack stack,int value) {
        CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putInt("myvillage_legacy_charge",value);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    public static boolean useArtifact(ServerPlayer p,ItemStack stack,String kind) {
        return CombatSessionManager.handleLegacyArtifact(p,()->useArtifactNow(p,stack,kind),rules().ticks("artifact_recovery_ticks"));
    }
    private static boolean useArtifactNow(ServerPlayer p,ItemStack stack,String kind) {
        if(!alive(p)||p.getMainHandItem()!=stack||p.containerMenu!=p.inventoryMenu)return false;
        var cds=ITEM_COOLDOWNS.computeIfAbsent(p.getUUID(),id->new HashMap<>());
        if(cds.getOrDefault(kind,0L)>now(p))return false;
        if(kind.equals("mirror")) {
            if(charges(stack,1)==0) {
                Vec3 at=p.getEyePosition().add(p.getLookAngle());
                var view=LegacyEffects.emit(p,LegacyProjectionEntity.MIRROR,at,at.add(p.getLookAngle()),rules().visual("mirror_capture_radius"),rules().ticks("mirror_capture_ticks"));
                var prior=MIRRORS.put(p.getUUID(),new Mirror(stack,p.getInventory().selected,p.level().dimension().location(),now(p)+rules().ticks("mirror_capture_ticks"),view));
                if(prior!=null&&prior.view()!=null)prior.view().discard();
                message(p,"mirror_ready");cds.put(kind,now(p)+rules().ticks("mirror_cooldown_ticks"));return true;
            }
            castRay(p,rules().number("mirror_range"),DamageType.WATER,rules().number("mirror_damage"));setCharges(stack,0);
            cds.put(kind,now(p)+rules().ticks("mirror_cooldown_ticks"));log(p,"mirror_release","");return true;
        }
        if(!kind.equals("gourd"))return false;
        int cap=rules().ticks("gourd_max_charges"),stored=charges(stack,cap);
        var embers=p.serverLevel().getEntitiesOfClass(LegacyProjectionEntity.class,p.getBoundingBox().inflate(rules().number("gourd_collect_radius")),e->e.kind()==LegacyProjectionEntity.EMBER&&!e.isRemoved()&&clear(p,p.getEyePosition(),e.position().add(0,.1,0))
                &&e.position().subtract(p.getEyePosition()).normalize().dot(p.getLookAngle())>rules().number("gourd_collect_facing_dot"));
        if(!embers.isEmpty()&&stored<cap) {
            embers.sort(Comparator.comparingDouble(p::distanceToSqr));var ember=embers.getFirst();
            LegacyEffects.emit(p,LegacyProjectionEntity.EMBER_COLLECT,ember.position(),p.getEyePosition(),rules().visual("fire_seed_radius"),(int)rules().visual("ray_ticks"));
            ember.discard();setCharges(stack,stored+1);log(p,"ember_collected",Integer.toString(stored+1));
        } else if(stored>0) {
            Vec3 end=clipped(p,p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(rules().number("gourd_range"))));
            castRay(p,rules().number("gourd_range"),DamageType.FIRE,rules().number("gourd_damage"));
            particles(p.serverLevel(),end,ParticleTypes.FLAME,18,.3);setCharges(stack,stored-1);log(p,"gourd_release",Integer.toString(stored-1));
        } else {message(p,"empty");return false;}
        cds.put(kind,now(p)+rules().ticks("gourd_cooldown_ticks"));return true;
    }
    private static void castRay(ServerPlayer p,double range,DamageType type,double base) {
        Vec3 from=p.getEyePosition(),to=clipped(p,from,from.add(p.getLookAngle().scale(range)));
        double width=type==DamageType.FIRE?rules().number("gourd_radius"):rules().number("mirror_ray_radius");
        Set<Integer> seen=new HashSet<>();int n=0;
        for(var e:p.level().getEntities(p,new AABB(from,to).inflate(width),e->legal(p,e)).stream().sorted(Comparator.comparingDouble(e->e.distanceToSqr(from))).toList()) {
            if(!e.getBoundingBox().inflate(width).clip(from,to).isPresent()||!clear(p,from,e.getBoundingBox().getCenter())||!seen.add(CombatTargets.hitId(e)))continue;
            hit(p,e,type,ElementalEffects.base(p,type),base/ElementalRules.number("base_damage",.1,100));if(++n>=rules().ticks("max_targets"))break;
        }
        LegacyEffects.emit(p,type==DamageType.FIRE?LegacyProjectionEntity.FIRE_JET:LegacyProjectionEntity.WATER_RAY,from,to,width,(int)rules().visual("ray_ticks"));
    }
    private static void tickMirrors(MinecraftServer server) {
        for(var entry:List.copyOf(MIRRORS.entrySet())) {
            var p=server.getPlayerList().getPlayer(entry.getKey());var m=entry.getValue();
            if(p==null||!alive(p)||now(p)>=m.until()||p.getMainHandItem()!=m.stack()||p.getInventory().selected!=m.slot()||!p.level().dimension().location().equals(m.dimension())){if(m.view()!=null)m.view().discard();MIRRORS.remove(entry.getKey());continue;}
            if(m.view()!=null&&!m.view().isRemoved()) {m.view().setPos(p.getEyePosition().add(p.getLookAngle()));m.view().visual(p.getLookAngle(),rules().visual("mirror_capture_radius"));}
            for(var projectile:p.level().getEntitiesOfClass(Projectile.class,p.getBoundingBox().inflate(rules().number("mirror_capture_radius")),e->e instanceof AbstractArrow||e instanceof SmallFireball)) {
                var owner=projectile.getOwner();Vec3 velocity=projectile.getDeltaMovement();
                if(owner==p||owner instanceof LivingEntity living&&!CombatActors.mayHarm(living,p)||velocity.lengthSqr()<.001||velocity.length()>rules().number("mirror_projectile_speed_limit"))continue;
                if(projectile.position().subtract(p.getEyePosition()).normalize().dot(p.getLookAngle())<rules().number("mirror_capture_facing_dot"))continue;
                if(velocity.dot(p.getEyePosition().subtract(projectile.position()))<=0||!clear(p,p.getEyePosition(),projectile.position()))continue;
                Vec3 at=p.getEyePosition().add(p.getLookAngle());
                LegacyEffects.emit(p,LegacyProjectionEntity.MIRROR_HIT,at,at.add(p.getLookAngle()),rules().visual("mirror_capture_radius"),(int)rules().visual("flash_ticks"));
                projectile.discard();setCharges(m.stack(),1);if(m.view()!=null)m.view().discard();MIRRORS.remove(entry.getKey());message(p,"mirror_caught");log(p,"mirror_capture",net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).toString());break;
            }
        }
    }
    public static String status(ServerPlayer p) {
        var out=new com.google.gson.JsonObject();out.addProperty("player",p.getGameProfile().getName());
        out.addProperty("weapon_away",weaponAway(p));out.addProperty("mirror",MIRRORS.containsKey(p.getUUID()));out.addProperty("charge",charges(p.getMainHandItem(),rules().ticks("gourd_max_charges")));
        var active=new com.google.gson.JsonArray();
        for(var s:states(p)){var row=new com.google.gson.JsonObject();row.addProperty("mode",s.art.mode());row.addProperty("entity",s.view.getId());row.addProperty("returning",s.returning);row.addProperty("remaining",Math.max(0,s.until-now(p)));
            var pos=new com.google.gson.JsonArray();pos.add(s.view.getX());pos.add(s.view.getY());pos.add(s.view.getZ());row.add("pos",pos);active.add(row);}
        out.add("active",active);
        var visuals=new com.google.gson.JsonArray();
        for(var e:p.serverLevel().getEntitiesOfClass(LegacyProjectionEntity.class,p.getBoundingBox().inflate(rules().visual("effect_range")),e->e.ownerId()==p.getId()&&e.kind()>=LegacyProjectionEntity.FIRE_BURST)) {
            var row=new com.google.gson.JsonObject();row.addProperty("kind",e.kind());row.addProperty("radius",e.radius());row.addProperty("age",e.age(0));row.addProperty("duration",e.duration());visuals.add(row);
        }
        out.add("visuals",visuals);return "LEGACY_STATUS "+out;
    }
    public static void message(ServerPlayer p,String key) {p.displayClientMessage(Component.translatable("legacy.myvillage."+key),true);}
    private static void log(ServerPlayer p,String result,String detail) {LoggerFactory.getLogger(LegacyRuntime.class).info("LEGACY player={} result={} detail={}",p.getGameProfile().getName(),result,detail);}
}
