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
    static{GENERATORS.register("planned_v1",()->PlannedChunkGenerator.CODEC);SOURCES.register("planned_source_v1",()->PlannedBiomeSource.CODEC);}
    private WorldPlanRuntime() {}
    public static void register(IEventBus bus){
        GENERATORS.register(bus);SOURCES.register(bus);NeoForge.EVENT_BUS.addListener(WorldPlanRuntime::createSpawn);
        NeoForge.EVENT_BUS.addListener(WorldPlanRuntime::started);NeoForge.EVENT_BUS.addListener(WorldPlanCommands::register);
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
