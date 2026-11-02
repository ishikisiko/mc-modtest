package com.example.myvillage.sect;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 同一真实分块在未对齐世界坐标、不同clip次序下得到相同最终方块。 */
class SectCompoundRealizationTest {
    static SectBlueprint data;
    static final Map<String,List<SectBlueprintPlacement.Cell>> cached=new HashMap<>();
    static JsonObject fallbacks;
    @BeforeAll static void bootstrap() throws Exception {
        if (LoadingModList.get()==null) LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        data=SectBlueprint.current();
        fallbacks=SectBlueprint.json(SectBlueprint.readClasspath("data/myvillage/mod_block_fallbacks.json",8*1024*1024));
    }
    static CompoundTag root(SectBlueprint.Tile tile) throws Exception {
        byte[] raw=SectBlueprint.readClasspath("data/"+tile.template().getNamespace()+"/structure/"+tile.template().getPath()+".nbt",4*1024*1024);
        SectBlueprint.requireHash(raw,tile.sha256());
        return NbtIo.readCompressed(new ByteArrayInputStream(raw),NbtAccounter.create(32L*1024*1024));
    }
    static BlockState state(CompoundTag tag) {
        ResourceLocation id=ResourceLocation.parse(tag.getString("Name"));
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            // 单元环境未启动FML：仅用于放置/裁切一致性；真实模组碰撞由原生世界验证。
            String replacement=fallbacks.has(id.toString())?fallbacks.get(id.toString()).getAsString():"minecraft:stone";
            String name=replacement.split("\\[",2)[0];
            return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(name)).defaultBlockState();
        }
        return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),tag);
    }
    static List<SectBlueprintPlacement.Cell> cells(SectBlueprint.Tile tile) {
        return cached.computeIfAbsent(tile.sha256(),key->{
            try {
                CompoundTag root=root(tile);ListTag tags=root.getList("palette",Tag.TAG_COMPOUND),blocks=root.getList("blocks",Tag.TAG_COMPOUND);
                List<BlockState> palette=new ArrayList<>();for(int i=0;i<tags.size();i++)palette.add(state(tags.getCompound(i)));
                List<SectBlueprintPlacement.Cell> result=new ArrayList<>();
                for(int i=0;i<blocks.size();i++) {
                    CompoundTag cell=blocks.getCompound(i);ListTag p=cell.getList("pos",Tag.TAG_INT);
                    result.add(new SectBlueprintPlacement.Cell(new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2)),palette.get(cell.getInt("state")),null));
                }
                return List.copyOf(result);
            } catch(Exception ex) { throw new IllegalStateException(ex); }
        });
    }
    static final SectBlueprintPlacement.Assets ASSETS=new SectBlueprintPlacement.Assets() {
        public int minimumY(){return -64;} public int maximumY(){return 320;}
        public BlockState material(String name){return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(name)).defaultBlockState();}
        public List<SectBlueprintPlacement.Cell> cells(SectBlueprint d,SectBlueprint.Tile tile){return SectCompoundRealizationTest.cells(tile);}
    };
    static class Memory implements SectBlueprintPlacement.Sink {
        final Map<BlockPos,BlockState> blocks=new HashMap<>();final BoundingBox allowed;
        Memory(BoundingBox allowed){this.allowed=allowed;}
        public BlockState get(BlockPos p){return blocks.getOrDefault(p,p.getY()<50?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());}
        public void set(BlockPos p,BlockState s,CompoundTag entity){assertTrue(allowed.isInside(p),"写出clip："+p);blocks.put(p.immutable(),s);}
    }
    @Test void completeCompiledTilesHaveBoundedUniqueCoordinatesAndVerifiedHashes() throws Exception {
        int total=0;
        for(var tile:data.tiles){
            var root=root(tile);ListTag size=root.getList("size",Tag.TAG_INT),blocks=root.getList("blocks",Tag.TAG_COMPOUND),palette=root.getList("palette",Tag.TAG_COMPOUND);
            assertEquals(16,size.getInt(0));assertEquals(data.height,size.getInt(1));assertEquals(16,size.getInt(2));
            assertTrue(root.getList("entities",Tag.TAG_COMPOUND).isEmpty());assertFalse(blocks.isEmpty());
            Set<BlockPos> seen=new HashSet<>();
            for(int i=0;i<blocks.size();i++){
                var cell=blocks.getCompound(i);var p=cell.getList("pos",Tag.TAG_INT);
                assertEquals(3,p.size());assertTrue(p.getInt(0)>=0&&p.getInt(0)<16&&p.getInt(2)>=0&&p.getInt(2)<16&&p.getInt(1)>=0&&p.getInt(1)<data.height);
                assertTrue(seen.add(new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2))));assertTrue(cell.getInt("state")>=0&&cell.getInt("state")<palette.size());
            }
            total+=blocks.size();
        }
        // 地形改为连续场，NBT 只包含显式建筑/道路/植被；分块完整性由正式导出逐格回读保证。
        assertTrue(data.tiles.size()<data.width/16*(data.depth/16));
        long terrainCells=0;for(int z=0;z<data.depth;z++)for(int x=0;x<data.width;x++)if(data.terrain.weight(x,z)>0)terrainCells+=data.ground(x,z)+1;
        assertTrue(total>0);assertTrue(total+terrainCells>100000);
        assertEquals(1,data.terrain.weight(data.entry.getX(),data.entry.getZ()));
    }
    @Test void unalignedChunkClipsMatchTheSameWholeRegionAndAreOrderIndependent(){
        BlockPos base=new BlockPos(-173,60,41);
        BoundingBox region=new BoundingBox(base.getX()+129,-64,base.getZ()+108,base.getX()+160,319,base.getZ()+139);
        Memory whole=new Memory(region);SectBlueprintPlacement.place(data,base,region,(x,z)->50,whole,ASSETS);
        Memory clipped=new Memory(region);
        List<BoundingBox> clips=new ArrayList<>();
        for(int z=Math.floorDiv(region.minZ(),16);z<=Math.floorDiv(region.maxZ(),16);z++)
            for(int x=Math.floorDiv(region.minX(),16);x<=Math.floorDiv(region.maxX(),16);x++)
                clips.add(new BoundingBox(Math.max(region.minX(),x*16),-64,Math.max(region.minZ(),z*16),Math.min(region.maxX(),x*16+15),319,Math.min(region.maxZ(),z*16+15)));
        Collections.reverse(clips);
        for(var box:clips) SectBlueprintPlacement.place(data,base,box,(x,z)->50,clipped,ASSETS);
        assertFalse(whole.blocks.isEmpty());assertEquals(whole.blocks,clipped.blocks);
        Map<BlockPos,BlockState> before=Map.copyOf(clipped.blocks);
        for(var box:clips) SectBlueprintPlacement.place(data,base,box,(x,z)->50,clipped,ASSETS);
        assertEquals(before,clipped.blocks,"重放当前clip不得抬高或改变蓝图");
    }
    @Test void compiledBedsHaveMatchingHalvesAcrossTileEdges() throws Exception {
        record Bed(String name,String facing,String part) { }
        Map<BlockPos,Bed> beds=new HashMap<>();
        for (var tile:data.tiles) {
            var nbt=root(tile);var palette=nbt.getList("palette",Tag.TAG_COMPOUND);var blocks=nbt.getList("blocks",Tag.TAG_COMPOUND);
            for (int i=0;i<blocks.size();i++) {
                var cell=blocks.getCompound(i);var state=palette.getCompound(cell.getInt("state"));
                if (!state.getString("Name").endsWith("_bed")) continue;
                var p=cell.getList("pos",Tag.TAG_INT);var properties=state.getCompound("Properties");
                beds.put(new BlockPos(tile.x()+p.getInt(0),p.getInt(1),tile.z()+p.getInt(2)),
                    new Bed(state.getString("Name"),properties.getString("facing"),properties.getString("part")));
            }
        }
        assertFalse(beds.isEmpty());
        for (var row:beds.entrySet()) {
            Bed bed=row.getValue();Direction direction=Direction.byName(bed.facing());assertNotNull(direction);
            assertTrue(bed.part().equals("foot") || bed.part().equals("head"));
            BlockPos other=row.getKey().relative(bed.part().equals("foot")?direction:direction.getOpposite());
            assertEquals(new Bed(bed.name(),bed.facing(),bed.part().equals("foot")?"head":"foot"),beds.get(other),
                "编译床缺少匹配另一半："+row.getKey());
        }
    }
    @Test void resourceHashDriftIsRejected(){assertThrows(IllegalArgumentException.class,()->SectBlueprint.requireHash(new byte[]{1},"0".repeat(64)));}
}
