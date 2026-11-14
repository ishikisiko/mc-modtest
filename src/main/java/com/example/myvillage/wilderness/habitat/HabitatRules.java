package com.example.myvillage.wilderness.habitat;

import com.google.gson.*;
import java.util.*;

/** 作者数值冻结进世界规则；实体、规划和刷新共用这些参数。 */
public final class HabitatRules {
    public record Kind(String id,String name,String faction,List<String> members,int guards,boolean nightHunt,String nearKind) {}
    public record Block(int x,int y,int z,String block) {}
    public record Loot(String item,int min,int max) {}
    private final JsonObject json;
    public final String region;
    public final List<Kind> kinds;
    public final Map<String,List<Block>> templates;
    public final List<Loot> loot;
    public HabitatRules(JsonObject input) {
        if(input==null)throw new IllegalArgumentException("缺少东荒生态规则");
        json=input.deepCopy();
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
            if(!ids.add(id))throw new IllegalArgumentException("重复生态类型");
            var members=k.getAsJsonArray("members").asList().stream().map(JsonElement::getAsString).toList();
            if(members.isEmpty()||members.size()>12)throw new IllegalArgumentException("群落成员数错误");
            int guards=k.get("guard_count").getAsInt();if(guards<0||guards>members.size())throw new IllegalArgumentException("留守数量错误");
            ks.add(new Kind(id,k.get("name").getAsString(),k.get("faction").getAsString(),members,guards,k.get("night_hunt").getAsBoolean(),k.get("near_kind").getAsString()));
        }
        kinds=List.copyOf(ks);if(kinds.size()!=4)throw new IllegalArgumentException("当前必须完整声明四种生态群落");
        Map<String,List<Block>> blocks=new LinkedHashMap<>();
        for(var k:kinds){List<Block> list=new ArrayList<>();Set<String> positions=new HashSet<>();
            for(var element:json.getAsJsonObject("templates").getAsJsonArray(k.id())){var b=element.getAsJsonObject();
                var block=new Block(b.get("x").getAsInt(),b.get("y").getAsInt(),b.get("z").getAsInt(),b.get("block").getAsString());
                if(Math.abs(block.x())>integer("site_radius")||Math.abs(block.z())>integer("site_radius")||block.y()< -1||block.y()>5||!positions.add(block.x()+","+block.y()+","+block.z()))throw new IllegalArgumentException("据点施工越界或重复");
                list.add(block);
            }blocks.put(k.id(),List.copyOf(list));
        }templates=Map.copyOf(blocks);
        List<Loot> drops=new ArrayList<>();for(var element:json.getAsJsonArray("loot")){var l=element.getAsJsonObject();var drop=new Loot(l.get("item").getAsString(),l.get("min").getAsInt(),l.get("max").getAsInt());if(drop.min()<1||drop.max()<drop.min()||drop.max()>64)throw new IllegalArgumentException("临时战利品数量错误");drops.add(drop);}loot=List.copyOf(drops);if(loot.isEmpty())throw new IllegalArgumentException("缺少临时战利品");
    }
    public boolean flag(String key){return json.get(key).getAsBoolean();}
    public int integer(String key){return json.get(key).getAsInt();}
    public double number(String key){return json.get(key).getAsDouble();}
    public Kind kind(String id){return kinds.stream().filter(k->k.id().equals(id)).findFirst().orElseThrow();}
    public JsonObject json(){return json.deepCopy();}
}
