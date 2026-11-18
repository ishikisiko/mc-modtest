package com.example.myvillage.wilderness.habitat;

import com.google.gson.*;
import java.util.*;

/** 作者数值冻结进世界规则；实体、规划和刷新共用这些参数。 */
public final class HabitatRules {
    public record Kind(String id,String name,String faction,List<String> members,int guards,boolean nightHunt,String nearKind) {}
    public record Block(int x,int y,int z,String block) {}
    private final JsonObject json;
    public final String region;
    public final List<Kind> kinds;
    public final Map<String,List<Block>> templates;
    public HabitatRules(JsonObject input) {
        if(input==null)throw new IllegalArgumentException("缺少东荒生态规则");
        if(!input.keySet().equals(Set.of("resource_sha256")))throw new IllegalArgumentException("生态规则必须使用精确资源引用");
        try(var stream=HabitatRules.class.getResourceAsStream("/data/yuanxian/wilderness/habitats.json")){
            if(stream==null)throw new IllegalArgumentException("缺少生态模板资源");
            byte[] bytes=stream.readAllBytes();
            if(bytes.length>1024*1024||!com.example.myvillage.worldplan.WorldPlanRules.sha256(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).equals(input.get("resource_sha256").getAsString()))throw new IllegalArgumentException("生态模板摘要不一致");
            json=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException error){throw new IllegalArgumentException("生态模板读取失败",error);}
        if(integer("schema")!=1)throw new IllegalArgumentException("未知生态规则格式");
        region=json.get("region").getAsString();
        if(!region.equals("donghuang"))throw new IllegalArgumentException("当前生态群落仅支持东荒");
        for(String key:List.of("sites_per_kind","candidate_attempts","minimum_separation","region_radius","site_radius","maximum_slope","reservation_margin","road_margin","activation_radius","spawn_min_distance","refresh_safe_distance","refresh_months","max_active_members","tick_interval","alarm_radius","conflict_radius","pursuit_radius","patrol_radius","construction_clearance","relocation_radius","paired_distance_min","paired_distance_max","navigation_interval_ticks","alarm_interval_ticks","route_step_ticks","guard_radius","max_surface_y","construction_radius","spawn_attempts","spawn_per_tick","conflict_scan_ticks"))
            if(integer(key)<1)throw new IllegalArgumentException("生态参数必须为正数："+key);
        if(integer("sites_per_kind")>16||integer("candidate_attempts")>4096||integer("site_radius")>16||integer("max_active_members")>128
                ||integer("spawn_min_distance")>=integer("activation_radius"))throw new IllegalArgumentException("生态规则超出容量或距离边界");
        for(String key:List.of("retreat_health_fraction","leader_retreat_fraction"))if(number(key)<=0||number(key)>=1)throw new IllegalArgumentException("撤退比例错误");
        if(number("navigation_speed")<=0||number("navigation_speed")>3)throw new IllegalArgumentException("导航速度错误");
        List<Kind> ks=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(var element:json.getAsJsonArray("kinds")){
            var k=element.getAsJsonObject();String id=k.get("id").getAsString();
            String nearKind=k.get("near_kind").getAsString();
            if(!nearKind.isEmpty()&&!ids.contains(nearKind))throw new IllegalArgumentException("相邻生态类型必须先声明："+nearKind);
            if(!ids.add(id))throw new IllegalArgumentException("重复生态类型");
            var members=k.getAsJsonArray("members").asList().stream().map(JsonElement::getAsString).toList();
            if(members.isEmpty()||members.size()>12)throw new IllegalArgumentException("群落成员数错误");
            int guards=k.get("guard_count").getAsInt();if(guards<0||guards>members.size())throw new IllegalArgumentException("留守数量错误");
            ks.add(new Kind(id,k.get("name").getAsString(),k.get("faction").getAsString(),members,guards,k.get("night_hunt").getAsBoolean(),nearKind));
        }
        kinds=List.copyOf(ks);if(kinds.size()!=4)throw new IllegalArgumentException("当前必须完整声明四种生态群落");
        JsonObject templateData=json.getAsJsonObject("templates");
        Map<String,List<Block>> blocks=new LinkedHashMap<>();
        for(var k:kinds){List<Block> list=new ArrayList<>();Set<String> positions=new HashSet<>();
            for(var element:templateData.getAsJsonArray(k.id())){var b=element.getAsJsonObject();
                var block=new Block(b.get("x").getAsInt(),b.get("y").getAsInt(),b.get("z").getAsInt(),b.get("block").getAsString());
                if(Math.abs(block.x())>integer("site_radius")||Math.abs(block.z())>integer("site_radius")||block.y()< -1||block.y()>5||!positions.add(block.x()+","+block.y()+","+block.z()))throw new IllegalArgumentException("据点施工越界或重复");
                list.add(block);
            }blocks.put(k.id(),List.copyOf(list));
        }templates=Map.copyOf(blocks);
    }
    public boolean flag(String key){return json.get(key).getAsBoolean();}
    public int integer(String key){return json.get(key).getAsInt();}
    public double number(String key){return json.get(key).getAsDouble();}
    public Kind kind(String id){return kinds.stream().filter(k->k.id().equals(id)).findFirst().orElseThrow();}
    public JsonObject json(){return json.deepCopy();}
}
