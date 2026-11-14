package com.example.myvillage.qianji;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;

/** 服务器和客户端选框使用相同的位置算法；书册间隙保留为空，行走碰撞仍来自原木构。 */
public final class ProceduralBookcaseOutline {
    private final List<double[]> frame;
    private final List<ProceduralBooks.Region> regions;
    private final Map<List<Integer>,VoxelShape> cache=new LinkedHashMap<>(4,.75f,true);
    public ProceduralBookcaseOutline(JsonObject profile){
        List<double[]> body=new ArrayList<>();List<ProceduralBooks.Region> rows=new ArrayList<>();
        for(var item:profile.getAsJsonArray("boxes")){
            var b=item.getAsJsonArray();
            if(b.get(6).getAsString().equals(ProceduralBooks.rules().markerRole())){
                var r=new JsonArray();for(int i:new int[]{0,1,2,3,4,5,7,8})r.add(b.get(i));
                rows.add(ProceduralBooks.Region.fromJson(r));
            }else{
                double[] box=new double[6];for(int i=0;i<6;i++)box[i]=b.get(i).getAsDouble();body.add(box);
            }
        }
        frame=List.copyOf(body);regions=List.copyOf(rows);
    }
    public boolean hasBooks(){return !regions.isEmpty();}
    public synchronized VoxelShape shape(Direction facing,BlockPos pos){
        int turn=switch(facing){case NORTH->0;case EAST->1;case SOUTH->2;case WEST->3;default->throw new IllegalArgumentException();};
        var rules=ProceduralBooks.rules();List<Integer> key=new ArrayList<>();key.add(turn);
        for(var r:regions){boolean alongX=(r.facing()%2==0)^(turn%2!=0);key.add(ProceduralBooks.pattern(alongX?pos.getX():pos.getZ(),pos.getY(),r.rowY(),rules));}
        var found=cache.get(key);if(found!=null)return found;
        VoxelShape result=Shapes.empty();
        for(double[] b:frame)result=Shapes.or(result,box(b,turn));
        for(int i=0;i<regions.size();i++)for(var b:ProceduralBooks.generate(regions.get(i),key.get(i+1),rules))
            result=Shapes.or(result,box(new double[]{b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1()},turn));
        result=result.optimize();cache.put(List.copyOf(key),result);
        while(cache.size()>rules.cachedVariants())cache.remove(cache.keySet().iterator().next());
        return result;
    }
    private static VoxelShape box(double[] source,int turn){
        double[] b=source;
        for(int i=0;i<turn;i++)b=new double[]{16-b[5],b[1],b[0],16-b[2],b[4],b[3]};
        return Block.box(b[0],b[1],b[2],b[3],b[4],b[5]);
    }
}
