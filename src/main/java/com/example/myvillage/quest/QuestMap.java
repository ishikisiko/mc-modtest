package com.example.myvillage.quest;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Immutable authored geometry, lighting probes and finite encounter roster. */
public record QuestMap(String id,String name,int[] size,int[] origin,int[] entry,int[] exit,String realmPool,
                       int stones,int reputation,List<String> palette,List<Run> runs,List<Group> groups,List<int[]> lightProbes) {
    public record Run(int x,int y,int z,int height,int material){}
    public record Actor(String kind,String entity,String weapon,boolean boss,int[] pos){}
    public record Group(String id,String name,boolean required,int stage,List<Actor> actors){}
    private static final Map<String,QuestMap> CACHE=new HashMap<>();
    public static QuestMap get(String id){return CACHE.computeIfAbsent(id,QuestMap::load);}
    private static int[] point(JsonElement value){
        if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()!=3)throw new IllegalArgumentException("远征坐标无效");
        int[] p=new int[3];for(int i=0;i<3;i++)p[i]=(int)Math.round(value.getAsJsonArray().get(i).getAsDouble());return p;
    }
    public static QuestMap parse(JsonObject j){
        if(j.get("schema").getAsInt()!=1)throw new IllegalArgumentException("远征场景版本无效");
        var size=point(j.get("size"));for(int value:size)if(value<1||value>512)throw new IllegalArgumentException("远征尺寸越界");
        var palette=new ArrayList<String>();j.getAsJsonArray("palette").forEach(v->palette.add(v.getAsString()));
        if(palette.isEmpty()||palette.size()>1024||!palette.getFirst().equals("minecraft:air"))throw new IllegalArgumentException("远征材质表无效");
        var runs=new ArrayList<Run>();
        for(var v:j.getAsJsonArray("runs")){
            var a=v.getAsJsonArray();if(a.size()!=5)throw new IllegalArgumentException("远征构件无效");
            var r=new Run(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),a.get(3).getAsInt(),a.get(4).getAsInt());
            if(r.x()<0||r.x()>=size[0]||r.z()<0||r.z()>=size[2]||r.y()<0||r.height()<1||r.y()+r.height()>size[1]||r.material()<1||r.material()>=palette.size())
                throw new IllegalArgumentException("远征构件越界");
            runs.add(r);
        }
        var groups=new ArrayList<Group>();var seen=new HashSet<String>();
        for(var v:j.getAsJsonArray("encounters")){
            var g=v.getAsJsonObject();String gid=g.get("id").getAsString();if(!seen.add(gid))throw new IllegalArgumentException("远征遭遇重复");
            var actors=new ArrayList<Actor>();
            for(var av:g.getAsJsonArray("actors")){var a=av.getAsJsonObject();actors.add(new Actor(a.get("kind").getAsString(),a.get("entity").getAsString(),a.get("weapon").getAsString(),a.get("boss").getAsBoolean(),point(a.get("pos"))));}
            if(actors.isEmpty()||actors.size()>6)throw new IllegalArgumentException("远征敌群越界");
            groups.add(new Group(gid,g.get("name").getAsString(),g.get("required").getAsBoolean(),g.get("stage").getAsInt(),List.copyOf(actors)));
        }
        if(groups.isEmpty()||groups.stream().noneMatch(Group::required))throw new IllegalArgumentException("远征缺少目标");
        var probes=new ArrayList<int[]>();j.getAsJsonArray("lighting_probes").forEach(v->probes.add(point(v)));
        if(probes.isEmpty())throw new IllegalArgumentException("远征缺少光照检查点");
        return new QuestMap(j.get("id").getAsString(),j.get("name").getAsString(),size,point(j.get("origin")),point(j.get("entry")),point(j.get("exit")),
                j.get("realm_pool").getAsString(),j.get("stones").getAsInt(),j.get("reputation").getAsInt(),List.copyOf(palette),List.copyOf(runs),List.copyOf(groups),List.copyOf(probes));
    }
    private static QuestMap load(String id){
        if(!QuestRules.get().maps().contains(id))throw new IllegalArgumentException("未知远征地图");
        try(var in=QuestMap.class.getResourceAsStream("/data/myvillage/expedition/"+id+".json.gz")){
            if(in==null)throw new IllegalArgumentException("远征地图资源缺失");
            byte[] bytes=in.readAllBytes();
            if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(QuestRules.get().hash(id)))throw new IllegalArgumentException("远征地图资源摘要不符");
            try(var gzip=new GZIPInputStream(new java.io.ByteArrayInputStream(bytes))){
                byte[] raw=gzip.readNBytes(64_000_001);if(raw.length>64_000_000)throw new IllegalArgumentException("远征地图资源过大");
                QuestMap result=parse(JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject());
                if(!result.id().equals(id))throw new IllegalArgumentException("远征地图身份不符");return result;
            }
        }catch(Exception error){throw new IllegalStateException("无法读取远征地图："+id,error);}
    }
}
