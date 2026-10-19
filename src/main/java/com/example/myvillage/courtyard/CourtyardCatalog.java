package com.example.myvillage.courtyard;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One semantic family per item, bounded style variants, per-cell render/collision geometry. */
public final class CourtyardCatalog {
    public static final String RESOURCE="/data/myvillage/courtyard/assemblies.json";
    public record Box(int x0,int y0,int z0,int x1,int y1,int z1) {
        public Box {if(x0<0||y0<0||z0<0||x1>16||y1>16||z1>16||x0>=x1||y0>=y1||z0>=z1)throw new IllegalArgumentException("Invalid assembly box");}
        public Box clockwise(){return new Box(16-z1,y0,x0,16-z0,y1,x1);}
        public VoxelShape shape(){return Block.box(x0,y0,z0,x1,y1,z1);}
    }
    public static final class Part {
        private final List<Box> boxes;private final boolean full;private final VoxelShape[] shapes=new VoxelShape[4];
        Part(List<Box> boxes,boolean full){
            if(boxes.size()>128)throw new IllegalArgumentException("Unbounded collision complexity");
            this.boxes=List.copyOf(boxes);this.full=full;List<Box> rotated=boxes;
            for(int i=0;i<4;i++){VoxelShape shape=Shapes.empty();for(Box b:rotated)shape=Shapes.or(shape,b.shape());shapes[i]=shape.optimize();rotated=rotated.stream().map(Box::clockwise).toList();}
        }
        public List<Box> boxes(){return boxes;}
        public boolean full(){return full;}
        public VoxelShape shape(Direction facing){return shapes[turn(facing)];}
    }
    public static final class Variant {
        private final String key;private final int width,height,depth;private final List<Part> parts;
        Variant(String key,int w,int h,int d,List<Part> parts){
            if(w<1||h<1||d<1||w>25||h>5||d>3||w*h*d>100||parts.size()!=w*h*d)throw new IllegalArgumentException("Invalid whole assembly size");
            this.key=key;width=w;height=h;depth=d;this.parts=List.copyOf(parts);
        }
        public String key(){return key;}public int width(){return width;}public int height(){return height;}public int depth(){return depth;}
        public int partCount(){return parts.size();}
        public boolean validPart(int p){return p>=0&&p<parts.size();}
        public Part part(int p){return validPart(p)?parts.get(p):Empty.PART;}
        public BlockPos offset(int part,Direction facing){
            if(!validPart(part))throw new IllegalArgumentException("Invalid assembly part");
            int x=part%width,z=(part/width)%depth,y=part/(width*depth);
            for(int i=0;i<turn(facing);i++){int next=-z;z=x;x=next;}
            return new BlockPos(x,y,z);
        }
        public int mirroredPart(int part){if(!validPart(part))return part;return part-part%width+width-1-part%width;}
    }
    private static final class Empty {static final Part PART=new Part(List.of(),false);}
    public record Family(String id,String sound,float strength,int light,List<Variant> variants) {
        public Family {variants=List.copyOf(variants);if(!id.matches("courtyard_[a-z_]+")||variants.size()<2||variants.size()>16||light<0||light>15||!Float.isFinite(strength)||strength<=0)throw new IllegalArgumentException("Invalid family");}
        public Variant variant(int index){if(index<0||index>=variants.size())throw new IllegalArgumentException("Invalid building variant");return variants.get(index);}
    }
    public static int turn(Direction d){return switch(d){case NORTH->0;case EAST->1;case SOUTH->2;case WEST->3;default->throw new IllegalArgumentException("Horizontal direction required");};}
    public static Direction facing(int t){return switch(Math.floorMod(t,4)){case 0->Direction.NORTH;case 1->Direction.EAST;case 2->Direction.SOUTH;default->Direction.WEST;};}
    private static final class Holder {static final Map<String,Family> DATA=load();}
    public static Collection<Family> all(){return Holder.DATA.values();}
    public static Family get(String id){var f=Holder.DATA.get(id);if(f==null)throw new IllegalArgumentException("Unknown courtyard family "+id);return f;}
    private static Map<String,Family> load(){
        try(var stream=CourtyardCatalog.class.getResourceAsStream(RESOURCE)){
            if(stream==null)throw new IllegalStateException("Missing "+RESOURCE);
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("format").getAsInt()!=1)throw new IllegalStateException("Unknown courtyard schema");
            Map<String,Family> result=new LinkedHashMap<>();
            for(var e:root.getAsJsonArray("families")){
                var f=e.getAsJsonObject();List<Variant> variants=new ArrayList<>();
                for(var ve:f.getAsJsonArray("variants")){
                    var v=ve.getAsJsonObject();List<Part> parts=new ArrayList<>();var size=v.getAsJsonArray("size");
                    for(var pe:v.getAsJsonArray("parts")){
                        var p=pe.getAsJsonObject();List<Box> boxes=new ArrayList<>();
                        for(var be:p.getAsJsonArray("boxes")){
                            var b=be.getAsJsonArray();if(b.size()!=6)throw new IllegalArgumentException("Invalid box length");
                            int[] n=new int[6];for(int i=0;i<6;i++){double a=b.get(i).getAsDouble();if(a!=(int)a)throw new IllegalArgumentException("Nonintegral voxel");n[i]=(int)a;}
                            boxes.add(new Box(n[0],n[1],n[2],n[3],n[4],n[5]));
                        }
                        parts.add(new Part(boxes,p.get("full").getAsBoolean()));
                    }
                    variants.add(new Variant(v.get("key").getAsString(),size.get(0).getAsInt(),size.get(1).getAsInt(),size.get(2).getAsInt(),parts));
                }
                var family=new Family(f.get("id").getAsString(),f.get("sound").getAsString(),f.get("strength").getAsFloat(),f.get("light").getAsInt(),variants);
                if(result.put(family.id(),family)!=null)throw new IllegalStateException("Duplicate building family");
            }
            return Collections.unmodifiableMap(result);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private CourtyardCatalog(){}
}
