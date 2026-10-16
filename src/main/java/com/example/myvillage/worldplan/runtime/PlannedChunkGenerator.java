package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.WorldPlanRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Opt-in world type. Every write belongs to the current chunk; frozen rules live in level.dat. */
public final class PlannedChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PlannedChunkGenerator> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(g->g.originalSource),
        NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(g->g.settings),
        Codec.STRING.fieldOf("rules").forGetter(g->g.rules.json()),
        Codec.STRING.optionalFieldOf("plan_hash","").forGetter(PlannedChunkGenerator::savedFingerprint),
        RegistryOps.retrieveGetter(Registries.NOISE)
    ).apply(i,(source,settings,json,hash,noises)->new PlannedChunkGenerator(source,settings,new WorldPlanRules(json),hash,noises)));
    private final BiomeSource originalSource;
    private final Holder<NoiseGeneratorSettings> settings;
    private final HolderGetter<NormalNoise.NoiseParameters> noises;
    private final WorldPlanRules rules;
    private final String expectedFingerprint;
    private final PlannedBiomeSource plannedSource;
    private final NoiseBasedChunkGenerator terrain;
    private volatile WorldPlan plan;
    private volatile RandomState terrainState;
    public PlannedChunkGenerator(BiomeSource source,Holder<NoiseGeneratorSettings> settings,WorldPlanRules rules,String expectedFingerprint,HolderGetter<NormalNoise.NoiseParameters> noises){
        this(new PlannedBiomeSource(source,rules),source,settings,rules,expectedFingerprint,noises);
    }
    private PlannedChunkGenerator(PlannedBiomeSource planned,BiomeSource source,Holder<NoiseGeneratorSettings> settings,WorldPlanRules rules,String expectedFingerprint,HolderGetter<NormalNoise.NoiseParameters> noises){
        super(planned);originalSource=source;plannedSource=planned;this.settings=settings;this.rules=rules;this.expectedFingerprint=expectedFingerprint;this.noises=noises;
        if(settings.value().seaLevel()!=rules.seaLevel)throw new IllegalArgumentException("Worldplan sea level must match noise settings");
        terrain=new NoiseBasedChunkGenerator(planned,settings);
    }
    @Override protected MapCodec<? extends ChunkGenerator> codec(){return CODEC;}
    public WorldPlan plan(){WorldPlan p=plan;if(p==null)throw new IllegalStateException("Worldplan requested before createState seed binding");return p;}
    public WorldPlanRules rules(){return rules;}
    public RandomState terrainState(){RandomState state=terrainState;if(state==null)throw new IllegalStateException("Worldplan noise requested before createState");return state;}
    private String savedFingerprint(){return plan==null?expectedFingerprint:plan.fingerprint();}
    @Override public synchronized ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures,RandomState ignored,long seed){
        if(plan!=null&&plan.seed()!=seed)throw new IllegalStateException("Cannot share a bound worldplan generator across different seeds");
        if(plan==null){
            WorldPlan p=new WorldPlan(seed,rules);
            if(!expectedFingerprint.isEmpty()&&!expectedFingerprint.equals(p.fingerprint()))throw new IllegalStateException("Saved worldplan fingerprint mismatch; refusing to move existing geography. Restore the matching rules/algorithm version.");
            // ChunkMap supplies dummy noise for custom generators; build the real settings-backed state.
            RandomState state=RandomState.create(settings.value(),noises,seed);
            terrainState=state;plannedSource.bind(p,state.sampler());plan=p;
        }
        return super.createState(structures,terrainState(),seed);
    }
    @Override public CompletableFuture<ChunkAccess> createBiomes(RandomState ignored,Blender blender,StructureManager structures,ChunkAccess chunk){return terrain.createBiomes(terrainState(),blender,structures,chunk);}
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender,RandomState ignored,StructureManager structures,ChunkAccess chunk){
        return terrain.fillFromNoise(blender,terrainState(),structures,chunk).thenApply(result->{shapeSurface(result);return result;});
    }
    private void shapeSurface(ChunkAccess chunk){
        BlockPos.MutableBlockPos at=new BlockPos.MutableBlockPos();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int wx=chunk.getPos().getMinBlockX()+x,wz=chunk.getPos().getMinBlockZ()+z;
            int target=plan().surfaceY(wx,wz),nativeFloor=chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)+1;
            int sourceTop=chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z)+1;
            int low=Math.max(chunk.getMinBuildHeight(),Math.min(target,nativeFloor)-rules.surfaceSeal);
            int high=Math.min(chunk.getMaxBuildHeight(),Math.max(Math.max(target,sourceTop),rules.seaLevel));
            for(int y=low;y<high;y++){at.set(wx,y,wz);BlockState before=chunk.getBlockState(at),after=columnBlock(before,y,target,nativeFloor);if(before!=after)chunk.setBlockState(at,after,false);}
        }
        Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
    }
    private BlockState columnBlock(BlockState original,int y,int target,int nativeFloor){
        if(y>=target)return y<rules.seaLevel?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState();
        if(y>=target-rules.surfaceSeal||y>=nativeFloor-rules.surfaceSeal)return Blocks.STONE.defaultBlockState();return original;
    }
    @Override public void buildSurface(WorldGenRegion region,StructureManager structures,RandomState ignored,ChunkAccess chunk){terrain.buildSurface(region,structures,terrainState(),chunk);}
    @Override public void applyCarvers(WorldGenRegion region,long seed,RandomState ignored,BiomeManager biomes,StructureManager structures,ChunkAccess chunk,GenerationStep.Carving step){
        if(!plan().intersectsCity(chunk.getPos().getMinBlockX(),chunk.getPos().getMinBlockZ(),chunk.getPos().getMaxBlockX(),chunk.getPos().getMaxBlockZ()))terrain.applyCarvers(region,seed,terrainState(),biomes,structures,chunk,step);
    }
    @Override public void createStructures(RegistryAccess registries,ChunkGeneratorStructureState state,StructureManager manager,ChunkAccess chunk,StructureTemplateManager templates){
        super.createStructures(registries,state,manager,chunk,templates);
        for(var entry:new ArrayList<>(chunk.getAllStarts().entrySet())){
            StructureStart start=entry.getValue();if(!start.isValid())continue;BoundingBox box=start.getBoundingBox();
            String id=registries.registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey()).toString();
            if(plan().intersectsCity(box.minX(),box.minZ(),box.maxX(),box.maxZ())||!plan().allowsStructure(id,chunk.getPos().getMiddleBlockX(),chunk.getPos().getMiddleBlockZ()))chunk.setStartForStructure(entry.getKey(),StructureStart.INVALID_START);
        }
    }
    @Override public void applyBiomeDecoration(WorldGenLevel level,ChunkAccess chunk,StructureManager structures){
        super.applyBiomeDecoration(level,chunk,structures);
        if(level.getLevel().getServer().getWorldData().worldGenOptions().generateStructures())PlannedCity.placeSlice(plan(),level,chunk);
    }
    @Override public void spawnOriginalMobs(WorldGenRegion region){terrain.spawnOriginalMobs(region);}
    @Override public int getGenDepth(){return terrain.getGenDepth();}
    @Override public int getMinY(){return terrain.getMinY();}
    @Override public int getSeaLevel(){return rules.seaLevel;}
    @Override public int getSpawnHeight(LevelHeightAccessor level){return rules.seaLevel+1;}
    @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor level,RandomState ignored){
        int y=plan().surfaceY(x,z);if(type!=Heightmap.Types.OCEAN_FLOOR&&type!=Heightmap.Types.OCEAN_FLOOR_WG)y=Math.max(y,rules.seaLevel);
        return Math.max(level.getMinBuildHeight(),Math.min(level.getMaxBuildHeight(),y));
    }
    @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor level,RandomState ignored){
        NoiseColumn nativeColumn=terrain.getBaseColumn(x,z,level,terrainState());
        int min=Math.max(getMinY(),level.getMinBuildHeight()),max=Math.min(getMinY()+getGenDepth(),level.getMaxBuildHeight());
        int nativeFloor=min;for(int y=max-1;y>=min;y--)if(!nativeColumn.getBlock(y).isAir()&&nativeColumn.getBlock(y).getFluidState().isEmpty()){nativeFloor=y+1;break;}
        int target=plan().surfaceY(x,z);BlockState[] states=new BlockState[max-min];
        for(int y=min;y<max;y++)states[y-min]=columnBlock(nativeColumn.getBlock(y),y,target,nativeFloor);return new NoiseColumn(min,states);
    }
    @Override public void addDebugScreenInfo(List<String> lines,RandomState ignored,BlockPos pos){
        WorldPlan.Context c=plan().sample(pos.getX(),pos.getZ());lines.add("Worldplan V1: "+c.displayName()+" tier="+c.tier()+" terrain="+c.surfaceY()+" hash="+plan().fingerprint().substring(0,12));
    }
}
