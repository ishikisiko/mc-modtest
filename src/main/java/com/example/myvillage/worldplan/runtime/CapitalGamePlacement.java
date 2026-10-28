package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.capital.CapitalCity;
import com.example.myvillage.worldplan.capital.CapitalGameBundle;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** Production overlays are streamed one 16xheightx16 sparse NBT at a time; never cached as a whole city. */
public final class CapitalGamePlacement {
    private static final Map<ServerLevel, Set<String>> VERIFIED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int MAX_TILE_BYTES = 8 * 1024 * 1024;
    public record Foundation(BlockState top,BlockState fill,BlockState subsurface,int depth) { }
    private CapitalGamePlacement() { }
    public static void clear() { VERIFIED.clear(); }

    /** A solid foundation through the entire column; the heightfield value is the TOP SOLID block. */
    public static BlockState column(Foundation ground,boolean inside,int y,int firstAir,int seaLevel,BlockState original) {
        if (y >= firstAir) return !inside && y < seaLevel ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        if (!inside) {
            // Preserve native surface materials in the transition; only replace voids with fill.
            if (y >= firstAir - 4 && !original.isAir() && original.getFluidState().isEmpty()) return original;
            return Blocks.STONE.defaultBlockState();
        }
        if (y == firstAir - 1) return ground.top();
        if (y >= firstAir - 1 - ground.depth()) return ground.subsurface();
        return ground.fill();
    }
    public static Foundation foundation(CapitalGameBundle bundle) {
        return new Foundation(block(bundle.topBlock),block(bundle.fillBlock),block(bundle.subsurfaceBlock),bundle.subsurfaceDepth);
    }
    private static BlockState block(String id) {
        ResourceLocation name=ResourceLocation.parse(id);
        if(!BuiltInRegistries.BLOCK.containsKey(name))throw new IllegalStateException("Unknown capital foundation block "+id);
        return BuiltInRegistries.BLOCK.get(name).defaultBlockState();
    }
    /** Reassert authored ground after vanilla surface rules, before features. No neighbouring writes. */
    public static void surface(WorldPlan plan, ChunkAccess chunk) {
        if(plan.rules().capitals==null)return;
        var at=new BlockPos.MutableBlockPos();
        Map<CapitalGameBundle,Foundation> materials=new IdentityHashMap<>();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            int wx=chunk.getPos().getMinBlockX()+x,wz=chunk.getPos().getMinBlockZ()+z;
            var city=plan.capitalTerrainAt(wx,wz).orElse(null);if(city==null)continue;
            Foundation ground=materials.computeIfAbsent(city.bundle(),CapitalGamePlacement::foundation);
            boolean inside=city.contains(wx,wz,0);
            int target=plan.surfaceY(wx,wz),source=chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z)+1;
            int max=Math.min(chunk.getMaxBuildHeight(),Math.max(Math.max(source,target),plan.rules().seaLevel));
            for(int y=chunk.getMinBuildHeight()+5;y<max;y++) {
                at.set(wx,y,wz);var original=chunk.getBlockState(at);
                var next=column(ground,inside,y,target,plan.rules().seaLevel,original);
                if(next!=original)chunk.setBlockState(at,next,false);
            }
        }
        Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
    }
    public static void placeSlice(WorldPlan plan,WorldGenLevel level,ChunkAccess chunk) {
        if(plan.rules().capitals==null)return;
        int wx=chunk.getPos().getMinBlockX(),wz=chunk.getPos().getMinBlockZ();
        var city=plan.capitalAt(wx,wz).orElse(null);if(city==null)return;
        verify(level.getLevel(),city.bundle());
        var tile=city.bundle().tile(wx-city.x(),wz-city.z()).orElse(null);if(tile==null)return;
        String file=CapitalGameBundle.templateFile(tile.template());
        byte[] raw=read(level.getLevel(),file,MAX_TILE_BYTES);CapitalGameBundle.requireHash(raw,tile.sha256(),file);
        try {
            CompoundTag root=NbtIo.readCompressed(new ByteArrayInputStream(raw),NbtAccounter.create(32L*1024*1024));
            if(!root.contains("entities",Tag.TAG_LIST)||!root.contains("blocks",Tag.TAG_LIST)||!root.contains("palette",Tag.TAG_LIST))
                throw new IllegalStateException("Missing capital sparse structure fields "+file);
            ListTag size=root.getList("size",Tag.TAG_INT);
            if(size.size()!=3||size.getInt(0)!=16||size.getInt(1)!=city.bundle().height||size.getInt(2)!=16
                    ||!root.getList("entities",Tag.TAG_COMPOUND).isEmpty())throw new IllegalStateException("Capital tile volume/entities mismatch "+file);
            ListTag tags=root.getList("palette",Tag.TAG_COMPOUND),cells=root.getList("blocks",Tag.TAG_COMPOUND);
            if(tags.isEmpty()||tags.size()>4096||cells.size()>16*city.bundle().height*16)throw new IllegalStateException("Capital tile bounds "+file);
            List<BlockState> palette=new ArrayList<>();
            for(int i=0;i<tags.size();i++) {
                CompoundTag state=tags.getCompound(i);ResourceLocation name=ResourceLocation.parse(state.getString("Name"));
                if(!BuiltInRegistries.BLOCK.containsKey(name))throw new IllegalStateException("Unknown production capital block "+name);
                palette.add(NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),state));
            }
            BitSet occupied=new BitSet(16*city.bundle().height*16);
            for(int i=0;i<cells.size();i++) {
                CompoundTag cell=cells.getCompound(i);ListTag p=cell.getList("pos",Tag.TAG_INT);int state=cell.getInt("state");
                if(!cell.contains("state",Tag.TAG_INT)||p.size()!=3||p.getInt(0)<0||p.getInt(0)>15||p.getInt(2)<0||p.getInt(2)>15||p.getInt(1)<0||p.getInt(1)>=city.bundle().height
                        ||state<0||state>=palette.size())throw new IllegalStateException("Capital sparse cell outside current chunk "+file);
                int key=(p.getInt(1)*16+p.getInt(2))*16+p.getInt(0);
                if(occupied.get(key))throw new IllegalStateException("Duplicate capital sparse cell "+file);occupied.set(key);
                BlockPos at=new BlockPos(wx+p.getInt(0),city.originY()+p.getInt(1),wz+p.getInt(2));
                if(at.getY()<chunk.getMinBuildHeight()||at.getY()>=chunk.getMaxBuildHeight())throw new IllegalStateException("Capital tile exceeds world height "+file);
                BlockState value=palette.get(state);BlockState before=chunk.setBlockState(at,value,false);
                if(before!=null&&before.hasBlockEntity())chunk.removeBlockEntity(at);
                if(value.hasBlockEntity()) {
                    // Match WorldGenRegion's deferred creation, including beds/chests/furnaces
                    // whose production tile needs no custom block-entity payload.
                    CompoundTag data=cell.contains("nbt",Tag.TAG_COMPOUND)?cell.getCompound("nbt").copy():new CompoundTag();
                    if(!data.contains("id",Tag.TAG_STRING))data.putString("id","DUMMY");
                    data.putInt("x",at.getX());data.putInt("y",at.getY());data.putInt("z",at.getZ());
                    chunk.setBlockEntityNbt(data);
                }else if(cell.contains("nbt",Tag.TAG_COMPOUND))throw new IllegalStateException("Capital block entity without matching block "+file);
                if(!value.getFluidState().isEmpty()||value.hasPostProcess(level,at))chunk.markPosForPostprocessing(at);
            }
            Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
        }catch(IOException e){throw new IllegalStateException("Cannot decode production capital slice "+file,e);}
    }
    /** Verify data-pack replacements once per city/reload, then hashes still guard each requested tile. */
    public static void verify(ServerLevel level,CapitalGameBundle bundle) {
        synchronized(VERIFIED) {
            var known=VERIFIED.computeIfAbsent(level,ignored->new HashSet<>());
            if(known.contains(bundle.bundleHash))return;
            byte[] json=read(level,bundle.resource,8*1024*1024);
            bundle.requireManifest(JsonParser.parseString(new String(json,StandardCharsets.UTF_8)).getAsJsonObject());
            CapitalGameBundle.requireHash(read(level,bundle.terrainResource,16*1024*1024),bundle.terrainHash,bundle.terrainResource);
            known.add(bundle.bundleHash);
        }
    }
    private static byte[] read(ServerLevel level,String name,int max) {
        try(var input=level.getServer().getResourceManager().getResourceOrThrow(ResourceLocation.parse(name)).open()) {
            byte[] data=input.readNBytes(max+1);if(data.length>max)throw new IllegalStateException("Capital resource too large "+name);return data;
        }catch(IOException e){throw new IllegalStateException("Missing frozen capital asset "+name,e);}
    }
}
