package com.example.myvillage.combat.elemental;

import com.google.gson.*;
import com.example.myvillage.stats.DamageType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** All balancing comes from the generated elemental document, authored under tools/elemental_arts. */
public final class ElementalRules {
    private static final JsonObject DOCUMENT=read();
    private static final JsonObject RULES=DOCUMENT.getAsJsonObject("rules");
    private static final Map<DamageType,ResourceKey<net.minecraft.world.damagesource.DamageType>> SOURCES=sources();
    private ElementalRules() {}
    private static JsonObject read() {
        try(var in=ElementalRules.class.getResourceAsStream("/data/myvillage/combat/elemental_arts.json")) {
            if(in==null)throw new IllegalArgumentException("Missing elemental arts");
            var doc=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            if(doc.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Elemental schema");
            return doc;
        } catch(Exception e) {throw new IllegalStateException("Invalid elemental rules",e);}
    }
    public static JsonObject document(){return DOCUMENT.deepCopy();}
    public static double number(String key,double min,double max){return number(RULES,key,min,max);}
    public static int integer(String key,int min,int max){return integer(RULES,key,min,max);}
    public static double number(JsonObject o,String key,double min,double max) {
        if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isNumber())throw new IllegalArgumentException(key);
        double n=o.get(key).getAsDouble();if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException(key);return n;
    }
    public static int integer(JsonObject o,String key,int min,int max) {
        double n=number(o,key,min,max);if(n!=Math.rint(n))throw new IllegalArgumentException(key);return (int)n;
    }
    private static Map<DamageType,ResourceKey<net.minecraft.world.damagesource.DamageType>> sources() {
        var result=new EnumMap<DamageType,ResourceKey<net.minecraft.world.damagesource.DamageType>>(DamageType.class);
        for(var type:DamageType.values())if(type!=DamageType.PHYSICAL)
            result.put(type,ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.parse("myvillage:elemental_"+type.id())));
        return Map.copyOf(result);
    }
    public static Optional<DamageType> type(DamageSource source) {
        return SOURCES.entrySet().stream().filter(e->source.is(e.getValue())).map(Map.Entry::getKey).findFirst();
    }
    public static DamageSource source(ServerPlayer player,DamageType type) {
        return new DamageSource(player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SOURCES.get(type)),player);
    }
}
