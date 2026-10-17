package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.WorldPlanRules;
import com.example.myvillage.worldplan.CityAuditPolicy;
import com.example.myvillage.region.runtime.RegionGeometry;
import com.google.gson.JsonObject;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.io.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Exactly one chunk-sized template per city chunk, naturally emitted in FEATURES. */
public final class PlannedCity {
    private PlannedCity() {}
    public static void placeSlice(WorldPlan plan,WorldGenLevel level,ChunkAccess chunk){
        var selected=plan.cityTile(chunk.getPos().x,chunk.getPos().z);if(selected.isEmpty())return;
        var tile=selected.get();var city=plan.city();ResourceLocation id=ResourceLocation.parse(tile.template());
        verifiedTag(level.getLevel(),tile);
        StructureTemplate template=level.getLevel().getStructureManager().get(id).orElseThrow(()->new IllegalStateException("Missing frozen city template "+id));
        if(template.getSize().getX()!=16||template.getSize().getZ()!=16||template.getSize().getY()!=city.height())throw new IllegalStateException("Unexpected frozen city tile size "+id);
        BlockPos origin=new BlockPos(city.x()+tile.x(),city.baseY(),city.z()+tile.z());
        BoundingBox clip=new BoundingBox(origin.getX(),city.baseY(),origin.getZ(),origin.getX()+15,city.baseY()+city.height()-1,origin.getZ()+15);
        StructurePlaceSettings settings=new StructurePlaceSettings().setBoundingBox(clip).setKnownShape(true).setIgnoreEntities(true);
        RandomSource random=RandomSource.create(RegionGeometry.hash(plan.seed(),tile.x(),tile.z(),0x43495459));
        try(var ownership=WorldPlanGuard.ownSlice(level,clip)){
            if(!template.placeInWorld(level,origin,origin,settings,random,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))throw new IllegalStateException("Failed city tile "+id+" at "+origin);
        }
    }
    private static CompoundTag verifiedTag(ServerLevel level,WorldPlanRules.Tile tile){
        ResourceLocation id=ResourceLocation.parse(tile.template());
        ResourceLocation file=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"structure/"+id.getPath()+".nbt");
        try(var in=level.getServer().getResourceManager().getResourceOrThrow(file).open()){
            byte[] bytes=in.readAllBytes();String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!digest.equals(tile.sha256()))throw new IllegalStateException("Frozen city asset differs from saved plan: "+file+". Restore its matching asset version.");
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtAccounter.unlimitedHeap());
        }catch(IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException("Cannot read city asset "+file,e);}
    }
    /** Reads loaded chunks only; auditing a partial city never completes it as a side effect. */
    public static JsonObject audit(WorldPlan plan,ServerLevel level){
        JsonObject result=new JsonObject();int loaded=0,missing=0,compared=0,differences=0,naturalChanges=0;List<String> examples=new ArrayList<>(),naturalExamples=new ArrayList<>();
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(var tile:plan.rules().city.tiles()){
                int ox=plan.city().x()+tile.x(),oz=plan.city().z()+tile.z();var chunk=level.getChunkSource().getChunkNow(ox>>4,oz>>4);
                if(chunk==null){missing++;continue;}loaded++;
                CompoundTag tag=verifiedTag(level,tile);ListTag entries=tag.getList("palette",Tag.TAG_COMPOUND);List<BlockState> palette=new ArrayList<>();
                for(int i=0;i<entries.size();i++)palette.add(NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),entries.getCompound(i)));
                ListTag blocks=tag.getList("blocks",Tag.TAG_COMPOUND);
                Map<BlockPos,BlockState> expectedBlocks=new LinkedHashMap<>();
                for(int i=0;i<blocks.size();i++){
                    CompoundTag b=blocks.getCompound(i);ListTag xyz=b.getList("pos",Tag.TAG_INT);
                    BlockPos at=new BlockPos(ox+xyz.getInt(0),plan.city().baseY()+xyz.getInt(1),oz+xyz.getInt(2));
                    expectedBlocks.put(at,palette.get(b.getInt("state")));
                }
                for(var entry:expectedBlocks.entrySet()){
                    BlockPos at=entry.getKey();BlockState expected=entry.getValue(),actual=chunk.getBlockState(at);compared++;
                    digest.update((at.getX()+","+at.getY()+","+at.getZ()+":"+actual.toString()+";").getBytes(StandardCharsets.UTF_8));
                    if(actual!=expected){
                        BlockState cover=expectedBlocks.getOrDefault(at.above(),Blocks.AIR.defaultBlockState());
                        boolean intactCover=cover.canOcclude()&&chunk.getBlockState(at.above())==cover;
                        String expectedId=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(expected.getBlock()).toString();
                        String actualId=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString();
                        String example=at.toShortString()+" expected="+expected+" actual="+actual;
                        if(CityAuditPolicy.naturalChange(expectedId,actualId,intactCover)){
                            naturalChanges++;if(naturalExamples.size()<6)naturalExamples.add(example);
                        }else{differences++;if(examples.size()<6)examples.add(example);}
                    }
                }
            }
            result.addProperty("loaded_tiles",loaded);result.addProperty("missing_tiles",missing);result.addProperty("compared_blocks",compared);
            result.addProperty("differences",differences);result.add("examples",WorldPlanRules.JSON.toJsonTree(examples));
            result.addProperty("natural_changes",naturalChanges);result.add("natural_examples",WorldPlanRules.JSON.toJsonTree(naturalExamples));
            result.addProperty("raw_differences",differences+naturalChanges);
            result.addProperty("strict_complete",missing==0&&differences+naturalChanges==0);
            result.addProperty("actual_sha256",HexFormat.of().formatHex(digest.digest()));result.addProperty("plan_sha256",plan.fingerprint());
            result.addProperty("chunks_requested",0);result.addProperty("complete",missing==0&&differences==0);return result;
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
