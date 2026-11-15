package com.example.myvillage.resources;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackedResourceStoreTest {
    private static final String MODEL="assets/yuanxian/models/block/test.json",NBT="data/yuanxian/structure/test.nbt";
    private static final Gson GSON=new GsonBuilder().disableHtmlEscaping().create();
    private static final byte[] MODEL_BYTES="{\"textures\":{\"a\":\"yuanxian:block/a\"},\"elements\":[]}\n".getBytes(StandardCharsets.UTF_8);
    private record Fixture(Map<String,byte[]> files,JsonObject manifest) {
        PackedResourceStore store()throws Exception{
            files.put("yuanxian_packed/manifest.json",GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
            return PackedResourceStore.load(path->{byte[] value=files.get(path);return value==null?null:new ByteArrayInputStream(value);});
        }
    }
    private static byte[] gzip(byte[] raw)throws Exception{
        var out=new ByteArrayOutputStream();try(var zip=new GZIPOutputStream(out)){zip.write(raw);}return out.toByteArray();
    }
    private static Fixture fixture()throws Exception{
        JsonObject config;
        try(var in=PackedResourceStoreTest.class.getResourceAsStream("/yuanxian_packed/rules.json")){
            assertNotNull(in);config=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
        config.addProperty("cache_groups",1);config.addProperty("cache_bytes",256);
        var limits=new JsonObject();
        for(String k:List.of("max_entries","max_file_bytes","max_group_bytes","max_manifest_bytes","cache_bytes","cache_groups"))limits.add(k,config.get(k));
        var manifest=new JsonObject();manifest.addProperty("schema",1);manifest.addProperty("namespace","yuanxian");manifest.add("limits",limits);
        var chunks=new JsonArray();Map<String,byte[]> files=new HashMap<>();
        List<byte[]> raw=new ArrayList<>();
        var parameters=List.of("{\"a\":\"yuanxian:block/a\"}","[]");
        raw.add(GSON.toJson(List.of(List.of(0,parameters))).getBytes(StandardCharsets.UTF_8));
        raw.add(GSON.toJson(List.of(List.of("{\"textures\":",",\"elements\":","}\n"))).getBytes(StandardCharsets.UTF_8));
        var binary=new ByteArrayOutputStream();try(var out=new DataOutputStream(binary)){
            out.writeInt(0x4d565242);out.writeByte(1);out.writeInt(1);out.writeInt(3);out.write(new byte[]{1,2,3});
        }
        raw.add(binary.toByteArray());
        for(int i=0;i<3;i++){
            String path="yuanxian_packed/"+i+".gz";byte[] packed=gzip(raw.get(i));files.put(path,packed);
            chunks.add(GSON.toJsonTree(List.of(path,List.of("models","templates","bytes").get(i),raw.get(i).length,PackedResourceStore.sha(packed),1)));
        }
        manifest.add("chunks",chunks);manifest.add("templates",GSON.toJsonTree(List.of(List.of(1,0))));
        manifest.add("entries",GSON.toJsonTree(List.of(List.of(MODEL,0,0,PackedResourceStore.sha(MODEL_BYTES),MODEL_BYTES.length),List.of(NBT,2,0,PackedResourceStore.sha(new byte[]{1,2,3}),3))));
        files.put("yuanxian_packed/rules.json",GSON.toJson(config).getBytes(StandardCharsets.UTF_8));return new Fixture(files,manifest);
    }
    @Test void restoresGeometryAndBinaryWithBoundedCacheAndClose()throws Exception{
        try(var store=fixture().store()){
            assertArrayEquals(MODEL_BYTES,store.open(MODEL).readAllBytes());assertArrayEquals(new byte[]{1,2,3},store.open(NBT).readAllBytes());
            assertNull(store.open("assets/yuanxian/missing.json"));
            assertTrue(store.stats().get("cached_bytes")<=256);assertTrue(store.stats().get("cached_groups")<=1);
            store.close();assertEquals(0,store.stats().get("cached_bytes"));assertThrows(IOException.class,()->store.open(MODEL));
        }
    }
    @Test void refusesTraversalDuplicateForeignNamespaceAndFractionalIndices()throws Exception{
        for(String bad:List.of("assets/yuanxian/../escape.json","assets/other/model.json","/assets/yuanxian/a.json")){
            var f=fixture();f.manifest.getAsJsonArray("entries").get(0).getAsJsonArray().set(0,new JsonPrimitive(bad));assertThrows(IOException.class,f::store);
        }
        var duplicate=fixture();var entries=duplicate.manifest.getAsJsonArray("entries");entries.add(entries.get(0).deepCopy());assertThrows(IOException.class,duplicate::store);
        var fraction=fixture();fraction.manifest.getAsJsonArray("entries").get(0).getAsJsonArray().set(1,new JsonPrimitive(0.5));assertThrows(IOException.class,fraction::store);
    }
    @Test void refusesCorruptGroupAndWrongRestoredResourceHash()throws Exception{
        var corrupt=fixture();corrupt.files.get("yuanxian_packed/0.gz")[10]^=1;
        try(var store=corrupt.store()){assertThrows(IOException.class,()->store.open(MODEL));}
        var wrong=fixture();wrong.manifest.getAsJsonArray("entries").get(0).getAsJsonArray().set(3,new JsonPrimitive("0".repeat(64)));
        try(var store=wrong.store()){assertThrows(IOException.class,()->store.open(MODEL));}
    }
    @Test void parallelModelReadsNeverMixParametersOrBreakEviction()throws Exception{
        try(var store=fixture().store();var executor=Executors.newFixedThreadPool(4)){
            List<Callable<Boolean>> work=new ArrayList<>();
            for(int i=0;i<128;i++)work.add(()->Arrays.equals(MODEL_BYTES,store.open(MODEL).readAllBytes())&&Arrays.equals(new byte[]{1,2,3},store.open(NBT).readAllBytes()));
            for(var future:executor.invokeAll(work))assertTrue(future.get());
            assertTrue(store.stats().get("cached_groups")<=1);
        }
    }
    @Test void standardPackEnumerationOverridesAndReloadKeepTheirMeaning()throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();
        var type=net.minecraft.server.packs.PackType.CLIENT_RESOURCES;
        var location=new net.minecraft.server.packs.PackLocationInfo("test/shared",net.minecraft.network.chat.Component.literal("共享资源测试"),net.minecraft.server.packs.repository.PackSource.BUILT_IN,Optional.empty());
        var store=fixture().store();
        try(var pack=new SharedResourcePack(location,type,store)){
            var id=net.minecraft.resources.ResourceLocation.parse("yuanxian:models/block/test.json");
            Map<net.minecraft.resources.ResourceLocation,net.minecraft.server.packs.resources.IoSupplier<InputStream>> listed=new HashMap<>();
            pack.listResources(type,"yuanxian","models",listed::put);
            assertEquals(Set.of(id),listed.keySet());assertArrayEquals(MODEL_BYTES,listed.get(id).get().readAllBytes());
            assertNull(pack.getResource(net.minecraft.server.packs.PackType.SERVER_DATA,id));
            var manager=new net.minecraft.server.packs.resources.FallbackResourceManager(type,"yuanxian");manager.push(pack);
            assertArrayEquals(MODEL_BYTES,manager.getResource(id).orElseThrow().open().readAllBytes());
            var userLocation=new net.minecraft.server.packs.PackLocationInfo("test/user",net.minecraft.network.chat.Component.literal("用户覆盖测试"),net.minecraft.server.packs.repository.PackSource.DEFAULT,Optional.empty());
            var override=new net.minecraft.server.packs.AbstractPackResources(userLocation){
                @Override public net.minecraft.server.packs.resources.IoSupplier<InputStream> getRootResource(String... path){return null;}
                @Override public net.minecraft.server.packs.resources.IoSupplier<InputStream> getResource(net.minecraft.server.packs.PackType requested,net.minecraft.resources.ResourceLocation name){return requested==type&&name.equals(id)?()->new ByteArrayInputStream(new byte[]{42}):null;}
                @Override public void listResources(net.minecraft.server.packs.PackType requested,String namespace,String prefix,net.minecraft.server.packs.PackResources.ResourceOutput out){}
                @Override public Set<String> getNamespaces(net.minecraft.server.packs.PackType requested){return Set.of("yuanxian");}
                @Override public void close(){}
            };
            manager.push(override);var resource=manager.getResource(id).orElseThrow();
            assertEquals("test/user",resource.sourcePackId());assertArrayEquals(new byte[]{42},resource.open().readAllBytes());
        }
        assertEquals(0,store.stats().get("cached_bytes"));assertThrows(IOException.class,()->store.open(MODEL));
        try(var fresh=fixture().store()){
            assertEquals(0,fresh.stats().get("cached_bytes"));assertArrayEquals(MODEL_BYTES,fresh.open(MODEL).readAllBytes());
        }
    }
}
