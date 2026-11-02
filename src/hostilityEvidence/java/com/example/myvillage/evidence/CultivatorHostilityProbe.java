package com.example.myvillage.evidence;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.npc.CultivatorEntity;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.entity.undead.UndeadEntities;
import com.example.myvillage.entity.undead.UndeadRules;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** No assigned targets or injected hits: observe acquisition, real damage and reactive NPC intent. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class CultivatorHostilityProbe {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Path RESULT=Path.of("cultivator-hostility-result.json");
    private static final List<Spec> SPECS=new ArrayList<>();
    private static final List<Case> CASES=new ArrayList<>();
    private static final List<String> FAILURES=new ArrayList<>();
    private static MinecraftServer server;
    private static int tick,waitTicks;
    private static boolean spawned,done;

    @SubscribeEvent public static void start(ServerStartedEvent event) throws Exception {
        server=event.getServer();Files.deleteIfExists(RESULT);var level=server.overworld();
        server.setDifficulty(Difficulty.NORMAL,true);level.setDayTime(18000);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
        for(var v:UndeadRules.get().variants())add(v.id(),UndeadEntities.TYPES.get(v.id()).get(),"combat");
        add("zombie",EntityType.ZOMBIE,"combat");add("husk",EntityType.HUSK,"combat");
        add("drowned",EntityType.DROWNED,"combat");add("zombie_villager",EntityType.ZOMBIE_VILLAGER,"combat");
        add("vindicator",EntityType.VINDICATOR,"combat");add("pillager",EntityType.PILLAGER,"combat");
        add("evoker",EntityType.EVOKER,"acquire");add("illusioner",EntityType.ILLUSIONER,"acquire");
        add("ravager",EntityType.RAVAGER,"combat");
        add("zombie_original_villager",EntityType.ZOMBIE,"villager");
        add("zombie_nearest_cultivator",EntityType.ZOMBIE,"nearest_cultivator");
        add("zombie_nearest_villager",EntityType.ZOMBIE,"nearest_villager");
        add("skeleton_unchanged",EntityType.SKELETON,"negative");
        add("corpse_allied",UndeadEntities.TYPES.get("wandering_corpse").get(),"allied");
        add("zombie_allied",EntityType.ZOMBIE,"allied");
        add("archer_wall",UndeadEntities.TYPES.get("bone_archer").get(),"wall");
        add("archer_far",UndeadEntities.TYPES.get("bone_archer").get(),"far");
        add("zombie_retarget",EntityType.ZOMBIE,"retarget");
    }
    private static void add(String id,EntityType<? extends Mob> type,String mode) {
        int i=SPECS.size(),x=512+(i%8)*96,z=512+(i/8)*96;
        var s=new Spec(id,type,mode,x,z);SPECS.add(s);var level=server.overworld();
        for(int dx=0;dx<=20;dx++)for(int dz=0;dz<=20;dz++) {
            var p=new BlockPos(x+dx,99,z+dz);level.setChunkForced(p.getX()>>4,p.getZ()>>4,true);
            level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());
            if(dx==0||dx==20||dz==0||dz==20)for(int y=100;y<104;y++)level.setBlockAndUpdate(new BlockPos(x+dx,y,z+dz),Blocks.STONE.defaultBlockState());
        }
        if(mode.equals("far"))for(int dx=3;dx<=13;dx++)for(int dz=75;dz<=85;dz++) {
            var p=new BlockPos(x+dx,99,z+dz);level.setChunkForced(p.getX()>>4,p.getZ()>>4,true);level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());
        }
        if(mode.equals("wall"))for(int dx=0;dx<=20;dx++)for(int y=100;y<108;y++)level.setBlockAndUpdate(new BlockPos(x+dx,y,z+8),Blocks.STONE.defaultBlockState());
    }
    private static boolean ready(BlockPos p) {
        var level=server.overworld();
        return level.areEntitiesLoaded(ChunkPos.asLong(p.getX()>>4,p.getZ()>>4))
                &&level.isPositionEntityTicking(p)&&level.getBlockState(p.below()).is(Blocks.STONE);
    }
    private static Mob create(EntityType<? extends Mob> type,double x,double z) {
        var level=server.overworld();var mob=type.create(level);if(mob==null)throw new IllegalStateException("create "+type);
        mob.moveTo(x,100,z,0,0);
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob,level,level.getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.COMMAND,null);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);mob.setHealth(mob.getMaxHealth());
        if(!level.addFreshEntity(mob))throw new IllegalStateException("fixture rejected "+type);
        return mob;
    }
    private static void spawnFixtures() {
        for(var s:SPECS) {
            Mob source=create(s.type,s.x+8.5,s.z+4.5);
            boolean villager=s.mode.equals("villager")||s.mode.equals("nearest_villager");
            Mob victim=create(villager?EntityType.VILLAGER:ModEntities.CULTIVATOR.get(),s.x+8.5,s.z+(s.mode.equals("far")?80.5:10.5));
            if(villager||negative(s.mode))victim.setNoAi(true);
            Mob other=null;
            if(s.mode.startsWith("nearest_")) {
                other=create(villager?ModEntities.CULTIVATOR.get():EntityType.VILLAGER,s.x+8.5,s.z+17.5);other.setNoAi(true);victim.setNoAi(true);
            }
            if(s.mode.equals("allied")) {
                var board=server.getScoreboard();PlayerTeam team=board.addPlayerTeam("hostility_"+CASES.size());
                board.addPlayerToTeam(source.getScoreboardName(),team);board.addPlayerToTeam(victim.getScoreboardName(),team);
            }
            if(source.getTarget()!=null||victim.getTarget()!=null)throw new IllegalStateException("fixture already has a target");
            CASES.add(new Case(s,source,victim,other));
        }
        System.out.println("CULTIVATOR_HOSTILITY started "+CASES.size()+" natural acquisition fixtures; no setTarget or injected damage");
    }
    private static boolean negative(String mode) {return Set.of("negative","allied","wall","far").contains(mode);}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) throws Exception {
        if(server==null||done)return;
        if(!spawned) {
            waitTicks++;boolean all=true;
            for(var s:SPECS)all&=ready(new BlockPos(s.x+8,100,s.z+4))&&ready(new BlockPos(s.x+8,100,s.z+(s.mode.equals("far")?80:10)))&&ready(new BlockPos(s.x+8,100,s.z+17));
            if(!all){if(waitTicks>=200){FAILURES.add("fixture chunks not entity-ready");finish();}return;}
            spawnFixtures();spawned=true;return;
        }
        tick++;
        for(var c:CASES) {
            if(server.overworld().getEntity(c.source.getUUID())!=c.source||server.overworld().getEntity(c.victim.getUUID())!=c.victim){FAILURES.add(c.spec.id+" fixture reference replaced");finish();return;}
            if(c.source.getTarget()!=null) {
                if(c.source.getTarget()==c.victim){if(c.firstTarget<0)c.firstTarget=tick;c.acquired=true;if(c.replaced)c.reacquired=true;}
                else c.otherTargets.add(BuiltInRegistries.ENTITY_TYPE.getKey(c.source.getTarget().getType()).toString());
            }
            float hp=c.victim.getHealth();
            if(hp<c.lastHealth){c.loss+=c.lastHealth-hp;c.damageEvents++;if(c.firstDamage<0)c.firstDamage=tick;}
            c.lastHealth=hp;
            if(c.victim instanceof NpcEntity npc&&npc.getTarget()!=null) {
                c.reactiveTargets.add(BuiltInRegistries.ENTITY_TYPE.getKey(npc.getTarget().getType()).toString());
                if(npc.getTarget()==c.source)c.retaliated=true;
            }
            if(c.spec.mode.equals("retarget")&&tick==200) {
                if(!c.acquired||c.loss<=0)FAILURES.add(c.spec.id+" did not fight first victim");
                double x=c.victim.getX(),z=c.victim.getZ();c.victim.discard();
                c.victim=create(ModEntities.CULTIVATOR.get(),x,z);c.lastHealth=c.victim.getHealth();c.replaced=true;
            }
        }
        if(tick>=400)finish();
    }
    private static void finish() throws Exception {
        var rows=new JsonArray();
        for(var c:CASES) {
            if(negative(c.spec.mode)) {
                if(c.acquired||c.loss>0)FAILURES.add(c.spec.id+" acquired or damaged a forbidden target");
            } else {
                if(!c.acquired)FAILURES.add(c.spec.id+" never acquired expected victim");
                if(Set.of("combat","villager","retarget").contains(c.spec.mode)&&c.loss<=0)FAILURES.add(c.spec.id+" no real AI damage");
                if(c.spec.mode.equals("combat")&&!c.retaliated)FAILURES.add(c.spec.id+" no NPC reactive target");
                if(c.spec.mode.equals("retarget")&&!c.reacquired)FAILURES.add(c.spec.id+" did not acquire replacement UUID");
            }
            var row=new JsonObject();row.addProperty("id",c.spec.id);row.addProperty("mode",c.spec.mode);row.addProperty("first_target_tick",c.firstTarget);row.addProperty("first_damage_tick",c.firstDamage);row.addProperty("health_loss",c.loss);row.addProperty("damage_events",c.damageEvents);row.addProperty("npc_retaliated",c.retaliated);row.addProperty("reacquired_replacement",c.reacquired);row.add("other_targets",JSON.toJsonTree(c.otherTargets));row.add("npc_reactive_targets",JSON.toJsonTree(c.reactiveTargets));rows.add(row);
            c.source.discard();c.victim.discard();if(c.other!=null)c.other.discard();
        }
        var result=new JsonObject();result.addProperty("phase","natural_acquisition");result.addProperty("status",FAILURES.isEmpty()?"passed":"failed");result.addProperty("selected",SPECS.size());result.addProperty("ticks",tick);result.addProperty("fixture_wait_ticks",waitTicks);result.addProperty("manual_target_assignments",0);result.addProperty("injected_damage",0);result.add("completed",rows);result.add("failed",JSON.toJsonTree(FAILURES));result.addProperty("unrun",SPECS.size()-CASES.size());Files.writeString(RESULT,JSON.toJson(result));done=true;server.halt(false);
    }
    private record Spec(String id,EntityType<? extends Mob> type,String mode,int x,int z) {}
    private static final class Case {
        final Spec spec;final Mob source;Mob victim;final Mob other;
        boolean acquired,retaliated,replaced,reacquired;float lastHealth,loss;int firstTarget=-1,firstDamage=-1,damageEvents;
        final Set<String> otherTargets=new LinkedHashSet<>(),reactiveTargets=new LinkedHashSet<>();
        Case(Spec s,Mob source,Mob victim,Mob other){spec=s;this.source=source;this.victim=victim;this.other=other;lastHealth=victim.getHealth();}
    }
}
