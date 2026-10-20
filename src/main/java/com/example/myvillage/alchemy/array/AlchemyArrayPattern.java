package com.example.myvillage.alchemy.array;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Every query uses loaded blocks only. Components cannot belong to two complete arrays. */
public final class AlchemyArrayPattern {
    public record Check(boolean formed,int mismatchIndex,int mismatchCount) { }
    public record Part(AlchemyArrayRules.Role role,Direction facing) { }
    public interface View { boolean loaded(BlockPos pos); Part part(BlockPos pos); }
    private static View view(Level level){return new View(){
        public boolean loaded(BlockPos pos){return level.hasChunkAt(pos);}
        public Part part(BlockPos pos){BlockState state=level.getBlockState(pos);return state.isAir()?new Part(AlchemyArrayRules.Role.AIR,Direction.NORTH):state.getBlock() instanceof AlchemyArrayBlock b?new Part(b.role(),state.getValue(AlchemyArrayBlock.FACING)):null;}
    };}
    private AlchemyArrayPattern() { }
    private static final class Candidates {
        static final Map<Direction,List<BlockPos>> OFFSETS=build();
        private static Map<Direction,List<BlockPos>> build(){
            Map<Direction,List<BlockPos>> result=new EnumMap<>(Direction.class);
            for(Direction own:Direction.Plane.HORIZONTAL){
                Set<BlockPos> offsets=new HashSet<>();
                for(var a:AlchemyArrayRules.get().cells())for(Direction other:Direction.Plane.HORIZONTAL)for(var b:AlchemyArrayRules.get().cells())
                    if(a.role()!=AlchemyArrayRules.Role.AIR&&a.role()==b.role())offsets.add(worldPos(BlockPos.ZERO,own,a).subtract(worldPos(BlockPos.ZERO,other,b)));
                offsets.remove(BlockPos.ZERO);result.put(own,List.copyOf(offsets));
            }
            return Map.copyOf(result);
        }
    }
    public static BlockPos worldPos(BlockPos origin,Direction facing,AlchemyArrayRules.Cell cell) {
        Direction right=facing.getClockWise(),back=facing.getOpposite();
        return origin.relative(right,cell.x()).above(cell.y()).relative(back,cell.z());
    }
    public static Direction outward(Direction facing,AlchemyArrayRules.Role role) {
        return switch(role){case INPUT->facing.getCounterClockWise();case OUTPUT->facing.getClockWise();case FUEL->facing.getOpposite();default->facing;};
    }
    public static Check raw(Level level,BlockPos origin,Direction facing) {
        return raw(view(level),origin,facing);
    }
    public static Check raw(View level,BlockPos origin,Direction facing) {
        int first=-1,count=0; var cells=AlchemyArrayRules.get().cells();
        for(int i=0;i<cells.size();i++) {
            var cell=cells.get(i); BlockPos pos=worldPos(origin,facing,cell);
            boolean valid=level.loaded(pos);
            if(valid) {
                Part state=level.part(pos);
                valid=state!=null&&state.role()==cell.role();
                if(valid&&cell.role()==AlchemyArrayRules.Role.CONTROLLER)valid=state.facing()==facing;
                if(valid&&(cell.role()==AlchemyArrayRules.Role.INPUT||cell.role()==AlchemyArrayRules.Role.OUTPUT||cell.role()==AlchemyArrayRules.Role.FUEL))
                    valid=state.facing()==outward(facing,cell.role());
            }
            if(!valid){if(first<0)first=i;count++;}
        }
        return new Check(count==0,first,count);
    }
    public static Check check(Level level,BlockPos origin,Direction facing) {
        return check(view(level),origin,facing);
    }
    public static Check check(View level,BlockPos origin,Direction facing) {
        Check raw=raw(level,origin,facing); if(!raw.formed())return raw;
        Set<BlockPos> occupied=new HashSet<>();
        for(var cell:AlchemyArrayRules.get().cells())if(cell.role()!=AlchemyArrayRules.Role.AIR)occupied.add(worldPos(origin,facing,cell));
        // Immutable, precomputed offsets cover every possible same-role shared component.
        for(BlockPos offset:Candidates.OFFSETS.get(facing)) {
            BlockPos other=origin.offset(offset);
            if(other.equals(origin)||!level.loaded(other))continue;
            Part state=level.part(other);
            if(state==null||state.role()!=AlchemyArrayRules.Role.CONTROLLER)continue;
            Direction direction=state.facing();
            boolean shared=false;
            for(var cell:AlchemyArrayRules.get().cells())if(cell.role()!=AlchemyArrayRules.Role.AIR&&occupied.contains(worldPos(other,direction,cell))){shared=true;break;}
            if(shared&&raw(level,other,direction).formed())return new Check(false,0,1);
        }
        return raw;
    }
    public static AlchemyArrayBlockEntity controller(Level level,BlockPos port) {
        AlchemyArrayBlockEntity found=null;
        if(!level.hasChunkAt(port))return null;
        BlockState state=level.getBlockState(port);
        if(!(state.getBlock() instanceof AlchemyArrayBlock block))return null;
        for(Direction facing:Direction.Plane.HORIZONTAL)for(var cell:AlchemyArrayRules.get().cells()) {
            if(cell.role()!=block.role())continue;
            BlockPos origin=port.subtract(worldPos(BlockPos.ZERO,facing,cell));
            if(!level.hasChunkAt(origin))continue;
            BlockState root=level.getBlockState(origin);
            if(!(root.getBlock() instanceof AlchemyArrayBlock b)||b.role()!=AlchemyArrayRules.Role.CONTROLLER||root.getValue(AlchemyArrayBlock.FACING)!=facing)continue;
            if(level.getBlockEntity(origin) instanceof AlchemyArrayBlockEntity array&&check(level,origin,facing).formed()) {
                if(found!=null&&found!=array)return null; found=array;
            }
        }
        return found;
    }
    public static void invalidatePorts(Level level,BlockPos origin,Direction facing){
        for(var cell:AlchemyArrayRules.get().cells())if(cell.role()==AlchemyArrayRules.Role.INPUT||cell.role()==AlchemyArrayRules.Role.OUTPUT||cell.role()==AlchemyArrayRules.Role.FUEL){
            BlockPos port=worldPos(origin,facing,cell);if(level.hasChunkAt(port))level.invalidateCapabilities(port);
        }
    }
}
