package com.example.myvillage.sect;

import com.example.myvillage.town.ModBlockFallback;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.*;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** 同一份编译分块服务命令、分帧实现与自然世界生成；只写当前clip。 */
public final class SectBlueprintPlacement {
    // 成对方块可能分属不同clip；原始蓝图写入期间不执行中间态邻居形状更新。
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    public interface Sink {
        BlockState get(BlockPos pos);
        void set(BlockPos pos,BlockState state,CompoundTag entity);
    }
    public record Cell(BlockPos position,BlockState state,CompoundTag entity) { }
    interface Assets {
        int minimumY(); int maximumY();
        BlockState material(String state);
        List<Cell> cells(SectBlueprint data,SectBlueprint.Tile tile);
    }
    private static final Map<MinecraftServer,Map<String,List<Cell>>> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
    private SectBlueprintPlacement() { }

    public static long place(ServerLevel level,SectBlueprint data,BlockPos base,BoundingBox clip,
                             SectGenerator.NaturalHeight natural,Sink target) {
        return place(level,data,base,clip,natural,target,"");
    }
    public static long place(ServerLevel level,SectBlueprint data,BlockPos base,BoundingBox clip,
                             SectGenerator.NaturalHeight natural,Sink target,String region) {
        return place(data,base,clip,natural,target,new Assets() {
            public int minimumY() { return level.getMinBuildHeight(); }
            public int maximumY() { return level.getMaxBuildHeight(); }
            public BlockState material(String state) { return parseState(level,compound(state)); }
            public List<Cell> cells(SectBlueprint d,SectBlueprint.Tile t) { return tile(level,d,t); }
        },region);
    }
    static long place(SectBlueprint data,BlockPos base,BoundingBox clip,
                      SectGenerator.NaturalHeight natural,Sink target,Assets assets) {
        return place(data,base,clip,natural,target,assets,"");
    }
    static long place(SectBlueprint data,BlockPos base,BoundingBox clip,
                      SectGenerator.NaturalHeight natural,Sink target,Assets assets,String region) {
        int x0=Math.max(clip.minX(),base.getX()-data.margin),x1=Math.min(clip.maxX(),base.getX()+data.width+data.margin-1);
        int z0=Math.max(clip.minZ(),base.getZ()-data.margin),z1=Math.min(clip.maxZ(),base.getZ()+data.depth+data.margin-1);
        if (x0>x1 || z0>z1) return 0;
        BlockState rock=assets.material(data.material(data.rock,region)),soil=assets.material(data.subsoil);
        Map<String,BlockState> surfaces=new HashMap<>();
        long written=0;
        for (int z=z0;z<=z1;z++) for (int x=x0;x<=x1;x++) {
            int lx=x-base.getX(),lz=z-base.getZ(),firstAir=natural.at(x,z);
            if(data.terrain.weight(lx,lz)==0)continue;
            int top=data.terrain.firstAir(base.getY(),lx,lz,firstAir)-1;
            int minY=Math.max(assets.minimumY()+1,clip.minY()),maxY=Math.min(assets.maximumY()-1,clip.maxY());
            for (int y=Math.max(minY,Math.min(firstAir-1,base.getY()));y<=Math.min(top,maxY);y++)
                written+=write(target,new BlockPos(x,y,z),rock,null);
            int clearTop=Math.min(maxY,Math.max(firstAir+data.vegetationClearance,base.getY()+data.maxY));
            for (int y=Math.max(top+1,minY);y<=clearTop;y++)
                written+=write(target,new BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),null);
            String state=data.surface(lx,lz,region);BlockState surface=surfaces.computeIfAbsent(state,assets::material);
            for(int y=Math.max(minY,top-data.topsoilDepth+1);y<=Math.min(top,maxY);y++)
                written+=write(target,new BlockPos(x,y,z),y==top?surface:state.equals(data.material(data.rock,region))?rock:soil,null);
        }
        for (SectBlueprint.Tile tile:data.tiles) {
            int ox=base.getX()+tile.x(),oz=base.getZ()+tile.z();
            if (ox>clip.maxX() || ox+15<clip.minX() || oz>clip.maxZ() || oz+15<clip.minZ()) continue;
            for (Cell cell:assets.cells(data,tile)) {
                BlockPos p=base.offset(tile.x(),0,tile.z()).offset(cell.position());
                if (clip.isInside(p)) written+=write(target,p,cell.state(),cell.entity());
            }
        }
        return written;
    }

    private static long write(Sink sink,BlockPos pos,BlockState state,CompoundTag entity) {
        if (sink.get(pos)==state && entity==null) return 0;
        sink.set(pos,state,entity);return 1;
    }
    private static CompoundTag compound(String state) {
        // 清单基础材质为无属性的注册方块；组件完整属性保留在分块palette中。
        CompoundTag tag=new CompoundTag();tag.putString("Name",state);return tag;
    }
    private static BlockState parseState(ServerLevel level,CompoundTag tag) {
        ResourceLocation id=ResourceLocation.parse(tag.getString("Name"));
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            if (id.getNamespace().equals("minecraft") || id.getNamespace().equals("myvillage"))
                throw new IllegalArgumentException("宗门必需方块没有注册："+id);
            return ModBlockFallback.resolveBlockState(level.getServer(),id);
        }
        String state=id.toString();CompoundTag properties=tag.getCompound("Properties");
        if (!properties.isEmpty()) {
            List<String> values=new ArrayList<>();
            for (String key:new TreeSet<>(properties.getAllKeys())) values.add(key+"="+properties.getString(key));
            state+="["+String.join(",",values)+"]";
        }
        try { return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK),state,false).blockState(); }
        catch (CommandSyntaxException ex) { throw new IllegalArgumentException("宗门方块属性无效："+state,ex); }
    }
    private static List<Cell> tile(ServerLevel level,SectBlueprint data,SectBlueprint.Tile tile) {
        Map<String,List<Cell>> cache;
        synchronized (CACHE) {
            cache=CACHE.computeIfAbsent(level.getServer(),s->new LinkedHashMap<>(16,.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String,List<Cell>> entry) { return size()>8; }
            });
        }
        String key=tile.template()+":"+tile.sha256();
        synchronized (cache) {
            List<Cell> found=cache.get(key);if (found!=null) return found;
            String file="structure/"+tile.template().getPath()+".nbt";
            ResourceLocation resource=ResourceLocation.fromNamespaceAndPath(tile.template().getNamespace(),file);
            try (InputStream input=level.getServer().getResourceManager().open(resource)) {
                byte[] bytes=SectBlueprint.limited(input,4*1024*1024);SectBlueprint.requireHash(bytes,tile.sha256());
                CompoundTag root=NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtAccounter.create(32L*1024*1024));
                ListTag size=root.getList("size",Tag.TAG_INT),blocks=root.getList("blocks",Tag.TAG_COMPOUND),tags=root.getList("palette",Tag.TAG_COMPOUND);
                if (size.size()!=3 || size.getInt(0)!=16 || size.getInt(1)!=data.height || size.getInt(2)!=16
                        || !root.contains("entities",Tag.TAG_LIST) || !root.getList("entities",Tag.TAG_COMPOUND).isEmpty()
                        || tags.isEmpty() || tags.size()>4096 || blocks.size()>16*data.height*16)
                    throw new IllegalArgumentException("宗门分块尺寸或实体边界无效");
                List<BlockState> palette=new ArrayList<>();
                for (int i=0;i<tags.size();i++) palette.add(parseState(level,tags.getCompound(i)));
                BitSet seen=new BitSet(16*data.height*16);List<Cell> cells=new ArrayList<>();
                for (int i=0;i<blocks.size();i++) {
                    CompoundTag cell=blocks.getCompound(i);ListTag p=cell.getList("pos",Tag.TAG_INT);int index=cell.getInt("state");
                    if (!cell.contains("state",Tag.TAG_INT) || p.size()!=3 || p.getInt(0)<0 || p.getInt(0)>15
                            || p.getInt(2)<0 || p.getInt(2)>15 || p.getInt(1)<0 || p.getInt(1)>=data.height || index<0 || index>=palette.size())
                        throw new IllegalArgumentException("宗门分块坐标或palette越界");
                    int bit=(p.getInt(1)*16+p.getInt(2))*16+p.getInt(0);
                    if (seen.get(bit)) throw new IllegalArgumentException("宗门分块坐标重复");seen.set(bit);
                    CompoundTag entity=cell.contains("nbt",Tag.TAG_COMPOUND)?cell.getCompound("nbt").copy():null;
                    if (entity!=null && !palette.get(index).hasBlockEntity()) throw new IllegalArgumentException("经回退方块不能接收原方块实体");
                    cells.add(new Cell(new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2)),palette.get(index),entity));
                }
                List<Cell> result=List.copyOf(cells);cache.put(key,result);return result;
            } catch (IOException ex) { throw new IllegalStateException("宗门分块读取失败："+tile.template(),ex); }
        }
    }
}
