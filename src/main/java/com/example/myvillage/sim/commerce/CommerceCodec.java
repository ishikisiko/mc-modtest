package com.example.myvillage.sim.commerce;

import com.example.myvillage.sim.SimFormatException;
import com.example.myvillage.sim.model.WorldState;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.*;

/** Strict component: old payloads get an empty market, never replenished saved goods. */
public final class CommerceCodec {
    private CommerceCodec() { }
    public static JsonObject write(CommerceState state) {
        JsonObject out=new JsonObject();out.addProperty("schema",1);JsonObject markets=new JsonObject();
        state.markets.forEach((id,m)->{
            JsonObject o=new JsonObject();o.addProperty("revision",m.revision);o.addProperty("transactions",m.transactions);
            JsonObject items=new JsonObject();m.items.forEach(items::addProperty);o.add("items",items);
            JsonArray roles=new JsonArray();m.staffedRoles.forEach(roles::add);o.add("staffed_roles",roles);markets.add(id,o);
        });out.add("markets",markets);
        JsonObject rest=new JsonObject();state.restUntil.forEach(rest::addProperty);out.add("rest_until",rest);return out;
    }
    public static CommerceState read(JsonObject root,WorldState world) {
        CommerceState result=new CommerceState();if(root==null)return result;
        try {
            if(!root.keySet().equals(Set.of("schema","markets","rest_until"))||number(root.get("schema"),1,1)!=1)throw new IllegalArgumentException("schema");
            JsonObject markets=root.getAsJsonObject("markets");
            if(markets.size()>CommerceState.MAX_MARKETS)throw new IllegalArgumentException("market limit");
            for(var entry:markets.entrySet()) {
                if(!world.life.sites.containsKey(entry.getKey()))throw new IllegalArgumentException("unknown site");
                var o=entry.getValue().getAsJsonObject();
                if(!o.keySet().equals(Set.of("revision","transactions","items","staffed_roles")))throw new IllegalArgumentException("market fields");
                var m=new CommerceState.Market();m.revision=number(o.get("revision"),0,Long.MAX_VALUE);
                m.transactions=number(o.get("transactions"),0,m.revision);
                var items=o.getAsJsonObject("items");if(items.size()>CommerceState.MAX_ITEMS)throw new IllegalArgumentException("item limit");
                for(var item:items.entrySet()) {
                    if(item.getKey().length()>160||!item.getKey().matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))throw new IllegalArgumentException("item id");
                    m.items.put(item.getKey(),(int)number(item.getValue(),0,20_000));
                }
                for(var role:o.getAsJsonArray("staffed_roles")) {
                    if(!role.isJsonPrimitive()||!role.getAsJsonPrimitive().isString()||!CommerceRules.ROLES.contains(role.getAsString())
                            ||!m.staffedRoles.add(role.getAsString()))throw new IllegalArgumentException("staff role");
                }
                result.markets.put(entry.getKey(),m);
            }
            var rest=root.getAsJsonObject("rest_until");if(rest.size()>CommerceState.MAX_COOLDOWNS)throw new IllegalArgumentException("cooldown limit");
            for(var entry:rest.entrySet()) {
                if(!UUID.fromString(entry.getKey()).toString().equals(entry.getKey()))throw new IllegalArgumentException("player UUID");
                result.restUntil.put(entry.getKey(),number(entry.getValue(),0,Long.MAX_VALUE));
            }
            return result;
        }catch(RuntimeException e){throw new SimFormatException("commerce: invalid saved component",e);}
    }
    private static long number(JsonElement e,long lo,long hi) {
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("number");
        long n=e.getAsBigDecimal().longValueExact();if(n<lo||n>hi)throw new IllegalArgumentException("range");return n;
    }
}
