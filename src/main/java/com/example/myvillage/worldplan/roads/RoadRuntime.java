package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.runtime.*;
import com.google.gson.GsonBuilder;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

/** Administrative generation/readback only. No ServerLevel block replacement or road construction tick. */
public final class RoadRuntime {
    private static final Map<ServerLevel,Inspection> INSPECTIONS=new IdentityHashMap<>();
    private static final class Inspection {
        final List<Long> chunks;
        final Map<Long,List<RoadPlan.Cell>> gates;
        final boolean generate;
        final List<Map<String,Object>> failures=new ArrayList<>();
        int cursor,skipped,checkedColumns,itemEntities;
        boolean paused;
        String phase;
        Inspection(RoadLayout layout,WorldPlan world,RoadRules rules,boolean generate){
            this.generate=generate;phase=generate?"pregenerating":"auditing";var by=new TreeMap<Long,List<RoadPlan.Cell>>();var seen=new HashSet<Long>();
            for(String id:rules.ports.keySet())for(var c:RoadPaver.declaredGate(world,rules,id))if(seen.add(c.key()))by.computeIfAbsent(c.chunk(),k->new ArrayList<>()).add(c);
            gates=Map.copyOf(by);var keys=new TreeSet<Long>(by.keySet());for(var s:layout.slices())keys.add(s.key);chunks=List.copyOf(keys);
        }
    }
    private RoadRuntime(){}
    public static void register(){NeoForge.EVENT_BUS.addListener(RoadRuntime::commands);NeoForge.EVENT_BUS.addListener(RoadRuntime::tick);NeoForge.EVENT_BUS.addListener(RoadRuntime::stop);}
    private static void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("myvillage").requires(s->s.hasPermission(2)).then(tree()));}
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> tree(){
        var root=Commands.literal("roads").requires(s->s.hasPermission(2));for(String action:List.of("status","export","audit","pregenerate","pause","resume"))root.then(Commands.literal(action).executes(c->command(c.getSource(),action)));return root;
    }
    public static Map<String,Object> status(ServerLevel level){
        var generator=WorldPlanRuntime.generator(level).orElseThrow(()->new IllegalArgumentException("仅用于当前修仙大陆的主世界"));var roads=generator.roads();var result=new LinkedHashMap<String,Object>();var layout=roads.snapshot().orElse(null);var job=INSPECTIONS.get(level);
        result.put("phase",job!=null?job.paused?"paused":job.phase:roads.failure().isEmpty()?layout==null?"planning":"ready":"failed");result.put("mode","chunk_generation");result.put("failure",roads.failure());result.put("cities",generator.plan().cities().size());result.put("planning_progress",roads.progress());result.put("planning_seconds",roads.seconds());
        result.put("layout_hash",layout==null?"":layout.hash());result.put("routes",layout==null?List.of():layout.routes().stream().map(r->Map.of("id",r.id(),"from",r.from,"to",r.to)).toList());result.put("road_chunks",layout==null?0:layout.slices().size());result.put("road_columns",layout==null?0:layout.columns());
        int loaded=0;if(layout!=null)for(var s:layout.slices())if(level.getChunkSource().getChunkNow((int)s.key,(int)(s.key>>>32))!=null)loaded++;result.put("loaded_road_chunks",loaded);
        if(job!=null){result.put("inspection",Map.of("phase",job.phase,"processed_chunks",job.cursor,"total_chunks",job.chunks.size(),"unloaded_chunks",job.skipped,"checked_columns",job.checkedColumns,"failure_count",job.failures.size(),"item_entities_seen",job.itemEntities,"generates_missing_chunks",job.generate));}
        return result;
    }
    private static int command(CommandSourceStack source,String action){
        try{
            var level=source.getLevel();var g=WorldPlanRuntime.generator(level).orElseThrow(()->new IllegalArgumentException("仅用于当前修仙大陆的主世界"));var layout=g.roads().snapshot().orElse(null);var existing=INSPECTIONS.get(level);
            switch(action){
                case "pregenerate","audit"->{if(layout==null)throw new IllegalStateException("路网布局仍在计算，请稍后查询 status");if(existing!=null&&(existing.phase.equals("pregenerating")||existing.phase.equals("auditing")))throw new IllegalStateException("已有检查或预生成任务，可暂停或继续");INSPECTIONS.put(level,new Inspection(layout,g.plan(),g.roads().rules(),action.equals("pregenerate")));}
                case "pause"->{if(existing!=null)existing.paused=true;}
                case "resume"->{if(existing!=null)existing.paused=false;}
                case "export"->{if(layout==null)throw new IllegalStateException("路网布局仍在计算");var data=new LinkedHashMap<String,Object>();data.put("status",status(level));data.put("world_hash",layout.worldHash());data.put("rules_hash",layout.rulesHash());data.put("layout_hash",layout.hash());data.put("routes",layout.routes().stream().map(r->Map.of("from",r.from,"to",r.to,"centerline",r.points())).toList());data.put("chunk_columns",layout.slices().stream().map(s->Map.of("chunk",s.key,"columns",s.cells())).toList());write(level,"roads.json",data);}
                default->{}
            }
            var state=status(level);source.sendSuccess(()->Component.literal(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer?describe(g.plan(),g.roads(),state):"官道 "+new com.google.gson.Gson().toJson(state)),false);return 1;
        }catch(Exception e){source.sendFailure(Component.literal("官道："+e.getMessage()));return 0;}
    }
    private static String describe(WorldPlan world,RoadGeneration roads,Map<String,Object> state){
        if(!roads.failure().isEmpty())return "官道布局失败："+roads.failure();var layout=roads.snapshot().orElse(null);
        if(layout==null)return "建世界官道布局计算中："+roads.progress().plannedRoutes()+" / "+roads.progress().requiredRoutes()+" 段，已用 "+Math.round(roads.seconds())+" 秒。";
        String text="官道随区块生成："+world.cities().size()+" 城、"+layout.routes().size()+" 段，当前已加载 "+state.get("loaded_road_chunks")+" / "+layout.slices().size()+" 个道路区块。";
        if(state.containsKey("inspection"))text+=" 检查／预生成状态："+state.get("phase")+"。";
        return text+"新区域生成时自然铺设；已有区块不回填。";
    }
    private static void tick(ServerTickEvent.Post event){
        for(var entry:List.copyOf(INSPECTIONS.entrySet())){
            var level=entry.getKey();var job=entry.getValue();if(job.paused||!(job.phase.equals("pregenerating")||job.phase.equals("auditing")))continue;
            var g=WorldPlanRuntime.generator(level).orElseThrow();var layout=g.roads().snapshot().orElseThrow();var rules=g.roads().rules();
            for(int n=0;n<rules.chunksPerTick&&job.cursor<job.chunks.size();n++){
                long key=job.chunks.get(job.cursor++);var pos=new ChunkPos(key);
                try{
                    if(job.generate)level.getChunk(pos.x,pos.z);
                    else if(level.getChunkSource().getChunkNow(pos.x,pos.z)==null){job.skipped++;continue;}
                    var slice=layout.slice(key);if(slice!=null)for(var c:slice.cells())auditColumn(level,g.plan(),rules,c,job);
                    for(var c:job.gates.getOrDefault(key,List.of())){try{RoadPaver.verify(level,c,rules.clearance);}catch(RuntimeException e){job.failures.add(Map.of("x",c.x(),"z",c.z(),"kind","gate","reason",String.valueOf(e.getMessage())));}job.checkedColumns++;}
                    job.itemEntities+=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos.getMinBlockX(),level.getMinBuildHeight(),pos.getMinBlockZ(),pos.getMaxBlockX()+1,level.getMaxBuildHeight(),pos.getMaxBlockZ()+1)).size();
                }catch(RuntimeException e){job.failures.add(Map.of("chunk_x",pos.x,"chunk_z",pos.z,"kind","generation","reason",String.valueOf(e.getMessage())));}
            }
            if(job.cursor==job.chunks.size()){
                job.phase=!job.failures.isEmpty()?"failed":job.skipped>0?"partial":"complete";
                try{write(level,"roads-audit.json",Map.of("status",status(level),"failures",job.failures,"owner_acceptance","not_verified","full_route_player_walk","not_verified"));}catch(Exception e){job.phase="failed";org.slf4j.LoggerFactory.getLogger(RoadRuntime.class).error("保存官道检查失败",e);}
                org.slf4j.LoggerFactory.getLogger(RoadRuntime.class).info("官道{}完成：{} 区块，{} 列，{} 处差异，{} 个未加载区块",job.generate?"预生成与检查":"已加载区块检查",job.cursor,job.checkedColumns,job.failures.size(),job.skipped);
            }
        }
    }
    private static void auditColumn(ServerLevel level,WorldPlan world,RoadRules rules,RoadPlan.Cell c,Inspection job){
        try{
            RoadPaver.verify(level,c,rules.clearance);var floor=new BlockPos(c.x(),c.floorY(),c.z());BlockState expected=RoadPaver.surface(world,rules,c);if(!level.getBlockState(floor).equals(expected))throw new IllegalStateException("路面材料不符："+level.getBlockState(floor)+"，预期 "+expected);
            String foundation=c.band()==3?rules.style.gradeFoundation():rules.foundation;var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(foundation));
            for(int y=RoadPaver.low(world,rules,c);y<c.floorY();y++)if(!level.getBlockState(new BlockPos(c.x(),y,c.z())).equals(block.defaultBlockState()))throw new IllegalStateException("路基不符："+c.x()+","+y+","+c.z());
        }catch(RuntimeException e){job.failures.add(Map.of("x",c.x(),"z",c.z(),"feet2",c.feet2(),"band",c.band(),"kind","road_column","reason",String.valueOf(e.getMessage())));}job.checkedColumns++;
    }
    private static void write(ServerLevel level,String name,Object data)throws Exception{var path=WorldPlanRuntime.directory(level).resolve(name);Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(data));}
    private static void stop(ServerStoppingEvent event){for(var level:event.getServer().getAllLevels())WorldPlanRuntime.generator(level).ifPresent(g->g.roads().close());INSPECTIONS.clear();}
}
