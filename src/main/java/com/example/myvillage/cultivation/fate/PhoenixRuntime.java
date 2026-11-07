package com.example.myvillage.cultivation.fate;

import com.example.myvillage.combat.foundation.HealingMath;
import com.example.myvillage.combat.foundation.HealingRuntime;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatReactionService;
import com.example.myvillage.combat.runtime.StaggerResistant;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.combat.session.CombatStopReason;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import com.example.myvillage.mixin.PhoenixPlayerListAccess;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 火种由不可变天命状态拥有；余烬保留真实玩家承伤，离线也不改用第二套减伤/死亡算法。 */
public final class PhoenixRuntime {
    private static final ResourceLocation HEAL_SOURCE=ResourceLocation.parse("myvillage:fate/phoenix_body");
    private static final TicketType<UUID> TICKET=TicketType.create("myvillage_phoenix_ember",Comparator.comparing(UUID::toString));
    private static final String REFORMED_UNTIL="myvillage:phoenix_reformed_until";
    private static final Map<UUID,Long> NEXT_HEAL=new HashMap<>();
    private static MinecraftServer running;
    private static boolean stopping;
    private PhoenixRuntime() { }
    public static void register() {
        SpiritBodyRules.get();
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,PhoenixRuntime::incoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,PhoenixRuntime::damaged);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,PhoenixRuntime::healing);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,PhoenixRuntime::death);
        NeoForge.EVENT_BUS.addListener((LivingKnockBackEvent e)->{if(e.getEntity() instanceof ServerPlayer p&&active(p))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent e)->{if(active(e.getEntity()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e)->{if(active(e.getEntity()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e)->{if(active(e.getEntity()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem e)->{if(active(e.getEntity()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e)->{if(active(e.getEntity()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific e)->{if(active(e.getEntity()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((LivingEntityUseItemEvent.Start e)->{if(e.getEntity() instanceof ServerPlayer p&&active(p))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((EntityMountEvent e)->{if(e.isMounting()&&e.getEntityMounting() instanceof ServerPlayer p&&active(p))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener(PhoenixRuntime::tick);
        NeoForge.EVENT_BUS.addListener(PhoenixRuntime::login);
        NeoForge.EVENT_BUS.addListener(PhoenixRuntime::tracking);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,PhoenixRuntime::started);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,PhoenixRuntime::stopping);
    }
    public static boolean enabled(ServerPlayer player){return FateService.effect(player,"phoenix")>0;}
    public static boolean active(Entity entity) {
        return running!=null && entity instanceof ServerPlayer player && running==player.getServer()
                && PhoenixEmbersData.get(running).embers.containsKey(player.getUUID());
    }
    public static PhoenixEmberClock clock(ServerPlayer player) {
        if(!active(player))return null;return PhoenixEmbersData.get(player.getServer()).embers.get(player.getUUID()).clock;
    }
    public static void refresh(ServerPlayer player) {
        if(!enabled(player))return;
        var state=FateService.state(player);var initialized=state.phoenix().initialize(SpiritBodyRules.get().phoenix());
        if(!initialized.equals(state.phoenix()))CultivationService.replaceFates(player,state.withPhoenix(initialized),false);
    }
    /** 只在原版图腾返回失败后调用；原致命伤已经通过全部减免并冻结了主伤害回执。 */
    public static boolean tryBegin(ServerPlayer player,DamageSource source) {
        if(player.level().isClientSide()||player.getServer()==null||!player.getServer().isSameThread()||player.getHealth()>0||active(player)||player.isRemoved()||player.isSpectator()||player.isCreative()
                ||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)||!enabled(player))return false;
        var data=PhoenixEmbersData.get(player.getServer());var rules=SpiritBodyRules.get().phoenix();
        var fate=FateService.state(player);var fire=fate.phoenix();
        if(!fire.ready(rules)||data.embers.size()>=rules.maximumOfflineEmbers())return false;
        var clock=PhoenixEmberClock.begin(player.getMaxHealth(),fire,rules);
        var e=new PhoenixEmbersData.Ember(player.getUUID(),player.getGameProfile().getName(),player.level().dimension().location().toString(),
                clock,player.saveWithoutId(new net.minecraft.nbt.CompoundTag()),cause(source),source.getEntity()==null?null:source.getEntity().getUUID());
        e.player=player;e.source=source;data.embers.put(e.owner,e);
        CultivationService.replaceFates(player,fate.withPhoenix(fire.emptyReserve()),false);
        player.setHealth(HealingMath.floorFloat(clock.durability()));
        stopActions(player);clearOldInjury(player);player.setForcedPose(Pose.SWIMMING);hold(player);
        ticket(e);e.capture();data.setDirty();broadcastForm(player,true);
        for(var mob:player.serverLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(rules.pushRadius()))) {
            if(!(mob instanceof net.minecraft.world.entity.monster.Enemy)&&mob.getTarget()!=player)continue;
            if(!CombatActors.mayHarm(mob,player)||StaggerResistant.resists(mob)||CombatReactionService.isControlImmune(mob))continue;
            var away=mob.position().subtract(player.position());
            mob.knockback(rules.pushStrength(),-away.x,-away.z);
        }
        SpiritBodyRuntime.sync(player);return true;
    }
    private static void stopActions(ServerPlayer player) {
        player.stopUsingItem();player.closeContainer();player.stopRiding();player.stopFallFlying();
        player.setSprinting(false);player.setJumping(false);
        CombatSessionManager.interrupt(player,CombatStopReason.DISALLOWED,false);
        MeditationManager.requestStop(player,MeditationStopReason.DAMAGED);
    }
    private static void clearOldInjury(ServerPlayer player) {
        player.clearFire();player.setTicksFrozen(0);
        player.removeEffect(MobEffects.POISON);player.removeEffect(MobEffects.WITHER);
    }
    private static void hold(ServerPlayer player) {
        player.setSprinting(false);player.setJumping(false);player.fallDistance=0;
        var delta=player.getDeltaMovement();player.setDeltaMovement(0,Math.min(0,delta.y),0);
    }
    private static void incoming(LivingIncomingDamageEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var clock=clock(player);long now=player.getServer().overworld().getGameTime();
        if(clock!=null&&(clock.protectionRemaining()>0||event.getSource().is(DamageTypes.DROWN)
                ||event.getSource().is(DamageTypes.ON_FIRE)||event.getSource().is(DamageTypes.WITHER)))event.setCanceled(true);
        else if(now<player.getPersistentData().getLong(REFORMED_UNTIL))event.setCanceled(true);
    }
    private static void damaged(LivingDamageEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||!active(player))return;
        var data=PhoenixEmbersData.get(player.getServer());var e=data.embers.get(player.getUUID());
        e.clock=e.clock.health(player.getHealth());
        if(event.getNewDamage()>0){e.source=event.getSource();e.causeType=cause(e.source);e.attacker=e.source.getEntity()==null?null:e.source.getEntity().getUUID();}
        data.setDirty();
    }
    private static void healing(LivingHealEvent event){if(active(event.getEntity()))event.setCanceled(true);}
    private static void death(LivingDeathEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var state=FateService.state(player);
        if(state.phoenix().initialized())CultivationService.replaceFates(player,state.withPhoenix(state.phoenix().emptyReserve()),false);
        if(active(player)){var data=PhoenixEmbersData.get(player.getServer());var e=data.embers.get(player.getUUID());e.dead=true;e.clock=e.clock.health(0);data.setDirty();}
        NEXT_HEAL.remove(player.getUUID());
    }
    private static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();if(stopping||running!=server)return;
        var data=PhoenixEmbersData.get(server);var rules=SpiritBodyRules.get().phoenix();
        for(var e:new ArrayList<>(data.embers.values())) {
            if(e.player==null){restore(server,e);if(e.player==null)continue;}
            var player=e.player;
            if(e.dead||player.getHealth()<=0){finish(e,false);continue;}
            if(e.offline)player.doTick();
            if(e.dead||player.getHealth()<=0){finish(e,false);continue;}
            hold(player);player.setForcedPose(Pose.SWIMMING);clearOldInjury(player);
            e.clock=e.clock.health(player.getHealth()).tick(player.isInWater(),rules);
            player.setHealth(HealingMath.floorFloat(e.clock.durability()));ticket(e);
            if(e.clock.failed()) {
                player.setHealth(0);player.die(e.source==null?player.damageSources().drown():e.source);
                if(e.dead)finish(e,false);
            } else if(e.clock.complete())finish(e,true);
            else {
                if(e.clock.remaining()%rules.particleIntervalTicks()==0)
                    player.serverLevel().sendParticles(ParticleTypes.FLAME,player.getX(),player.getY()+0.15,player.getZ(),rules.particlesPerPulse(),0.18,0.08,0.18,0.01);
                e.capture();data.setDirty();
            }
        }
        long now=server.overworld().getGameTime();
        for(var player:server.getPlayerList().getPlayers()) {
            if(active(player)||!player.isAlive()||player.isRemoved()||player.isCreative()||player.isSpectator()||!enabled(player))continue;
            refresh(player);var state=FateService.state(player);
            var fire=state.phoenix().recover((warm(player,rules.warmRadius())?rules.warmPerSecond():rules.naturalPerSecond())/20,rules);
            CultivationService.replaceFates(player,state.withPhoenix(fire),false);
            long next=NEXT_HEAL.computeIfAbsent(player.getUUID(),ignored->now+20);
            if(now<next)continue;NEXT_HEAL.put(player.getUUID(),now+20);
            double budget=fire.healingBudget(rules),raw=player.getMaxHealth()*rules.healingHealthFractionPerSeed()*budget;
            double expected=raw*HealingRuntime.multiplier(player);
            if(raw<=0||expected<=0||player.getHealth()>=player.getMaxHealth())continue;
            double actual=HealingRuntime.heal(player,HealingMath.floorFloat(raw),HEAL_SOURCE);
            if(actual>0){var latest=FateService.state(player);double spent=Math.min(budget,budget*actual/expected);
                CultivationService.replaceFates(player,latest.withPhoenix(latest.phoenix().spendHealing(spent,rules)),false);}
        }
    }
    private static boolean warm(ServerPlayer player,int radius) {
        var level=player.serverLevel();var center=player.blockPosition();
        for(var pos:BlockPos.betweenClosed(center.offset(-radius,-radius,-radius),center.offset(radius,radius,radius))) {
            if(pos.distSqr(center)>radius*radius||!level.hasChunkAt(pos))continue;
            var block=level.getBlockState(pos);
            boolean heat=block.is(Blocks.FIRE)||block.is(Blocks.SOUL_FIRE)||block.is(Blocks.LAVA)
                    ||block.getBlock() instanceof CampfireBlock&&block.getValue(CampfireBlock.LIT);
            if(!heat)continue;
            Vec3 point=Vec3.atCenterOf(pos);
            var hit=level.clip(new ClipContext(player.getEyePosition(),point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            if(hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(pos))return true;
        }
        return false;
    }
    private static void finish(PhoenixEmbersData.Ember e,boolean success) {
        var player=e.player;var server=player.getServer();var data=PhoenixEmbersData.get(server);
        if(success) {
            clearOldInjury(player);player.setHealth(HealingMath.floorFloat(Math.min(player.getMaxHealth(),e.clock.maximumHealth()*SpiritBodyRules.get().phoenix().rebirthHealthFraction())));
            player.getPersistentData().putLong(REFORMED_UNTIL,server.overworld().getGameTime()+SpiritBodyRules.get().phoenix().rebirthProtectionTicks());
        }
        player.setForcedPose(null);broadcastForm(player,false);releaseTicket(e);
        data.embers.remove(e.owner);data.setDirty();
        if(e.offline) {
            ((PhoenixPlayerListAccess)server.getPlayerList()).myvillage$savePhoenixPlayer(player);
            player.serverLevel().removePlayerImmediately(player,Entity.RemovalReason.UNLOADED_WITH_PLAYER);
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(java.util.List.of(e.owner)));
        } else SpiritBodyRuntime.sync(player);
    }
    /** PlayerList 已完成普通退出清理后，临时保留同一个玩家实体；不保留在线连接或技能权限。 */
    public static void disconnected(ServerPlayer player) {
        NEXT_HEAL.remove(player.getUUID());if(!active(player)||stopping)return;
        var data=PhoenixEmbersData.get(player.getServer());var e=data.embers.get(player.getUUID());
        e.player=player;e.offline=true;e.capture();player.revive();
        player.getServer().getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(java.util.List.of(player)));
        player.serverLevel().addNewPlayer(player);ticket(e);broadcastForm(player,true);data.setDirty();
    }
    /** 在原生读取玩家文件之前，把离线实体的最新状态保存并撤出，避免同 UUID 双实体。 */
    public static void beforeLogin(ServerPlayer joining) {
        if(running!=joining.getServer())return;
        var data=PhoenixEmbersData.get(running);var e=data.embers.get(joining.getUUID());
        if(e==null)return;
        if(e.player==null)restore(running,e);
        if(e.player!=null) {
            e.capture();((PhoenixPlayerListAccess)running.getPlayerList()).myvillage$savePhoenixPlayer(e.player);
            releaseTicket(e);e.player.serverLevel().removePlayerImmediately(e.player,Entity.RemovalReason.UNLOADED_WITH_PLAYER);
            e.player=null;e.offline=false;data.setDirty();
        }
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var data=PhoenixEmbersData.get(player.getServer());var e=data.embers.get(player.getUUID());
        if(e!=null){e.player=player;e.offline=false;((com.example.myvillage.mixin.PhoenixServerPlayerAccess)player).myvillage$setEmberSpawnProtection(0);player.setHealth(HealingMath.floorFloat(e.clock.durability()));player.setForcedPose(Pose.SWIMMING);ticket(e);broadcastForm(player,true);data.setDirty();}
        else refresh(player);
        SpiritBodyRuntime.sync(player);
    }
    private static void tracking(PlayerEvent.StartTracking event) {
        if(event.getEntity() instanceof ServerPlayer watcher&&event.getTarget() instanceof ServerPlayer player&&active(player))
            PacketDistributor.sendToPlayer(watcher,new PhoenixFormPayload(player.getId(),player.getUUID(),true));
    }
    private static void started(ServerStartedEvent event) {
        running=event.getServer();stopping=false;NEXT_HEAL.clear();
        for(var e:new ArrayList<>(PhoenixEmbersData.get(running).embers.values()))restore(running,e);
    }
    private static void restore(MinecraftServer server,PhoenixEmbersData.Ember e) {
        var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(e.dimension)));if(level==null)return;
        var player=new ServerPlayer(server,level,new GameProfile(e.owner,e.name),ClientInformation.createDefault());
        player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(player.getGameProfile(),false));
        player.load(e.snapshot.copy());((com.example.myvillage.mixin.PhoenixServerPlayerAccess)player).myvillage$setEmberSpawnProtection(0);player.setServerLevel(level);player.setHealth(HealingMath.floorFloat(e.clock.durability()));player.setForcedPose(Pose.SWIMMING);
        e.player=player;e.offline=true;
        var holder=server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.parse(e.causeType))).orElseThrow();
        Entity attacker=null;if(e.attacker!=null)for(var world:server.getAllLevels()){attacker=world.getEntity(e.attacker);if(attacker!=null)break;}
        e.source=new DamageSource(holder,attacker);
        if(attacker instanceof net.minecraft.world.entity.LivingEntity living)player.setLastHurtByMob(living);
        if(attacker instanceof net.minecraft.world.entity.player.Player p)player.setLastHurtByPlayer(p);
        ticket(e);server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(java.util.List.of(player)));
        level.addNewPlayer(player);broadcastForm(player,true);
    }
    private static void stopping(ServerStoppingEvent event) {
        stopping=true;var data=PhoenixEmbersData.get(event.getServer());
        for(var e:data.embers.values()){e.capture();if(e.player!=null&&e.offline)((PhoenixPlayerListAccess)event.getServer().getPlayerList()).myvillage$savePhoenixPlayer(e.player);releaseTicket(e);}
        data.setDirty();NEXT_HEAL.clear();
    }
    private static void ticket(PhoenixEmbersData.Ember e) {
        var pos=new ChunkPos(e.player.blockPosition());if(pos.equals(e.ticket))return;
        releaseTicket(e);e.player.serverLevel().getChunkSource().addRegionTicket(TICKET,pos,2,e.owner);e.ticket=pos;
    }
    private static void releaseTicket(PhoenixEmbersData.Ember e) {
        if(e.ticket!=null&&e.player!=null)e.player.serverLevel().getChunkSource().removeRegionTicket(TICKET,e.ticket,2,e.owner);e.ticket=null;
    }
    private static void broadcastForm(ServerPlayer player,boolean active) {
        var payload=new PhoenixFormPayload(player.getId(),player.getUUID(),active);
        for(var viewer:player.getServer().getPlayerList().getPlayers())if(viewer.level()==player.level())PacketDistributor.sendToPlayer(viewer,payload);
    }
    private static String cause(DamageSource source){return source.typeHolder().unwrapKey().map(k->k.location().toString()).orElse("minecraft:generic");}
    /** 每个包只调用一次；同 tick 共用速度预算，总移动也有上限，保留原生碰撞。 */
    public static Vec3 constrainMovement(ServerPlayer player,Vec3 requested) {
        if(!active(player))return requested;
        var e=PhoenixEmbersData.get(player.getServer()).embers.get(player.getUUID());var rules=SpiritBodyRules.get().phoenix();
        long now=player.getServer().overworld().getGameTime();if(e.movementTick!=now){e.movementTick=now;e.movementThisTick=0;}
        var offset=requested.subtract(player.position());double distance=offset.horizontalDistance();
        double budget=Math.max(0,Math.min(rules.movementBlocksPerSecond()/20-e.movementThisTick,rules.maximumMovement()-e.clock.movementUsed()));
        double used=Math.min(distance,budget),factor=distance<=0?0:used/distance;
        e.movementThisTick+=used;e.clock=e.clock.move(used);PhoenixEmbersData.get(player.getServer()).setDirty();
        return new Vec3(player.getX()+offset.x*factor,Math.min(player.getY(),requested.y),player.getZ()+offset.z*factor);
    }
}
