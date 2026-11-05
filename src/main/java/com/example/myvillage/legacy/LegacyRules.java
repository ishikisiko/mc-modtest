package com.example.myvillage.legacy;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Bundled author data. Runtime and resource generators read the same catalogue. */
public final class LegacyRules {
    public record Beast(String id, String name, String spawn, JsonObject data) {
        public double physical(String key) { return data.getAsJsonObject("physical").get(key).getAsDouble(); }
        public String mode(String animation) {
            for (var e : data.getAsJsonArray("moves")) {
                var move=e.getAsJsonObject();
                if (move.get("animation").getAsString().equals(animation)) return move.get("mode").getAsString();
            }
            throw new IllegalArgumentException("Missing beast action: "+id+"/"+animation);
        }
        public String material() { return data.getAsJsonArray("material").get(0).getAsString(); }
    }
    public record Art(ResourceLocation id, String mode, Optional<ResourceLocation> item,
            int cost, int cooldown, int duration, double range, double speed, double returnSpeed,
            double radius, double damage) {}
    private final Map<String, Beast> beasts;
    private final Map<ResourceLocation, Art> arts;
    private final JsonObject document;
    private LegacyRules(JsonObject root, Map<String,Beast> beasts, Map<ResourceLocation,Art> arts) {
        document=root;this.beasts=Map.copyOf(beasts);this.arts=Map.copyOf(arts);
    }
    private static final class Holder { static final LegacyRules INSTANCE=parse(read("/data/myvillage/legacy/encounters.json")); }
    public static LegacyRules get() { return Holder.INSTANCE; }
    public static JsonObject read(String path) {
        try (var stream=LegacyRules.class.getResourceAsStream(path)) {
            if(stream==null)throw new IllegalArgumentException("Missing "+path);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(IOException e) {throw new UncheckedIOException(e);}
    }
    public static JsonObject artsDocument() { return read("/data/myvillage/combat/legacy_arts.json"); }
    public static LegacyRules parse(JsonObject root) {
        if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("legacy schema");
        Map<String,Beast> beasts=new LinkedHashMap<>();
        for(var e:root.getAsJsonArray("mobs")) {
            var b=e.getAsJsonObject();String id=b.get("id").getAsString();
            if(!id.matches("[a-z][a-z0-9_]+") || beasts.put(id,new Beast(id,b.get("name").getAsString(),b.get("spawn").getAsString(),b.deepCopy()))!=null)throw new IllegalArgumentException("legacy beast id");
            for(String key:List.of("width","height","eye_height","shadow_radius"))bounded(b.getAsJsonObject("physical"),key,.05,5);
        }
        Map<ResourceLocation,Art> arts=new LinkedHashMap<>();
        for(var e:root.getAsJsonArray("arts")) {
            var a=e.getAsJsonObject();var id=ResourceLocation.fromNamespaceAndPath("myvillage",a.get("id").getAsString());
            String mode=a.get("mode").getAsString();
            if(!Set.of("sword","shadow","fire","ruler").contains(mode))throw new IllegalArgumentException("legacy mode");
            var item=a.get("item").isJsonNull()?Optional.<ResourceLocation>empty():Optional.of(ResourceLocation.fromNamespaceAndPath("myvillage",a.get("item").getAsString()));
            var art=new Art(id,mode,item,integer(a,"qi_cost",1,100),integer(a,"cooldown_ticks",20,1200),integer(a,"duration_ticks",1,600),bounded(a,"range",1,24),bounded(a,"speed",0,3),bounded(a,"return_speed",0,3),bounded(a,"radius",.1,8),bounded(a,"damage",0,4));
            if(arts.put(id,art)!=null)throw new IllegalArgumentException("duplicate legacy art");
        }
        if(beasts.size()!=4 || arts.size()!=4)throw new IllegalArgumentException("legacy requires four encounters and arts");
        for(var e:root.getAsJsonObject("runtime").entrySet())bounded(root.getAsJsonObject("runtime"),e.getKey(),.01,1000);
        return new LegacyRules(root.deepCopy(),beasts,arts);
    }
    private static double bounded(JsonObject o,String k,double min,double max) {
        double v=o.get(k).getAsDouble();if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException("legacy bounds: "+k);return v;
    }
    private static int integer(JsonObject o,String k,int min,int max) {
        double v=bounded(o,k,min,max);if(v!=Math.rint(v))throw new IllegalArgumentException("legacy integer: "+k);return (int)v;
    }
    public Collection<Beast> beasts() {return beasts.values();}
    public Beast beast(String id) {var b=beasts.get(id);if(b==null)throw new IllegalArgumentException("Unknown legacy beast "+id);return b;}
    public Optional<Art> art(ResourceLocation id) {return Optional.ofNullable(arts.get(id));}
    public Collection<Art> arts() {return arts.values();}
    public double number(String key) {return document.getAsJsonObject("runtime").get(key).getAsDouble();}
    public int ticks(String key) {return document.getAsJsonObject("runtime").get(key).getAsInt();}
    public JsonArray items() {return document.getAsJsonArray("items").deepCopy();}
}
