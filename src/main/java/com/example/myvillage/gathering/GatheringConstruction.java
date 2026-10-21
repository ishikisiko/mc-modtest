package com.example.myvillage.gathering;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;

/** Generated geometry is shared by server validation, block collision and the player's blueprint. */
public final class GatheringConstruction {
    public record Piece(BlockPos offset, ResourceLocation block, Direction facing) {
        public boolean air() { return block.equals(ResourceLocation.withDefaultNamespace("air")); }
    }
    private record Data(Map<String,VoxelShape[]> shapes, Map<String,Integer> lights, Map<GatheringTier,List<Piece>> tiers) { }
    private static class Holder { static final Data DATA=load(); }
    public static List<Piece> pieces(GatheringTier tier) { return Holder.DATA.tiers.get(tier); }
    public static int lightLevel(String id) { return Holder.DATA.lights.get(id); }
    public static VoxelShape shape(String id, Direction facing) { return Holder.DATA.shapes.get(id)[facing.get2DDataValue()]; }
    public static VoxelShape rotate(VoxelShape shape, Direction facing) {
        int turns=switch(facing){case SOUTH->0;case WEST->1;case NORTH->2;case EAST->3;default->throw new IllegalArgumentException();};
        for(int i=0;i<turns;i++) {
            VoxelShape next=Shapes.empty();
            for(var a:shape.toAabbs()) next=Shapes.or(next,Shapes.box(1-a.maxZ,a.minY,a.minX,1-a.minZ,a.maxY,a.maxX));
            shape=next;
        }
        return shape;
    }
    private static Data load() {
        try(var stream=GatheringConstruction.class.getResourceAsStream("/data/myvillage/gathering/construction.json")) {
            if(stream==null)throw new IllegalStateException("Missing gathering construction");
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String,VoxelShape[]> shapes=new HashMap<>();
            Map<String,Integer> lights=new HashMap<>();
            for(var entry:root.getAsJsonObject("parts").entrySet()) {
                int light=entry.getValue().getAsJsonObject().get("light_level").getAsInt();
                if(light<0||light>15)throw new IllegalStateException("Invalid gathering light level");
                lights.put(entry.getKey(),light);
                VoxelShape shape=Shapes.empty();
                for(var raw:entry.getValue().getAsJsonObject().getAsJsonArray("collision_boxes")) {
                    var b=raw.getAsJsonObject();var a=b.getAsJsonArray("from");var z=b.getAsJsonArray("to");
                    shape=Shapes.or(shape,Block.box(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),z.get(0).getAsDouble(),z.get(1).getAsDouble(),z.get(2).getAsDouble()));
                }
                var variants=new VoxelShape[4];for(var facing:Direction.Plane.HORIZONTAL)variants[facing.get2DDataValue()]=rotate(shape,facing);
                shapes.put(entry.getKey(),variants);
            }
            Map<GatheringTier,List<Piece>> tiers=new EnumMap<>(GatheringTier.class);
            for(var tier:GatheringTier.values()) {
                List<Piece> pieces=new ArrayList<>();Set<BlockPos> occupied=new HashSet<>();
                for(var raw:root.getAsJsonObject("tiers").getAsJsonArray(tier.id)) {
                    var row=raw.getAsJsonObject();var a=row.getAsJsonArray("offset");
                    var p=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
                    var geometry=GatheringRules.get().tier(tier);
                    if(!occupied.add(p)||Math.abs(p.getX())>geometry.radius()||Math.abs(p.getZ())>geometry.radius()||p.getY()<1||p.getY()>3
                       ||(Math.abs(p.getX())<=geometry.innerRadius()&&Math.abs(p.getZ())<=geometry.innerRadius()))throw new IllegalStateException("Invalid gathering piece: "+p);
                    Direction facing=Direction.byName(row.get("facing").getAsString());
                    if(facing==null||facing.getAxis().isVertical())throw new IllegalStateException("Invalid facing");
                    pieces.add(new Piece(p,ResourceLocation.parse(row.get("block").getAsString()),facing));
                }
                tiers.put(tier,List.copyOf(pieces));
            }
            return new Data(Map.copyOf(shapes),Map.copyOf(lights),Map.copyOf(tiers));
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private GatheringConstruction() { }
}
