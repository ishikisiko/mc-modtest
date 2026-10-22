package com.example.myvillage.forging.array;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Structure, blueprint and timing share one bundled definition. Economics stay in forging rules. */
public final class ForgingArrayRules {
    public enum Role { AIR, CONTROLLER, FOUNDATION, CASING, HEARTH, PYLON }
    public record Cell(int x, int y, int z, Role role) { }
    private final List<Cell> cells;
    private final int duration, light, particleInterval;
    private final double ringSpeed, bob;
    private ForgingArrayRules(JsonObject root) {
        if (!root.keySet().equals(Set.of("version", "duration_ticks", "light_level", "particle_interval_ticks", "ring_degrees_per_tick", "item_bob", "cells")))
            throw new IllegalArgumentException("Unexpected forging array fields");
        integer(root, "version", 1, 1);
        duration=integer(root,"duration_ticks",1,30000);
        light=integer(root,"light_level",0,15);
        particleInterval=integer(root,"particle_interval_ticks",1,200);
        ringSpeed=decimal(root,"ring_degrees_per_tick",0,10);
        bob=decimal(root,"item_bob",0,.2);
        var parsed=new ArrayList<Cell>();var seen=new HashSet<String>();
        for(var e:root.getAsJsonArray("cells")) {
            var c=e.getAsJsonObject();
            if(!c.keySet().equals(Set.of("x","y","z","role")))throw new IllegalArgumentException("Cell fields");
            int x=integer(c,"x",-2,2),y=integer(c,"y",-1,3),z=integer(c,"z",0,4);
            if(!seen.add(x+","+y+","+z))throw new IllegalArgumentException("Duplicate cell");
            parsed.add(new Cell(x,y,z,Role.valueOf(c.get("role").getAsString().toUpperCase(Locale.ROOT))));
        }
        if(parsed.size()!=125 || parsed.stream().filter(c->c.role()==Role.CONTROLLER).count()!=1
                || parsed.stream().noneMatch(c->c.role()==Role.CONTROLLER&&c.x()==0&&c.y()==0&&c.z()==0)
                || parsed.stream().filter(c->c.role()==Role.HEARTH).count()!=1
                || parsed.stream().filter(c->c.role()==Role.PYLON).count()!=4)
            throw new IllegalArgumentException("Invalid 5x5x5 array layout");
        parsed.sort(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::z).thenComparingInt(Cell::x));
        cells=List.copyOf(parsed);
    }
    private static int integer(JsonObject o,String name,int min,int max) {
        var value=o.get(name);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(name);
        int n=value.getAsBigDecimal().intValueExact();
        if(n<min||n>max)throw new IllegalArgumentException(name);return n;
    }
    private static double decimal(JsonObject o,String name,double min,double max) {
        var value=o.get(name);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(name);
        double n=value.getAsDouble();if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException(name);return n;
    }
    public static ForgingArrayRules parse(Reader reader){
        try(var json=new JsonReader(reader)){
            json.setLenient(false);var root=read(json,0);if(json.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing JSON");
            return new ForgingArrayRules(root.getAsJsonObject());
        } catch(IOException error){throw new IllegalArgumentException("Invalid forging array rules",error);}
        catch(RuntimeException error){throw new IllegalArgumentException("Invalid forging array rules",error);}
    }
    private static JsonElement read(JsonReader reader,int depth)throws IOException{
        if(depth>8)throw new IllegalArgumentException("JSON too deep");
        return switch(reader.peek()){
            case BEGIN_OBJECT->{reader.beginObject();var o=new JsonObject();while(reader.hasNext()){var key=reader.nextName();if(o.has(key))throw new IllegalArgumentException("Duplicate key "+key);o.add(key,read(reader,depth+1));}reader.endObject();yield o;}
            case BEGIN_ARRAY->{reader.beginArray();var a=new JsonArray();while(reader.hasNext())a.add(read(reader,depth+1));reader.endArray();yield a;}
            case NUMBER->new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case STRING->new JsonPrimitive(reader.nextString());
            default->throw new IllegalArgumentException("Unexpected JSON token");
        };
    }
    private static class Holder { static final ForgingArrayRules INSTANCE=load(); }
    public static ForgingArrayRules get(){return Holder.INSTANCE;}
    private static ForgingArrayRules load(){
        try(var stream=ForgingArrayRules.class.getResourceAsStream("/data/myvillage/forging/array.json")){
            if(stream==null)throw new IllegalStateException("Missing forging array rules");
            return parse(new InputStreamReader(stream,StandardCharsets.UTF_8));
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public List<Cell> cells(){return cells;}
    public int duration(){return duration;} public int light(){return light;}
    public int particleInterval(){return particleInterval;} public double ringSpeed(){return ringSpeed;} public double bob(){return bob;}
}
