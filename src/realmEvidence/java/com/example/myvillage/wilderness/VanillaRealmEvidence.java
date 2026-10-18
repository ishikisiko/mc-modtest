package com.example.myvillage.wilderness;

import com.example.myvillage.region.runtime.RegionPlacement;
import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.runtime.RealmArmorRuntime;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.example.myvillage.wilderness.runtime.VanillaMonsterRealms;
import com.example.myvillage.wilderness.runtime.WildernessRuntime;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Compiled only with -PvanillaRealmEvidence; native server fixtures, not mapped-player evidence. */
@EventBusSubscriber(modid="myvillage")
public final class VanillaRealmEvidence {
    private static final boolean EXPLORATION = "1".equals(System.getenv("MYVILLAGE_EXPLORATION_EVIDENCE"));
    private static final Path OUT = Path.of(EXPLORATION ? "../reports/exploration-native" : "../reports/vanilla-monster-realms");
    private static final List<Map<String,Object>> CHECKS = new ArrayList<>();
    private static int ticks;
    private static boolean finished;
    private static boolean initialDone;
    private static final boolean RESTART_BOOT = Files.exists(OUT.resolve("restart.json"));
    private static List<Mob> splitChildren = List.of();
    private static LivingEntity converted;
    private static final String BIRTH = VanillaMonsterRealms.BIRTH;
    private static void check(String name, boolean pass, Object detail) {
        CHECKS.add(Map.of("name",name,"passed",pass,"detail",String.valueOf(detail)));
        if (!pass) throw new AssertionError(name + ": " + detail);
    }
    private static void near(String name, double actual, double expected) { check(name, Math.abs(actual-expected)<0.003, actual+" expected "+expected); }
    private static double factor(LivingEntity mob) { return RealmArmorRuntime.defenderScale(mob).factor(); }
    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, BlockPos pos, MobSpawnType reason) {
        level.getChunk(pos);
        T mob=type.spawn(level, pos, reason);
        if(mob==null)throw new AssertionError("spawn failed "+type);
        mob.setNoAi(true);mob.setNoGravity(true);mob.setPersistenceRequired();
        mob.clearFire();
        return mob;
    }
    private static Mob target(ServerLevel level, BlockPos pos) {
        Mob mob=spawn(level,EntityType.COW,pos,MobSpawnType.COMMAND);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);mob.setHealth(1024);
        return mob;
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void split(net.neoforged.neoforge.event.entity.living.MobSplitEvent event) {
        splitChildren=List.copyOf(event.getChildren());
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void conversion(net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Post event) {
        converted=event.getOutcome();
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if ("1".equals(System.getenv("MYVILLAGE_EXPLORATION_V2_EVIDENCE"))) return;
        if(finished || ++ticks<40)return;
        MinecraftServer server=event.getServer();
        try {
            Files.createDirectories(OUT);
            server.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            server.overworld().setDayTime(18000);
            if(RESTART_BOOT) {
                var data=JsonParser.parseString(Files.readString(OUT.resolve("restart.json"))).getAsJsonObject();
                UUID uuid=UUID.fromString(data.get("uuid").getAsString());
                server.overworld().getChunk(data.get("x").getAsInt()>>4,data.get("z").getAsInt()>>4);
                var entity=server.overworld().getEntity(uuid);
                if(entity==null && ticks<240)return;
                if(EXPLORATION && !com.example.myvillage.exploration.ExplorationEvidence.restart(server,CHECKS,ticks>=240))return;
                check("real_restart_entity_loaded",entity instanceof Mob,uuid);
                Mob mob=(Mob)entity;
                near("real_restart_health",mob.getHealth(),data.get("health").getAsDouble());
                near("real_restart_max_health",mob.getMaxHealth(),data.get("maxHealth").getAsDouble());
                near("real_restart_attack",mob.getAttributeValue(Attributes.ATTACK_DAMAGE),data.get("attack").getAsDouble());
                check("real_restart_birth_unchanged",mob.getPersistentData().getCompound(BIRTH).toString().equals(data.get("birth").getAsString()),mob.getPersistentData());
                finish(server,"restart",null);
            } else {
                if(!initialDone){run(server);if(EXPLORATION)com.example.myvillage.exploration.ExplorationEvidence.initial(server,CHECKS);initialDone=true;}
                // Allow native entity-section loading/movement and save queues to settle.
                if(ticks>=100)finish(server,"initial",null);
            }
        } catch(Throwable error) { finish(server,RESTART_BOOT?"restart":"initial",error); }
    }
    private static void finish(MinecraftServer server,String phase,Throwable error) {
        finished=true;
        try {
            var result=new LinkedHashMap<String,Object>();result.put("phase",phase);result.put("passed",error==null);
            result.put("checks",CHECKS);result.put("method","native dedicated-server fixtures; no mapped input or encounter-density claim");
            if(error!=null){error.printStackTrace();result.put("error",error.toString());}
            Files.writeString(OUT.resolve(phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        }catch(Exception e){throw new RuntimeException(e);}
        server.halt(false);
    }
    private static void run(MinecraftServer server)throws Exception {
        ServerLevel level=server.overworld();
        var graph=RegionRuntimeService.graph().orElseThrow();
        for(var region:graph.regions()){
            int[] xz=RegionPlacement.worldBlockFromGraph(region.posX(),region.posZ());
            BlockPos p=new BlockPos(xz[0],200,xz[1]);
            String profile=WildernessRuntime.regionAt(level,p).orElseThrow();
            Zombie mob=spawn(level,EntityType.ZOMBIE,p,MobSpawnType.NATURAL);
            CompoundTag birth=mob.getPersistentData().getCompound(BIRTH);
            check("region_range_"+region.id(),WildernessRules.bundled().region(profile).orElseThrow().beast()
                    .contains(new RealmKey(birth.getString("realm"),birth.getInt("stage"))),birth);
            near("region_health_"+region.id(),mob.getMaxHealth(),mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue()*factor(mob));
            near("birth_full_health_"+region.id(),mob.getHealth(),mob.getMaxHealth());mob.discard();
        }
        BlockPos p=new BlockPos(0,200,0);
        for(var type:net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE){
            if(!VanillaMonsterRules.bundled().includes(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(),type.getCategory()==MobCategory.MONSTER))continue;
            @SuppressWarnings("unchecked") var mobType=(EntityType<? extends Mob>)type;
            Mob mob=spawn(level,mobType,p,MobSpawnType.NATURAL);
            check("ordinary_"+type,factor(mob)>1,StatsRuntime.info(mob));mob.discard();
        }
        for(var reason:MobSpawnType.values()){
            Zombie mob=spawn(level,EntityType.ZOMBIE,p,reason);
            check("spawn_reason_"+reason,mob.getPersistentData().contains(BIRTH)==VanillaMonsterRules.bundled().spawnReasons().contains(reason.name()),StatsRuntime.info(mob));
            mob.discard();
        }
        for(var type:List.of(EntityType.COW,EntityType.WARDEN,EntityType.ELDER_GUARDIAN)){
            Mob mob=spawn(level,type,p,MobSpawnType.NATURAL);
            near("excluded_"+type,factor(mob),1);mob.discard();
        }
        Zombie outside=spawn(level,EntityType.ZOMBIE,new BlockPos(12000,200,12000),MobSpawnType.NATURAL);
        if(EXPLORATION)check("planned_outer_birth_assigned",outside.getPersistentData().contains(BIRTH),outside.getPersistentData());
        else near("outside_region_baseline",factor(outside),1);outside.discard();
        Zombie nether=spawn(server.getLevel(Level.NETHER),EntityType.ZOMBIE,p,MobSpawnType.NATURAL);
        check("nether_explicit_birth_range",factor(nether)>1 && nether.getPersistentData().contains(BIRTH),nether.getPersistentData());nether.discard();
        Zombie legacy=spawn(level,EntityType.ZOMBIE,p,MobSpawnType.COMMAND);
        CompoundTag legacySave=new CompoundTag();legacy.saveWithoutId(legacySave);legacy.discard();
        Zombie loaded=EntityType.ZOMBIE.create(level);loaded.load(legacySave);level.addFreshEntity(loaded);
        near("legacy_reload_stays_unassigned",factor(loaded),1);loaded.discard();
        Zombie zombie=spawn(level,EntityType.ZOMBIE,p,MobSpawnType.NATURAL);
        for(var slot:EquipmentSlot.values())zombie.setItemSlot(slot,net.minecraft.world.item.ItemStack.EMPTY);
        Mob victim=target(level,p.offset(2,0,0));float before=victim.getHealth();
        check("native_melee_hit",zombie.doHurtTarget(victim),zombie);
        near("melee_scaled_once",before-victim.getHealth(),zombie.getAttributeValue(Attributes.ATTACK_DAMAGE));victim.discard();
        Skeleton skeleton=spawn(level,EntityType.SKELETON,p,MobSpawnType.NATURAL);
        victim=target(level,p.offset(2,0,0));before=victim.getHealth();
        ImpactArrow arrow=new ImpactArrow(level);arrow.setOwner(skeleton);arrow.setBaseDamage(4);arrow.setDeltaMovement(1,0,0);arrow.hit(victim);
        near("native_arrow_scaled_once",before-victim.getHealth(),4*factor(skeleton));victim.discard();
        // Existing explicitly assigned ruin skeletons use the same non-attribute damage hook.
        Skeleton ruin=spawn(level,EntityType.SKELETON,p,MobSpawnType.COMMAND);StatsRuntime.applyRealm(ruin,"myvillage:qi_refining",0);
        victim=target(level,p.offset(2,0,0));before=victim.getHealth();
        arrow=new ImpactArrow(level);arrow.setOwner(ruin);arrow.setBaseDamage(4);arrow.setDeltaMovement(1,0,0);arrow.hit(victim);
        near("explicit_ruin_arrow_scaled_once",before-victim.getHealth(),4*factor(ruin));victim.discard();ruin.discard();skeleton.discard();
        BlockPos blast=p.offset(80,0,0);
        Creeper baseline=spawn(level,EntityType.CREEPER,blast,MobSpawnType.COMMAND);
        victim=target(level,blast.offset(2,0,0));before=victim.getHealth();
        level.explode(baseline,baseline.getX(),baseline.getY(),baseline.getZ(),3,Level.ExplosionInteraction.NONE);
        float base=before-victim.getHealth();check("native_explosion_baseline_hits",base>0,base);victim.discard();baseline.discard();
        Creeper creeper=spawn(level,EntityType.CREEPER,blast,MobSpawnType.NATURAL);
        victim=target(level,blast.offset(2,0,0));before=victim.getHealth();
        level.explode(creeper,creeper.getX(),creeper.getY(),creeper.getZ(),3,Level.ExplosionInteraction.NONE);
        near("native_explosion_scaled_once",before-victim.getHealth(),base*factor(creeper));victim.discard();creeper.discard();
        // Actual native slime death/split event, followed by vanilla NBT loading for changed size.
        Slime slime=spawn(level,EntityType.SLIME,p.offset(32,0,0),MobSpawnType.NATURAL);slime.setSize(4,true);
        var slimeBirth=slime.getPersistentData().getCompound(BIRTH).copy();var box=slime.getBoundingBox().inflate(6);
        slime.setHealth(0);slime.remove(Entity.RemovalReason.KILLED);
        var children=splitChildren;
        check("native_split_has_children",children.size()>=2,children.size());
        for(Mob child:children){
            check("split_child_added",child.isAddedToLevel(),child.getUUID());
            check("split_inherits_birth",slimeBirth.equals(child.getPersistentData().getCompound(BIRTH)),child.getPersistentData());
            near("split_health_scaled",child.getMaxHealth(),child.getAttribute(Attributes.MAX_HEALTH).getBaseValue()*factor(child));
            child.setHealth(child.getMaxHealth()/2);CompoundTag saved=new CompoundTag();child.saveWithoutId(saved);
            Slime copy=EntityType.SLIME.create(level);copy.load(saved);
            near("slime_nbt_health",copy.getHealth(),child.getHealth());near("slime_nbt_max",copy.getMaxHealth(),child.getMaxHealth());child.discard();
        }
        Zombie converting=spawn(level,EntityType.ZOMBIE,p.offset(48,0,0),MobSpawnType.NATURAL);
        var original=converting.getPersistentData().getCompound(BIRTH).copy();
        var method=Zombie.class.getDeclaredMethod("convertToZombieType",EntityType.class);method.setAccessible(true);method.invoke(converting,EntityType.DROWNED);
        check("native_conversion_exists",converted instanceof Drowned && converted.isAddedToLevel(),converted);
        check("conversion_inherits_birth",original.equals(converted.getPersistentData().getCompound(BIRTH)),converted.getPersistentData());
        near("conversion_health_scaled",converted.getHealth(),converted.getMaxHealth());converted.discard();
        var birth=zombie.getPersistentData().getCompound(BIRTH).copy();
        int[] destination=RegionPlacement.worldBlockFromGraph(graph.regions().stream().filter(r->!r.id().equals("zhongzhou")).findFirst().orElseThrow().posX(),graph.regions().stream().filter(r->!r.id().equals("zhongzhou")).findFirst().orElseThrow().posZ());
        BlockPos dest=new BlockPos(destination[0],200,destination[1]);level.getChunk(dest);zombie.teleportTo(dest.getX()+.5,200,dest.getZ()+.5);
        zombie.setHealth(zombie.getMaxHealth()/2);zombie.setInvulnerable(true);
        check("cross_region_birth_unchanged",birth.equals(zombie.getPersistentData().getCompound(BIRTH)),zombie.getPersistentData());
        level.setChunkForced(dest.getX()>>4,dest.getZ()>>4,true);
        var restart=new LinkedHashMap<String,Object>();restart.put("uuid",zombie.getUUID().toString());restart.put("x",dest.getX());restart.put("z",dest.getZ());
        restart.put("health",zombie.getHealth());restart.put("maxHealth",zombie.getMaxHealth());restart.put("attack",zombie.getAttributeValue(Attributes.ATTACK_DAMAGE));restart.put("birth",birth.toString());
        Files.writeString(OUT.resolve("restart.json"),new GsonBuilder().create().toJson(restart));
    }
    private static final class ImpactArrow extends Arrow {
        ImpactArrow(ServerLevel level){super(EntityType.ARROW,level);}
        void hit(Entity target){onHitEntity(new EntityHitResult(target));}
    }
}
