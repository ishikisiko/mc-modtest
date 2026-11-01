package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
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

/** 后台只规划不可变地理；加载检查、保护检查及全部施工在服务器线程执行。 */
public final class RoadRuntime {
    private static final Map<ServerLevel,Session> SESSIONS=new IdentityHashMap<>();
    private static ExecutorService worker;
    private static final class Session {
        final RoadRules rules=RoadRules.load();
        Future<RoadNetworkPlanner.Result> pending;
        volatile RoadNetworkPlanner.Progress progress=new RoadNetworkPlanner.Progress("idle","","",0,0);
        Map<Long,RoadPlan> owners=Map.of();
        boolean bulk;
        long started;
        double planningSeconds;
    }
    private RoadRuntime() {}
    public static void register() {
        NeoForge.EVENT_BUS.addListener(RoadRuntime::commands);
        NeoForge.EVENT_BUS.addListener(RoadRuntime::tick);
        NeoForge.EVENT_BUS.addListener(RoadRuntime::stop);
    }
    public static RoadPlan plan(ServerLevel level){var d=RoadSavedData.get(level);return d.resolved(d.selected());}
    public static Map<String,Object> status(ServerLevel level) {
        var d=RoadSavedData.get(level);var s=SESSIONS.get(level);var out=new LinkedHashMap<String,Object>();
        out.put("phase",d.phase);out.put("failure",d.failure);out.put("cities",WorldPlanRuntime.generator(level).map(g->g.plan().cities().size()).orElse(0));
        out.put("routes",d.routes.values().stream().map(r->Map.of("id",r.id,"from",r.from,"to",r.to,"phase",r.phase)).toList());
        out.put("completed_chunks",d.job==null?RoadPlan.chunks(d.committed).size():d.job.completed.size());
        out.put("chunks",d.job==null?RoadPlan.chunks(d.committed).size():d.job.writeChunks.size());
        out.put("protected_chunks",d.job==null?0:d.job.blocked.size());out.put("columns",d.job==null?d.committed.size():d.job.target.size());
        out.put("selected",d.selectedId);out.put("mode",s!=null&&s.bulk?"bulk":"near_players");out.put("planning_seconds",s==null?0:s.pending!=null?(System.nanoTime()-s.started)/1e9:s.planningSeconds);
        if(s!=null)out.put("planning_progress",s.progress);
        return out;
    }
    private static void commands(RegisterCommandsEvent event) {event.getDispatcher().register(Commands.literal("myvillage").requires(s->s.hasPermission(2)).then(tree()));}
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> tree() {
        var root=Commands.literal("roads").requires(s->s.hasPermission(2));
        root.then(Commands.literal("plan").executes(c->command(c.getSource(),"network","",""))
            .then(Commands.argument("起点",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(RoadRules.load().ports.keySet(),b))
            .then(Commands.argument("终点",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(RoadRules.load().ports.keySet(),b))
            .executes(c->command(c.getSource(),"plan",StringArgumentType.getString(c,"起点"),StringArgumentType.getString(c,"终点"))))));
        for(String action:List.of("network","build","resume","status","pause","check","audit","export"))root.then(Commands.literal(action).executes(c->command(c.getSource(),action,"","")));
        root.then(Commands.literal("select").then(Commands.argument("路线",StringArgumentType.word()).executes(c->command(c.getSource(),"select",StringArgumentType.getString(c,"路线"),""))));
        return root;
    }
    private static void begin(ServerLevel level,WorldPlan world,Session session,RoadSavedData data,String from,String to) {
        if(session.pending!=null||data.job!=null)throw new IllegalStateException("已有路网任务；可用 resume 续建或 status 查看");
        if(worker==null)worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"MyVillage-RoadNetwork");t.setDaemon(true);return t;});
        var completed=data.completedPlans();var previous=List.copyOf(data.committed);data.phase="planning";data.failure="";data.setDirty();session.started=System.nanoTime();session.planningSeconds=0;session.progress=new RoadNetworkPlanner.Progress("searching",from,to,0,from.isEmpty()?world.cities().size()-1:1);
        session.pending=worker.submit(()->{
            if(from.isEmpty())return RoadNetworkPlanner.plan(world,session.rules,completed,previous,p->session.progress=p);
            if(dataId(completed,from,to))throw new IllegalArgumentException("该路线已经存在");
            var a=RoadPaver.exit(world,session.rules,from,RoadPaver.declaredGate(world,session.rules,from));
            var b=RoadPaver.exit(world,session.rules,to,RoadPaver.declaredGate(world,session.rules,to));
            var road=WorldRoadPlanner.plan(world,session.rules,a,b,List.of(),previous);var all=new ArrayList<>(completed);all.add(road);
            session.progress=new RoadNetworkPlanner.Progress("merging","","",1,1);
            return new RoadNetworkPlanner.Result(all,RoadNetworkGeometry.merge(world,session.rules,all,previous));
        });
    }
    private static boolean dataId(List<RoadPlan> plans,String a,String b){String id=RoadNetworkGeometry.id(a,b);return plans.stream().anyMatch(p->RoadNetworkGeometry.id(p.from(),p.to()).equals(id));}
    private static int command(CommandSourceStack source,String action,String from,String to) {
        try {
            var level=source.getLevel();var world=WorldPlanRuntime.generator(level).orElseThrow(()->new IllegalArgumentException("仅用于当前修仙大陆的主世界")).plan();
            var s=SESSIONS.computeIfAbsent(level,k->new Session());var d=RoadSavedData.get(level);
            switch(action) {
                case "plan","network" -> {d.manualPause=false;begin(level,world,s,d,action.equals("plan")?from:"",to);}
                case "build","resume" -> {
                    d.manualPause=false;s.bulk=action.equals("build");
                    if(d.job!=null){d.job.blocked.clear();d.failure="";d.phase="building";d.setDirty();}
                    else if(!d.phase.equals("complete")&&s.pending==null)begin(level,world,s,d,"","");
                }
                case "pause" -> {d.manualPause=true;s.bulk=false;if(s.pending!=null){s.pending.cancel(true);s.pending=null;}d.phase="paused";d.setDirty();}
                case "select" -> {if(!d.routes.containsKey(from))throw new IllegalArgumentException("路线不存在："+from);d.selectedId=from;d.setDirty();}
                case "check","audit" -> {
                    int checked=0;for(var c:d.materialized())if(level.getChunkSource().getChunkNow(c.x()>>4,c.z()>>4)!=null){RoadPaver.verify(level,c,s.rules.clearance);checked++;}
                    final int count=checked;source.sendSuccess(()->Component.literal("已检查当前加载区块中的道路列："+count+"；未加载路段尚未检查"),false);
                }
                case "export" -> {
                    var path=WorldPlanRuntime.directory(level).resolve("roads.json");Files.createDirectories(path.getParent());
                    Map<String,Object> out=new LinkedHashMap<>();out.put("status",status(level));out.put("routes",d.routes.values().stream().map(d::resolved).toList());out.put("blocked",d.job==null?Map.of():d.job.blocked);
                    Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(out));source.sendSuccess(()->Component.literal("全网规划已导出："+path),false);
                }
                default -> {}
            }
            var state=status(level);
            source.sendSuccess(()->Component.literal(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer?describe(state,world):"驿道 "+new com.google.gson.Gson().toJson(state)),false);return 1;
        } catch(Exception e){source.sendFailure(Component.literal("驿道："+e.getMessage()));return 0;}
    }
    private static String describe(Map<String,Object> state,WorldPlan world) {
        String phase=(String)state.get("phase");long seconds=Math.round(((Number)state.get("planning_seconds")).doubleValue());
        var progress=(RoadNetworkPlanner.Progress)state.get("planning_progress");
        return switch(phase) {
            case "planning" -> "官道规划中：已规划 "+progress.plannedRoutes()+" / "+progress.requiredRoutes()+" 段，"+(progress.phase().equals("merging")?"正在合并全网路口":progress.from().isEmpty()?"正在准备":("当前 "+regionName(world,progress.from())+"—"+regionName(world,progress.to())))+"，已用 "+seconds+" 秒。使用 /myvillage roads status 刷新进度。";
            case "building" -> "官道施工中：已完成 "+state.get("completed_chunks")+" / "+state.get("chunks")+" 个区块，受保护区块 "+state.get("protected_chunks")+" 个；"+(state.get("mode").equals("bulk")?"全网批量施工":"玩家附近随探索施工")+"。";
            case "failed" -> "官道任务失败"+(seconds>0?"（规划用时 "+seconds+" 秒）":"")+"："+state.get("failure");
            case "complete" -> "官道路网施工完成，共连接 "+state.get("cities")+" 座省城。";
            case "paused" -> "官道任务已暂停；使用 /myvillage roads resume 继续。";
            default -> "官道任务尚未开始。";
        };
    }
    private static String regionName(WorldPlan world,String id){return world.regions().stream().filter(r->r.id().equals(id)).map(r->r.profile().name()).findFirst().orElse(id);}
    private static void requireIdentity(WorldPlan world,RoadRules rules,RoadPlan road) {
        if(!road.worldHash().equals(world.fingerprint())||!road.rulesHash().equals(rules.fingerprint))throw new IllegalStateException("路网与当前世界或配置不一致");
    }
    private static void tick(ServerTickEvent.Post event) {
        for(var level:event.getServer().getAllLevels()) {
            var generator=WorldPlanRuntime.generator(level);if(generator.isEmpty())continue;
            var world=generator.get().plan();var s=SESSIONS.computeIfAbsent(level,k->new Session());var d=RoadSavedData.get(level);
            try {
                if(d.phase.equals("idle")&&s.rules.automatic&&!d.manualPause)begin(level,world,s,d,"","");
                if(s.pending!=null&&s.pending.isDone()) {
                    var done=s.pending;s.pending=null;s.planningSeconds=(System.nanoTime()-s.started)/1e9;var result=done.get();
                    d.stage(result.routes(),result.cells());s.owners=owners(d);
                    for(var player:level.players())player.sendSystemMessage(Component.literal("官道路网规划完成："+world.cities().size()+" 城、"+result.routes().size()+" 条连接，开始"+(s.bulk?"批量":"随探索")+"施工。"));
                    LoggerFactory.getLogger(RoadRuntime.class).info("全省城路网规划完成：{} 城，{} 路段，{} 列，{} 秒",world.cities().size(),result.routes().size(),result.cells().size(),s.planningSeconds);
                }
                if(d.job==null||d.manualPause||!d.phase.equals("building"))continue;
                if(s.owners.isEmpty()){for(var r:d.routes.values())requireIdentity(world,s.rules,r.plan);s.owners=owners(d);}
                for(int n=0;n<s.rules.chunksPerTick;n++) {
                    var job=d.job;if(job.completed.size()==job.writeChunks.size()){d.completeJob();break;}
                    var chunk=next(level,s,job);if(chunk==null)break;
                    long key=chunk.getKey();var pos=new ChunkPos(key);
                    try {
                        if(s.bulk)level.getChunk(pos.x,pos.z);
                        RoadPaver.preflight(level,world,s.rules,s.owners,job,job.chunk(key));
                        RoadPaver.pave(level,world,s.rules,s.owners,job,chunk.getValue());job.markWritten(key);d.setDirty();
                    } catch(RuntimeException e){job.blocked.put(key,e.getMessage()==null?e.toString():e.getMessage());d.failure="部分区块受保护，未覆盖现场："+pos+" "+e.getMessage();d.setDirty();LoggerFactory.getLogger(RoadRuntime.class).warn(d.failure);}
                }
            } catch(Exception e){d.phase="failed";d.failure=String.valueOf(e.getCause()==null?e.getMessage():e.getCause().getMessage());d.setDirty();LoggerFactory.getLogger(RoadRuntime.class).error("全网道路任务失败：{}",d.failure,e);for(var player:level.players())player.sendSystemMessage(Component.literal("官道任务失败。使用 /myvillage roads status 查看原因。"));}
        }
    }
    private static Map<Long,RoadPlan> owners(RoadSavedData d){Map<Long,RoadPlan> result=new HashMap<>();for(var r:d.routes.values())for(var c:r.plan.cells())if(!c.existing())result.putIfAbsent(c.key(),r.plan);return result;}
    private static Map.Entry<Long,List<RoadNetworkGeometry.Change>> next(ServerLevel level,Session s,RoadSavedData.Job job) {
        Map.Entry<Long,List<RoadNetworkGeometry.Change>> best=null;long distance=Long.MAX_VALUE;
        for(var chunk:job.writeChunks) {
            long key=chunk.getKey();if(job.written(key)||job.blocked.containsKey(key))continue;if(s.bulk)return chunk;
            var p=new ChunkPos(key);if(level.getChunkSource().getChunkNow(p.x,p.z)==null)continue;
            for(var player:level.players()){var q=player.chunkPosition();int dx=Math.abs(p.x-q.x),dz=Math.abs(p.z-q.z);if(Math.max(dx,dz)>s.rules.buildRadiusChunks)continue;long score=(long)dx*dx+(long)dz*dz;if(score<distance){distance=score;best=chunk;}}
        }
        return best;
    }
    private static void stop(ServerStoppingEvent event){for(var s:SESSIONS.values())if(s.pending!=null)s.pending.cancel(true);SESSIONS.clear();if(worker!=null){worker.shutdownNow();worker=null;}}
}
