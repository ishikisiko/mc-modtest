package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.region.runtime.RegionRuntimeState;
import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.WorldPlanExporter;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** No global plan: each generator owns its seed and frozen rules. */
public final class WorldPlanRuntime {
    private static final Logger LOG=LoggerFactory.getLogger(WorldPlanRuntime.class);
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS=DeferredRegister.create(Registries.CHUNK_GENERATOR,MyVillageMod.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> SOURCES=DeferredRegister.create(Registries.BIOME_SOURCE,MyVillageMod.MOD_ID);
    static{
        GENERATORS.register("planned_v1",()->PlannedChunkGenerator.CODEC);
        GENERATORS.register("planned_v2",()->PlannedChunkGenerator.CODEC_V2);
        GENERATORS.register("planned_v3",()->PlannedChunkGenerator.CODEC_V3);
        GENERATORS.register("planned_v4",()->PlannedChunkGenerator.CODEC_V4);
        GENERATORS.register("planned_v5",()->PlannedChunkGenerator.CODEC_V5);
        GENERATORS.register("planned_v6",()->PlannedChunkGenerator.CODEC_V6);
        GENERATORS.register("planned_v7",()->PlannedChunkGenerator.CODEC_V7);
        GENERATORS.register("planned_v8",()->PlannedChunkGenerator.CODEC_V8);
        GENERATORS.register("planned_v9",()->PlannedChunkGenerator.CODEC_V9);
        SOURCES.register("planned_source_v1",()->PlannedBiomeSource.CODEC);
        SOURCES.register("planned_source_v2",()->PlannedBiomeSource.CODEC_V2);
        SOURCES.register("planned_source_v3",()->PlannedBiomeSource.CODEC_V3);
        SOURCES.register("planned_source_v4",()->PlannedBiomeSource.CODEC_V4);
        SOURCES.register("planned_source_v5",()->PlannedBiomeSource.CODEC_V5);
        SOURCES.register("planned_source_v6",()->PlannedBiomeSource.CODEC_V6);
        SOURCES.register("planned_source_v7",()->PlannedBiomeSource.CODEC_V7);
        SOURCES.register("planned_source_v8",()->PlannedBiomeSource.CODEC_V8);
        SOURCES.register("planned_source_v9",()->PlannedBiomeSource.CODEC_V9);
    }
    private WorldPlanRuntime() {}
    public static void register(IEventBus bus){
        GENERATORS.register(bus);SOURCES.register(bus);NeoForge.EVENT_BUS.addListener(WorldPlanRuntime::createSpawn);
        NeoForge.EVENT_BUS.addListener(WorldPlanRuntime::started);NeoForge.EVENT_BUS.addListener(WorldPlanCommands::register);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e)->{WorldAtlasCache.clear();CapitalGamePlacement.clear();});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.AddReloadListenerEvent e)->CapitalGamePlacement.clear());
    }
    public static Optional<PlannedChunkGenerator> generator(ServerLevel level){
        return level.dimension().equals(Level.OVERWORLD)&&level.getChunkSource().getGenerator() instanceof PlannedChunkGenerator g?Optional.of(g):Optional.empty();
    }
    public static Optional<WorldPlan.Context> context(ServerLevel level,int x,int z){return generator(level).map(g->g.plan().sample(x,z));}
    public static Path directory(ServerLevel level){return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/myvillage/worldplan");}
    public static void export(ServerLevel level,int resolution)throws IOException{
        WorldPlan plan=generator(level).orElseThrow(()->new IllegalArgumentException("Not a cultivation world")).plan();
        WorldPlanExporter.export(plan,directory(level),resolution,plan.rules().coreRadius+2*plan.rules().coreBlend);
    }
    private static void createSpawn(LevelEvent.CreateSpawnPosition event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        generator(level).ifPresent(g->{var spawn=g.plan().spawn();BlockPos at=new BlockPos(spawn.x(),spawn.y(),spawn.z());
            level.setDefaultSpawnPos(at,0);RegionRuntimeState.get(level).markSpawnBound(spawn.regionId(),at);
            if(g.rules().clearSpawnCanopy()){
                var radius=level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS);
                radius.set(Math.min(radius.get(),g.rules().spawnPadRadius),level.getServer());
            }
            // First creation only; restarts and custom administrator spawns are never overwritten.
            event.setCanceled(true);
        });
    }
    private static void started(ServerStartedEvent event){
        ServerLevel level=event.getServer().overworld();generator(level).ifPresent(g->{
            try{export(level,256);LOG.info("Worldplan {} exported to {}",g.plan().fingerprint(),directory(level));}
            catch(IOException failure){LOG.error("Worldplan atlas export failed; terrain plan remains active",failure);}
        });
    }
}
