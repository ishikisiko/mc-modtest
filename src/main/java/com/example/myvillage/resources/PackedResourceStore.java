package com.example.myvillage.resources;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** 当前构建的共享几何与原始资源；不依赖客户端，也不保存世界状态。 */
public final class PackedResourceStore implements AutoCloseable {
    public static final String ROOT="yuanxian_packed/";
    @FunctionalInterface public interface Source { InputStream open(String path) throws IOException; }
    public record Entry(String path,int group,int index,String sha256,int bytes) { }
    private record Chunk(String path,String codec,int bytes,String sha256,int count) { }
    private record Group(int bytes,JsonArray json,List<byte[]> binary) { }
    private final Source source;
    private final Map<String,Entry> entries;
    private final List<Chunk> chunks;
    private final List<int[]> templates;
    private final int maxFile,maxGroup,cacheBudget,cacheGroups;
    private final Map<Integer,Group> cache=new LinkedHashMap<>(16,.75f,true);
    private int cachedBytes;
    private long hits,misses;
    private boolean closed;

    public static PackedResourceStore classpath() throws IOException {
        return load(path->PackedResourceStore.class.getResourceAsStream("/"+path));
    }
    public static PackedResourceStore load(Source source) throws IOException {
        byte[] rules=read(source,ROOT+"rules.json",16384);
        JsonObject config=parse(rules);
        if(integer(config,"schema")!=1||!config.get("root").getAsString().equals("yuanxian_packed")
                ||!config.get("namespace").getAsString().equals("yuanxian"))throw new IOException("共享资源规则版本或命名空间错误");
        int maximum=positive(config,"max_manifest_bytes");
        return new PackedResourceStore(source,config,parse(read(source,ROOT+"manifest.json",maximum)));
    }
    private PackedResourceStore(Source source,JsonObject config,JsonObject manifest) throws IOException {
        this.source=source;
        if(integer(manifest,"schema")!=1||!manifest.get("namespace").getAsString().equals("yuanxian"))throw new IOException("共享资源清单版本错误");
        JsonObject limits=manifest.getAsJsonObject("limits");
        for(String key:List.of("max_entries","max_file_bytes","max_group_bytes","max_manifest_bytes","cache_bytes","cache_groups"))
            if(positive(limits,key)!=positive(config,key))throw new IOException("共享资源规则和清单限制不一致："+key);
        maxFile=positive(limits,"max_file_bytes");maxGroup=positive(limits,"max_group_bytes");
        cacheBudget=positive(limits,"cache_bytes");cacheGroups=positive(limits,"cache_groups");
        List<Chunk> groups=new ArrayList<>();Set<String> groupPaths=new HashSet<>();
        for(JsonElement row:manifest.getAsJsonArray("chunks")){
            JsonArray a=array(row,5);String path=a.get(0).getAsString(),codec=a.get(1).getAsString();
            int bytes=number(a.get(2)),count=number(a.get(4));String sha=a.get(3).getAsString();
            if(!validPath(path)||!path.startsWith(ROOT)||!path.endsWith(".gz")||!groupPaths.add(path)
                    ||!Set.of("bytes","models","templates").contains(codec)||bytes<1||bytes>maxGroup||count<1||count>4096||!validHash(sha))
                throw new IOException("共享资源分组记录错误："+path);
            groups.add(new Chunk(path,codec,bytes,sha,count));
        }
        chunks=List.copyOf(groups);
        List<int[]> geometry=new ArrayList<>();
        for(JsonElement row:manifest.getAsJsonArray("templates")){
            JsonArray a=array(row,2);int group=number(a.get(0)),index=number(a.get(1));
            requireRecord(group,index,"templates");geometry.add(new int[]{group,index});
        }
        templates=List.copyOf(geometry);
        Map<String,Entry> files=new LinkedHashMap<>();
        for(JsonElement row:manifest.getAsJsonArray("entries")){
            JsonArray a=array(row,5);String path=a.get(0).getAsString(),sha=a.get(3).getAsString();
            int group=number(a.get(1)),index=number(a.get(2)),bytes=number(a.get(4));
            if(!validPath(path)||!(path.startsWith("assets/yuanxian/")||path.startsWith("data/yuanxian/"))
                    ||bytes<0||bytes>maxFile||!validHash(sha)||group<0||group>=chunks.size())throw new IOException("共享资源路径或大小错误："+path);
            requireRecord(group,index,chunks.get(group).codec);
            if(chunks.get(group).codec.equals("templates")||files.putIfAbsent(path,new Entry(path,group,index,sha,bytes))!=null
                    ||files.size()>positive(limits,"max_entries"))throw new IOException("共享资源重复或数量越界："+path);
        }
        entries=Collections.unmodifiableMap(files);
    }
    public Map<String,Entry> entries(){return entries;}
    public boolean contains(String path){return entries.containsKey(path);}
    public InputStream open(String path) throws IOException {
        if(!validPath(path))throw new IOException("非法共享资源路径："+path);
        Entry entry=entries.get(path);if(entry==null)return null;
        Group group=group(entry.group);byte[] value;
        if(group.binary!=null)value=group.binary.get(entry.index);
        else{
            JsonArray recipe=array(group.json.get(entry.index),2);int template=number(recipe.get(0));
            if(template<0||template>=templates.size())throw new IOException("几何模板编号越界："+path);
            int[] location=templates.get(template);JsonArray constants=group(location[0]).json.get(location[1]).getAsJsonArray();
            JsonArray parameters=recipe.get(1).getAsJsonArray();
            if(constants.size()!=parameters.size()+1)throw new IOException("几何模板参数数量不符："+path);
            ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(entry.bytes,maxFile));
            for(int i=0;i<constants.size();i++){
                out.write(constants.get(i).getAsString().getBytes(StandardCharsets.UTF_8));
                if(i<parameters.size())out.write(parameters.get(i).getAsString().getBytes(StandardCharsets.UTF_8));
                if(out.size()>maxFile)throw new IOException("恢复模型超过大小限制："+path);
            }
            value=out.toByteArray();
        }
        if(value.length!=entry.bytes||!sha(value).equals(entry.sha256))throw new IOException("共享资源恢复摘要不符："+path);
        return new ByteArrayInputStream(value);
    }
    private synchronized Group group(int id) throws IOException {
        if(closed)throw new IOException("共享资源已经关闭");
        Group found=cache.get(id);if(found!=null){hits++;return found;}
        misses++;Chunk chunk=chunks.get(id);byte[] packed=read(source,chunk.path,maxGroup+65536);
        if(!sha(packed).equals(chunk.sha256))throw new IOException("共享资源分组摘要不符："+chunk.path);
        byte[] raw;
        try(var gzip=new GZIPInputStream(new ByteArrayInputStream(packed))){
            raw=gzip.readNBytes(chunk.bytes+1);if(raw.length!=chunk.bytes)throw new IOException("共享资源分组解压大小错误："+chunk.path);
        }
        if(chunk.codec.equals("bytes")){
            List<byte[]> values=new ArrayList<>();
            try(var in=new DataInputStream(new ByteArrayInputStream(raw))){
                if(in.readInt()!=0x4d565242||in.readUnsignedByte()!=1||in.readInt()!=chunk.count)throw new IOException("共享资源二进制头错误");
                for(int i=0;i<chunk.count;i++){
                    int bytes=in.readInt();if(bytes<0||bytes>maxFile||bytes>in.available())throw new IOException("共享资源二进制长度越界");
                    values.add(in.readNBytes(bytes));
                }
                if(in.read()!=-1)throw new IOException("共享资源二进制存在尾部数据");
            }
            found=new Group(raw.length,null,List.copyOf(values));
        }else{
            JsonArray json;
            try{json=JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonArray();}
            catch(RuntimeException error){throw new IOException("共享资源JSON格式错误："+chunk.path,error);}
            if(json.size()!=chunk.count)throw new IOException("共享资源JSON记录数量错误");
            found=new Group(raw.length,json,null);
        }
        if(found.bytes<=cacheBudget){
            cache.put(id,found);cachedBytes+=found.bytes;
            while(cachedBytes>cacheBudget||cache.size()>cacheGroups){
                var key=cache.keySet().iterator().next();cachedBytes-=cache.remove(key).bytes;
            }
        }
        return found;
    }
    public synchronized Map<String,Long> stats(){return Map.of("cached_bytes",(long)cachedBytes,"cached_groups",(long)cache.size(),"hits",hits,"misses",misses);}
    @Override public synchronized void close(){closed=true;cache.clear();cachedBytes=0;}
    private void requireRecord(int group,int index,String codec) throws IOException {
        if(group<0||group>=chunks.size()||index<0||index>=chunks.get(group).count||!chunks.get(group).codec.equals(codec))
            throw new IOException("共享资源分组索引越界");
    }
    public static boolean validPath(String path){
        if(path==null||path.isEmpty()||!path.matches("[a-z0-9_./-]+"))return false;
        return Arrays.stream(path.split("/",-1)).noneMatch(p->p.isEmpty()||p.equals(".")||p.equals(".."));
    }
    public static String sha(byte[] value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static boolean validHash(String value){return value.matches("[0-9a-f]{64}");}
    private static byte[] read(Source source,String path,int limit) throws IOException {
        try(InputStream in=source.open(path)){
            if(in==null)throw new FileNotFoundException(path);
            byte[] bytes=in.readNBytes(limit+1);if(bytes.length>limit)throw new IOException("共享资源文件超过大小限制："+path);return bytes;
        }
    }
    private static JsonObject parse(byte[] value) throws IOException {
        try{return JsonParser.parseString(new String(value,StandardCharsets.UTF_8)).getAsJsonObject();}
        catch(RuntimeException error){throw new IOException("共享资源JSON清单错误",error);}
    }
    private static JsonArray array(JsonElement row,int size) throws IOException {
        if(!row.isJsonArray()||row.getAsJsonArray().size()!=size)throw new IOException("共享资源记录长度错误");return row.getAsJsonArray();
    }
    private static int number(JsonElement value) throws IOException {
        try{return value.getAsBigDecimal().intValueExact();}catch(RuntimeException error){throw new IOException("共享资源整数错误",error);}
    }
    private static int integer(JsonObject value,String key) throws IOException{return number(value.get(key));}
    private static int positive(JsonObject value,String key) throws IOException {
        int result=integer(value,key);if(result<1||result>Integer.MAX_VALUE-65536)throw new IOException("共享资源限制越界："+key);return result;
    }
}
