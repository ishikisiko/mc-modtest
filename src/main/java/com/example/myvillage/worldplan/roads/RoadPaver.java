package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import java.util.*;

/** Narrow server writer. It never changes city cells, never widens WorldPlanGuard permissions. */
public final class RoadPaver {
    public static final class Obstacle extends IllegalStateException {
        public final BlockPos position;
        Obstacle(BlockPos pos,BlockState state){super("施工位置含受保护方块："+pos.toShortString()+" "+BuiltInRegistries.BLOCK.getKey(state.getBlock()));position=pos.immutable();}
    }
    private RoadPaver() {}
    public static List<RoadPlan.Cell> gate(ServerLevel level,WorldPlan world,RoadRules rules,String region) {
        var port=rules.ports.get(region);
        if(port==null)throw new IllegalArgumentException("首版只配置了南疆、西漠的正式公共出口");
        var city=world.capitalOf(region).orElseThrow();
        if(city.bundle().width!=port.width()||city.bundle().depth!=port.depth())throw new IllegalStateException("城门声明与实际城市尺寸不一致");
        ArrayList<RoadPlan.Cell> cells=new ArrayList<>();int expected=city.originY()+port.feetY();
        for(int z=city.z()+port.z();z<city.z()+city.bundle().depth;z++)for(int dx=-port.gateWidth()/2;dx<=port.gateWidth()/2;dx++) {
            int x=city.x()+port.x()+dx;level.getChunk(x>>4,z>>4);
            cells.add(new RoadPlan.Cell(x,z,findFeet(level,x,z,expected,rules.gateProbeRadius,rules.clearance),true));
        }
        return List.copyOf(cells);
    }
    private static int findFeet(ServerLevel level,int x,int z,int expected,int radius,int clearance) {
        for(int distance=0;distance<=radius;distance++)for(int sign:distance==0?new int[]{0}:new int[]{1,-1}) {
            int y=expected-1+distance*sign;BlockPos pos=new BlockPos(x,y,z);BlockState state=level.getBlockState(pos);
            var shape=state.getCollisionShape(level,pos);
            if(shape.isEmpty()||!state.getFluidState().isEmpty())continue;
            boolean clear=true;
            for(int dy=1;dy<=clearance;dy++)if(!empty(level,pos.above(dy))){clear=false;break;}
            if(clear)return (int)Math.ceil((y+shape.max(Direction.Axis.Y))*2);
        }
        throw new IllegalStateException("公共出城通道缺少地坪或净空："+x+","+z);
    }
    public static WorldRoadPlanner.Exit exit(WorldPlan world,RoadRules rules,String region,List<RoadPlan.Cell> gate) {
        var city=world.capitalOf(region).orElseThrow();var p=rules.ports.get(region);
        int edge=city.z()+city.bundle().depth,feet=-1;
        for(var c:gate)if(c.z()==edge-1&&c.x()==city.x()+p.x())feet=c.feet2();
        if(feet<0)throw new IllegalStateException("缺少城门出口断面");
        return new WorldRoadPlanner.Exit(region,city.x()+p.x(),edge,edge+city.margin()+rules.width/2+rules.gridStep*2,feet,gate);
    }
    private static boolean empty(ServerLevel level,BlockPos pos) {
        var s=level.getBlockState(pos);return s.getFluidState().isEmpty()&&s.getCollisionShape(level,pos).isEmpty();
    }
    static int low(WorldPlan world,RoadRules rules,RoadPlan.Cell c){return Math.min(world.surfaceY(c.x(),c.z())-rules.foundationDepth,c.floorY()-rules.foundationDepth);}
    static int high(WorldPlan world,RoadRules rules,RoadPlan.Cell c){return Math.max(world.surfaceY(c.x(),c.z())+rules.clearance,c.floorY()+rules.clearance+1);}
    public static long preflight(ServerLevel level,WorldPlan world,RoadRules rules,RoadPlan road,List<RoadPlan.Cell> cells) {
        long hash=0xcbf29ce484222325L;
        for(var c:cells) {
            if(c.existing()) {verify(level,c,rules.clearance);continue;}
            if(!WorldRoadPlanner.columnAllowed(world,rules,road.from(),road.to(),c.x(),c.z()))throw new IllegalStateException("施工触及保护区："+c.x()+","+c.z());
            if(Math.abs(c.feet2()-Math.max(world.surfaceY(c.x(),c.z()),world.rules().seaLevel+1)*2)>rules.maxEarthwork*2)
                throw new IllegalStateException("施工土方越界");
            for(int y=low(world,rules,c);y<=high(world,rules,c);y++) {
                BlockPos p=new BlockPos(c.x(),y,c.z());BlockState state=level.getBlockState(p);
                if(level.getBlockEntity(p)!=null||!replaceable(world,rules,c.x(),c.z(),state))throw new Obstacle(p,state);
                hash=(hash^net.minecraft.world.level.block.Block.getId(state))*0x100000001b3L;
            }
        }
        return hash;
    }
    public static boolean protectedAt(ServerLevel level,WorldPlan world,RoadRules rules,BlockPos pos) {
        return level.getBlockEntity(pos)!=null||!replaceable(world,rules,pos.getX(),pos.getZ(),level.getBlockState(pos));
    }
    public static WorldRoadPlanner.Box obstacleBounds(ServerLevel level,RoadRules rules,BlockPos pos) {
        var chunk=level.getChunk(pos.getX()>>4,pos.getZ()>>4);
        long references=chunk.getAllReferences().values().stream().mapToLong(set->set.size()).sum();
        if(references>rules.maxStructureStarts)throw new IllegalStateException("障碍结构引用超过配置预算");
        int minX=pos.getX(),maxX=pos.getX(),minZ=pos.getZ(),maxZ=pos.getZ();
        for(var start:level.structureManager().startsForStructure(chunk.getPos(),structure->true)) {
            if(!start.isValid())continue;var box=start.getBoundingBox();
            if(!coversColumn(box,pos))continue;
            minX=Math.min(minX,box.minX());maxX=Math.max(maxX,box.maxX());minZ=Math.min(minZ,box.minZ());maxZ=Math.max(maxZ,box.maxZ());
        }
        return new WorldRoadPlanner.Box(minX,minZ,maxX,maxZ);
    }
    static boolean coversColumn(net.minecraft.world.level.levelgen.structure.BoundingBox box,BlockPos pos) {
        return pos.getX()>=box.minX()&&pos.getX()<=box.maxX()&&pos.getZ()>=box.minZ()&&pos.getZ()<=box.maxZ();
    }
    private static boolean replaceable(WorldPlan world,RoadRules rules,int x,int z,BlockState state) {
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if(state.isAir()||state.canBeReplaced()||state.is(BlockTags.LEAVES)||state.is(BlockTags.LOGS)
            ||rules.replaceable.contains(id)||id.equals(rules.surface)||id.equals(rules.slab)||id.equals(rules.foundation))return true;
        // The capital terrain generator owns these collar materials. They do not grant
        // permission to edit actual city cells (columnAllowed already rejected those).
        for(var city:world.cities())if(city.contains(x,z,city.margin()))
            return List.of(city.bundle().topBlock,city.bundle().fillBlock,city.bundle().subsurfaceBlock).contains(id);
        return false;
    }
    public static void pave(ServerLevel level,WorldPlan world,RoadRules rules,RoadPlan road,List<RoadPlan.Cell> cells) {
        BlockState surface=block(rules.surface),foundation=block(rules.foundation),slab=block(rules.slab);
        if(!(slab.getBlock() instanceof SlabBlock))throw new IllegalStateException("驿道半砖配置错误");
        slab=slab.setValue(SlabBlock.TYPE,SlabType.BOTTOM).setValue(SlabBlock.WATERLOGGED,false);
        for(var c:cells) {
            if(c.existing())continue;
            if(!WorldRoadPlanner.columnAllowed(world,rules,road.from(),road.to(),c.x(),c.z()))throw new IllegalStateException("驿道写入边界不一致");
            for(int y=low(world,rules,c);y<=high(world,rules,c);y++) {
                BlockState state=y<c.floorY()?foundation:y==c.floorY()?(c.feet2()%2==0?surface:slab):Blocks.AIR.defaultBlockState();
                level.setBlock(new BlockPos(c.x(),y,c.z()),state,3);
            }
        }
        for(var c:cells)verify(level,c,rules.clearance);
    }
    public static void verify(ServerLevel level,RoadPlan.Cell c,int clearance) {
        BlockPos floor=new BlockPos(c.x(),c.floorY(),c.z());BlockState state=level.getBlockState(floor);var shape=state.getCollisionShape(level,floor);
        if(shape.isEmpty()||!state.getFluidState().isEmpty()||Math.abs((c.floorY()+shape.max(Direction.Axis.Y))*2-c.feet2())>0.01)
            throw new IllegalStateException("驿道路幅地坪不符："+floor.toShortString());
        for(int dy=1;dy<=clearance;dy++)if(!empty(level,floor.above(dy)))throw new IllegalStateException("驿道路幅净空不足："+floor.above(dy).toShortString());
    }
    private static BlockState block(String id) {
        var key=ResourceLocation.parse(id);if(!BuiltInRegistries.BLOCK.containsKey(key))throw new IllegalStateException("驿道材料不存在："+id);
        return BuiltInRegistries.BLOCK.get(key).defaultBlockState();
    }
}
