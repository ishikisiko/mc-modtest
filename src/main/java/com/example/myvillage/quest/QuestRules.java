package com.example.myvillage.quest;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Generated bundled configuration; no client-selected difficulty or duplicated realm table. */
public final class QuestRules {
    private static QuestRules bundled;
    public final JsonObject json;
    public QuestRules(JsonObject json) {
        this.json=json.deepCopy();
        if(integer("schema")!=1||integer("max_party")<1||integer("max_party")>4||integer("blocks_per_tick")<1
                ||integer("tick_interval")<1||integer("rescue_count")<0||integer("retention_millis")<1000)
            throw new IllegalArgumentException("远征配置无效");
        if(json.getAsJsonArray("party_health").size()!=integer("max_party"))throw new IllegalArgumentException("远征人数倍率缺失");
    }
    public int integer(String key){return json.get(key).getAsInt();}
    public double number(String key){return json.get(key).getAsDouble();}
    public int light(String key){return json.getAsJsonObject("lighting").get(key).getAsInt();}
    public double partyHealth(int count){if(count<1||count>integer("max_party"))throw new IllegalArgumentException("人数越界");return json.getAsJsonArray("party_health").get(count-1).getAsDouble();}
    public List<String> maps(){var result=new ArrayList<String>();json.getAsJsonArray("maps").forEach(v->result.add(v.getAsString()));return List.copyOf(result);}
    public String hash(String map){return json.getAsJsonObject("manifest").getAsJsonObject(map).get("sha256").getAsString();}
    public static QuestRules get(){
        if(bundled==null)try(var in=QuestRules.class.getResourceAsStream("/data/myvillage/expedition/rules.json")){
            if(in==null)throw new IllegalStateException("远征配置资源缺失");
            bundled=new QuestRules(JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject());
        }catch(Exception error){throw new IllegalStateException("无法读取远征配置",error);}
        return bundled;
    }
}
