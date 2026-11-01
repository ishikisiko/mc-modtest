package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import java.util.*;

/** Narrow server writer. It never changes city cells, never widens WorldPlanGuard permissions. */
public final class RoadPaver {
    /** Flags: 1 = permitted vegetation may disappear; 2 = currently air. */
    public record Snapshot(long signature,int[] states,byte[] flags) {
        public Snapshot {states=states.clone();flags=flags.clone();if(states.length!=flags.length)throw new IllegalArgumentException("快照尺寸");}
        public boolean accepts(Snapshot current) {
            if(states.length!=current.states.length)return false;
            for(int i=0;i<states.length;i++)if(states[i]!=current.states[i]&& !((flags[i]&1)!=0&&(current.flags[i]&2)!=0))return false;
            return true;
        }
    }
    public static final class Obstacle extends IllegalStateException {
        public final BlockPos position;
        Obstacle(BlockPos pos,BlockState state){super("施工位置含受保护方块："+pos.toShortString()+" "+BuiltInRegistries.BLOCK.getKey(state.getBlock()));position=pos.immutable();}
    }
    private RoadPaver() {}
    public static List<RoadPlan.Cell> gate(ServerLevel level,WorldPlan world,RoadRules rules,String region) {
        var cells=declaredGate(world,rules,region);
        for(var c:cells){level.getChunk(c.x()>>4,c.z()>>4);verify(level,c,rules.clearance);}
        return cells;
    }
    public static List<RoadPlan.Cell> declaredGate(WorldPlan world,RoadRules rules,String region) {
        var port=rules.ports.get(region);
        if(port==null)throw new IllegalArgumentException("该城尚未配置正式公共出口："+region);
        var city=world.capitalOf(region).orElseThrow();
        if(city.bundle().width!=port.width()||city.bundle().depth!=port.depth())throw new IllegalStateException("城门声明与实际城市尺寸不一致");
        return port.floors().stream().map(f->new RoadPlan.Cell(city.x()+f.x(),city.z()+f.z(),city.originY()*2+f.feet2(),true)).toList();
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
        var frame=RoadPortGeometry.frame(p,city.x(),city.z(),city.margin(),rules.width,rules.gridStep);int feet=-1;
        for(var c:gate)if(c.x()==frame.edgeX()-frame.dx()&&c.z()==frame.edgeZ()-frame.dz())feet=c.feet2();
        if(feet<0)throw new IllegalStateException("缺少城门出口断面");
        return new WorldRoadPlanner.Exit(region,frame,feet,gate);
    }
    private static boolean empty(ServerLevel level,BlockPos pos) {
        var s=level.getBlockState(pos);return s.getFluidState().isEmpty()&&s.getCollisionShape(level,pos).isEmpty();
    }
    static int low(WorldPlan world,RoadRules rules,RoadPlan.Cell c){int ground=world.surfaceY(c.x(),c.z());
        boolean bridge=world.rules().seaLevel-ground>rules.maxWaterDepth;
        boolean pier=Math.floorMod(c.x(),rules.bridgePierSpacing)==0||Math.floorMod(c.z(),rules.bridgePierSpacing)==0;
        return bridge&&!pier?c.floorY()-rules.foundationDepth:Math.min(ground-rules.foundationDepth,c.floorY()-rules.foundationDepth);}
    static int high(WorldPlan world,RoadRules rules,RoadPlan.Cell c){return Math.max(world.surfaceY(c.x(),c.z())+rules.clearance,c.floorY()+rules.clearance+1);}
    private static int low(ServerLevel level,WorldPlan world,RoadRules rules,RoadSavedData.Job job,RoadPlan.Cell c){var old=job.before.get(c.key());return old==null?low(world,rules,c):Math.min(low(world,rules,c),low(world,rules,old));}
    private static int high(ServerLevel level,WorldPlan world,RoadRules rules,RoadSavedData.Job job,RoadPlan.Cell c){var old=job.before.get(c.key());return old==null?high(world,rules,c):Math.max(high(world,rules,c),high(world,rules,old));}
    private static void permission(WorldPlan world,RoadRules rules,Map<Long,RoadPlan> owners,RoadPlan.Cell c) {
        var owner=owners.get(c.key());if(owner==null||!WorldRoadPlanner.columnAllowed(world,rules,owner.from(),owner.to(),c.x(),c.z()))throw new IllegalStateException("施工触及保护区或无列权限来源："+c.x()+","+c.z());
    }
    private static BlockState expected(WorldPlan world,RoadRules rules,RoadPlan.Cell c,int y) {
        if(y<c.floorY())return block(c.band()==3?rules.style.gradeFoundation():rules.foundation);
        if(y==c.floorY())return surface(world,rules,c);
        if(y==c.floorY()+1&&c.rail()>=0)return guardState(c);
        return Blocks.AIR.defaultBlockState();
    }
    private static void verifyOwned(ServerLevel level,WorldPlan world,RoadRules rules,RoadPlan.Cell c) {
        for(int y=low(world,rules,c);y<=high(world,rules,c);y++) {
            var p=new BlockPos(c.x(),y,c.z());var actual=level.getBlockState(p);var expected=expected(world,rules,c,y);
            if(!actual.equals(expected)||level.getBlockEntity(p)!=null)throw new IllegalStateException("已建道路出现修改，保留现场并停止："+p.toShortString()+"，预期 "+expected+"，实际 "+actual);
        }
    }
    public static Snapshot preflight(ServerLevel level,WorldPlan world,RoadRules rules,Map<Long,RoadPlan> owners,RoadSavedData.Job job,List<RoadPlan.Cell> cells) {
        long hash=0xcbf29ce484222325L;
        int count=cells.stream().filter(c->!c.existing()).mapToInt(c->high(level,world,rules,job,c)-low(level,world,rules,job,c)+1).sum();
        int[] states=new int[count];byte[] flags=new byte[count];int index=0;
        for(var c:cells) {
            if(c.existing()) {verify(level,c,rules.clearance);continue;}
            permission(world,rules,owners,c);
            var old=job.written(c.chunk())?c:job.before.get(c.key());if(old!=null)verifyOwned(level,world,rules,old);
            if(Math.abs(c.feet2()-Math.max(world.surfaceY(c.x(),c.z()),world.rules().seaLevel+1)*2)>rules.maxEarthwork*2)
                throw new IllegalStateException("施工土方越界");
            for(int y=low(level,world,rules,job,c);y<=high(level,world,rules,job,c);y++) {
                BlockPos p=new BlockPos(c.x(),y,c.z());BlockState state=level.getBlockState(p);
                if(level.getBlockEntity(p)!=null||!replaceable(world,rules,c.x(),c.z(),state))throw new Obstacle(p,state);
                BlockState fingerprint=state;
                if(state.is(BlockTags.LEAVES)&&state.hasProperty(net.minecraft.world.level.block.LeavesBlock.DISTANCE))
                    fingerprint=state.setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,state.getBlock().defaultBlockState().getValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE));
                int value=net.minecraft.world.level.block.Block.getId(fingerprint);
                String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                states[index]=value;flags[index++]=(byte)((state.isAir()?2:0)|
                    (state.getFluidState().isEmpty()&&(state.canBeReplaced()||state.is(BlockTags.LEAVES)||rules.vegetation.contains(id))?1:0));
                hash=(hash^value)*0x100000001b3L;
            }
        }
        return new Snapshot(hash,states,flags);
    }
    public static boolean protectedAt(BlockGetter level,WorldPlan world,RoadRules rules,BlockPos pos) {
        return level.getBlockEntity(pos)!=null||!replaceable(world,rules,pos.getX(),pos.getZ(),level.getBlockState(pos));
    }
    /** 外侧整地可在障碍前收口；使用与预检相同的写入高度范围，不放宽材料保护。 */
    public static int trimObstructedGrades(BlockGetter level,WorldPlan world,RoadRules rules,RoadSavedData.Job job,long chunk) {
        return job.trimGrades(chunk,c->{
            for(int y=low(world,rules,c);y<=high(world,rules,c);y++)if(protectedAt(level,world,rules,new BlockPos(c.x(),y,c.z())))return true;
            return false;
        });
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
            ||rules.replaceable.contains(id)||rules.vegetation.contains(id)||id.equals(rules.surface)||id.equals(rules.slab)||id.equals(rules.foundation)||id.equals("myvillage:"+rules.guard.id)
            ||Set.of(rules.style.edgeSurface(),rules.style.edgeSlab(),rules.style.curbSurface(),rules.style.curbSlab(),rules.style.gradeFoundation()).contains(id))return true;
        // The capital terrain generator owns these collar materials. They do not grant
        // permission to edit actual city cells (columnAllowed already rejected those).
        for(var city:world.cities())if(city.contains(x,z,city.margin()))
            return List.of(city.bundle().topBlock,city.bundle().fillBlock,city.bundle().subsurfaceBlock).contains(id);
        return false;
    }
    public static void pave(ServerLevel level,WorldPlan world,RoadRules rules,Map<Long,RoadPlan> owners,RoadSavedData.Job job,List<RoadNetworkGeometry.Change> changes) {
        try(var placement=RoadGuardrailBlock.placing()) {
        for(var change:changes) {
            var c=change.after();if(c.existing())throw new IllegalStateException("增量任务含城内写入");
            permission(world,rules,owners,c);
            BlockState foundation=block(c.band()==3?rules.style.gradeFoundation():rules.foundation);
            BlockState surface=surface(world,rules,c);
            for(int y=low(level,world,rules,job,c);y<=high(level,world,rules,job,c);y++) {
                BlockState state=y<c.floorY()?foundation:y==c.floorY()?surface:Blocks.AIR.defaultBlockState();
                level.setBlock(new BlockPos(c.x(),y,c.z()),state,3);
            }
        }
        for(var change:changes){var c=change.after();if(c.rail()>=0)level.setBlock(new BlockPos(c.x(),c.floorY()+1,c.z()),guardState(c),3);}
        }
        for(var change:changes)verify(level,change.after(),rules.clearance);
    }
    public static BlockState guardState(RoadPlan.Cell c) {
        if(c.rail()<0)throw new IllegalArgumentException("该路列没有护栏");
        return RoadContent.GUARDRAIL.get().defaultBlockState().setValue(RoadGuardrailBlock.MASK,c.rail())
            .setValue(RoadGuardrailBlock.LOWERED,c.feet2()%2!=0).setValue(RoadGuardrailBlock.PLANNED,true);
    }
    public static BlockState surface(WorldPlan world,RoadRules rules,RoadPlan.Cell c) {
        String full=switch(c.band()){case 1->rules.style.edgeSurface();case 2->rules.style.curbSurface();case 3->rules.style.gradeSurfaces().get(world.coreRegionAt(c.x(),c.z()).orElseThrow().id());default->rules.surface;};
        if(c.feet2()%2==0)return block(full);
        String half=switch(c.band()){case 1->rules.style.edgeSlab();case 2->rules.style.curbSlab();default->rules.slab;};
        if(c.band()==3)throw new IllegalStateException("缓坡地表必须为完整方块");
        BlockState slab=block(half);if(!(slab.getBlock() instanceof SlabBlock))throw new IllegalStateException("官道半砖配置错误");
        return slab.setValue(SlabBlock.TYPE,SlabType.BOTTOM).setValue(SlabBlock.WATERLOGGED,false);
    }
    public static void verify(ServerLevel level,RoadPlan.Cell c,int clearance) {
        BlockPos floor=new BlockPos(c.x(),c.floorY(),c.z());BlockState state=level.getBlockState(floor);var shape=state.getCollisionShape(level,floor);
        if(shape.isEmpty()||!state.getFluidState().isEmpty()||Math.abs((c.floorY()+shape.max(Direction.Axis.Y))*2-c.feet2())>0.01)
            throw new IllegalStateException("驿道路幅地坪不符："+floor.toShortString());
        if(c.rail()>=0&&!level.getBlockState(floor.above()).equals(guardState(c)))throw new IllegalStateException("驿道护栏缺失或连接不符："+floor.above().toShortString());
        for(int dy=c.rail()>=0?2:1;dy<=clearance;dy++)if(!empty(level,floor.above(dy)))throw new IllegalStateException("驿道路幅净空不足："+floor.above(dy).toShortString());
    }
    private static BlockState block(String id) {
        var key=ResourceLocation.parse(id);if(!BuiltInRegistries.BLOCK.containsKey(key))throw new IllegalStateException("驿道材料不存在："+id);
        return BuiltInRegistries.BLOCK.get(key).defaultBlockState();
    }
}
