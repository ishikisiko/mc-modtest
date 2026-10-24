package com.example.myvillage.story.entrance;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

/** Entrance receipt is independent of the frozen plan and personal accepted chapters. */
final class EntranceData extends SavedData {
    static final String NAME="myvillage_qianzhen_entrance";
    enum Phase { EMPTY, BUILDING, COMPLETE, REUSED, UNAVAILABLE, FAILED }
    Phase phase=Phase.EMPTY;
    EntrancePlan plan;
    EntranceRules rules;
    String frozenRules="", reason="";
    EntrancePlan.Cell notice;
    int noticeY;
    final Set<UUID> discovered=new HashSet<>(),tracking=new HashSet<>();

    static EntranceData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(()->{
            if(Files.exists(path(level)))throw new IllegalStateException("阡镇入口记录损坏，不能自动重新部署");
            return new EntranceData();
        },EntranceData::load),NAME);
    }
    static java.nio.file.Path path(ServerLevel level) {return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(NAME+".dat");}
    void begin(EntrancePlan p,String rawRules) {
        if(phase!=Phase.EMPTY)throw new IllegalStateException("阡镇入口不能重放");
        plan=p;frozenRules=rawRules;rules=EntranceRules.read(new StringReader(rawRules));
        phase=Phase.BUILDING;notice=p.notice();noticeY=p.groundY()+1;setDirty();
    }
    boolean discover(UUID id) {
        if(discovered.size()>=10_000&&!discovered.contains(id))return false;
        if(discovered.add(id)){setDirty();return true;}return false;
    }
    void track(UUID id,boolean on) {
        if(!discovered.contains(id))throw new IllegalStateException("未知招募不得追踪");
        if(on?tracking.add(id):tracking.remove(id))setDirty();
    }
    void checkpoint(ServerLevel level)throws IOException {
        IOUtilities.waitUntilIOWorkerComplete();Files.createDirectories(path(level).getParent());
        CompoundTag root=new CompoundTag();root.put("data",save(new CompoundTag(),level.registryAccess()));
        NbtUtils.addCurrentDataVersion(root);IOUtilities.writeNbtCompressed(root,path(level));setDirty(false);
    }
    static EntranceData load(CompoundTag tag,HolderLookup.Provider registries) {
        if(!tag.contains("phase",Tag.TAG_STRING)||!tag.contains("reason",Tag.TAG_STRING)||!tag.contains("rules",Tag.TAG_STRING))throw new IllegalStateException("入口状态字段缺失");
        if(!tag.contains("schema",Tag.TAG_INT)||tag.getInt("schema")!=1)throw new IllegalStateException("阡镇入口存档版本非法");
        EntranceData d=new EntranceData();
        try {d.phase=Phase.valueOf(tag.getString("phase"));}catch(IllegalArgumentException bad){throw new IllegalStateException("入口状态非法",bad);}
        d.reason=tag.getString("reason");d.frozenRules=tag.getString("rules");
        if(d.reason.length()>512||d.frozenRules.length()>16_384)throw new IllegalStateException("入口存档超限");
        if(!d.frozenRules.isEmpty())d.rules=EntranceRules.read(new StringReader(d.frozenRules));
        if(tag.contains("plan",Tag.TAG_COMPOUND)) {
            var p=tag.getCompound("plan");var rect=p.getIntArray("rect");var origin=p.getIntArray("origin");
            if(rect.length!=4||origin.length!=3||d.rules==null)throw new IllegalStateException("入口空间记录不完整");
            int[] raw=p.getIntArray("route");if(raw.length<2||raw.length%2!=0||raw.length>2*d.rules.maxRouteLength())throw new IllegalStateException("入口路线超限");
            var route=new java.util.ArrayList<EntrancePlan.Cell>();
            for(int i=0;i<raw.length;i+=2) {
                var cell=new EntrancePlan.Cell(raw[i],raw[i+1]);
                if(!route.isEmpty()) {var prev=route.getLast();if(Math.abs((long)prev.x()-cell.x())+Math.abs((long)prev.z()-cell.z())!=1)throw new IllegalStateException("入口路线不连续");}
                route.add(cell);
            }
            int[] notice=p.getIntArray("notice");if(notice.length!=2)throw new IllegalStateException("入口木牌记录缺失");
            long width=(long)rect[2]-rect[0]+1,depth=(long)rect[3]-rect[1]+1;
            if(width<=0||depth<=0||width>1024||depth>1024||width*depth>100_000)throw new IllegalStateException("入口矩形超限");
            if(Math.abs((long)origin[0])>30_000_000||Math.abs((long)origin[2])>30_000_000||origin[1]<-64||origin[1]>320)
                throw new IllegalStateException("入口原点非法");
            for(int v:new int[]{rect[0],rect[2]})if(Math.abs((long)v-origin[0])>1024)throw new IllegalStateException("入口矩形偏移超限");
            for(int v:new int[]{rect[1],rect[3]})if(Math.abs((long)v-origin[2])>1024)throw new IllegalStateException("入口矩形偏移超限");
            for(var c:route)if(Math.abs((long)c.x()-origin[0])+Math.abs((long)c.z()-origin[2])>2L*d.rules.maxRouteLength())throw new IllegalStateException("入口路线偏移超限");
            d.plan=new EntrancePlan(origin[0],origin[1],origin[2],new EntrancePlan.Rect(rect[0],rect[1],rect[2],rect[3]),route,new EntrancePlan.Cell(notice[0],notice[1]));
            if(d.plan.chunks(d.rules).size()>d.rules.maxChunks())throw new IllegalStateException("入口面积超限");
        }
        if(tag.contains("notice",Tag.TAG_INT_ARRAY)) {
            int[] a=tag.getIntArray("notice");if(a.length!=3)throw new IllegalStateException("入口告示坐标非法");
            if(Math.abs((long)a[0])>30_000_000||Math.abs((long)a[2])>30_000_000||a[1]<-64||a[1]>320)throw new IllegalStateException("入口木牌坐标超限");
            d.notice=new EntrancePlan.Cell(a[0],a[2]);d.noticeY=a[1];
        }
        for(String key:List.of("discovered","tracking")) {
            if(!tag.contains(key,Tag.TAG_LIST))throw new IllegalStateException("入口个人记录缺失");
            var list=tag.getList(key,Tag.TAG_STRING);if(list.size()>10_000)throw new IllegalStateException("入口个人记录超限");
            var target=key.equals("discovered")?d.discovered:d.tracking;
            for(var entry:list)if(!target.add(UUID.fromString(entry.getAsString())))throw new IllegalStateException("入口个人记录重复");
        }
        if(!d.discovered.containsAll(d.tracking))throw new IllegalStateException("未发现的入口不能已追踪");
        if((d.phase==Phase.BUILDING||d.phase==Phase.COMPLETE)&&(d.plan==null||d.notice==null))throw new IllegalStateException("入口部署记录缺失");
        if((d.phase==Phase.BUILDING||d.phase==Phase.COMPLETE)&&(!d.notice.equals(d.plan.notice())||d.noticeY!=d.plan.groundY()+1))throw new IllegalStateException("入口木牌与部署计划不符");
        if(d.phase==Phase.REUSED&&(d.rules==null||d.notice==null))throw new IllegalStateException("复用入口配置缺失");
        if(d.phase==Phase.EMPTY&&(d.plan!=null||d.notice!=null||!d.discovered.isEmpty()))throw new IllegalStateException("有内容的入口不能标为空");
        return d;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putInt("schema",1);tag.putString("phase",phase.name());tag.putString("reason",reason);tag.putString("rules",frozenRules);
        if(plan!=null) {
            var p=new CompoundTag();var b=plan.scene();p.putIntArray("origin",new int[]{plan.originX(),plan.groundY(),plan.originZ()});
            p.putIntArray("rect",new int[]{b.minX(),b.minZ(),b.maxX(),b.maxZ()});
            int[] route=new int[plan.route().size()*2];for(int i=0;i<plan.route().size();i++){route[i*2]=plan.route().get(i).x();route[i*2+1]=plan.route().get(i).z();}
            p.putIntArray("route",route);p.putIntArray("notice",new int[]{plan.notice().x(),plan.notice().z()});tag.put("plan",p);
        }
        if(notice!=null)tag.putIntArray("notice",new int[]{notice.x(),noticeY,notice.z()});
        for(String key:List.of("discovered","tracking")) {
            ListTag list=new ListTag();(key.equals("discovered")?discovered:tracking).stream().map(UUID::toString).sorted().forEach(s->list.add(StringTag.valueOf(s)));tag.put(key,list);
        }
        return tag;
    }
}
