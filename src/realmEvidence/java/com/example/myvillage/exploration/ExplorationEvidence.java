package com.example.myvillage.exploration;

import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.life.NpcLifeSites;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.example.myvillage.wilderness.runtime.VanillaMonsterRealms;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** Opt-in native generation, spawning and container lifecycle fixtures. Never packaged in the jar. */
public final class ExplorationEvidence {
    private static final Path OUT = Path.of("../reports/exploration-native");
    private static List<Map<String,Object>> checks;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static void check(String name, boolean pass, Object detail) {
        checks.add(Map.of("name",name,"passed",pass,"detail",String.valueOf(detail)));
        if(!pass)throw new AssertionError(name+": "+detail);
    }
    private static RealmKey birth(Mob mob) {
        var nbt=mob.getPersistentData().getCompound(VanillaMonsterRealms.BIRTH);
        return new RealmKey(nbt.getString("realm"),nbt.getInt("stage"));
    }
    private static <T extends Mob> T spawn(ServerLevel level,EntityType<T> type,BlockPos pos,MobSpawnType reason) {
        level.getChunk(pos); T mob=type.spawn(level,pos,reason);
        if(mob==null)throw new AssertionError("spawn "+type);
        mob.setNoAi(true);mob.setNoGravity(true);mob.setPersistenceRequired();mob.setInvulnerable(true);return mob;
    }
    private static BlockPos natural(ServerLevel level,String id) {
        var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var key=ResourceKey.create(Registries.STRUCTURE,ResourceLocation.parse(id));
        var holder=registry.getHolderOrThrow(key);
        var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),BlockPos.ZERO,64,false);
        check("native_locate_"+id,found!=null,found);
        var chunk=level.getChunk(found.getFirst().getX()>>4,found.getFirst().getZ()>>4);
        StructureStart start=chunk.getStartForStructure(holder.value());
        check("native_generated_start_"+id,start!=null&&start.isValid(),chunk.getPos());
        BlockPos p=start.getBoundingBox().getCenter();
        // Fixture preparation may load referenced chunks; the product resolver itself must not.
        level.getChunk(p);level.getChunk(start.getChunkPos().x,start.getChunkPos().z);
        var context=ExplorationRuntime.at(level,p,null).orElseThrow();
        check("native_location_"+id,context.location().structures().contains(id),context);
        return p;
    }
    public static void initial(MinecraftServer server,List<Map<String,Object>> output)throws Exception {
        checks=output;Files.createDirectories(OUT);var level=server.overworld();var rules=ExplorationRules.get();
        check("planned_v4_world_active",WorldPlanRuntime.generator(level).isPresent(),level.getChunkSource().getGenerator());
        // Coverage is checked against the actual 1.21.1 registry, not a copied list of remembered names.
        var structures=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<String> covered=new HashSet<>();rules.locations().forEach(l->covered.addAll(l.structures()));
        for(var id:structures.keySet())if(id.getNamespace().equals("minecraft"))check("catalogue_"+id,covered.contains(id),id);
        BlockPos mine=natural(level,"minecraft:mineshaft");
        var zombie=spawn(level,EntityType.ZOMBIE,mine,MobSpawnType.NATURAL);
        var mineContext=ExplorationRuntime.at(level,mine,null).orElseThrow();
        check("native_mine_birth_matches_context",mineContext.encounters().contains(birth(zombie)),zombie.getPersistentData());zombie.discard();
        BlockPos trialPos=natural(level,"minecraft:trial_chambers");
        BlockPos trialBlock=trialPos.above(60);level.setBlockAndUpdate(trialBlock,Blocks.TRIAL_SPAWNER.defaultBlockState());
        var trial=(TrialSpawnerBlockEntity)level.getBlockEntity(trialBlock);
        for(boolean ominous:new boolean[]{false,true}) {
            // Real native spawner controller is passed through NeoForge's actual spawner hook.
            if(ominous)trial.getTrialSpawner().applyOminous(level,trialBlock);
            else trial.getTrialSpawner().removeOminous(level,trialBlock);
            var mob=EntityType.ZOMBIE.create(level);mob.moveTo(trialBlock.getX()+.5,trialBlock.getY()+1,trialBlock.getZ()+.5,0,0);
            EventHooks.finalizeMobSpawnSpawner(mob,level,level.getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.TRIAL_SPAWNER,null,trial.getTrialSpawner(),true);
            level.addFreshEntity(mob);var expected=ExplorationRuntime.at(level,mob.blockPosition(),ominous?"ominous_trial":"trial").orElseThrow();
            check("native_trial_spawner_"+ominous,expected.encounters().contains(birth(mob))
                    &&mob.getPersistentData().getCompound(VanillaMonsterRealms.BIRTH).getString("location").equals(ominous?"ominous_trial":"trial"),mob.getPersistentData());
            mob.discard();
        }
        var nether=server.getLevel(Level.NETHER);BlockPos fortress=natural(nether,"minecraft:fortress");
        var blaze=spawn(nether,EntityType.BLAZE,fortress,MobSpawnType.NATURAL);
        check("native_fortress_birth",rules.location("fortress").encounters().contains(birth(blaze)),blaze.getPersistentData());
        blaze.setHealth(blaze.getMaxHealth()/2);nether.setChunkForced(blaze.getBlockX()>>4,blaze.getBlockZ()>>4,true);
        BlockPos bastion=natural(nether,"minecraft:bastion_remnant");
        var brute=spawn(nether,EntityType.PIGLIN_BRUTE,bastion,MobSpawnType.STRUCTURE);
        check("native_bastion_birth",rules.location("bastion").encounters().contains(birth(brute)),brute.getPersistentData());brute.discard();
        // Moving to another biome/structure never reapplies the assignment or heals.
        var first=blaze.getPersistentData().getCompound(VanillaMonsterRealms.BIRTH).copy();
        blaze.teleportTo(bastion.getX()+.5,bastion.getY(),bastion.getZ()+.5);
        nether.setChunkForced(blaze.getBlockX()>>4,blaze.getBlockZ()>>4,true);
        check("native_nether_travel_keeps_birth",first.equals(blaze.getPersistentData().getCompound(VanillaMonsterRealms.BIRTH)),blaze.getPersistentData());
        var end=server.getLevel(Level.END);var endMob=spawn(end,EntityType.ENDERMAN,new BlockPos(0,100,0),MobSpawnType.NATURAL);
        check("end_v1_not_assigned",!endMob.getPersistentData().contains(VanillaMonsterRealms.BIRTH),endMob.getPersistentData());endMob.discard();
        // A lookup into an absent chunk is a read-only miss. It must not create a ticket/chunk.
        BlockPos absent=new BlockPos(15000000,100,15000000);
        check("unloaded_query_returns_empty",ExplorationRuntime.at(level,absent,null).isEmpty(),absent);
        check("unloaded_query_does_not_load",level.getChunkSource().getChunkNow(absent.getX()>>4,absent.getZ()>>4)==null,absent);
        BlockPos chestPos=new BlockPos(0,200,20);level.getChunk(chestPos);level.setBlockAndUpdate(chestPos,Blocks.CHEST.defaultBlockState());
        var chest=(ChestBlockEntity)level.getBlockEntity(chestPos);
        var tableKey=ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.withDefaultNamespace("chests/simple_dungeon"));
        chest.setLootTable(tableKey,29383L);chest.unpackLootTable(null);
        String chestContents=chest.saveWithoutMetadata(level.registryAccess()).toString();
        int modCount=0,vanillaCount=0;
        for(int i=0;i<chest.getContainerSize();i++){
            var stack=chest.getItem(i);if(stack.isEmpty())continue;
            if(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("myvillage"))modCount++;else vanillaCount++;
        }
        check("native_container_keeps_vanilla_and_adds_cultivation",modCount>0&&vanillaCount>0,chestContents);
        chest.unpackLootTable(null);check("native_container_no_second_reward",chestContents.equals(chest.saveWithoutMetadata(level.registryAccess()).toString()),chestContents);
        level.setChunkForced(chestPos.getX()>>4,chestPos.getZ()>>4,true);
        for(String table:rules.lootTables().keySet()){
            var key=ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.parse(table));
            var lootTable=server.reloadableRegistries().getLootTable(key);
            check("loot_table_exists_"+table,lootTable!=LootTable.EMPTY,table);
            var loot=lootTable.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(chestPos)).create(LootContextParamSets.CHEST),3294L);
            check("loot_appended_"+table,loot.stream().anyMatch(s->BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("myvillage")),loot);
            for(var stack:loot)if(stack.getItem() instanceof com.example.myvillage.item.TechniqueManualItem)
                check("loot_manual_readable",com.example.myvillage.item.TechniqueManualItem.technique(stack,level.registryAccess()).isPresent(),stack);
        }
        var sim=WorldSimRuntime.sim().orElseThrow();var site=NpcLifeSites.yunshan(server,sim);
        for(String id:List.of(rules.guideHome(),rules.guideWork())){
            var place=site.place(id).orElseThrow();
            for(int x=(place.x()-1)>>4;x<=(place.x()+1)>>4;x++)for(int z=(place.z()-1)>>4;z<=(place.z()+1)>>4;z++)level.getChunk(x,z);
        }
        NpcLifeSites.observe(server,sim,site);int guide=sim.staffTownGuide(site.id(),rules.guideHome(),rules.guideWork());
        check("guide_assigned_existing_local_person",guide>0&&sim.life(guide).orElseThrow().role().equals("guide"),guide);
        check("guide_assignment_idempotent",guide==sim.staffTownGuide(site.id(),rules.guideHome(),rules.guideWork()),guide);
        WorldSimSavedData.get(level).setDirty();
        var save=new JsonObject();save.addProperty("nether_uuid",blaze.getUUID().toString());save.addProperty("nether_x",blaze.getBlockX());save.addProperty("nether_z",blaze.getBlockZ());
        save.addProperty("nether_health",blaze.getHealth());save.addProperty("nether_max_health",blaze.getMaxHealth());save.addProperty("nether_birth",first.toString());
        save.addProperty("chest",chestContents);save.addProperty("guide",guide);Files.writeString(OUT.resolve("exploration-restart.json"),GSON.toJson(save));
    }
    public static boolean restart(MinecraftServer server,List<Map<String,Object>> output,boolean finalAttempt)throws Exception {
        checks=output;var saved=JsonParser.parseString(Files.readString(OUT.resolve("exploration-restart.json"))).getAsJsonObject();
        var nether=server.getLevel(Level.NETHER);nether.getChunk(saved.get("nether_x").getAsInt()>>4,saved.get("nether_z").getAsInt()>>4);
        var entity=nether.getEntity(UUID.fromString(saved.get("nether_uuid").getAsString()));
        if(entity==null&&!finalAttempt)return false;
        check("nether_real_restart_loaded",entity instanceof Mob,entity);var mob=(Mob)entity;
        check("nether_real_restart_no_heal",Math.abs(mob.getHealth()-saved.get("nether_health").getAsDouble())<.003,mob.getHealth());
        check("nether_real_restart_max_health",Math.abs(mob.getMaxHealth()-saved.get("nether_max_health").getAsDouble())<.003,mob.getMaxHealth());
        check("nether_real_restart_birth",mob.getPersistentData().getCompound(VanillaMonsterRealms.BIRTH).toString().equals(saved.get("nether_birth").getAsString()),mob.getPersistentData());
        var world=server.overworld();BlockPos chestPos=new BlockPos(0,200,20);world.getChunk(chestPos);
        var chest=(ChestBlockEntity)world.getBlockEntity(chestPos);chest.unpackLootTable(null);
        check("chest_real_restart_no_refill",chest.saveWithoutMetadata(world.registryAccess()).toString().equals(saved.get("chest").getAsString()),chest.saveWithoutMetadata(world.registryAccess()));
        var sim=WorldSimRuntime.sim().orElseThrow();int id=saved.get("guide").getAsInt();
        check("guide_real_restart_same_identity",sim.townGuide("yunshan").orElseThrow()==id&&sim.life(id).orElseThrow().role().equals("guide"),id);
        check("guide_real_restart_one_assignment",sim.lifeResidents().stream().filter(r->r.siteId().equals("yunshan")&&r.role().equals("guide")).count()==1,sim.lifeResidents());
        return true;
    }
}
