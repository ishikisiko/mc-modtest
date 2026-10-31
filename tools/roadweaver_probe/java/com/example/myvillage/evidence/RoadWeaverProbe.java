package com.example.myvillage.evidence;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.capital.CapitalCity;
import com.example.myvillage.worldplan.capital.CapitalGameBundle;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.shiroha233.roadweaver.api.RoadNetworkApi;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.core.model.*;
import net.shiroha233.roadweaver.pathfinding.cache.FastHeightSampler;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Developer experiment only. It requests roads and reads blocks; it never edits city/road blocks. */
@EventBusSubscriber(modid = "myvillage")
public final class RoadWeaverProbe {
    private static final boolean ENABLED = "1".equals(System.getenv("MYVILLAGE_ROAD_PROBE"));
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT = new Gson();
    private static final JsonObject CONFIG = config();
    private static final String MODE = System.getenv().getOrDefault("MYVILLAGE_ROAD_PROBE_MODE", "COARSE_CORRIDOR");
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "myvillage-road-probe"); t.setDaemon(true); return t;
    });
    private static final Map<String,Object> RESULT = new LinkedHashMap<>();
    private static String phase = "idle";
    private static CompletableFuture<Void> generation;
    private static List<RoadData> roads = List.of();
    private static final Deque<ChunkPos> PENDING = new ArrayDeque<>();
    private static final Map<Long,List<Column>> COLUMNS = new LinkedHashMap<>();
    private static final List<Map<String,Object>> SAMPLES = new ArrayList<>();
    private static final Set<String> MATERIALS = new HashSet<>();
    private static long started;
    private static int chunks;
    private record Endpoint(CapitalCity city, CapitalGameBundle.Entry entry, BlockPos pos) {}
    private record Pair(Endpoint a, Endpoint b, WorldPlan.Border border, double distance) {}
    private record Column(int x,int floorY,int z,int segment) {}

    private RoadWeaverProbe() {}
    private static JsonObject config() {
        if (!ENABLED) return new JsonObject();
        try { return JsonParser.parseString(Files.readString(Path.of(System.getenv("MYVILLAGE_ROAD_PROBE_CONFIG")))).getAsJsonObject(); }
        catch (Exception e) { throw new IllegalStateException("Cannot read explicit road experiment configuration",e); }
    }
    private static int n(String key) { return CONFIG.get(key).getAsInt(); }
    private static Path out() { return Path.of(System.getenv("MYVILLAGE_ROAD_PROBE_OUT")); }
    private static List<Integer> xyz(BlockPos p) { return List.of(p.getX(),p.getY(),p.getZ()); }
    private static String block(BlockState state) { return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(); }
    private static void write(String file,Object value) {
        try { Files.createDirectories(out()); Path p=out().resolve(file),tmp=out().resolve(file+".tmp");
            Files.writeString(tmp,JSON.toJson(value)); Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING); }
        catch (Exception e) { throw new IllegalStateException("Cannot retain road experiment evidence",e); }
    }
    private static void checkpoint() { RESULT.put("phase",phase); RESULT.put("mode",MODE); write("probe.json",RESULT); }

    @SubscribeEvent public static void configure(ServerAboutToStartEvent event) {
        if (!ENABLED) return;
        var c=ConfigService.get();
        c.structurePrediction().setEnabled(false);
        c.planning().setInitialPlanRadiusChunks(1);
        c.planning().setDynamicPlanEnabled(false);
        c.planning().setTerrainSamplingMode(TerrainSamplingMode.valueOf(MODE));
        c.performance().setOpenclCoarseSamplingEnabled(false);
        c.performance().setOpenclAccurateSamplingEnabled(false);
        c.performance().setSharedWorkerThreads(2);
        c.performance().setMaxConcurrentGenerations(1);
        c.performance().setIdleGenerationEnabled(false);
        c.roadAppearance().setRoadsEnabled(true);
        c.roadAppearance().setAllowArtificial(true);
        c.roadAppearance().setAllowNatural(false);
        c.roadAppearance().setRoadWidth(n("road_width"));
        c.roadAppearance().setSpawnCabinEnabled(false);
        c.roadsideStructure().setEnabled(false);
        c.roadsideVillage().setEnabled(false);
        c.sanitize();
        RESULT.put("source_commit",System.getenv("MYVILLAGE_ROAD_PROBE_SOURCE"));
        RESULT.put("configuration",CONFIG); RESULT.put("no_world_block_edits_by_probe",true);
        checkpoint();
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if (!ENABLED) return;
        var root=Commands.literal("roadprobe").requires(s->s.hasPermission(4));
        for(String action:List.of("prepare","generate","bake","status")) root.then(Commands.literal(action).executes(ctx->{
            try {
                ServerLevel level=ctx.getSource().getServer().overworld();
                if(action.equals("prepare")) prepare(level);
                else if(action.equals("generate")) generate(level);
                else if(action.equals("bake")) bake(level);
                Map<String,Object> status=new LinkedHashMap<>();status.put("phase",phase);status.put("mode",MODE);
                status.put("roads",roads.size());status.put("chunks_done",chunks);status.put("chunks_pending",PENDING.size());
                status.put("columns_done",SAMPLES.size());status.put("result",out().toString());
                ctx.getSource().sendSuccess(()->Component.literal("ROADPROBE "+COMPACT.toJson(status)),false);return 1;
            } catch(Throwable e) { fail(e);ctx.getSource().sendFailure(Component.literal("ROADPROBE_ERROR "+e));return 0; }
        }));
        event.getDispatcher().register(root);
    }

    private static List<Endpoint> endpoints(CapitalCity c) {
        return c.bundle().entries.stream().map(e->new Endpoint(c,e,new BlockPos(c.x()+e.x(),c.originY()+e.y(),c.z()+e.z()))).toList();
    }
    private static Map<String,Object> endpoint(Endpoint e) {
        return Map.of("city",e.city.cityId(),"region",e.city.regionId(),"name",e.city.bundle().displayName,
            "entry",e.entry.id(),"facing",e.entry.facing(),"pos",xyz(e.pos),"blend_margin",e.city.bundle().blendMargin);
    }
    private static Pair selected;
    private static void prepare(ServerLevel level) {
        if(!phase.equals("idle"))throw new IllegalStateException("prepare requires a fresh experiment world/process");
        if(level.getSeed()!=CONFIG.get("seed").getAsLong())throw new IllegalStateException("Unexpected world seed");
        if(!WorldDataProvider.getInstance().getStructureConnections(level).isEmpty()||RoadShardStorage.hasAnyRoad(level))
            throw new IllegalStateException("Experiment requires no pre-existing roads/connections");
        var plan=WorldPlanRuntime.generator(level).orElseThrow().plan();
        List<Pair> choices=new ArrayList<>();
        for(var border:plan.borders()) {
            if(!border.passable())continue;
            CapitalCity a=plan.capitalOf(border.a()).orElseThrow(),b=plan.capitalOf(border.b()).orElseThrow();
            if(plan.rules().profile(a.regionId()).role().equals("walled")||plan.rules().profile(b.regionId()).role().equals("walled"))continue;
            for(var ea:endpoints(a))for(var eb:endpoints(b)) {
                double distance=Math.sqrt(ea.pos.distSqr(eb.pos));
                if(distance<=n("max_pair_distance")) choices.add(new Pair(ea,eb,border,distance));
            }
        }
        choices.sort(Comparator.comparingDouble(Pair::distance).thenComparing(p->p.a.city.cityId()).thenComparing(p->p.b.city.cityId()));
        selected=choices.stream().findFirst().orElseThrow(()->new IllegalStateException("No bounded adjacent passable capital pair"));
        RESULT.put("world_seed",level.getSeed());RESULT.put("plan_fingerprint",plan.fingerprint());
        RESULT.put("world_path",level.getServer().getWorldPath(LevelResource.ROOT).toString());
        RESULT.put("from",endpoint(selected.a));RESULT.put("to",endpoint(selected.b));RESULT.put("straight_distance",selected.distance);
        RESULT.put("border",Map.of("a",selected.border.a(),"b",selected.border.b(),"gate",selected.border.gate(),"crossing",selected.border.crossing()));
        FastHeightSampler fast=FastHeightSampler.create(level);List<Map<String,Object>> samples=new ArrayList<>();
        var generator=level.getChunkSource().getGenerator();var random=level.getChunkSource().getGeneratorState().randomState();
        for(int i=0;i<=64;i++) {
            int x=(int)Math.round(selected.a.pos.getX()+(selected.b.pos.getX()-selected.a.pos.getX())*i/64.0);
            int z=(int)Math.round(selected.a.pos.getZ()+(selected.b.pos.getZ()-selected.a.pos.getZ())*i/64.0);
            samples.add(Map.of("x",x,"z",z,"fast",fast.sampleHeight(x,z),"plan",plan.surfaceY(x,z),
                "generator",generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,random)));
        }
        RESULT.put("height_samples",samples);phase="prepared";checkpoint();
    }
    private static void generate(ServerLevel level) {
        if(!phase.equals("prepared"))throw new IllegalStateException("generate requires prepared endpoints");
        RoadNetworkApi.registerStructureEndpoint(level,selected.a.pos,"myvillage:"+selected.a.city.cityId(),false);
        RoadNetworkApi.registerStructureEndpoint(level,selected.b.pos,"myvillage:"+selected.b.city.cityId(),false);
        RoadNetworkApi.ensureConnection(level,selected.a.pos,selected.b.pos,false);
        started=System.nanoTime();phase="generating";checkpoint();
        generation=CompletableFuture.runAsync(()->RoadNetworkApi.ensureConnection(level,selected.a.pos,selected.b.pos,true),WORKER);
    }
    private static void collect(ServerLevel level) {
        generation.join();RoadShardStorage.flushAll(level);roads=RoadShardStorage.loadAll(level);
        RESULT.put("generation_seconds",(System.nanoTime()-started)/1e9);
        RESULT.put("connections",WorldDataProvider.getInstance().getStructureConnections(level).stream()
            .map(c->Map.of("from",xyz(c.from()),"to",xyz(c.to()),"status",c.status().toString())).toList());
        List<Map<String,Object>> raw=new ArrayList<>();
        var plan=WorldPlanRuntime.generator(level).orElseThrow().plan();
        Set<String> crossings=new TreeSet<>();List<Map<String,Object>> forbidden=new ArrayList<>();
        for(RoadData road:roads) {
            road.materials().forEach(s->MATERIALS.add(block(s)));road.slabMaterials().forEach(s->MATERIALS.add(block(s)));
            List<Map<String,Object>> segments=new ArrayList<>();String previous=null;
            for(int i=0;i<road.roadSegmentList().size();i++) {
                var seg=road.roadSegmentList().get(i);int y=road.targetY().size()>i?road.targetY().get(i):seg.middlePos().getY();
                String region=plan.sample(seg.middlePos().getX(),seg.middlePos().getZ()).regionId();
                if(previous!=null&&!previous.equals(region)) {
                    String a=previous,b=region;crossings.add(a+":"+b);
                    var border=plan.borders().stream().filter(e->(e.a().equals(a)&&e.b().equals(b))||(e.b().equals(a)&&e.a().equals(b))).findFirst();
                    if(border.isEmpty()||!border.get().passable())forbidden.add(Map.of("from",a,"to",b,"pos",xyz(seg.middlePos())));
                }
                previous=region;
                segments.add(Map.of("index",i,"middle",xyz(seg.middlePos()),"target_y",y,"region",region,
                    "positions",seg.positions().stream().map(RoadWeaverProbe::xyz).toList()));
            }
            raw.add(Map.of("width",road.width(),"materials",road.materials().stream().map(Object::toString).toList(),
                "segments",segments,"spans",road.spans().stream().map(s->Map.of("type",s.type().toString(),"start",xyz(s.start()),"end",xyz(s.end()))).toList()));
        }
        RESULT.put("road_count",roads.size());RESULT.put("region_crossings",crossings);RESULT.put("forbidden_crossings",forbidden);
        write("roads.json",raw);phase=roads.isEmpty()?"no_road":"generated";checkpoint();
    }
    private static void bake(ServerLevel level) {
        if(!phase.equals("generated"))throw new IllegalStateException("bake requires generated road records");
        Map<String,Column> unique=new LinkedHashMap<>();
        for(RoadData road:roads)for(int i=0;i<road.roadSegmentList().size();i++) {
            var seg=road.roadSegmentList().get(i);int y=road.targetY().size()>i?road.targetY().get(i):seg.middlePos().getY();
            for(BlockPos p:seg.positions())unique.putIfAbsent(p.getX()+":"+y+":"+p.getZ(),new Column(p.getX(),y,p.getZ(),i));
        }
        if(unique.size()>n("max_path_columns"))throw new IllegalStateException("Path column budget exceeded: "+unique.size());
        for(Column c:unique.values())COLUMNS.computeIfAbsent(ChunkPos.asLong(c.x>>4,c.z>>4),k->new ArrayList<>()).add(c);
        if(COLUMNS.size()>n("max_path_chunks"))throw new IllegalStateException("Path chunk budget exceeded: "+COLUMNS.size());
        COLUMNS.keySet().forEach(k->PENDING.add(new ChunkPos(k)));
        RESULT.put("declared_columns",unique.size());RESULT.put("declared_chunks",COLUMNS.size());phase="baking";started=System.nanoTime();checkpoint();
    }
    private static Map<String,Object> sample(ServerLevel level,Column c) {
        int radius=n("height_scan_radius");List<Map<String,Object>> blocks=new ArrayList<>();
        int low=Math.max(level.getMinBuildHeight()+1,c.floorY-radius),high=Math.min(level.getMaxBuildHeight()-3,c.floorY+radius);
        boolean reserved=WorldPlanRuntime.generator(level).orElseThrow().plan().reserved(c.x,c.floorY,c.z);
        int actualSurface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,c.x,c.z);
        for(int y=low;y<=high;y++) {
            BlockPos p=new BlockPos(c.x,y,c.z);BlockState state=level.getBlockState(p);
            boolean solid=!state.getCollisionShape(level,p).isEmpty(),fluid=!state.getFluidState().isEmpty();
            if(solid||fluid||MATERIALS.contains(block(state))) {
                var feet=level.getBlockState(p.above());var head=level.getBlockState(p.above(2));
                boolean clear=feet.getCollisionShape(level,p.above()).isEmpty()&&feet.getFluidState().isEmpty()
                    &&head.getCollisionShape(level,p.above(2)).isEmpty()&&head.getFluidState().isEmpty();
                blocks.add(Map.of("y",y,"state",state.toString(),"block",block(state),"solid",solid,"fluid",fluid,"clear",clear,
                    "pavement",MATERIALS.contains(block(state)),"below",level.getBlockState(p.below()).toString()));
            }
        }
        return Map.of("x",c.x,"z",c.z,"target_y",c.floorY,"segment",c.segment,"reserved",reserved,"actual_surface",actualSurface,"blocks",blocks);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!ENABLED)return;ServerLevel level=event.getServer().overworld();
        try {
            if(phase.equals("generating")&&generation.isDone())collect(level);
            if(phase.equals("baking")) {
                ChunkPos chunk=PENDING.poll();
                if(chunk!=null) {
                    level.getChunk(chunk.x,chunk.z);chunks++;
                    for(Column c:COLUMNS.get(chunk.toLong()))SAMPLES.add(sample(level,c));
                    if(chunks%16==0)checkpoint();
                } else {
                    RESULT.put("bake_seconds",(System.nanoTime()-started)/1e9);RESULT.put("sampled_columns",SAMPLES.size());
                    write("columns.json",SAMPLES);phase="audited";checkpoint();
                }
            }
        } catch(Throwable e){fail(e);}
    }
    private static void fail(Throwable e) {phase="failed";RESULT.put("error",e.toString());RESULT.put("stack",Arrays.stream(e.getStackTrace()).map(Object::toString).toList());checkpoint();}
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {if(ENABLED){WORKER.shutdownNow();checkpoint();}}
}
