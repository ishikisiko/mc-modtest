package myvillage.tools;

import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.fml.loading.LoadingModList;

/** 独立数据生成工具；不进入模组 JAR，不启动服务器，也不访问玩家世界。 */
public final class NativeTreeCapture {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private static BlockPos position(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static JsonArray position(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
    private static ResourceLocation id(String s){return ResourceLocation.parse(s);}
    private static String hash(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    private static <T extends Comparable<T>> String property(Property<T> p,BlockState state){return p.getName(state.getValue(p));}
    private static String state(BlockState state){
        String name=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();Map<String,String> properties=new TreeMap<>();
        for(Property<?> p:state.getProperties())properties.put(p.getName(),property(p,state));
        return properties.isEmpty()?name:name+"["+String.join(",",properties.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList())+"]";
    }

    private static void bindVanillaTags(ZipFile jar){
        Map<String,JsonArray> source=new TreeMap<>();
        jar.stream().filter(e->e.getName().startsWith("data/minecraft/tags/block/")&&e.getName().endsWith(".json")).forEach(e->{
            try(var in=jar.getInputStream(e)){
                String name="minecraft:"+e.getName().substring("data/minecraft/tags/block/".length(),e.getName().length()-5);
                source.put(name,JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values"));
            }catch(Exception error){throw new IllegalStateException(e.getName(),error);}
        });
        Map<String,List<Holder<Block>>> resolved=new HashMap<>();Set<String> active=new HashSet<>();
        class Resolve {
            List<Holder<Block>> get(String name){
                if(resolved.containsKey(name))return resolved.get(name);
                if(!active.add(name))throw new IllegalStateException("原版方块标签循环："+name);
                JsonArray entries=Objects.requireNonNull(source.get(name),name);Set<Holder<Block>> result=new LinkedHashSet<>();
                for(JsonElement entry:entries){
                    boolean required=!entry.isJsonObject()||!entry.getAsJsonObject().has("required")||entry.getAsJsonObject().get("required").getAsBoolean();
                    String value=entry.isJsonObject()?entry.getAsJsonObject().get("id").getAsString():entry.getAsString();
                    if(value.startsWith("#")){
                        if(source.containsKey(value.substring(1)))result.addAll(get(value.substring(1)));
                        else if(required)throw new IllegalStateException("缺少原版标签："+value);
                    }else{
                        var holder=BuiltInRegistries.BLOCK.getHolder(id(value));
                        if(required&&holder.isEmpty())throw new IllegalStateException("缺少原版方块："+value);
                        holder.ifPresent(result::add);
                    }
                }
                active.remove(name);List<Holder<Block>> list=List.copyOf(result);resolved.put(name,list);return list;
            }
        }
        Resolve resolver=new Resolve();Map<TagKey<Block>,List<Holder<Block>>> tags=new HashMap<>();
        for(String name:source.keySet())tags.put(TagKey.create(Registries.BLOCK,id(name)),resolver.get(name));
        BuiltInRegistries.BLOCK.bindTags(tags);
    }

    /** 只给原版 Feature 提供有界的真实方块状态；建筑占位一律不可替换。 */
    private static final class World implements InvocationHandler {
        final Set<BlockPos> obstacles;final Map<BlockPos,BlockState> blocks;final Set<BlockPos> soil;
        final BlockPos low,high;final int minY,height;final WorldGenLevel level;boolean refused;
        World(Set<BlockPos> obstacles,Map<BlockPos,BlockState> previous,Set<BlockPos> soil,BlockPos low,BlockPos high,JsonObject capture){
            this.obstacles=obstacles;this.blocks=new HashMap<>(previous);this.soil=soil;this.low=low;this.high=high;
            minY=capture.get("min_build_y").getAsInt();height=capture.get("build_height").getAsInt();
            for(BlockPos p:soil)blocks.put(p,Blocks.DIRT.defaultBlockState());
            level=(WorldGenLevel)Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),new Class<?>[]{WorldGenLevel.class},this);
        }
        BlockState at(BlockPos p){return blocks.getOrDefault(p,obstacles.contains(p)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());}
        boolean inside(BlockPos p){return p.getX()>=low.getX()&&p.getY()>=low.getY()&&p.getZ()>=low.getZ()&&p.getX()<high.getX()&&p.getY()<high.getY()&&p.getZ()<high.getZ();}
        @SuppressWarnings("unchecked") public Object invoke(Object proxy,Method method,Object[] raw)throws Throwable{
            Object[] args=raw==null?new Object[0]:raw;
            switch(method.getName()){
                case "toString":return "七府原版树数据生成世界";
                case "hashCode":return System.identityHashCode(proxy);
                case "equals":return proxy==args[0];
                case "getBlockState":return at((BlockPos)args[0]);
                case "getFluidState":return at((BlockPos)args[0]).getFluidState();
                case "isEmptyBlock":return at((BlockPos)args[0]).isAir();
                case "isStateAtPosition":return ((Predicate<BlockState>)args[1]).test(at((BlockPos)args[0]));
                case "isFluidAtPosition":return ((Predicate<net.minecraft.world.level.material.FluidState>)args[1]).test(at((BlockPos)args[0]).getFluidState());
                case "getMinBuildHeight":return minY;
                case "getMaxBuildHeight":return minY+height;
                case "getHeight":if(args.length==0)return height;break;
                case "getSeed":return 0L;
                case "ensureCanWrite":return inside((BlockPos)args[0]);
                case "isClientSide":return false;
                case "scheduleTick":return null; // 保存原生状态快照；不执行真实世界的异步 tick。
                case "getBlockEntity":return args.length==2?Optional.empty():null;
                case "setBlock":{
                    BlockPos p=((BlockPos)args[0]).immutable();BlockState value=(BlockState)args[1];
                    if(value.equals(at(p)))return true;
                    if(!inside(p)||(obstacles.contains(p)&&!soil.contains(p))){refused=true;return false;}
                    blocks.put(p,value);return true;
                }
            }
            if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);
            throw new UnsupportedOperationException("原版树调用了尚未支持的世界接口："+method);
        }
    }

    private static void leafDistances(Map<BlockPos,BlockState> blocks){
        int maximum=Collections.max(LeavesBlock.DISTANCE.getPossibleValues());
        Map<BlockPos,Integer> distances=new HashMap<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();
        for(var entry:blocks.entrySet())if(entry.getValue().is(BlockTags.LOGS)){distances.put(entry.getKey(),0);queue.add(entry.getKey());}
        while(!queue.isEmpty()){
            BlockPos p=queue.remove();int distance=distances.get(p)+1;if(distance>=maximum)continue;
            for(Direction direction:Direction.values()){
                BlockPos next=p.relative(direction);BlockState state=blocks.get(next);
                if(state!=null&&state.getBlock() instanceof LeavesBlock&&distance<distances.getOrDefault(next,maximum)){
                    distances.put(next,distance);queue.add(next);
                }
            }
        }
        for(var entry:blocks.entrySet())if(entry.getValue().getBlock() instanceof LeavesBlock){
            if(entry.getValue().getValue(LeavesBlock.PERSISTENT))throw new IllegalStateException("不能把自然树叶冻结为永久装饰");
            entry.setValue(entry.getValue().setValue(LeavesBlock.DISTANCE,distances.getOrDefault(entry.getKey(),maximum)));
        }
    }

    public static void main(String[] args)throws Exception{
        LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        JsonObject input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        try(ZipFile vanilla=new ZipFile(args[2])){
            String version;
            try(var stream=vanilla.getInputStream(vanilla.getEntry("version.json"))){version=JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject().get("id").getAsString();}
            if(!version.equals(input.get("minecraft_version").getAsString()))throw new IllegalStateException("原版数据版本不符");
            bindVanillaTags(vanilla);var lookup=VanillaRegistries.createLookup();var features=lookup.lookupOrThrow(Registries.CONFIGURED_FEATURE);
            JsonObject output=new JsonObject();output.addProperty("schema",1);output.addProperty("minecraft_version",version);output.add("source_hashes",input.get("source_hashes"));output.add("engine_versions",input.get("engine_versions"));
            output.addProperty("vanilla_jar_sha256",hash(Files.readAllBytes(Path.of(args[2]))));
            output.addProperty("leaf_distance_max",Collections.max(LeavesBlock.DISTANCE.getPossibleValues()));
            JsonObject cities=new JsonObject();output.add("cities",cities);
            for(var cityEntry:input.getAsJsonObject("cities").entrySet()){
                JsonObject city=cityEntry.getValue().getAsJsonObject();Set<BlockPos> obstacles=new HashSet<>();
                for(var p:city.getAsJsonArray("obstacles"))obstacles.add(position(p.getAsJsonArray()));
                Map<BlockPos,BlockState> blocks=new HashMap<>();JsonArray records=new JsonArray();
                BlockPos low=position(city.getAsJsonArray("bounds").get(0).getAsJsonArray()),high=position(city.getAsJsonArray("bounds").get(1).getAsJsonArray());
                for(var treeElement:city.getAsJsonArray("trees")){
                    JsonObject tree=treeElement.getAsJsonObject(),species=input.getAsJsonObject("species").getAsJsonObject(tree.get("species").getAsString());
                    ResourceKey<ConfiguredFeature<?,?>> key=ResourceKey.create(Registries.CONFIGURED_FEATURE,id(species.get("feature").getAsString()));
                    var feature=features.getOrThrow(key).value();Block sapling=BuiltInRegistries.BLOCK.get(id(species.get("sapling").getAsString()));
                    if(!(sapling instanceof SaplingBlock)||!sapling.defaultBlockState().isRandomlyTicking())throw new IllegalStateException("须使用可自然生长的原版树苗");
                    String leafLoot="data/minecraft/loot_table/blocks/"+id(species.get("leaves").getAsString()).getPath()+".json";
                    try(var stream=vanilla.getInputStream(Objects.requireNonNull(vanilla.getEntry(leafLoot),leafLoot))){
                        if(!new String(stream.readAllBytes(),StandardCharsets.UTF_8).contains(species.get("sapling").getAsString()))throw new IllegalStateException("原版树叶没有对应树苗掉落");
                    }
                    boolean accepted=false;
                    search:for(var candidateElement:tree.getAsJsonArray("candidates")){
                        JsonObject candidate=candidateElement.getAsJsonObject();BlockPos root=position(candidate.getAsJsonArray("root"));Set<BlockPos> soil=new HashSet<>();
                        for(var p:candidate.getAsJsonArray("soil"))soil.add(position(p.getAsJsonArray()));
                        for(var seedElement:tree.getAsJsonArray("seeds")){
                            long seed=seedElement.getAsLong();World world=new World(obstacles,blocks,soil,low,high,input.getAsJsonObject("capture"));
                            if(!sapling.defaultBlockState().canSurvive(world.level,root))throw new IllegalStateException("原版树苗不能在树池存活："+tree.get("id"));
                            if(!feature.place(world.level,null,RandomSource.create(seed),root)||world.refused)continue;
                            long logs=world.blocks.values().stream().filter(s->s.is(BlockTags.LOGS)).count();
                            if(logs<=blocks.values().stream().filter(s->s.is(BlockTags.LOGS)).count())continue;
                            blocks=world.blocks;JsonObject record=new JsonObject();record.add("id",tree.get("id"));record.add("species",tree.get("species"));
                            record.add("root",position(root));record.add("soil",candidate.get("soil"));record.add("feature",species.get("feature"));record.add("sapling",species.get("sapling"));record.addProperty("seed",seed);
                            records.add(record);accepted=true;break search;
                        }
                    }
                    if(!accepted)throw new IllegalStateException("原版树没有净空或有效生长结果："+tree.get("id"));
                }
                leafDistances(blocks);JsonArray states=new JsonArray();
                Comparator<BlockPos> order=Comparator.comparingInt((BlockPos p)->p.getY()).thenComparingInt(p->p.getZ()).thenComparingInt(p->p.getX());
                for(var entry:blocks.entrySet().stream().sorted(Map.Entry.comparingByKey(order)).toList()){
                    if(!BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlock()).getNamespace().equals("minecraft"))throw new IllegalStateException("原版树结果混入新装饰方块");
                    JsonArray row=new JsonArray();row.add(position(entry.getKey()));row.add(state(entry.getValue()));states.add(row);
                }
                JsonObject value=new JsonObject();value.add("trees",records);value.add("blocks",states);cities.add(cityEntry.getKey(),value);
                System.out.println(cityEntry.getKey()+"：真实原版树 "+records.size()+" 棵，原生方块 "+states.size()+" 格");
            }
            output.addProperty("content_sha256",hash(JSON.toJson(cities).getBytes(StandardCharsets.UTF_8)));
            Path target=Path.of(args[1]);Files.createDirectories(target.getParent());Files.writeString(target,JSON.toJson(output)+"\n");
        }
    }
}
