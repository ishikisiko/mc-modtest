package com.example.myvillage.story.realm;

import com.example.myvillage.mixin.RealmServerAccess;
import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.story.StoryAttachments;
import com.example.myvillage.story.StoryProgress;
import com.example.myvillage.story.StoryRuntime;
import com.example.myvillage.story.scene.QianzhenScene;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.LoggerFactory;

/** Owner-only, persistent ServerLevels. No reset, shared story cast or client-provided destination. */
public final class QianzhenRealms {
    private static RealmRules rules;
    private static final Map<UUID,QianzhenScene.PersonalBuild> BUILDING=new LinkedHashMap<>();
    private static final Set<UUID> WAITING=new HashSet<>();
    private static final ChunkProgressListener NO_PROGRESS=new ChunkProgressListener(){
        public void updateSpawnPos(ChunkPos p){} public void onStatusChange(ChunkPos p,ChunkStatus s){}
        public void start(){} public void stop(){}
    };
    private QianzhenRealms(){}
    public static void register(){
        NeoForge.EVENT_BUS.addListener(QianzhenRealms::started);
        NeoForge.EVENT_BUS.addListener(QianzhenRealms::stopping);
        NeoForge.EVENT_BUS.addListener(QianzhenRealms::tick);
        NeoForge.EVENT_BUS.addListener(QianzhenRealms::commands);
        NeoForge.EVENT_BUS.addListener(QianzhenRealms::travel);
        NeoForge.EVENT_BUS.addListener(QianzhenRealms::login);
        NeoForge.EVENT_BUS.addListener(QianzhenRealms::logout);
    }
    private static void started(ServerStartedEvent event){
        BUILDING.clear();WAITING.clear();rules=RealmRules.load(event.getServer());
        // Restore dimension keys before player data is loaded. Never replay scene templates here.
        for(UUID owner:RealmSaves.get(event.getServer()).entries().keySet())createLevel(event.getServer(),owner);
    }
    private static void stopping(ServerStoppingEvent event){
        for(var p:event.getServer().getPlayerList().getPlayers())rememberInside(p);
        BUILDING.clear();WAITING.clear();rules=null;
    }
    public static boolean privateLevel(ServerLevel level){return RealmIdentity.owner(level.dimension().location().toString()).isPresent();}
    public static String castId(ServerLevel level,String legacy){
        return RealmIdentity.owner(level.dimension().location().toString()).map(RealmIdentity::cast).orElse(legacy);
    }
    public static Optional<String> region(ServerLevel level){
        return RealmIdentity.owner(level.dimension().location().toString()).map(id->RealmSaves.get(level.getServer()).get(id)).map(RealmSaves.Entry::region);
    }
    private static ResourceKey<Level> key(UUID owner){return ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(RealmIdentity.dimension(owner)));}
    public static Optional<ServerLevel> existing(ServerPlayer p){return Optional.ofNullable(p.getServer().getLevel(key(p.getUUID())));}
    public static boolean allowed(ServerPlayer p,ServerLevel destination){
        String id=destination.dimension().location().toString();
        if(!RealmIdentity.reserved(id))return true;
        return RealmIdentity.permits(p.getUUID(),id)&&RealmSaves.get(p.getServer()).get(p.getUUID())!=null
                &&QianzhenScene.at(destination).isPresent();
    }
    public static boolean ownsCurrent(ServerPlayer p){return allowed(p,p.serverLevel());}
    public static ServerLevel loginLevel(MinecraftServer server,ResourceKey<Level> requested,ServerPlayer p){
        ServerLevel destination=server.getLevel(requested);
        if(!RealmIdentity.reserved(requested.location().toString()))return destination;
        if(destination!=null&&allowed(p,destination))return destination;
        var at=outside(p);ServerLevel safe=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(at.dimension())));
        p.setPos(at.x(),at.y(),at.z());p.setYRot(at.yaw());p.setXRot(at.pitch());return safe;
    }
    public static DimensionTransition respawn(ServerPlayer p,DimensionTransition original){
        if(privateLevel(p.serverLevel())&&allowed(p,p.serverLevel())){
            return personalRespawn(p,original.postDimensionTransition());
        }
        if(allowed(p,original.newLevel()))return original;
        var at=outside(p);var level=p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(at.dimension())));
        return new DimensionTransition(level,new Vec3(at.x(),at.y(),at.z()),Vec3.ZERO,at.yaw(),at.pitch(),original.postDimensionTransition());
    }
    public static DimensionTransition personalRespawn(ServerPlayer p,DimensionTransition.PostDimensionTransition post){
        BlockPos at=QianzhenScene.position(p.serverLevel(),"spawn").orElseThrow();
        if(safe(p.serverLevel(),at.getX()+.5,at.getY(),at.getZ()+.5))
            return new DimensionTransition(p.serverLevel(),Vec3.atBottomCenterOf(at),Vec3.ZERO,180,0,post);
        var target=outside(p);var level=p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(target.dimension())));
        return new DimensionTransition(level,new Vec3(target.x(),target.y(),target.z()),Vec3.ZERO,target.yaw(),target.pitch(),post);
    }
    private static ServerLevel createLevel(MinecraftServer server,UUID owner){
        var key=key(owner);var old=server.getLevel(key);if(old!=null)return old;
        var template=server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(ResourceLocation.parse(RealmIdentity.TEMPLATE));
        if(template==null||!(template.generator() instanceof FlatLevelSource flat))throw new IllegalStateException("阡镇秘境模板缺失或生成器不符");
        var stem=new LevelStem(template.type(),new FlatLevelSource(flat.settings()));
        var level=new ServerLevel(server,Util.backgroundExecutor(),((RealmServerAccess)server).myvillage$storage(),
                new DerivedLevelData(server.getWorldData(),server.getWorldData().overworldData()),key,stem,NO_PROGRESS,false,
                server.overworld().getSeed(),List.of(),false,null);
        var bounds=QianzhenScene.layout(level).bounds();
        var origin=QianzhenScene.at(level).map(QianzhenScene.Placement::origin).orElse(rules.origin());
        double x=origin.getX()+(bounds.min().x()+bounds.max().x()+1)/2.0;
        double z=origin.getZ()+(bounds.min().z()+bounds.max().z()+1)/2.0;
        level.getWorldBorder().setCenter(x,z);
        level.getWorldBorder().setSize(Math.max(bounds.max().x()-bounds.min().x()+1,bounds.max().z()-bounds.min().z()+1)+2*rules.borderPadding());
        level.setSpawnSettings(false,false);
        server.forgeGetWorldMap().put(key,level);server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));return level;
    }
    private static RealmSaves.Location location(ServerPlayer p){return new RealmSaves.Location(p.level().dimension().location().toString(),p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());}
    public static boolean enter(ServerPlayer p){
        if(!p.isAlive()||p.isRemoved()||p.isPassenger()||p.isSleeping()){say(p,"请先结束当前动作，再进入秘境。");return false;}
        if(privateLevel(p.serverLevel())){say(p,"你已在个人秘境中；用 /qianzhen leave 返回。");return false;}
        if(RealmIdentity.reserved(p.level().dimension().location().toString()))return false;
        var progress=p.getExistingData(StoryAttachments.PROGRESS).orElse(StoryProgress.EMPTY);
        if(progress.accepted()&&!progress.dimension().equals(RealmIdentity.dimension(p.getUUID()))){
            say(p,"你已有旧场景的第一章记录，已保留原进度；不能自动重置为个人秘境。");return false;
        }
        var server=p.getServer();var saves=RealmSaves.get(server);var entry=saves.get(p.getUUID());
        if(entry==null){
            if(Files.exists(DimensionType.getStorageFolder(key(p.getUUID()),server.getWorldPath(LevelResource.ROOT))))
                throw new IllegalStateException("秘境目录已存在但归属记录缺失；请恢复匹配备份，不会覆盖原目录。");
            var region=RegionRuntimeService.spawnSelection().map(s->s.regionId())
                    .filter(id->WorldSimRuntime.sim().filter(sim->sim.hasRegion(id)).isPresent()).orElseThrow(()->new IllegalStateException("命簿尚未就绪"));
            entry=new RealmSaves.Entry(p.getUUID(),region,location(p),null);saves.put(entry);saves.checkpoint(server);
        }
        var level=createLevel(server,p.getUUID());
        if(QianzhenScene.at(level).isPresent())return transferIn(p,level);
        if(entry.inside()!=null&&QianzhenScene.phase(level).equals("EMPTY"))
            throw new IllegalStateException("已使用秘境的场景记录缺失；请恢复匹配备份，不会重新生成或清空进度。");
        if(!BUILDING.containsKey(p.getUUID())){
            BUILDING.put(p.getUUID(),QianzhenScene.beginPersonal(level,rules.origin(),rules.roadsPerTick()));
        }
        WAITING.add(p.getUUID());say(p,"正在准备你的阡镇秘境，完成后会自动进入；可用 /qianzhen leave 取消等待。");return true;
    }
    private static boolean transferIn(ServerPlayer p,ServerLevel level){
        if(!p.isAlive()||p.isRemoved()||p.isPassenger()||RealmIdentity.reserved(p.level().dimension().location().toString()))return false;
        var saves=RealmSaves.get(p.getServer());var entry=saves.get(p.getUUID());
        var at=entry.inside();
        if(at==null||!safe(level,at.x(),at.y(),at.z())){
            var spawn=QianzhenScene.position(level,"spawn").orElseThrow();
            at=new RealmSaves.Location(level.dimension().location().toString(),spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5,180,0);
        }
        if(!safe(level,at.x(),at.y(),at.z()))throw new IllegalStateException("秘境入口被阻挡，未传送也未改动方块。");
        // The return location must reach disk before the player can leave this dimension.
        saves.put(new RealmSaves.Entry(entry.owner(),entry.region(),location(p),at));saves.checkpoint(p.getServer());
        boolean moved=p.teleportTo(level,at.x(),at.y(),at.z(),Set.of(),at.yaw(),at.pitch());
        if(moved){p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;say(p,"已进入个人秘境·阡镇。退出：/qianzhen leave；再次进入会继续当前进度。");StoryRuntime.sync(p);}
        return moved;
    }
    public static boolean leave(ServerPlayer p){
        if(WAITING.remove(p.getUUID())){say(p,"已取消进入；秘境准备完成后仍会保留。");return true;}
        if(!privateLevel(p.serverLevel())){say(p,"你当前不在个人秘境中。");return false;}
        rememberInside(p);var at=outside(p);var level=p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(at.dimension())));
        boolean moved=p.teleportTo(level,at.x(),at.y(),at.z(),Set.of(),at.yaw(),at.pitch());
        if(moved){p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;StoryRuntime.sync(p);say(p,"已离开阡镇秘境，进度已保留。");}return moved;
    }
    private static void rememberInside(ServerPlayer p){
        if(!privateLevel(p.serverLevel())||!RealmIdentity.permits(p.getUUID(),p.level().dimension().location().toString()))return;
        var saves=RealmSaves.get(p.getServer());var e=saves.get(p.getUUID());
        if(e!=null){saves.put(new RealmSaves.Entry(e.owner(),e.region(),e.outside(),location(p)));saves.checkpoint(p.getServer());}
    }
    private static boolean safe(ServerLevel level,double x,double y,double z){
        var p=BlockPos.containing(x,y,z);
        var body=new net.minecraft.world.phys.AABB(x-.3,y,z-.3,x+.3,y+1.8,z+.3);
        if(!level.getWorldBorder().isWithinBounds(body)||p.getY()<=level.getMinBuildHeight()||p.getY()+2>=level.getMaxBuildHeight())return false;
        for(var at:BlockPos.betweenClosed(p.below(),p.above())){
            var state=level.getBlockState(at);
            if(state.is(net.minecraft.tags.BlockTags.FIRE)||state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                    ||state.is(net.minecraft.world.level.block.Blocks.CACTUS)||state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                    ||state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)||state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW))return false;
        }
        return level.noCollision(body)&&!level.containsAnyLiquid(body)
                &&level.getBlockState(p.below()).isFaceSturdy(level,p.below(),net.minecraft.core.Direction.UP);
    }
    private static RealmSaves.Location outside(ServerPlayer p){
        var entry=RealmSaves.get(p.getServer()).get(p.getUUID());var desired=entry==null?null:entry.outside();
        var level=desired==null?null:p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(desired.dimension())));
        if(level!=null&&!RealmIdentity.reserved(level.dimension().location().toString())&&safe(level,desired.x(),desired.y(),desired.z()))return desired;
        if(level==null||RealmIdentity.reserved(level.dimension().location().toString()))level=p.getServer().overworld();
        BlockPos center=desired!=null&&desired.dimension().equals(level.dimension().location().toString())
                ?BlockPos.containing(desired.x(),desired.y(),desired.z()):level.getSharedSpawnPos();
        var nearby=findSafe(level,center);if(nearby.isPresent())return nearby.get();
        var fallback=p.getServer().overworld();
        return findSafe(fallback,fallback.getSharedSpawnPos())
                .orElseThrow(()->new IllegalStateException("返回点和世界出生点附近都没有安全落脚处；未覆盖方块或强制传送。"));
    }
    private static Optional<RealmSaves.Location> findSafe(ServerLevel level,BlockPos center){
        int radius=Objects.requireNonNull(rules,"秘境尚未就绪").returnRadius();
        for(int r=0;r<=radius;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
            int x=center.getX()+dx,z=center.getZ()+dz,y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            if(safe(level,x+.5,y,z+.5))return Optional.of(new RealmSaves.Location(level.dimension().location().toString(),x+.5,y,z+.5,0,0));
        }
        return Optional.empty();
    }
    private static void tick(ServerTickEvent.Post event){
        // One realm stage per tick globally, not one heavy stage per waiting player.
        var it=BUILDING.entrySet().iterator();
        if(it.hasNext()){
            var job=it.next();boolean finished=false;
            try{finished=job.getValue().step();}catch(RuntimeException failure){
                finished=true;WAITING.remove(job.getKey());LoggerFactory.getLogger(QianzhenRealms.class).error("Personal realm build failed for {}",job.getKey(),failure);
                var p=event.getServer().getPlayerList().getPlayer(job.getKey());if(p!=null)say(p,failure.getMessage());
            }
            if(finished){UUID owner=job.getKey();it.remove();if(WAITING.remove(owner)){
                var p=event.getServer().getPlayerList().getPlayer(owner);
                if(p!=null)try{transferIn(p,event.getServer().getLevel(key(owner)));}catch(RuntimeException failure){say(p,"秘境已保留，进入失败："+failure.getMessage());}
            }}
        }
    }
    private static void travel(EntityTravelToDimensionEvent event){
        if(!(event.getEntity().level() instanceof ServerLevel source))return;
        String to=event.getDimension().location().toString(),from=source.dimension().location().toString();
        if(!RealmIdentity.reserved(to)&&!RealmIdentity.reserved(from))return;
        if(event.getEntity() instanceof ServerPlayer p){
            var target=p.getServer().getLevel(event.getDimension());if(target==null||!allowed(p,target))event.setCanceled(true);
        }else event.setCanceled(true); // Pets, vehicles and NPCs cannot carry a second actor across the boundary.
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer p&&!privateLevel(p.serverLevel())) {
            var progress=p.getExistingData(StoryAttachments.PROGRESS).orElse(StoryProgress.EMPTY);
            boolean legacy=progress.accepted()&&!progress.dimension().equals(RealmIdentity.dimension(p.getUUID()));
            String command=legacy?"/qianzhen where":"/qianzhen enter";
            String text=legacy?"旧版阡镇剧情进度已保留。点击查看原场景位置。":"第一章·阡镇：点击进入个人秘境（也可输入 /qianzhen enter）";
            p.displayClientMessage(Component.literal(text).withStyle(s->s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,command))),false);
        }
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        WAITING.remove(event.getEntity().getUUID());if(event.getEntity() instanceof ServerPlayer p)rememberInside(p);
    }
    private static void say(ServerPlayer p,String text){p.displayClientMessage(Component.literal(text),false);}
    private static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("qianzhen")
                .then(Commands.literal("enter").executes(c->command(c.getSource().getPlayerOrException(),true)))
                .then(Commands.literal("leave").executes(c->command(c.getSource().getPlayerOrException(),false))));
        event.getDispatcher().register(Commands.literal("myvillage").then(Commands.literal("qianzhen").requires(s->s.hasPermission(2))
                .then(Commands.literal("realm_status").then(Commands.argument("player",EntityArgument.player()).executes(c->{
                    var player=EntityArgument.getPlayer(c,"player");
                    c.getSource().sendSuccess(()->Component.literal("QIANZHEN_REALM_STATUS "+readback(player)),false);return 1;
                })))));
    }
    private static int command(ServerPlayer p,boolean enter){
        try{return (enter?enter(p):leave(p))?1:0;}catch(RuntimeException failure){say(p,"秘境操作未完成："+failure.getMessage());return 0;}
    }
    public static String readback(ServerPlayer p){
        var o=new com.google.gson.JsonObject();o.addProperty("owner",p.getUUID().toString());o.addProperty("dimension",RealmIdentity.dimension(p.getUUID()));
        var e=RealmSaves.get(p.getServer()).get(p.getUUID());var level=existing(p).orElse(null);
        o.addProperty("exists",e!=null);o.addProperty("phase",level==null?"ABSENT":QianzhenScene.phase(level));
        o.addProperty("current_dimension",p.level().dimension().location().toString());o.addProperty("authorized",ownsCurrent(p));
        o.addProperty("camera_self",p.getCamera()==p);
        o.addProperty("building",BUILDING.containsKey(p.getUUID()));o.addProperty("waiting",WAITING.contains(p.getUUID()));
        if(e!=null){o.addProperty("region",e.region());o.add("outside",new com.google.gson.Gson().toJsonTree(e.outside()));o.add("inside",new com.google.gson.Gson().toJsonTree(e.inside()));}
        return o.toString();
    }
}
