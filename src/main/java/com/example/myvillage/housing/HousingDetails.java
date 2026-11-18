package com.example.myvillage.housing;

import com.example.myvillage.resources.BuiltinResources;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 住宅细构件的模型与碰撞均由同一份作者几何生成。 */
public final class HousingDetails {
    public static final class Shape {
        private final List<int[]> boxes;
        private final VoxelShape[] cache=new VoxelShape[4];
        Shape(List<int[]> boxes){this.boxes=List.copyOf(boxes);}
        public VoxelShape shape(Direction facing){
            int turn=switch(facing){case NORTH->0;case EAST->1;case SOUTH->2;case WEST->3;default->throw new IllegalArgumentException("Horizontal facing required");};
            if(cache[turn]!=null)return cache[turn];
            var result=Shapes.empty();
            for(var original:boxes){
                var b=original.clone();
                for(int t=0;t<turn;t++)b=new int[]{16-b[5],b[1],b[0],16-b[2],b[4],b[3]};
                result=Shapes.or(result,Block.box(b[0],b[1],b[2],b[3],b[4],b[5]));
            }
            return cache[turn]=result.optimize();
        }
    }
    public record Group(String id,List<Shape> shapes){}
    private static final class Holder {static final List<Group> GROUPS=load();}
    public static List<Group> groups(){return Holder.GROUPS;}
    public static Shape get(int group,int variant){return groups().get(group).shapes().get(variant);}
    private static List<Group> load(){
        try(var stream=BuiltinResources.open("data/yuanxian/housing/details.json")){
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("format").getAsInt()!=1)throw new IllegalArgumentException("Unknown housing detail format");
            List<Group> groups=new ArrayList<>();
            for(var element:root.getAsJsonArray("groups")){
                var object=element.getAsJsonObject();List<Shape> shapes=new ArrayList<>();
                for(var variant:object.getAsJsonArray("shapes")){
                    List<int[]> boxes=new ArrayList<>();
                    for(var box:variant.getAsJsonArray()){
                        var array=box.getAsJsonArray();if(array.size()!=6)throw new IllegalArgumentException("Invalid housing box");
                        int[] b=new int[6];for(int i=0;i<6;i++)b[i]=array.get(i).getAsInt();
                        for(int i=0;i<3;i++)if(b[i]<0||b[i+3]>16||b[i]>=b[i+3])throw new IllegalArgumentException("Invalid housing box bounds");
                        boxes.add(b);
                    }
                    if(boxes.isEmpty()||boxes.size()>128)throw new IllegalArgumentException("Unbounded housing collision");
                    shapes.add(new Shape(boxes));
                }
                String id=object.get("id").getAsString();
                if(!id.equals("housing_details_"+groups.size())||shapes.size()<2||shapes.size()>128)throw new IllegalArgumentException("Invalid housing group");
                groups.add(new Group(id,List.copyOf(shapes)));
            }
            if(groups.isEmpty()||groups.size()>64)throw new IllegalArgumentException("Unbounded housing groups");
            return List.copyOf(groups);
        }catch(Exception e){throw new IllegalStateException("无法载入住宅细构件",e);}
    }
    private HousingDetails(){}
}
