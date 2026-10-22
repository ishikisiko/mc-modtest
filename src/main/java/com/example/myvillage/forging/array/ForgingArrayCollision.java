package com.example.myvillage.forging.array;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.*;

/** Static coarse boxes come from the same authored generator as the displayed shell. */
public final class ForgingArrayCollision {
    private static class Holder { static final Map<Direction,Map<BlockPos,VoxelShape>> SHAPES=load(); }
    private ForgingArrayCollision() { }
    private static Map<Direction,Map<BlockPos,VoxelShape>> load(){
        try(var stream=ForgingArrayCollision.class.getResourceAsStream("/assets/myvillage/forging_array/formed_mesh.json")){
            if(stream==null)throw new IllegalStateException("Missing forging array mesh");
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            Map<Direction,Map<BlockPos,VoxelShape>> all=new EnumMap<>(Direction.class);
            for(Direction facing:Direction.Plane.HORIZONTAL){
                Map<BlockPos,VoxelShape> shapes=new HashMap<>();var right=facing.getClockWise();var back=facing.getOpposite();
                for(var c:ForgingArrayRules.get().cells()){
                    if(c.role()==ForgingArrayRules.Role.AIR)continue;
                    VoxelShape shape=Shapes.empty();
                    for(var e:root.getAsJsonArray("collision_boxes")){
                        var box=e.getAsJsonArray();var lo=box.get(0).getAsJsonArray();var hi=box.get(1).getAsJsonArray();
                        double x0=Math.max(0,lo.get(0).getAsDouble()+.5-c.x()),x1=Math.min(1,hi.get(0).getAsDouble()+.5-c.x());
                        double y0=Math.max(0,lo.get(1).getAsDouble()-1-c.y()),y1=Math.min(1,hi.get(1).getAsDouble()-1-c.y());
                        double z0=Math.max(0,lo.get(2).getAsDouble()+2.5-c.z()),z1=Math.min(1,hi.get(2).getAsDouble()+2.5-c.z());
                        if(x0>=x1||y0>=y1||z0>=z1)continue;
                        double ax=.5+right.getStepX()*(x0-.5)+back.getStepX()*(z0-.5),az=.5+right.getStepZ()*(x0-.5)+back.getStepZ()*(z0-.5);
                        double bx=.5+right.getStepX()*(x1-.5)+back.getStepX()*(z1-.5),bz=.5+right.getStepZ()*(x1-.5)+back.getStepZ()*(z1-.5);
                        shape=Shapes.or(shape,Shapes.box(Math.min(ax,bx),y0,Math.min(az,bz),Math.max(ax,bx),y1,Math.max(az,bz)));
                    }
                    // A thin selectable remnant for structural cells supporting an overhanging ornament.
                    if(shape.isEmpty())shape=Shapes.box(.35,0,.35,.65,.125,.65);
                    shapes.put(ForgingArrayPattern.worldPos(BlockPos.ZERO,facing,c),shape.optimize());
                }
                all.put(facing,Map.copyOf(shapes));
            }
            return Map.copyOf(all);
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static VoxelShape shape(BlockPos root,Direction facing,BlockPos part){return Holder.SHAPES.get(facing).getOrDefault(part.subtract(root),Shapes.block());}
    public static ForgingArrayBlockEntity owner(BlockGetter view,BlockPos pos,ForgingArrayRules.Role role){
        for(Direction facing:Direction.Plane.HORIZONTAL)for(var cell:ForgingArrayRules.get().cells()){
            if(cell.role()!=role)continue;
            BlockPos origin=pos.subtract(ForgingArrayPattern.worldPos(BlockPos.ZERO,facing,cell));
            if(view instanceof net.minecraft.world.level.Level level&&!level.hasChunkAt(origin))continue;
            if(view.getBlockEntity(origin) instanceof ForgingArrayBlockEntity root&&!root.isRemoved()&&root.formed()
                    &&root.getBlockState().getValue(ForgingArrayBlock.FACING)==facing)return root;
        }
        return null;
    }
}
