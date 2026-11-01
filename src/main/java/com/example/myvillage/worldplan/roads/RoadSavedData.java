package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

/** 全网共享列状态；区块按探索顺序完成，暂停与障碍均持久保存。 */
public final class RoadSavedData extends SavedData {
    public static final class Route {
        public final String id,from,to;
        public RoadPlan plan;
        public String phase="idle",failure="";
        public int completedChunks;
        public final List<WorldPlan.Position> blocked=new ArrayList<>();
        public Route(String a,String b){id=RoadNetworkGeometry.id(a,b);from=a;to=b;}
    }
    public static final class Job {
        public final Set<String> routeIds;
        public final List<Cell> target;
        public final Map<Long,Cell> before,after;
        public final List<Map.Entry<Long,List<RoadNetworkGeometry.Change>>> writeChunks;
        public final Set<Long> completed=new HashSet<>();
        public final Map<Long,String> blocked=new LinkedHashMap<>();
        private final Map<Long,List<Cell>> allChunks;
        private final Set<Long> writeKeys;
        public Job(Collection<String> ids,List<Cell> committed,List<Cell> target) {
            routeIds=Set.copyOf(ids);this.target=List.copyOf(target);before=RoadNetworkGeometry.index(committed);after=RoadNetworkGeometry.index(target);
            allChunks=RoadPlan.chunks(target);
            var writes=new TreeMap<Long,List<RoadNetworkGeometry.Change>>();
            for(var c:RoadNetworkGeometry.changes(committed,target))writes.computeIfAbsent(c.after().chunk(),k->new ArrayList<>()).add(c);
            writeChunks=List.copyOf(writes.entrySet());writeKeys=Set.copyOf(writes.keySet());
        }
        public boolean written(long chunk){return completed.contains(chunk);}
        public void markWritten(long chunk){if(!writeKeys.contains(chunk)||!completed.add(chunk))throw new IllegalStateException("增量区块重复或不在任务中");blocked.remove(chunk);}
        public List<Cell> chunk(long key){return Objects.requireNonNull(allChunks.get(key),"区块未包含在任务中");}
    }
    public final Map<String,Route> routes=new LinkedHashMap<>();
    public String selectedId="",phase="idle",failure="";
    public boolean manualPause;
    public List<Cell> committed=List.of();
    public Job job;
    public int revision;
    public RoadSavedData() {}
    public static RoadSavedData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(RoadSavedData::new,RoadSavedData::load),"myvillage_roads");}
    public Route selected(){return routes.get(selectedId);}
    public List<RoadPlan> completedPlans(){return routes.values().stream().filter(r->r.phase.equals("complete")).map(r->r.plan).toList();}
    public void stage(List<RoadPlan> plans,List<Cell> target) {
        if(job!=null)throw new IllegalStateException("已有增量施工任务");
        Set<String> ids=new LinkedHashSet<>();
        for(var p:plans){String id=RoadNetworkGeometry.id(p.from(),p.to());var r=routes.computeIfAbsent(id,k->new Route(p.from(),p.to()));if(!r.phase.equals("complete")){r.plan=p;r.phase="building";r.failure="";ids.add(id);}}
        if(ids.isEmpty()){if(!RoadNetworkGeometry.changes(committed,target).isEmpty())throw new IllegalStateException("无新增路线却出现道路改写");phase="complete";failure="";setDirty();return;}
        selectedId=ids.iterator().next();job=new Job(ids,committed,target);phase=manualPause?"paused":"building";failure="";setDirty();
    }
    public void completeJob() {
        if(job==null||job.completed.size()!=job.writeChunks.size())throw new IllegalStateException("增量施工未完成");
        committed=job.target;
        for(String id:job.routeIds){var r=routes.get(id);r.completedChunks=r.plan.chunks().size();r.phase="complete";r.failure="";}
        job=null;phase="complete";failure="";revision++;setDirty();
    }
    public List<Cell> materialized() {
        if(job==null)return committed;
        var result=RoadNetworkGeometry.index(committed);
        for(Cell c:job.target)if(!c.existing()&&job.written(c.chunk()))result.put(c.key(),c);
        return List.copyOf(result.values());
    }
    public RoadPlan resolved(Route route) {
        if(route==null||route.plan==null)return null;
        Map<Long,Cell> by=RoadNetworkGeometry.index(job==null?committed:job.target);var p=route.plan;
        return new RoadPlan(p.from(),p.to(),p.worldHash(),p.rulesHash(),p.width(),p.gateWidths(),p.obstacles(),p.centerline(),p.cells().stream().map(c->by.getOrDefault(c.key(),c)).toList());
    }
    static RoadSavedData load(CompoundTag tag,HolderLookup.Provider lookup) {
        if(tag.getInt("schema")!=4)throw new IllegalStateException("驿道路网存档格式不匹配，请使用当前开发版新世界");
        var rules=RoadRules.load();var data=new RoadSavedData();data.revision=tag.getInt("revision");
        data.phase=tag.getString("phase");data.failure=tag.getString("failure");data.manualPause=tag.getBoolean("manual_pause");
        if(data.revision<0||!Set.of("idle","planning","failed","building","paused","complete").contains(data.phase))throw new IllegalStateException("路网状态不合法");
        data.committed=unpack(tag.getIntArray("committed"),rules.maxNetworkColumns);
        var entries=tag.getList("routes",Tag.TAG_COMPOUND);if(entries.size()>rules.maxRoutes)throw new IllegalStateException("路网路线数越界");
        for(var element:entries) {
            var r=(CompoundTag)element;var route=new Route(r.getString("from"),r.getString("to"));
            if(!route.id.equals(r.getString("id"))||data.routes.putIfAbsent(route.id,route)!=null)throw new IllegalStateException("路线身份重复或不一致");
            route.phase=r.getString("phase");route.completedChunks=r.getInt("completed_chunks");
            if(!Set.of("building","complete").contains(route.phase)||route.completedChunks<0||route.completedChunks>rules.maxNetworkChunks)throw new IllegalStateException("路线进度不合法");
            route.plan=readPlan(r.getCompound("plan"),rules);
            if(!route.from.equals(route.plan.from())||!route.to.equals(route.plan.to()))throw new IllegalStateException("路线端点与计划不一致");
        }
        data.selectedId=tag.getString("selected");if(!data.selectedId.isEmpty()&&!data.routes.containsKey(data.selectedId))throw new IllegalStateException("选中路线不存在");
        if(tag.contains("job")) {
            var j=tag.getCompound("job");Set<String> ids=new LinkedHashSet<>();for(var id:j.getList("routes",Tag.TAG_STRING))if(!ids.add(id.getAsString()))throw new IllegalStateException("增量路线重复");
            if(ids.isEmpty()||ids.stream().anyMatch(id->!data.routes.containsKey(id)||!data.routes.get(id).phase.equals("building")))throw new IllegalStateException("增量任务没有有效路线");
            data.job=new Job(ids,data.committed,unpack(j.getIntArray("target"),rules.maxNetworkColumns));
            for(long key:j.getLongArray("completed"))data.job.markWritten(key);
            var blocked=j.getList("blocked",Tag.TAG_COMPOUND);if(blocked.size()>rules.maxNetworkChunks)throw new IllegalStateException("受保护区块数量越界");
            for(var b:blocked){var t=(CompoundTag)b;long key=t.getLong("chunk");if(!data.job.writeKeys.contains(key)||data.job.completed.contains(key)||data.job.blocked.putIfAbsent(key,t.getString("reason"))!=null)throw new IllegalStateException("受保护区块身份不合法");}
        }
        for(var r:data.routes.values())if(r.phase.equals("building")&&(data.job==null||!data.job.routeIds.contains(r.id)))throw new IllegalStateException("未完成路线缺少增量任务");
        if(data.phase.equals("building")&&data.job==null)throw new IllegalStateException("施工状态缺少任务");
        if(data.phase.equals("planning")){data.phase="idle";data.setDirty();}
        if(data.manualPause&&data.phase.equals("building"))data.phase="paused";
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        tag.putInt("schema",4);tag.putInt("revision",revision);tag.putString("phase",phase);tag.putString("failure",failure);tag.putBoolean("manual_pause",manualPause);tag.putString("selected",selectedId);tag.putIntArray("committed",pack(committed));
        var entries=new ListTag();for(var route:routes.values()) {
            var r=new CompoundTag();r.putString("id",route.id);r.putString("from",route.from);r.putString("to",route.to);r.putString("phase",route.phase);r.putInt("completed_chunks",route.completedChunks);r.put("plan",writePlan(route.plan));entries.add(r);
        }tag.put("routes",entries);
        if(job!=null){var j=new CompoundTag();var ids=new ListTag();job.routeIds.stream().sorted().forEach(id->ids.add(StringTag.valueOf(id)));j.put("routes",ids);j.putLongArray("completed",job.completed.stream().sorted().mapToLong(Long::longValue).toArray());j.putIntArray("target",pack(job.target));var blocked=new ListTag();job.blocked.forEach((key,reason)->{var b=new CompoundTag();b.putLong("chunk",key);b.putString("reason",reason);blocked.add(b);});j.put("blocked",blocked);tag.put("job",j);}
        return tag;
    }
    private static int[] pack(List<Cell> cells){int[] a=new int[cells.size()*6];for(int i=0;i<cells.size();i++){Cell c=cells.get(i);a[i*6]=c.x();a[i*6+1]=c.z();a[i*6+2]=c.feet2();a[i*6+3]=c.existing()?1:0;a[i*6+4]=c.band();a[i*6+5]=c.rail();}return a;}
    private static List<Cell> unpack(int[] a,int max){if(a.length%6!=0||a.length/6>max)throw new IllegalStateException("道路列存档越界或截断");List<Cell> out=new ArrayList<>();for(int i=0;i<a.length;i+=6){if(a[i+3]!=0&&a[i+3]!=1)throw new IllegalStateException("只读列标记不合法");out.add(new Cell(a[i],a[i+1],a[i+2],a[i+3]!=0,a[i+4],a[i+5]));}RoadNetworkGeometry.index(out);return List.copyOf(out);}
    private static CompoundTag writePlan(RoadPlan p){var t=new CompoundTag();t.putString("from",p.from());t.putString("to",p.to());t.putString("world_hash",p.worldHash());t.putString("rules_hash",p.rulesHash());t.putInt("width",p.width());t.putInt("from_gate_width",p.gateWidths().get(p.from()));t.putInt("to_gate_width",p.gateWidths().get(p.to()));t.putIntArray("cells",pack(p.cells()));int[] path=new int[p.centerline().size()*2];for(int i=0;i<p.centerline().size();i++){path[i*2]=p.centerline().get(i).x();path[i*2+1]=p.centerline().get(i).z();}t.putIntArray("path",path);int[] boxes=new int[p.obstacles().size()*4];for(int i=0;i<p.obstacles().size();i++){var b=p.obstacles().get(i);boxes[i*4]=b.minX();boxes[i*4+1]=b.minZ();boxes[i*4+2]=b.maxX();boxes[i*4+3]=b.maxZ();}t.putIntArray("obstacles",boxes);return t;}
    private static RoadPlan readPlan(CompoundTag t,RoadRules rules){String a=t.getString("from"),b=t.getString("to");int[] path=t.getIntArray("path"),boxes=t.getIntArray("obstacles");if(path.length%2!=0||path.length>200_000||boxes.length%4!=0||boxes.length/4>rules.maxObstacles||t.getInt("width")!=rules.width)throw new IllegalStateException("路线几何存档越界");List<RoadAStar.Point> line=new ArrayList<>();for(int i=0;i<path.length;i+=2)line.add(new RoadAStar.Point(path[i],path[i+1]));List<WorldRoadPlanner.Box> obstacles=new ArrayList<>();for(int i=0;i<boxes.length;i+=4)obstacles.add(new WorldRoadPlanner.Box(boxes[i],boxes[i+1],boxes[i+2],boxes[i+3]));return new RoadPlan(a,b,t.getString("world_hash"),t.getString("rules_hash"),rules.width,Map.of(a,t.getInt("from_gate_width"),b,t.getInt("to_gate_width")),obstacles,line,unpack(t.getIntArray("cells"),rules.maxColumns));}
}
