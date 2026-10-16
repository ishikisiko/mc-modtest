package com.example.myvillage.forging;

import com.google.gson.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Generated collision and authored feedback settings share the model's geometry source. */
public final class ForgingDingVisuals {
    public static final String RESOURCE = "/data/myvillage/forging/ding.json";
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Box {
            if (!Double.isFinite(x0+y0+z0+x1+y1+z1) || x0<0 || y0<0 || z0<0
                    || x1>16 || y1>24 || z1>16 || x0>=x1 || y0>=y1 || z0>=z1)
                throw new IllegalArgumentException("Invalid ding collision box");
        }
        public Box clockwise() { return new Box(16-z1,y0,x0,16-z0,y1,x1); }
        public VoxelShape shape() { return Block.box(x0,y0,z0,x1,y1,z1); }
    }
    public record Feedback(int durationTicks, int lightLevel, int sparkCount, double sparkSpread,
                           double sparkSpeed, double ambientProbability, float soundVolume, float soundPitch) {}
    private final List<Box> boxes;
    private final Map<Direction,VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final Feedback feedback;
    private final double hearthX,hearthY,hearthZ;

    private ForgingDingVisuals(JsonObject root) {
        fields(root,"version","collision","hearth","feedback");
        integer(root,"version",1,1);
        List<Box> parsed = new ArrayList<>();
        var array = root.getAsJsonArray("collision");
        if (array.isEmpty() || array.size()>64) throw new IllegalArgumentException("Invalid ding collision count");
        for (var element:array) {
            var b=element.getAsJsonArray();
            if (b.size()!=6) throw new IllegalArgumentException("Ding box must have six coordinates");
            parsed.add(new Box(coordinate(b,0),coordinate(b,1),coordinate(b,2),coordinate(b,3),coordinate(b,4),coordinate(b,5)));
        }
        boxes=List.copyOf(parsed);
        for (Direction direction:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            VoxelShape shape=Shapes.empty();
            for (Box b:parsed) shape=Shapes.or(shape,b.shape());
            shapes.put(direction,shape.optimize());
            parsed=parsed.stream().map(Box::clockwise).toList();
        }
        var hearth=root.getAsJsonArray("hearth");
        if (hearth.size()!=3) throw new IllegalArgumentException("Ding hearth must have three coordinates");
        hearthX=coordinate(hearth,0)/16;hearthY=coordinate(hearth,1)/16;hearthZ=coordinate(hearth,2)/16;
        var f=root.getAsJsonObject("feedback");
        fields(f,"duration_ticks","light_level","spark_count","spark_spread","spark_speed","ambient_probability","sound_volume","sound_pitch");
        feedback=new Feedback(integer(f,"duration_ticks",1,200),integer(f,"light_level",0,15),integer(f,"spark_count",0,32),
                number(f,"spark_spread",0,1),number(f,"spark_speed",0,1),number(f,"ambient_probability",0,1),
                (float)number(f,"sound_volume",0,1),(float)number(f,"sound_pitch",.5,2));
    }
    public List<Box> boxes() { return boxes; }
    public VoxelShape shape(Direction facing) {
        if (!shapes.containsKey(facing)) throw new IllegalArgumentException("Horizontal ding facing required");
        return shapes.get(facing);
    }
    public Feedback feedback() { return feedback; }
    public double hearthX() { return hearthX; }
    public double hearthY() { return hearthY; }
    public double hearthZ() { return hearthZ; }
    private static final class Holder { static final ForgingDingVisuals VALUE=load(); }
    public static ForgingDingVisuals get() { return Holder.VALUE; }
    private static ForgingDingVisuals load() {
        try (var stream=ForgingDingVisuals.class.getResourceAsStream(RESOURCE)) {
            if (stream==null) throw new IllegalStateException("Missing "+RESOURCE);
            return parse(new InputStreamReader(stream,StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static ForgingDingVisuals parse(Reader reader) {
        try { return new ForgingDingVisuals(JsonParser.parseReader(reader).getAsJsonObject()); }
        catch (IllegalStateException|NullPointerException e) { throw new IllegalArgumentException("Invalid ding presentation",e); }
    }
    private static void fields(JsonObject o,String... keys) {
        if (!o.keySet().equals(Set.of(keys))) throw new IllegalArgumentException("Unexpected ding fields");
    }
    private static int integer(JsonObject o,String key,int min,int max) {
        double n=number(o,key,min,max);
        if (n!=(int)n) throw new IllegalArgumentException("Integer required: "+key);
        return (int)n;
    }
    private static double number(JsonObject o,String key,double min,double max) {
        var e=o.get(key);
        if (e==null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Number required: "+key);
        double n=e.getAsDouble();
        if (!Double.isFinite(n) || n<min || n>max) throw new IllegalArgumentException("Out of bounds: "+key);
        return n;
    }
    private static double coordinate(JsonArray values,int index) {
        var o=new JsonObject();o.add("coordinate",values.get(index));return number(o,"coordinate",0,24);
    }
}
