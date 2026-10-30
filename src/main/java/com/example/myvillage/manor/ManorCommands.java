package com.example.myvillage.manor;

import com.example.myvillage.MyVillageMod;
import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 只在独立空白样板维度施工，完全不读取城市选址或生成计划。 */
public final class ManorCommands {
    public static final ResourceKey<Level> GALLERY=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(ManorCatalog.get().gallery.dimension()));
    private static final Map<MinecraftServer,State> STATES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String,JsonObject> SPECS=new HashMap<>();
    private static final class State {
        final Map<String,Job> jobs=new LinkedHashMap<>();
        final Map<UUID,ReturnPoint> returns=new HashMap<>();
    }
    private record ReturnPoint(ResourceKey<Level> dimension,Vec3 position,float yaw,float pitch) { }
    private static final class Job {
        final String city;final ManorCatalog.Manor manor;final JsonObject spec;final BlockPos origin;
        final Set<UUID> visitors=new LinkedHashSet<>();
        int nextTile,index,placed;ListTag cells;List<BlockState> palette;BlockPos tileOrigin;
        String phase="building",error="";
        Job(String city,ManorCatalog.Manor manor,JsonObject spec,BlockPos origin){this.city=city;this.manor=manor;this.spec=spec;this.origin=origin;}
    }
    private static State state(MinecraftServer server){return STATES.computeIfAbsent(server,k->new State());}
    private static JsonObject spec(String city) {
        return SPECS.computeIfAbsent(city,id->{
            String name="/data/myvillage/lord_manors/"+id+".json";
            try(var stream=ManorCommands.class.getResourceAsStream(name)) {
                if(stream==null)throw new IllegalStateException("缺少府邸结构目录："+id);
                return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            }catch(IOException e){throw new IllegalStateException(e);}
        });
    }
    private static BlockPos origin(String city) {
        var catalog=ManorCatalog.get();int i=0;while(!catalog.manors.get(i).city().equals(city))i++;
        var g=catalog.gallery;return new BlockPos(i%g.columns()*g.spacing(),g.floorY(),i/g.columns()*g.spacing());
    }
    private static BlockPos marker(BlockPos origin){return origin.offset(-2,-1,-2);}
    public static void registerRuntime() {
        NeoForge.EVENT_BUS.addListener(ManorCommands::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event)->STATES.remove(event.getServer()));
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(MyVillageMod.MOD_ID).requires(s->s.hasPermission(2))
            .then(Commands.literal("manor")
                .then(Commands.literal("list").executes(c->list(c.getSource())))
                .then(Commands.literal("visit").then(Commands.argument("city",StringArgumentType.word())
                    .suggests((c,b)->SharedSuggestionProvider.suggest(ManorCatalog.get().manors.stream().map(ManorCatalog.Manor::city),b))
                    .executes(c->visit(c.getSource(),StringArgumentType.getString(c,"city")))))
                .then(Commands.literal("status").executes(c->status(c.getSource())))
                .then(Commands.literal("leave").executes(c->leave(c.getSource())))));
    }
    private static int list(CommandSourceStack source) {
        for(var m:ManorCatalog.get().manors)source.sendSuccess(()->Component.literal(m.city()+" — "+m.name()+"（"+m.width()+"×"+m.height()+"×"+m.depth()+"）"),false);
        source.sendSuccess(()->Component.literal("创造模式使用 /myvillage manor visit <city>，仅进入独立样板，不接入城市。"),false);return 7;
    }
    private static int visit(CommandSourceStack source,String city) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=source.getPlayerOrException();
        if(!player.isCreative()&&!player.isSpectator()){source.sendFailure(Component.literal("请先进入创造模式，再飞行查看府邸样板。"));return 0;}
        ManorCatalog.Manor manor;
        try{manor=ManorCatalog.get().manor(city);}catch(IllegalArgumentException e){source.sendFailure(Component.literal(e.getMessage()));return 0;}
        ServerLevel level=source.getServer().getLevel(GALLERY);
        if(level==null){source.sendFailure(Component.literal("七府样板维度未加载，请确认安装当前版本并重启世界。"));return 0;}
        var s=state(source.getServer());
        if(!player.level().dimension().equals(GALLERY))s.returns.put(player.getUUID(),new ReturnPoint(player.level().dimension(),player.position(),player.getYRot(),player.getXRot()));
        BlockPos origin=origin(city);var job=s.jobs.get(city);
        if(job!=null&&job.phase.equals("building")){job.visitors.add(player.getUUID());source.sendSuccess(()->Component.literal("正在施工："+manor.name()),false);return 1;}
        if(level.getBlockState(marker(origin)).is(ManorContent.MATERIALS.get("manor_survey_stone").get())) {
            if(job==null){job=new Job(city,manor,spec(city),origin);job.phase="ready";job.placed=manor.blocks();s.jobs.put(city,job);}
            teleport(player,job);return 1;
        }
        if(!level.isInWorldBounds(origin)||!level.isInWorldBounds(origin.offset(manor.width()-1,manor.height()-1,manor.depth()-1))
                ||!level.getWorldBorder().isWithinBounds(origin)||!level.getWorldBorder().isWithinBounds(origin.offset(manor.width()-1,0,manor.depth()-1))) {
            source.sendFailure(Component.literal("府邸样板超出维度边界。"));return 0;
        }
        job=new Job(city,manor,spec(city),origin);job.visitors.add(player.getUUID());s.jobs.put(city,job);
        source.sendSuccess(()->Component.literal("开始构造 "+manor.name()+"；完成后自动进入，仅在独立空白样板维度写入。"),false);return 1;
    }
    private static void teleport(ServerPlayer player,Job job) {
        ServerLevel level=player.getServer().getLevel(GALLERY);if(level==null)return;
        var camera=job.spec.getAsJsonObject("cameras").getAsJsonObject("overview");var e=camera.getAsJsonArray("eye");var t=camera.getAsJsonArray("target");
        double[] eye=new double[3],target=new double[3];int[] min=job.manor.sourceMin();double scale=ManorCatalog.get().gallery.cameraScale();
        for(int i=0;i<3;i++){target[i]=t.get(i).getAsDouble()-min[i];eye[i]=target[i]+(e.get(i).getAsDouble()-t.get(i).getAsDouble())*scale;}
        double dx=target[0]-eye[0],dy=target[1]-eye[1],dz=target[2]-eye[2];
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        player.teleportTo(level,job.origin.getX()+eye[0],job.origin.getY()+eye[1]-player.getEyeHeight(),job.origin.getZ()+eye[2],Set.of(),yaw,pitch);
        player.getAbilities().flying=true;player.onUpdateAbilities();player.fallDistance=0;
        player.sendSystemMessage(Component.literal(job.manor.name()+" · 独立样板。可自由飞行查看，/myvillage manor leave 离开。"));
    }
    private static int leave(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=source.getPlayerOrException();var point=state(source.getServer()).returns.remove(player.getUUID());
        ServerLevel target=point==null?source.getServer().overworld():source.getServer().getLevel(point.dimension());
        if(target==null)target=source.getServer().overworld();
        Vec3 position;
        if(point!=null&&target.dimension().equals(point.dimension()))position=point.position();
        else{var p=target.getSharedSpawnPos();position=new Vec3(p.getX()+.5,target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ())+1,p.getZ()+.5);}
        player.teleportTo(target,position.x,position.y,position.z,Set.of(),point==null?0:point.yaw(),point==null?0:point.pitch());return 1;
    }
    private static int status(CommandSourceStack source) {
        JsonObject result=new JsonObject();JsonArray jobs=new JsonArray();
        for(var job:state(source.getServer()).jobs.values()) {
            var row=new JsonObject();row.addProperty("city",job.city);row.addProperty("phase",job.phase);row.addProperty("placed",job.placed);
            row.addProperty("total",job.manor.blocks());row.addProperty("tiles",job.nextTile);row.addProperty("total_tiles",job.spec.getAsJsonArray("tiles").size());row.addProperty("error",job.error);jobs.add(row);
        }
        result.add("jobs",jobs);source.sendSuccess(()->Component.literal("MANOR_STATUS "+result),false);return jobs.size();
    }
    private static void loadTile(ServerLevel level,Job job) throws Exception {
        var entry=job.spec.getAsJsonArray("tiles").get(job.nextTile++).getAsJsonObject();ResourceLocation template=ResourceLocation.parse(entry.get("template").getAsString());
        ResourceLocation file=ResourceLocation.fromNamespaceAndPath(template.getNamespace(),"structure/"+template.getPath()+".nbt");
        byte[] bytes;try(var input=level.getServer().getResourceManager().getResource(file).orElseThrow().open()){bytes=input.readNBytes(8*1024*1024+1);}
        if(bytes.length>8*1024*1024||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(entry.get("sha256").getAsString()))throw new IllegalStateException("七府结构切片摘要不符："+file);
        CompoundTag root=NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtAccounter.create(64L*1024*1024));
        var size=root.getList("size",Tag.TAG_INT);if(size.size()!=3||size.getInt(0)!=16||size.getInt(1)!=job.manor.height()||size.getInt(2)!=16||!root.getList("entities",Tag.TAG_COMPOUND).isEmpty())throw new IllegalStateException("七府切片尺寸错误");
        var tags=root.getList("palette",Tag.TAG_COMPOUND);var cells=root.getList("blocks",Tag.TAG_COMPOUND);
        if(tags.isEmpty()||tags.size()>65536||cells.size()>16*job.manor.height()*16)throw new IllegalStateException("七府切片体积错误");
        List<BlockState> palette=new ArrayList<>();
        for(int i=0;i<tags.size();i++) {
            var tag=tags.getCompound(i);var id=ResourceLocation.parse(tag.getString("Name"));
            if(!BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalStateException("七府方块未注册："+id);
            palette.add(NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),tag));
        }
        var offset=entry.getAsJsonArray("offset");job.tileOrigin=job.origin.offset(offset.get(0).getAsInt(),offset.get(1).getAsInt(),offset.get(2).getAsInt());
        level.getChunkAt(job.tileOrigin);job.cells=cells;job.palette=palette;job.index=0;
    }
    private static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=STATES.get(server);if(s==null)return;ServerLevel level=server.getLevel(GALLERY);if(level==null)return;
        int budget=ManorCatalog.get().gallery.blocksPerTick();
        for(var job:s.jobs.values()) {
            if(!job.phase.equals("building"))continue;
            try {
                while(budget>0) {
                    if(job.cells==null||job.index>=job.cells.size()) {
                        if(job.nextTile>=job.spec.getAsJsonArray("tiles").size()) {
                            if(job.placed!=job.manor.blocks())throw new IllegalStateException("七府切片总量与目录不符");
                            level.setBlock(marker(job.origin),ManorContent.MATERIALS.get("manor_survey_stone").get().defaultBlockState(),Block.UPDATE_CLIENTS);
                            job.phase="ready";job.cells=null;job.palette=null;
                            for(UUID visitor:job.visitors){var player=server.getPlayerList().getPlayer(visitor);if(player!=null)teleport(player,job);}job.visitors.clear();break;
                        }
                        loadTile(level,job);
                    }
                    var cell=job.cells.getCompound(job.index++);var p=cell.getList("pos",Tag.TAG_INT);int index=cell.getInt("state");
                    if(p.size()!=3||p.getInt(0)<0||p.getInt(0)>15||p.getInt(1)<0||p.getInt(1)>=job.manor.height()||p.getInt(2)<0||p.getInt(2)>15||index<0||index>=job.palette.size())throw new IllegalStateException("七府切片坐标或状态越界");
                    level.setBlock(job.tileOrigin.offset(p.getInt(0),p.getInt(1),p.getInt(2)),job.palette.get(index),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
                    budget--;job.placed++;
                }
            }catch(Exception e) {
                job.phase="failed";job.error=e.toString();job.cells=null;job.palette=null;
                for(UUID visitor:job.visitors){var player=server.getPlayerList().getPlayer(visitor);if(player!=null)player.sendSystemMessage(Component.literal("府邸构造失败："+job.error));}job.visitors.clear();
                System.err.println("MANOR_BUILD_FAILED "+job.city+" "+e);e.printStackTrace();
            }
            if(budget<=0)break;
        }
    }
    private ManorCommands(){ }
}
