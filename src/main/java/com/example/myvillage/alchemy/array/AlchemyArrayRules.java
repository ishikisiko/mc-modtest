package com.example.myvillage.alchemy.array;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bundled immutable layout and presentation tuning; recipe economics remain in AlchemyRules. */
public final class AlchemyArrayRules {
    public enum Role { AIR, CONTROLLER, CASING, INPUT, OUTPUT, FUEL, VENT }
    public record Cell(int x, int y, int z, Role role) { }
    private final List<Cell> cells;
    private final int smokeIntervalTicks, lightLevel;
    private final double ringSpeedDegrees;
    private AlchemyArrayRules(JsonObject root) {
        fields(root,"version","smoke_interval_ticks","light_level","ring_speed_degrees","cells");
        if (integer(root,"version",1,1) != 1) throw new IllegalArgumentException("array version");
        smokeIntervalTicks=integer(root,"smoke_interval_ticks",1,200);
        lightLevel=integer(root,"light_level",0,15);
        if(!root.get("ring_speed_degrees").isJsonPrimitive()||!root.getAsJsonPrimitive("ring_speed_degrees").isNumber())throw new IllegalArgumentException("array ring speed type");
        ringSpeedDegrees=root.get("ring_speed_degrees").getAsDouble();
        if (!Double.isFinite(ringSpeedDegrees)||ringSpeedDegrees<=0||ringSpeedDegrees>20) throw new IllegalArgumentException("array ring speed");
        List<Cell> parsed=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for(JsonElement value:root.getAsJsonArray("cells")) {
            JsonObject c=value.getAsJsonObject();fields(c,"x","y","z","role");int x=integer(c,"x",-1,1),y=integer(c,"y",-1,1),z=integer(c,"z",0,2);
            if(!seen.add(x+","+y+","+z)) throw new IllegalArgumentException("Duplicate array cell");
            parsed.add(new Cell(x,y,z,Role.valueOf(c.get("role").getAsString().toUpperCase(Locale.ROOT))));
        }
        if(parsed.size()!=27) throw new IllegalArgumentException("Array requires all 27 cells");
        Map<Role,Long> counts=new EnumMap<>(Role.class);
        parsed.forEach(c->counts.merge(c.role(),1L,Long::sum));
        for(Role r:Role.values()) if(counts.getOrDefault(r,0L)!=(r==Role.CASING?21L:1L)) throw new IllegalArgumentException("Invalid array role counts");
        if(parsed.stream().noneMatch(c->c.x()==0&&c.y()==0&&c.z()==0&&c.role()==Role.CONTROLLER)) throw new IllegalArgumentException("Controller must be origin");
        parsed.sort(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::z).thenComparingInt(Cell::x)); cells=List.copyOf(parsed);
    }
    private static int integer(JsonObject o,String name,int min,int max) {
        JsonElement e=o.get(name);if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(name);
        int n=e.getAsBigDecimal().intValueExact();
        if(n<min||n>max) throw new IllegalArgumentException(name); return n;
    }
    private static class Holder { static final AlchemyArrayRules INSTANCE=load(); }
    public static AlchemyArrayRules get(){return Holder.INSTANCE;}
    private static AlchemyArrayRules load() {
        try(InputStream stream=AlchemyArrayRules.class.getResourceAsStream("/data/myvillage/alchemy/array.json")) {
            if(stream==null) throw new IllegalStateException("Missing alchemy array rules");
            return parse(new InputStreamReader(stream,StandardCharsets.UTF_8));
        } catch(IOException e){throw new IllegalStateException(e);}
    }
    public static AlchemyArrayRules parse(Reader reader){
        try(JsonReader json=new JsonReader(reader)){
            json.setLenient(false);JsonElement root=read(json,0);if(json.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing array JSON");
            return new AlchemyArrayRules(root.getAsJsonObject());
        }catch(IOException|IllegalStateException|ArithmeticException e){throw new IllegalArgumentException("Invalid array JSON",e);}
    }
    private static JsonElement read(JsonReader reader,int depth)throws IOException{
        if(depth>8)throw new IllegalArgumentException("Array JSON too deep");
        return switch(reader.peek()){
            case BEGIN_OBJECT->{reader.beginObject();JsonObject obj=new JsonObject();while(reader.hasNext()){String key=reader.nextName();if(obj.has(key))throw new IllegalArgumentException("Duplicate key "+key);obj.add(key,read(reader,depth+1));}reader.endObject();yield obj;}
            case BEGIN_ARRAY->{reader.beginArray();JsonArray a=new JsonArray();while(reader.hasNext())a.add(read(reader,depth+1));reader.endArray();yield a;}
            case NUMBER->new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case STRING->new JsonPrimitive(reader.nextString());
            default->throw new IllegalArgumentException("Unexpected array JSON token");
        };
    }
    private static void fields(JsonObject obj,String... expected){if(!obj.keySet().equals(Set.of(expected)))throw new IllegalArgumentException("Unexpected array fields");}
    public List<Cell> cells(){return cells;}
    public int smokeIntervalTicks(){return smokeIntervalTicks;}
    public int lightLevel(){return lightLevel;}
    public double ringSpeedDegrees(){return ringSpeedDegrees;}
}
