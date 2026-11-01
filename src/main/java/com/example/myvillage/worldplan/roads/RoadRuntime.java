package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

/** Explicit admin prototype. Planning is pure; chunk access and ALL writes run on the server thread. */
public final class RoadRuntime {
    private static final Map<ServerLevel,Session> SESSIONS=new IdentityHashMap<>();
    private static ExecutorService worker;
    private static final class Session {
        final RoadRules rules=RoadRules.load();
        Future<RoadPlan> pending;
        List<Map.Entry<Long,List<RoadPlan.Cell>>> chunks=List.of();
        final Map<Long,Long> preflight=new HashMap<>();
        int cursor;
        long started;
        double planningSeconds;
        boolean auditing;
        int auditCursor, auditFailures, missingMaterial;
        final List<Map<String,Object>> samples=new ArrayList<>(),lost=new ArrayList<>();
    }
    private RoadRuntime() {}
    public static void register() {
        NeoForge.EVENT_BUS.addListener(RoadRuntime::commands);
        NeoForge.EVENT_BUS.addListener(RoadRuntime::tick);
        NeoForge.EVENT_BUS.addListener(RoadRuntime::stop);
    }
    public static RoadPlan plan(ServerLevel level){return RoadSavedData.get(level).plan;}
    public static Map<String,Object> status(ServerLevel level) {
        var d=RoadSavedData.get(level);var s=SESSIONS.get(level);var out=new LinkedHashMap<String,Object>();
        out.put("phase",s!=null&&s.pending!=null?"planning":d.phase);out.put("failure",d.failure);
        out.put("completed_chunks",d.completedChunks);out.put("validated_chunks",s==null?0:s.cursor);
        if(s!=null)out.put("planning_seconds",s.planningSeconds);
        if(s!=null){out.put("audit_phase",s.auditing?"running":s.auditCursor>0?"complete":"idle");out.put("audit_chunks",s.auditCursor);out.put("audit_failures",s.auditFailures);out.put("missing_material",s.missingMaterial);}
        if(d.plan!=null){out.put("from",d.plan.from());out.put("to",d.plan.to());out.put("outside_width",d.plan.width());out.put("gate_widths",d.plan.gateWidths());out.put("columns",d.plan.cells().size());out.put("chunks",d.plan.chunks().size());}
        return out;
    }
    private static void commands(RegisterCommandsEvent event) {
        var roads=Commands.literal("roads")
            .then(Commands.literal("plan").then(Commands.argument("起点",StringArgumentType.word())
                .suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("nanjiang","ximo"),b))
                .then(Commands.argument("终点",StringArgumentType.word())
                    .suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("nanjiang","ximo"),b))
                    .executes(c->command(c.getSource(),"plan",StringArgumentType.getString(c,"起点"),StringArgumentType.getString(c,"终点"))))))
            .then(Commands.literal("build").executes(c->command(c.getSource(),"build","","")))
            .then(Commands.literal("status").executes(c->command(c.getSource(),"status","","")))
            .then(Commands.literal("audit").executes(c->command(c.getSource(),"audit","","")))
            .then(Commands.literal("export").executes(c->command(c.getSource(),"export","","")))
            .then(Commands.literal("pause").executes(c->command(c.getSource(),"pause","","")));
        event.getDispatcher().register(Commands.literal("myvillage").requires(s->s.hasPermission(2)).then(roads));
    }
    private static int command(CommandSourceStack source,String action,String from,String to) {
        try {
            ServerLevel level=source.getLevel();WorldPlan world=WorldPlanRuntime.generator(level)
                .orElseThrow(()->new IllegalArgumentException("驿道原型仅用于修仙世界的主世界")).plan();
            Session session=SESSIONS.computeIfAbsent(level,k->new Session());RoadSavedData data=RoadSavedData.get(level);
            switch(action) {
                case "plan" -> {
                    if(session.pending!=null||session.auditing||Set.of("validating","building").contains(data.phase))throw new IllegalStateException("已有驿道任务正在执行");
                    if(data.completedChunks>0)throw new IllegalStateException("首版每个世界保留一条试验驿道；请使用现有任务");
                    String a=world.capitalOf(from).orElseThrow(()->new IllegalArgumentException("省城不存在："+from)).regionId();
                    String b=world.capitalOf(to).orElseThrow(()->new IllegalArgumentException("省城不存在："+to)).regionId();
                    var exitA=RoadPaver.exit(world,session.rules,a,RoadPaver.gate(level,world,session.rules,a));
                    var exitB=RoadPaver.exit(world,session.rules,b,RoadPaver.gate(level,world,session.rules,b));
                    if(worker==null)worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"MyVillage-RoadPlan");t.setDaemon(true);return t;});
                    data.plan=null;data.phase="idle";data.failure="";data.setDirty();session.started=System.nanoTime();
                    session.pending=worker.submit(()->WorldRoadPlanner.plan(world,session.rules,exitA,exitB));
                }
                case "build" -> {
                    if(session.auditing)throw new IllegalStateException("请等待驿道路幅检查完成");
                    if(session.pending!=null||data.plan==null)throw new IllegalStateException("请先等待驿道规划完成");
                    if(data.phase.equals("complete"))break;
                    if(!Set.of("ready","paused","blocked").contains(data.phase))throw new IllegalStateException("当前驿道不能开始施工");
                    requireIdentity(world,session.rules,data.plan);
                    session.chunks=new ArrayList<>(data.plan.chunks().entrySet());session.preflight.clear();session.cursor=0;
                    session.auditCursor=0;session.auditFailures=0;session.missingMaterial=0;session.samples.clear();session.lost.clear();
                    data.phase="validating";data.failure="";data.setDirty();
                }
                case "audit" -> {
                    if(data.plan==null||session.pending!=null||session.auditing||Set.of("validating","building").contains(data.phase))throw new IllegalStateException("请等待规划或施工任务结束");
                    requireIdentity(world,session.rules,data.plan);
                    session.chunks=new ArrayList<>(data.plan.chunks().entrySet());session.auditCursor=0;session.auditFailures=0;session.missingMaterial=0;
                    session.samples.clear();session.lost.clear();session.auditing=true;
                }
                case "pause" -> {
                    if(session.pending!=null){session.pending.cancel(true);session.pending=null;}
                    if(Set.of("validating","building").contains(data.phase))data.phase="paused";
                    data.setDirty();
                }
                case "export" -> {
                    var path=WorldPlanRuntime.directory(level).resolve("roads.json");Files.createDirectories(path.getParent());
                    Map<String,Object> result=new LinkedHashMap<>();result.put("status",status(level));result.put("plan",data.plan);
                    Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(result));
                    source.sendSuccess(()->Component.literal("驿道规划已导出："+path),false);
                }
                default -> {}
            }
            source.sendSuccess(()->Component.literal("驿道 "+new com.google.gson.Gson().toJson(status(level))),false);return 1;
        } catch(Exception e) {source.sendFailure(Component.literal("驿道："+e.getMessage()));return 0;}
    }
    private static void requireIdentity(WorldPlan world,RoadRules rules,RoadPlan road) {
        if(!road.worldHash().equals(world.fingerprint())||!road.rulesHash().equals(rules.fingerprint))throw new IllegalStateException("驿道规划与当前世界或配置不一致");
    }
    private static void tick(ServerTickEvent.Post event) {
        for(var item:SESSIONS.entrySet()) {
            ServerLevel level=item.getKey();Session session=item.getValue();RoadSavedData data=RoadSavedData.get(level);
            try {
                if(session.auditing){audit(level,session,data);continue;}
                if(session.pending!=null&&session.pending.isDone()) {
                    Future<RoadPlan> done=session.pending;session.pending=null;data.plan=done.get();data.phase="ready";data.failure="";
                    session.planningSeconds=(System.nanoTime()-session.started)/1e9;data.setDirty();
                    LoggerFactory.getLogger(RoadRuntime.class).info("驿道规划完成：{}，{} 列，{} 秒",data.plan.from()+"—"+data.plan.to(),data.plan.cells().size(),session.planningSeconds);
                }
                if(!Set.of("validating","building").contains(data.phase))continue;
                var world=WorldPlanRuntime.generator(level).orElseThrow().plan();requireIdentity(world,session.rules,data.plan);
                for(int n=0;n<session.rules.chunksPerTick;n++) {
                    boolean validating=data.phase.equals("validating");int cursor=validating?session.cursor:data.completedChunks;
                    if(cursor>=session.chunks.size()) {
                        data.phase=validating?"building":"complete";data.setDirty();break;
                    }
                    var chunk=session.chunks.get(cursor);long key=chunk.getKey();var pos=new ChunkPos(key);
                    level.getChunk(pos.x,pos.z); // One bounded full chunk per tick; never a worker world read.
                    long signature=RoadPaver.preflight(level,world,session.rules,data.plan,chunk.getValue());
                    if(validating){session.preflight.put(key,signature);session.cursor++;}
                    else {
                        if(!Objects.equals(session.preflight.get(key),signature))throw new IllegalStateException("施工预检后方块发生变化；请重新预检："+pos);
                        RoadPaver.pave(level,world,session.rules,data.plan,chunk.getValue());data.completedChunks++;data.setDirty();
                    }
                }
            } catch(Exception e) {
                session.auditing=false;
                Throwable cause=e instanceof ExecutionException&&e.getCause()!=null?e.getCause():e;
                data.phase=data.plan==null?"failed":"blocked";data.failure=cause.toString();data.setDirty();
                LoggerFactory.getLogger(RoadRuntime.class).error("驿道任务停止，保留失败及已完成区块：{}",data.failure,cause);
            }
        }
    }
    private static void audit(ServerLevel level,Session session,RoadSavedData data)throws java.io.IOException {
        if(session.auditCursor>=session.chunks.size()) {
            var result=new LinkedHashMap<String,Object>();result.put("from",data.plan.from());result.put("to",data.plan.to());
            result.put("world_hash",data.plan.worldHash());result.put("rules_hash",data.plan.rulesHash());result.put("outside_width",data.plan.width());result.put("gate_widths",data.plan.gateWidths());
            result.put("columns",session.samples.size());result.put("failed_columns",session.auditFailures);result.put("missing_material",session.missingMaterial);
            result.put("lost_positions",session.lost);result.put("samples",session.samples);
            Path path=WorldPlanRuntime.directory(level).resolve("roads-audit.json"),temporary=path.resolveSibling("roads-audit.tmp");
            Files.createDirectories(path.getParent());Files.writeString(temporary,new com.google.gson.Gson().toJson(result));
            Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);session.auditing=false;return;
        }
        var entry=session.chunks.get(session.auditCursor++);var pos=new ChunkPos(entry.getKey());level.getChunk(pos.x,pos.z);
        for(var cell:entry.getValue()) {
            var floor=new net.minecraft.core.BlockPos(cell.x(),cell.floorY(),cell.z());var state=level.getBlockState(floor);
            String material=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            var sample=new LinkedHashMap<String,Object>();sample.put("x",cell.x());sample.put("z",cell.z());sample.put("feet2",cell.feet2());sample.put("existing",cell.existing());sample.put("floor",state.toString());
            sample.put("head",java.util.stream.IntStream.rangeClosed(1,session.rules.clearance).mapToObj(i->level.getBlockState(floor.above(i)).toString()).toList());
            if(!cell.existing()&&!material.equals(session.rules.surface)&&!material.equals(session.rules.slab))session.missingMaterial++;
            try {RoadPaver.verify(level,cell,session.rules.clearance);sample.put("walkable",true);}
            catch(IllegalStateException e){sample.put("walkable",false);sample.put("failure",e.getMessage());session.auditFailures++;session.lost.add(sample);}
            session.samples.add(sample);
        }
    }
    private static void stop(ServerStoppingEvent event) {
        for(var s:SESSIONS.values())if(s.pending!=null)s.pending.cancel(true);
        if(worker!=null){worker.shutdownNow();worker=null;}
        SESSIONS.clear();
    }
}
