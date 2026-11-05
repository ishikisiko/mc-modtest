package com.example.myvillage.worldplan.mine;

import com.example.myvillage.worldplan.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.*;
import java.util.*;
import java.util.function.Function;

/** 复用原版 createRoom/createTunnel 的形状和分叉；强制核心洞体，不执行随机起洞概率。
 * 每个实例只服务一个区块。原版形状回调被替换为有界的空腔收集，不使用原版含水层拒绝逻辑。
 * 世界写入仍只发生在当前生成区块；已有干洞保留，只有接触空腔的流体被岩石封隔。 */
public final class StarterMineCaveCarver extends CaveWorldCarver {
    public record Shape(Set<BlockPos> air,Map<BlockPos,BlockState> fixed) {}
    private final WorldPlan world;
    private final StarterMineLayout layout;
    private final StarterMineDefinition def;
    private final StarterMineCavePlan plan;
    private final int minX,maxX,minZ,maxZ;
    private final Map<Long,Integer> ceilings=new HashMap<>();
    private final Set<BlockPos> air=new HashSet<>();
    private final Map<BlockPos,BlockState> fixed=new HashMap<>();

    private StarterMineCaveCarver(WorldPlan world,StarterMineGeneration mine,ChunkAccess chunk){
        super(CaveCarverConfiguration.CODEC);this.world=world;layout=mine.layout();def=mine.definition();plan=mine.caves();
        int halo=def.caves.seal+1;minX=chunk.getPos().getMinBlockX()-halo;maxX=chunk.getPos().getMaxBlockX()+halo;minZ=chunk.getPos().getMinBlockZ()-halo;maxZ=chunk.getPos().getMaxBlockZ()+halo;
    }
    public static Shape shape(WorldPlan world,StarterMineGeneration mine,ChunkAccess chunk){
        var generator=new StarterMineCaveCarver(world,mine,chunk);generator.collect(chunk);return new Shape(Set.copyOf(generator.air),Map.copyOf(generator.fixed));
    }
    private void collect(ChunkAccess chunk){
        var c=def.caves;double x=layout.worldX(def.entry.x(),plan.endZ)+.5,z=layout.worldZ(def.entry.x(),plan.endZ)+.5,y=layout.y+plan.centerY;
        CarveSkipChecker skip=(context,dx,dy,dz,wy)->dy<=c.floorLevel||dx*dx+dy*dy+dz*dz>=1;
        // 这两个原版方法仅把上下文传给 carveEllipsoid；本类的回调只使用几何数值。
        createRoom(null,null,chunk,null,null,x-1,y,z,plan.radius-1.5f,plan.heightScale,null,skip);
        for(var branch:plan.branches)createTunnel(null,null,chunk,null,branch.seed(),null,x,y,z,c.horizontalMultiplier,c.verticalMultiplier,
            (float)branch.thickness(),(float)(branch.yaw()+layout.rotation*Math.PI/2),(float)branch.pitch(),0,branch.length(),1.0,null,skip);
        collectAccess();air.removeAll(fixed.keySet());
    }
    private boolean inSlice(int x,int z){return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
    private boolean allowed(int x,int y,int z){
        int lx=layout.localX(x,z),lz=layout.localZ(x,z);var c=def.caves;
        if(lx<c.margin||lx>=def.width-c.margin||lz<c.margin||lz>=def.depth-c.margin||y<layout.y+c.minY)return false;
        long key=((long)x<<32)^(z&0xffffffffL);int ceiling=ceilings.computeIfAbsent(key,k->(def.weight(lx,lz)==255?layout.y+def.top(lx,lz):layout.surface(world,def,x,z))-c.cover);return y<=ceiling;
    }
    @Override protected boolean carveEllipsoid(CarvingContext context,CaveCarverConfiguration config,ChunkAccess chunk,Function<BlockPos,Holder<Biome>> biomes,Aquifer aquifer,
                                               double cx,double cy,double cz,double rx,double ry,CarvingMask mask,CarveSkipChecker skip){
        boolean changed=false;
        for(int z=Math.max(minZ,Mth.floor(cz-rx));z<=Math.min(maxZ,Mth.floor(cz+rx));z++)for(int x=Math.max(minX,Mth.floor(cx-rx));x<=Math.min(maxX,Mth.floor(cx+rx));x++)
            for(int y=Mth.floor(cy-ry);y<=Mth.ceil(cy+ry);y++){
                if(!allowed(x,y,z)||skip.shouldSkip(null,(x+.5-cx)/rx,(y-.5-cy)/ry,(z+.5-cz)/rx,y))continue;
                changed|=air.add(new BlockPos(x,y,z));
            }
        return changed;
    }
    private void collectAccess(){
        var c=def.caves;
        for(var p:plan.access){
            int x=layout.worldX(p.x(),p.z()),z=layout.worldZ(p.x(),p.z());if(!inSlice(x,z))continue;
            int floor=layout.y+p.floorY(),head=layout.y+(int)Math.ceil(p.feet2()/2.0)+c.headroom-1;
            for(int y=floor-c.floorDepth+1;y<=floor;y++)fixed.put(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState());
            if(Math.floorMod(p.feet2(),2)==1)fixed.put(new BlockPos(x,floor,z),Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
            for(int y=floor+1;y<=head;y++)air.add(new BlockPos(x,y,z));
        }
        for(int z=c.flatEndZ+2;z<=plan.endZ-plan.radius;z++)for(int side:new int[]{-1,1})for(int dy=0;dy<c.liningHeight;dy++){
            int x=def.entry.x()+side*(c.connectionRadius+1),y=(int)Math.ceil(plan.at(def.entry.x(),z).feet2()/2.0)+dy;
            fixed(x,y,z,Blocks.STONE.defaultBlockState());
        }
        for(var ore:plan.ores)fixed(ore.x(),ore.y(),ore.z(),switch(ore.block()){
            case "minecraft:coal_ore"->Blocks.COAL_ORE.defaultBlockState();case "minecraft:iron_ore"->Blocks.IRON_ORE.defaultBlockState();case "minecraft:copper_ore"->Blocks.COPPER_ORE.defaultBlockState();default->throw new IllegalStateException("未知开局矿物");});
        for(var torch:plan.torches)fixed(torch.x(),torch.y(),torch.z(),torch.state(layout));
    }
    private void fixed(int lx,int ly,int lz,BlockState state){int x=layout.worldX(lx,lz),z=layout.worldZ(lx,lz);if(inSlice(x,z))fixed.put(new BlockPos(x,layout.y+ly,z),state);}
    private static boolean owns(ChunkAccess chunk,BlockPos p){return p.getX()>=chunk.getPos().getMinBlockX()&&p.getX()<=chunk.getPos().getMaxBlockX()&&p.getZ()>=chunk.getPos().getMinBlockZ()&&p.getZ()<=chunk.getPos().getMaxBlockZ();}
    public static void requireGenerating(ChunkAccess chunk){
        if(!(chunk instanceof ProtoChunk)||chunk instanceof ImposterProtoChunk||chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL))throw new IllegalStateException("洞穴只能写入当前生成区块");
    }
    public static void carve(WorldPlan world,StarterMineGeneration mine,ChunkAccess chunk){
        var layout=mine.layout();var def=mine.definition();int x=chunk.getPos().getMinBlockX(),z=chunk.getPos().getMinBlockZ();
        if(x>layout.maxX(def)||x+15<layout.minX(def)||z>layout.maxZ(def)||z+15<layout.minZ(def))return;
        requireGenerating(chunk);var shape=shape(world,mine,chunk);Set<BlockPos> seal=new HashSet<>();
        for(var p:shape.air())for(var direction:net.minecraft.core.Direction.values())for(int n=1;n<=def.caves.seal;n++){
            var neighbor=p.relative(direction,n);if(owns(chunk,neighbor)&&!shape.air().contains(neighbor)&&!shape.fixed().containsKey(neighbor)&&!chunk.getFluidState(neighbor).isEmpty())seal.add(neighbor);
        }
        for(var p:seal)chunk.setBlockState(p,Blocks.STONE.defaultBlockState(),false);
        for(var p:shape.air())if(owns(chunk,p)){if(chunk.getBlockState(p).is(Blocks.BEDROCK))throw new IllegalStateException("保证洞体触及基岩");chunk.setBlockState(p,Blocks.CAVE_AIR.defaultBlockState(),false);}
        for(var entry:shape.fixed().entrySet())if(owns(chunk,entry.getKey()))chunk.setBlockState(entry.getKey(),entry.getValue(),false);
    }
    /** 装饰结束后只重申短连接段和落脚面，洞体其余部分保留普通矿脉及洞穴装饰。 */
    public static void finishAccess(WorldPlan world,StarterMineGeneration mine,ChunkAccess chunk){
        requireGenerating(chunk);var g=new StarterMineCaveCarver(world,mine,chunk);g.collectAccess();g.air.removeAll(g.fixed.keySet());
        for(var p:g.air)if(owns(chunk,p))chunk.setBlockState(p,Blocks.CAVE_AIR.defaultBlockState(),false);
        for(var entry:g.fixed.entrySet())if(owns(chunk,entry.getKey()))chunk.setBlockState(entry.getKey(),entry.getValue(),false);
    }
}
