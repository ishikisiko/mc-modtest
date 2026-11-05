package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.mine.*;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StarterMineCaveTest {
    private static Registry<Biome> biomes;
    @BeforeAll static void bootstrap(){
        if(net.neoforged.fml.loading.LoadingModList.get()==null)net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var registry=new MappedRegistry<Biome>(Registries.BIOME,Lifecycle.stable());
        registry.register(Biomes.PLAINS,new Biome.BiomeBuilder().hasPrecipitation(false).temperature(.5f).downfall(0)
            .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
            .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build(),RegistrationInfo.BUILT_IN);
        biomes=registry.freeze();
    }
    /** 固定介质用于验证施工权限、空洞/含水层边界；不冒充原生噪声地形。 */
    static final class TerrainChunk extends ProtoChunk {
        final Map<BlockPos,BlockState> writes=new HashMap<>();final BlockState base;
        TerrainChunk(ChunkPos p,BlockState base){super(p,UpgradeData.EMPTY,LevelHeightAccessor.create(-64,384),biomes,null);this.base=base;}
        @Override public BlockState getBlockState(BlockPos p){return writes.getOrDefault(p,base);}
        @Override public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
        @Override public BlockState setBlockState(BlockPos p,BlockState value,boolean moving){
            assertEquals(getPos().x,p.getX()>>4,"cross-chunk x write");assertEquals(getPos().z,p.getZ()>>4,"cross-chunk z write");
            assertTrue(p.getY()>=getMinBuildHeight()&&p.getY()<getMaxBuildHeight());return writes.put(p.immutable(),value);
        }
    }
    private record Fixture(WorldPlan world,StarterMineGeneration mine) {}
    private static Fixture fixture(long seed,boolean horizontal)throws Exception{
        var world=StarterMinePlanTest.flatWorld(seed);var roads=StarterMinePlanTest.road(world,horizontal);var mine=new StarterMineGeneration(Optional.empty());mine.bind(world,roads);return new Fixture(world,mine);
    }
    private static Map<Long,TerrainChunk> generate(Fixture f,BlockState base,boolean reverse){
        var m=f.mine.layout();var d=f.mine.definition();List<ChunkPos> order=new ArrayList<>();
        for(int z=m.minZ(d)>>4;z<=m.maxZ(d)>>4;z++)for(int x=m.minX(d)>>4;x<=m.maxX(d)>>4;x++)order.add(new ChunkPos(x,z));
        if(reverse)Collections.reverse(order);Map<Long,TerrainChunk> chunks=new HashMap<>();
        for(var at:order){var chunk=new TerrainChunk(at,base);StarterMineCaveCarver.carve(f.world,f.mine,chunk);StarterMineCaveCarver.finishAccess(f.world,f.mine,chunk);chunks.put(at.toLong(),chunk);}
        return chunks;
    }
    private static BlockState at(Map<Long,TerrainChunk> chunks,BlockPos p){var c=chunks.get(ChunkPos.asLong(p.getX()>>4,p.getZ()>>4));return c==null?Blocks.STONE.defaultBlockState():c.getBlockState(p);}
    private static Map<BlockPos,BlockState> writes(Map<Long,TerrainChunk> chunks){Map<BlockPos,BlockState> out=new HashMap<>();chunks.values().forEach(c->out.putAll(c.writes));return out;}
    static Map<BlockPos,BlockState> lights(StarterMineLayout layout,List<MineAccessLighting.Torch> torches){
        var result=new HashMap<BlockPos,BlockState>();for(var torch:torches)assertNull(result.put(torch.position(layout),torch.state(layout)));return result;
    }
    static void assertPairedLighting(StarterMineDefinition def,StarterMineLayout layout,List<MineAccessLighting.Torch> torches,int endZ,Map<BlockPos,BlockState> actual){
        int first=def.caves.flatEndZ+2;
        assertEquals(2*((endZ-first)/def.caves.torchSpacing+1),torches.size());
        var planned=lights(layout,torches);
        assertEquals(planned,actual.entrySet().stream().filter(e->e.getValue().is(Blocks.WALL_TORCH)).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)));
        for(var torch:torches){
            assertEquals(def.caves.connectionRadius,Math.abs(torch.x()-def.entry.x()));
            assertEquals(0,(torch.z()-first)%def.caves.torchSpacing);
            var pos=torch.position(layout);var state=actual.get(pos);
            var support=pos.relative(state.getValue(WallTorchBlock.FACING).getOpposite());
            int localSupportX=layout.localX(support.getX(),support.getZ());
            assertEquals(def.caves.connectionRadius+1,Math.abs(localSupportX-def.entry.x()),"火把必须朝向矿道中心并依附外侧岩壁");
            assertEquals(torch.z(),layout.localZ(support.getX(),support.getZ()));
            assertNotNull(actual.get(support),"跨区块的火把支撑不能缺失");
            assertTrue(actual.get(support).is(Blocks.STONE)||actual.get(support).is(Blocks.COAL_ORE)||actual.get(support).is(Blocks.IRON_ORE)||actual.get(support).is(Blocks.COPPER_ORE));
            var other=new BlockPos(layout.worldX(2*def.entry.x()-torch.x(),torch.z()),pos.getY(),layout.worldZ(2*def.entry.x()-torch.x(),torch.z()));
            assertTrue(actual.get(other).is(Blocks.WALL_TORCH),"同一高度两侧必须都有火把");
            assertTrue(actual.get(new BlockPos(layout.worldX(def.entry.x(),torch.z()),pos.getY(),layout.worldZ(def.entry.x(),torch.z()))).isAir(),"火把不得堵住矿道中线");
        }
    }
    @Test void everySeedHasARealCavernConnectedAccessAndReachableStarterOres()throws Exception{
        var def=fixture(42,false).mine.definition();Set<String> plans=new HashSet<>();
        for(int i=0;i<256;i++){
            long seed=i%2==0?i:-i;var p=new StarterMineCavePlan(seed,def);plans.add(p.hash);
            assertEquals(p.hash,new StarterMineCavePlan(seed,def).hash);assertTrue(p.coreAirBlocks>=def.caves.minimumAir);
            assertTrue(p.branches.size()>=def.caves.branches.min());assertEquals(def.caves.coal+def.caves.iron+def.caves.copper,p.ores.size());
            for(var ore:p.ores){assertTrue(p.lining(def,ore.x(),ore.y(),ore.z()));assertTrue(p.at(ore.x()+Integer.signum(def.entry.x()-ore.x()),ore.z())!=null);}
            for(int z=def.entry.z();z<p.endZ;z++)assertTrue(Math.abs(p.at(def.entry.x(),z).feet2()-p.at(def.entry.x(),z+1).feet2())<=1);
        }
        assertEquals(256,plans.size());
    }
    @Test void originalCarverProducesDifferentVoxelLayoutsAndChunkOrderDoesNotChangeThem()throws Exception{
        Set<Integer> geometries=new HashSet<>();
        for(long seed:new long[]{0,1,-1,20261105})for(boolean horizontal:new boolean[]{false,true}){
            var f=fixture(seed,horizontal);var first=generate(f,Blocks.STONE.defaultBlockState(),false);var second=generate(f,Blocks.STONE.defaultBlockState(),true);
            assertEquals(writes(first),writes(second));var all=writes(first);var normalized=new HashMap<BlockPos,BlockState>();var layout=f.mine.layout();all.forEach((pos,state)->normalized.put(new BlockPos(layout.localX(pos.getX(),pos.getZ()),pos.getY()-layout.y,layout.localZ(pos.getX(),pos.getZ())),state));geometries.add(normalized.hashCode());
            assertTrue(all.values().stream().filter(BlockState::isAir).count()>=f.mine.definition().caves.minimumAir);
            assertEquals(12,all.values().stream().filter(s->s.is(Blocks.COAL_ORE)).count());assertEquals(12,all.values().stream().filter(s->s.is(Blocks.IRON_ORE)).count());
            assertEquals(8,all.values().stream().filter(s->s.is(Blocks.COPPER_ORE)).count());
            assertPairedLighting(f.mine.definition(),layout,f.mine.caves().torches,f.mine.caves().endZ-f.mine.caves().radius,all);
        }
        assertEquals(8,geometries.size());
    }
    @Test void emptyAndWaterFilledUndergroundKeepTheAccessDrySupportedAndUsable()throws Exception{
        var f=fixture(20261105,false);var m=f.mine.layout();var def=f.mine.definition();
        for(var base:List.of(Blocks.AIR.defaultBlockState(),Blocks.WATER.defaultBlockState())){
            var chunks=generate(f,base,false);
            var lighting=lights(m,f.mine.caves().torches);
            for(var p:f.mine.caves().access){
                int x=m.worldX(p.x(),p.z()),z=m.worldZ(p.x(),p.z()),floor=m.y+p.floorY();
                assertEquals(Math.floorMod(p.feet2(),2)==1,at(chunks,new BlockPos(x,floor,z)).is(Blocks.STONE_SLAB));assertFalse(at(chunks,new BlockPos(x,floor,z)).isAir());assertTrue(at(chunks,new BlockPos(x,floor,z)).getFluidState().isEmpty());
                for(int y=floor+1;y<=m.y+(int)Math.ceil(p.feet2()/2.0)+def.caves.headroom-1;y++){
                    var pos=new BlockPos(x,y,z);assertEquals(lighting.getOrDefault(pos,Blocks.CAVE_AIR.defaultBlockState()),at(chunks,pos));
                }
            }
            if(base.is(Blocks.WATER))for(var e:writes(chunks).entrySet())if(e.getValue().isAir())for(var direction:Direction.values())assertTrue(at(chunks,e.getKey().relative(direction)).getFluidState().isEmpty(),e.getKey()+" "+direction);
        }
    }
    @Test void fullyGeneratedChunksCannotBeRecarvedOrHaveMinedBlocksRefilled()throws Exception{
        var f=fixture(42,false);var entry=f.mine.layout().position(f.mine.definition().entry);var c=new TerrainChunk(new ChunkPos(entry.x()>>4,entry.z()>>4),Blocks.AIR.defaultBlockState());c.setPersistedStatus(ChunkStatus.FULL);
        assertThrows(IllegalStateException.class,()->StarterMineCaveCarver.carve(f.world,f.mine,c));assertThrows(IllegalStateException.class,()->StarterMineCaveCarver.finishAccess(f.world,f.mine,c));assertTrue(c.writes.isEmpty());
    }
}
