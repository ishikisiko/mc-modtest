package com.example.myvillage.streetfurniture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 模型与碰撞来自同一份 authored 设施目录，不再把小构件取整为完整方块。 */
public final class StreetFurnitureCatalog {
    public static final String RESOURCE="/data/myvillage/street_furniture/catalog.json";

    public record Box(double x0,double y0,double z0,double x1,double y1,double z1) {
        public Box {
            if(!Double.isFinite(x0+y0+z0+x1+y1+z1)||x0<0||y0<0||z0<0||x1>16||y1>16||z1>16||x0>=x1||y0>=y1||z0>=z1)
                throw new IllegalArgumentException("街景碰撞盒越界");
        }
        public Box clockwise(){return new Box(16-z1,y0,x0,16-z0,y1,x1);}
        public VoxelShape shape(){return Block.box(x0,y0,z0,x1,y1,z1);}
    }

    public static final class Part {
        private final BlockPos offset;
        private final List<Box> outline,collision;
        private final int light;
        private final VoxelShape[] outlines=new VoxelShape[4],collisions=new VoxelShape[4];
        Part(BlockPos offset,List<Box> outline,List<Box> collision,int light){
            if(outline.isEmpty()||outline.size()>32||collision.size()>32||light<0||light>15)throw new IllegalArgumentException("街景分格参数越界");
            this.offset=offset;this.outline=List.copyOf(outline);this.collision=List.copyOf(collision);this.light=light;
            fillShapes(outline,outlines);fillShapes(collision,collisions);
        }
        private static void fillShapes(List<Box> boxes,VoxelShape[] shapes){
            for(int i=0;i<4;i++){
                VoxelShape shape=Shapes.empty();for(Box box:boxes)shape=Shapes.or(shape,box.shape());shapes[i]=shape.optimize();
                boxes=boxes.stream().map(Box::clockwise).toList();
            }
        }
        public BlockPos offset(){return offset;}
        public List<Box> outline(){return outline;}
        public List<Box> collision(){return collision;}
        public int light(){return light;}
        public VoxelShape outline(Direction facing){return outlines[turn(facing)];}
        public VoxelShape collision(Direction facing){return collisions[turn(facing)];}
    }

    public record Family(String id,int width,int height,int depth,String sound,float strength,List<Part> parts){
        public Family {
            parts=List.copyOf(parts);
            if(!Set.of("rune_lamp","drying_rack").contains(id)||width<1||width>2||height!=2||depth!=1||parts.size()!=width*height*depth
                    ||!Set.of("metal","wood").contains(sound)||!Float.isFinite(strength)||strength<=0||strength>20)
                throw new IllegalArgumentException("街景设施目录不合法");
            for(int i=0;i<parts.size();i++)if(!parts.get(i).offset().equals(new BlockPos(i%width,i/(width*depth),(i/width)%depth)))
                throw new IllegalArgumentException("街景分格偏移不一致");
        }
        public Part part(int index){if(index<0||index>=parts.size())throw new IllegalArgumentException("街景分格编号越界");return parts.get(index);}
        public BlockPos offset(int index,Direction facing){
            var p=part(index).offset();int x=p.getX(),z=p.getZ();
            for(int i=0;i<turn(facing);i++){int next=-z;z=x;x=next;}
            return new BlockPos(x,p.getY(),z);
        }
        public int mirroredPart(int index){part(index);return index-index%width+width-1-index%width;}
    }

    public static int turn(Direction facing){return switch(facing){case NORTH->0;case EAST->1;case SOUTH->2;case WEST->3;default->throw new IllegalArgumentException("需要水平朝向");};}
    private static final class Holder {static final Map<String,Family> DATA=load();}
    public static Family get(String id){var family=Holder.DATA.get(id);if(family==null)throw new IllegalArgumentException("未知街景设施 "+id);return family;}

    private static List<Box> boxes(JsonArray values){
        List<Box> result=new ArrayList<>();
        for(var value:values){var b=value.getAsJsonArray();if(b.size()!=6)throw new IllegalArgumentException("街景碰撞盒长度");
            result.add(new Box(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble()));}
        return result;
    }
    private static int integer(com.google.gson.JsonElement value){double n=value.getAsDouble();if(!Double.isFinite(n)||n!=Math.rint(n)||Math.abs(n)>1024)throw new IllegalArgumentException("街景整数参数越界");return (int)n;}
    private static Map<String,Family> load(){
        try(var stream=StreetFurnitureCatalog.class.getResourceAsStream(RESOURCE)){
            if(stream==null)throw new IllegalStateException("Missing "+RESOURCE);
            JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(integer(root.get("schema"))!=1)throw new IllegalArgumentException("未知街景目录格式");
            Map<String,Family> result=new LinkedHashMap<>();
            for(var entry:root.getAsJsonArray("families")){
                var f=entry.getAsJsonObject();var size=f.getAsJsonArray("size");if(size.size()!=3)throw new IllegalArgumentException("街景尺寸长度");
                List<Part> parts=new ArrayList<>();
                for(var piece:f.getAsJsonArray("parts")){
                    var p=piece.getAsJsonObject();var offset=p.getAsJsonArray("offset");if(offset.size()!=3)throw new IllegalArgumentException("街景偏移长度");
                    parts.add(new Part(new BlockPos(integer(offset.get(0)),integer(offset.get(1)),integer(offset.get(2))),boxes(p.getAsJsonArray("outline")),boxes(p.getAsJsonArray("collision")),integer(p.get("light"))));
                }
                var family=new Family(f.get("id").getAsString(),integer(size.get(0)),integer(size.get(1)),integer(size.get(2)),f.get("sound").getAsString(),f.get("strength").getAsFloat(),parts);
                if(result.put(family.id(),family)!=null)throw new IllegalArgumentException("重复街景设施");
            }
            if(!result.keySet().equals(Set.of("rune_lamp","drying_rack")))throw new IllegalArgumentException("街景设施不齐全");
            return Map.copyOf(result);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private StreetFurnitureCatalog(){}
}
