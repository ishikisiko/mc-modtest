package com.example.myvillage.quest;

import com.example.myvillage.mixin.RealmServerAccess;
import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.ruins.BattleReceipts;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.economy.*;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.example.myvillage.wilderness.*;
import com.example.myvillage.entity.beast.BeastEntity;
import com.example.myvillage.sim.economy.TaskReward;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.LoggerFactory;

/** Server-owned temporary realms; all admission, spawning and settlement is checked here. */
public final class QuestRuntime {
    private static final String TAG="myvillage:quest_instance";
    private static final String MARKER="myvillage-expedition.txt";
    private static final ResourceLocation HEALTH=ResourceLocation.parse("myvillage:expedition_health");
    private static final Map<UUID,ServerBossEvent> BARS=new HashMap<>();
    private static final Map<UUID,Invitation> INVITES=new HashMap<>();
    private static final Map<UUID,ReturnRequest> RETURNS=new HashMap<>();
    private static final Map<UUID,Integer> RESCUE=new HashMap<>();
    private static final Map<UUID,List<BlockState>> PALETTES=new HashMap<>();
    private static final Map<UUID,Set<ChunkPos>> TICKETS=new HashMap<>();
    private static final Set<UUID> WAITING=new HashSet<>();
    private static final TicketType<UUID> TICKET=TicketType.<UUID>create("myvillage_expedition",Comparator.naturalOrder());
    private record Invitation(UUID instance,long until){}
    private record ReturnRequest(long at,Vec3 origin,boolean abandon){}
    private static final ChunkProgressListener NO_PROGRESS=new ChunkProgressListener(){
        public void updateSpawnPos(ChunkPos p){}public void onStatusChange(ChunkPos p,ChunkStatus s){}public void start(){}public void stop(){}
    };
    private QuestRuntime(){}
    public static void register(){
        NeoForge.EVENT_BUS.addListener(QuestRuntime::started);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::stopping);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::tick);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::commands);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::joined);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::travel);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::loggedOut);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::loggedIn);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,QuestRuntime::death);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::drops);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::damage);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::breakBlock);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::placeBlock);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::explosion);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::interact);
        NeoForge.EVENT_BUS.addListener(QuestRuntime::sleep);
    }
    private static QuestRules rules(){return QuestRules.get();}
    private static QuestSaves saves(MinecraftServer server){return QuestSaves.get(server);}
    private static QuestState state(MinecraftServer server){return saves(server).state;}
    private static void save(MinecraftServer server){saves(server).checkpoint(server);}
    public static boolean reserved(String id){return QuestState.reserved(id);}
    public static boolean realm(ServerLevel level){return QuestState.identity(level.dimension().location().toString()).isPresent();}
    private static ResourceKey<Level> key(UUID id){return ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(QuestState.dimension(id)));}
    private static QuestState.Instance current(ServerPlayer p){var member=state(p.getServer()).members.get(p.getUUID());return member==null?null:state(p.getServer()).instances.get(member.instance);}
    private static QuestState.Member member(ServerPlayer p){return state(p.getServer()).members.get(p.getUUID());}
    private static BlockPos pos(QuestMap map,int[] relative){return new BlockPos(map.origin()[0]+relative[0],map.origin()[1]+relative[1],map.origin()[2]+relative[2]);}
    private static QuestState.Place location(ServerPlayer p){return new QuestState.Place(p.level().dimension().location().toString(),p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());}
    private static void say(ServerPlayer p,String text){p.displayClientMessage(Component.literal(text),false);}
    private static void button(ServerPlayer p,String label,String command){p.sendSystemMessage(Component.literal("["+label+"]").withStyle(s->s.withColor(0x83C9AE).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,command))));}
    private static void started(ServerStartedEvent e){
        BARS.clear();INVITES.clear();RETURNS.clear();RESCUE.clear();PALETTES.clear();TICKETS.clear();WAITING.clear();
        for(String id:rules().maps())QuestMap.get(id);
        for(var run:state(e.getServer()).instances.values()){
            if(run.state.equals("FORMING"))continue;
            if(run.state.equals("RECLAIMING"))continue;
            if(run.state.equals("BUILDING")||run.state.equals("LIGHTING"))run.state="FAILED";
            try{createLevel(e.getServer(),run,false);}catch(Exception failure){run.state="FAILED";LoggerFactory.getLogger(QuestRuntime.class).error("远征恢复失败，保留目录 {}",run.id,failure);}
        }
        save(e.getServer());
    }
    private static void stopping(ServerStoppingEvent e){
        for(var p:e.getServer().getPlayerList().getPlayers())remember(p);
        for(var bar:BARS.values())bar.removeAllPlayers();BARS.clear();
        save(e.getServer());PALETTES.clear();INVITES.clear();RETURNS.clear();RESCUE.clear();TICKETS.clear();
    }
    private static GameRules localRules(MinecraftServer server){
        var copy=server.getGameRules().copy();
        copy.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
        copy.getRule(GameRules.RULE_DOFIRETICK).set(false,null);
        copy.getRule(GameRules.RULE_MOBGRIEFING).set(false,null);return copy;
    }
    private static ServerLevel createLevel(MinecraftServer server,QuestState.Instance run,boolean fresh){
        var dimension=key(run.id);var old=server.getLevel(dimension);if(old!=null)return old;
        Path folder=DimensionType.getStorageFolder(dimension,server.getWorldPath(LevelResource.ROOT));
        try{
            if(fresh&&Files.exists(folder))throw new IllegalStateException("远征目录已存在，未覆盖");
            if(!fresh&&(!Files.isRegularFile(folder.resolve(MARKER))||!Files.readString(folder.resolve(MARKER)).equals(run.id+"\n"+run.hash)))throw new IllegalStateException("远征目录标记无效");
            var template=server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(ResourceLocation.parse("myvillage:expedition_template"));
            if(template==null||!(template.generator() instanceof FlatLevelSource flat))throw new IllegalStateException("远征模板缺失");
            var worldData=new DerivedLevelData(server.getWorldData(),server.getWorldData().overworldData()){
                private final GameRules ownRules=localRules(server);
                @Override public GameRules getGameRules(){return ownRules;}
            };
            var level=new ServerLevel(server,Util.backgroundExecutor(),((RealmServerAccess)server).myvillage$storage(),worldData,dimension,
                    new LevelStem(template.type(),new FlatLevelSource(flat.settings())),NO_PROGRESS,false,server.overworld().getSeed(),List.of(),false,null);
            var map=QuestMap.get(run.map);level.getWorldBorder().setCenter(map.origin()[0]+map.size()[0]/2.0,map.origin()[2]+map.size()[2]/2.0);
            level.getWorldBorder().setSize(Math.max(map.size()[0],map.size()[2])+2);level.setSpawnSettings(false,false);
            server.forgeGetWorldMap().put(dimension,level);server.markWorldsDirty();NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
            Files.createDirectories(folder);if(fresh)Files.writeString(folder.resolve(MARKER),run.id+"\n"+run.hash,StandardOpenOption.CREATE_NEW);
            return level;
        }catch(Exception failure){throw new IllegalStateException("无法准备远征世界",failure);}
    }
    public static boolean allowed(ServerPlayer p,ServerLevel destination){
        String id=destination.dimension().location().toString();if(!reserved(id))return true;
        var target=QuestState.identity(id);if(target.isEmpty())return false;
        var r=state(p.getServer()).instances.get(target.get());var m=member(p);
        return r!=null&&m!=null&&m.instance.equals(r.id)&&m.result.equals("ACTIVE")&&r.party.contains(p.getUUID())
                &&Set.of("READY","RUNNING","SUSPENDED","GOAL").contains(r.state)
                &&(r.expiresAt==0||r.expiresAt>System.currentTimeMillis())&&r.hash.equals(rules().hash(r.map));
    }
    public static ServerLevel loginLevel(MinecraftServer server,ResourceKey<Level> requested,ServerPlayer p){
        if(!reserved(requested.location().toString()))return null;
        var destination=server.getLevel(requested);
        if(destination!=null&&allowed(p,destination))return destination;
        var out=outside(p);p.setPos(out.x,out.y,out.z);p.setYRot(out.yaw);p.setXRot(out.pitch);
        return server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(out.dimension)));
    }
    public static DimensionTransition respawn(ServerPlayer p,DimensionTransition original){
        if(!reserved(p.level().dimension().location().toString())&&!reserved(original.newLevel().dimension().location().toString()))return original;
        var out=outside(p);return new DimensionTransition(p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(out.dimension))),new Vec3(out.x,out.y,out.z),Vec3.ZERO,out.yaw,out.pitch,original.postDimensionTransition());
    }
    public static String rangeLabel(String region,String pool){
        var r=WildernessRules.bundled().region(region).orElseThrow(()->new IllegalArgumentException("签发地没有境界配置"));
        var range=pool.equals("rogue")?r.rogue():r.beast();return label(range.min())+"至"+label(range.max());
    }
    private static String label(com.example.myvillage.stats.RealmKey key){
        String name=switch(key.realmId()){case "myvillage:qi_refining"->"炼气";case "myvillage:foundation_establishment"->"筑基";case "myvillage:golden_core"->"金丹";case "myvillage:nascent_soul"->"元婴";default->key.realmId();};
        return key.realmId().equals("myvillage:qi_refining")?name+(key.stage()+1)+"层":name+List.of("初期","中期","后期").get(Math.min(2,key.stage()));
    }
    public static void atBoard(ServerPlayer p,BlockPos board,String site,String mapId){
        if(!EconomyRuntime.valid(p,board,false,site,-1)||!rules().maps().contains(mapId))throw new IllegalArgumentException("unavailable");
        var old=member(p);if(old!=null&&(old.pendingReward||old.claiming)){claim(p);return;}
        if(current(p)!=null&&old.result.equals("ACTIVE")){status(p);return;}
        var sim=WorldSimRuntime.sim().orElseThrow();var source=sim.lifeSite(site).orElseThrow();
        var def=QuestMap.get(mapId);var region=WildernessRules.bundled().region(source.regionId()).orElseThrow(()->new IllegalArgumentException("unavailable"));
        var range=def.realmPool().equals("rogue")?region.rogue():region.beast();
        var run=state(p.getServer()).issue(p.getUUID(),mapId,site,source.name(),source.regionId(),p.getServer().overworld().getRandom().nextLong(),range,def,rules().hash(mapId));
        member(p).outside=location(p);save(p.getServer());p.closeContainer();
        say(p,"已接取"+def.name()+"，签发地："+source.name()+"；守敌境界："+rangeLabel(run.region,def.realmPool())+"。可以单人进入，或先邀请附近队友。");status(p);
    }
    private static boolean enter(ServerPlayer p){
        var r=current(p);var m=member(p);
        if(r==null||m==null||!m.result.equals("ACTIVE")||!p.isAlive()||p.isPassenger()||p.isSleeping()||p.isSpectator()){say(p,"当前没有可进入的远征，或动作尚未结束。");return false;}
        if(realm(p.serverLevel())){say(p,"你已在远征秘境中。");return false;}
        if(!p.level().dimension().equals(Level.OVERWORLD)){say(p,"请先回到主世界再进入远征。");return false;}
        if(r.state.equals("FORMING")){
            for(UUID id:r.party){var person=p.getServer().getPlayerList().getPlayer(id);if(person!=null)state(p.getServer()).members.get(id).outside=location(person);}
            r.state="BUILDING";save(p.getServer());
            try{createLevel(p.getServer(),r,true);}catch(Exception error){r.state="FAILED";save(p.getServer());throw error;}
            WAITING.add(p.getUUID());say(p,"正在准备完整关卡，完成场景与光照检查后会开放进入。");return true;
        }
        if(r.state.equals("BUILDING")||r.state.equals("LIGHTING")){WAITING.add(p.getUUID());say(p,"关卡仍在准备，请稍候。");return false;}
        var level=p.getServer().getLevel(key(r.id));if(level==null||!allowed(p,level)){say(p,"本轮远征尚未就绪或已失效，可放弃后重新接取。");return false;}
        var map=QuestMap.get(r.map);m.outside=location(p);QuestState.Place at=m.inside;
        if(at==null||!safe(level,at.x,at.y,at.z))at=entry(level,map);
        if(at==null){say(p,"入口没有安全站位，未传送。");return false;}
        save(p.getServer());boolean moved=p.teleportTo(level,at.x,at.y,at.z,Set.of(),at.yaw,at.pitch);
        if(moved){r.state=state(p.getServer()).goal(r,map)?"GOAL":"RUNNING";r.expiresAt=0;p.fallDistance=0;p.setDeltaMovement(Vec3.ZERO);m.inside=location(p);save(p.getServer());status(p);}return moved;
    }
    private static QuestState.Place entry(ServerLevel level,QuestMap map){
        var result=findSafe(level,pos(map,map.entry()),6);if(result==null)return null;
        var first=map.groups().stream().filter(QuestMap.Group::required).min(Comparator.comparingInt(QuestMap.Group::stage)).orElseThrow();var target=pos(map,first.actors().getFirst().pos());
        result.yaw=(float)Math.toDegrees(Math.atan2(-(target.getX()+.5-result.x),target.getZ()+.5-result.z));return result;
    }
    private static boolean safe(ServerLevel level,double x,double y,double z){
        var body=new AABB(x-.3,y,z-.3,x+.3,y+1.8,z+.3);
        return level.getWorldBorder().isWithinBounds(body)&&level.noCollision(body)&&!level.containsAnyLiquid(body)
                &&!level.noCollision(body.move(0,-.08,0));
    }
    private static QuestState.Place findSafe(ServerLevel level,BlockPos around,int radius){
        for(int r=0;r<=radius;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
            for(int dy=0;dy<=3;dy++){double x=around.getX()+dx+.5,y=around.getY()+dy,z=around.getZ()+dz+.5;
                if(safe(level,x,y,z))return new QuestState.Place(level.dimension().location().toString(),x,y,z,180,0);}
        }return null;
    }
    private static QuestState.Place outside(ServerPlayer p){
        var m=member(p);var at=m==null?null:m.outside;
        var level=at==null?null:p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(at.dimension)));
        if(level!=null&&!reserved(at.dimension)&&safe(level,at.x,at.y,at.z))return at;
        if(level==null||reserved(level.dimension().location().toString()))level=p.getServer().overworld();
        BlockPos center=at==null?level.getSharedSpawnPos():BlockPos.containing(at.x,at.y,at.z);
        for(int radius=0;radius<=rules().integer("return_search_radius");radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            int x=center.getX()+dx,z=center.getZ()+dz,y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            if(safe(level,x+.5,y,z+.5))return new QuestState.Place(level.dimension().location().toString(),x+.5,y,z+.5,0,0);
        }
        throw new IllegalStateException("返回点附近没有安全站位，未覆盖地形。");
    }
    private static void remember(ServerPlayer p){if(realm(p.serverLevel())&&member(p)!=null)member(p).inside=location(p);}
    private static void status(ServerPlayer p){
        var r=current(p);var m=member(p);
        if(m!=null&&m.pendingReward){say(p,"远征已完成，报酬尚未领取。请到任一登记的千机阁江湖榜办理远征。");button(p,"领取报酬","/expedition claim");return;}
        if(r==null){say(p,"尚未接取远征。可到千机阁江湖榜第二页选择山寨清剿或兽巢讨伐。");return;}
        say(p,QuestMap.get(r.map).name()+"；签发地："+r.sourceName+"；境界："+rangeLabel(r.region,QuestMap.get(r.map).realmPool())+"；队伍 "+r.party.size()+"人；救援余量 "+Math.max(0,rules().integer("rescue_count")-m.rescues)+"；状态 "+r.state);
        if(!realm(p.serverLevel()))button(p,r.state.equals("FORMING")?"开始远征":"继续远征","/expedition enter");
        else {button(p,"暂离并保留","/expedition leave");if(r.state.equals("GOAL"))button(p,"出口验核","/expedition finish");}
        button(p,"放弃本轮","/expedition abandon");
    }

    private static List<ServerPlayer> present(MinecraftServer server,QuestState.Instance r){
        var level=server.getLevel(key(r.id));if(level==null)return List.of();
        return level.players().stream().filter(p->r.party.contains(p.getUUID())&&member(p)!=null&&member(p).instance.equals(r.id)&&member(p).result.equals("ACTIVE")&&p.isAlive()&&!p.isSpectator()).toList();
    }
    private static void tickets(ServerLevel level,QuestState.Instance r,Collection<ChunkPos> chunks){
        var old=TICKETS.computeIfAbsent(r.id,id->new HashSet<>());var desired=new HashSet<>(chunks);
        for(var chunk:new HashSet<>(old))if(!desired.contains(chunk)){level.getChunkSource().removeRegionTicket(TICKET,chunk,2,r.id);old.remove(chunk);}
        for(var chunk:desired)if(old.add(chunk)){level.getChunkSource().addRegionTicket(TICKET,chunk,2,r.id);level.getChunk(chunk.x,chunk.z);}
    }
    private static Set<ChunkPos> allChunks(QuestMap map){
        var chunks=new HashSet<ChunkPos>();for(int x=0;x<map.size()[0];x+=16)for(int z=0;z<map.size()[2];z+=16)chunks.add(new ChunkPos(map.origin()[0]+x>>4,map.origin()[2]+z>>4));return chunks;
    }
    private static void build(ServerLevel level,QuestState.Instance r){
        var map=QuestMap.get(r.map);tickets(level,r,allChunks(map));
        var palette=PALETTES.computeIfAbsent(r.id,id->map.palette().stream().map(s->{try{return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK),s,false).blockState();}catch(Exception error){throw new IllegalArgumentException("远征方块状态无效："+s,error);}}).toList());
        int remaining=rules().integer("blocks_per_tick");
        while(remaining>0&&r.buildRun<map.runs().size()){
            var run=map.runs().get(r.buildRun);int count=Math.min(remaining,run.height()-r.buildOffset);
            for(int i=0;i<count;i++)level.setBlock(pos(map,new int[]{run.x(),run.y()+r.buildOffset+i,run.z()}),palette.get(run.material()),2|16);
            r.buildOffset+=count;remaining-=count;if(r.buildOffset==run.height()){r.buildRun++;r.buildOffset=0;}
        }
        if(r.buildRun==map.runs().size()){
            r.state="LIGHTING";r.lightingAt=level.getServer().getTickCount();PALETTES.remove(r.id);save(level.getServer());
        }
    }
    private static void lighting(ServerLevel level,QuestState.Instance r){
        long age=level.getServer().getTickCount()-r.lightingAt;if(age<rules().light("settle_ticks"))return;
        var map=QuestMap.get(r.map);int min=15;
        for(var probe:map.lightProbes())min=Math.min(min,level.getBrightness(LightLayer.BLOCK,pos(map,probe)));
        r.lightMinimum=min;
        if(min<rules().light("minimum_light")){
            if(age>rules().light("timeout_ticks")){r.state="FAILED";tickets(level,r,List.of());save(level.getServer());notify(level.getServer(),r,"关卡光照检查未通过，未开放入口。可以放弃后重新接取。");}
            return;
        }
        if(entry(level,map)==null){r.state="FAILED";tickets(level,r,List.of());save(level.getServer());notify(level.getServer(),r,"关卡入口没有安全站位，未开放入口。");return;}
        for(var group:map.groups())for(var actor:group.actors()){
            if(!BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(actor.entity()))||!actor.weapon().isBlank()&&!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(actor.weapon())))
                throw new IllegalStateException("远征守敌或武器未登记："+actor.entity());
            var mob=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(actor.entity())).create(level);var at=pos(map,actor.pos());
            if(!(mob instanceof Mob))throw new IllegalStateException("远征守敌未登记："+actor.entity());
            mob.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,180,0);
            if(!level.noCollision(mob)||level.containsAnyLiquid(mob.getBoundingBox())||level.noCollision(mob.getBoundingBox().move(0,-.08,0)))
                throw new IllegalStateException("远征出生点不安全："+group.id()+" "+at);
        }
        level.save(null,true,false);r.state="READY";r.expiresAt=System.currentTimeMillis()+rules().integer("retention_millis");save(level.getServer());
        tickets(level,r,List.of());notify(level.getServer(),r,"关卡已就绪，光照检查通过。可从行迹菜单或聊天按钮进入。");
        for(UUID id:r.party)if(WAITING.remove(id)){var p=level.getServer().getPlayerList().getPlayer(id);if(p!=null)enter(p);}
    }
    private static void notify(MinecraftServer server,QuestState.Instance r,String text){for(UUID id:r.party){var p=server.getPlayerList().getPlayer(id);if(p!=null)say(p,text);}}
    private static QuestState.Slot slot(QuestState.Instance r,UUID id){return r.slots.stream().filter(s->s.id.equals(id)).findFirst().orElse(null);}
    private static boolean groupDone(QuestState.Instance r,String group){return r.slots.stream().filter(s->s.group.equals(group)).allMatch(s->s.dead);}
    private static boolean eligible(QuestState.Instance r,QuestMap map,QuestMap.Group group){
        if(group.required())return map.groups().stream().filter(g->g.required()&&g.stage()<group.stage()).allMatch(g->groupDone(r,g.id()));
        return map.groups().stream().filter(QuestMap.Group::required).min(Comparator.comparingInt(QuestMap.Group::stage)).map(g->groupDone(r,g.id())).orElse(false);
    }
    private static boolean spawn(ServerLevel level,QuestState.Instance r,QuestState.Slot slot,QuestMap.Actor actor){
        if(level.getDifficulty()==Difficulty.PEACEFUL)return false;
        var map=QuestMap.get(r.map);var at=pos(map,actor.pos());
        var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(actor.entity()));var entity=type.create(level);
        if(!(entity instanceof Mob mob))throw new IllegalStateException("远征守敌类型无效："+actor.entity());
        mob.setUUID(slot.id);mob.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,180,0);
        if(!level.noCollision(mob)||level.containsAnyLiquid(mob.getBoundingBox())||level.noCollision(mob.getBoundingBox().move(0,-.08,0)))return false;
        if(present(level.getServer(),r).stream().anyMatch(p->p.distanceToSqr(mob)<Math.pow(rules().integer("spawn_clear_radius"),2)))return false;
        mob.getPersistentData().putUUID(TAG,r.id);mob.setPersistenceRequired();mob.setCanPickUpLoot(false);mob.restrictTo(at,rules().integer("leash_radius"));
        if(!actor.weapon().isBlank()){
            var weapon=BuiltInRegistries.ITEM.get(ResourceLocation.parse(actor.weapon()));if(weapon==Items.AIR)throw new IllegalStateException("远征武器缺失");mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(weapon));
        }
        for(var equipment:EquipmentSlot.values())mob.setDropChance(equipment,0);
        if(mob instanceof BeastEntity beast)beast.assignWildernessBirth(new BeastBirth(slot.key(),r.region,at.getX(),at.getY(),at.getZ(),false));
        else StatsRuntime.applyRealm(mob,slot.realm,slot.stage);
        double multiplier=rules().partyHealth(r.party.size())*(actor.boss()?rules().number("boss_health"):1);
        var health=mob.getAttribute(Attributes.MAX_HEALTH);if(health==null)throw new IllegalStateException("远征守敌缺少生命属性");
        health.removeModifier(HEALTH);if(multiplier!=1)health.addPermanentModifier(new AttributeModifier(HEALTH,multiplier-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(Component.literal(actor.boss()?"首领 · ":"").append(mob.getType().getDescription()).append("（"+label(slot.key())+"）"));mob.setCustomNameVisible(actor.boss());
        // Persist ownership before entity insertion; a missing entity is never inferred dead or re-created.
        slot.spawned=true;save(level.getServer());BattleReceipts.get(level).register(slot.id);
        if(!level.addFreshEntity(mob)){slot.spawned=false;BattleReceipts.get(level).forget(slot.id);save(level.getServer());return false;}
        return true;
    }
    private static void encounters(ServerLevel level,QuestState.Instance r,List<ServerPlayer> players){
        var map=QuestMap.get(r.map);var receipts=BattleReceipts.get(level);boolean changed=false;
        for(var slot:r.slots)if(slot.spawned&&!slot.dead){
            String receipt=receipts.status(slot.id);
            if(receipt.equals("dead")){state(level.getServer()).killed(r.id,slot.id);receipts.forget(slot.id);changed=true;}
            else if(receipt.equals("retry")&&level.getEntity(slot.id)==null){slot.spawned=false;receipts.forget(slot.id);changed=true;}
            else if(receipt.equals("unknown")&&level.getEntity(slot.id) instanceof Mob)receipts.register(slot.id);
        }
        var chunks=new HashSet<ChunkPos>();
        for(var slot:r.slots)if(slot.spawned&&!slot.dead){
            var group=map.groups().stream().filter(g->g.id().equals(slot.group)).findFirst().orElseThrow();
            var home=pos(map,group.actors().get(slot.index).pos());chunks.add(new ChunkPos(home));
            var entity=level.getEntity(slot.id);
            if(entity instanceof Mob mob){
                mob.setNoAi(false);mob.setNoGravity(false);
                if(mob.distanceToSqr(Vec3.atCenterOf(home))>Math.pow(rules().integer("leash_radius"),2)){
                    if(level.noCollision(mob.getBoundingBox().move(Vec3.atBottomCenterOf(home).subtract(mob.position()))))mob.teleportTo(home.getX()+.5,home.getY(),home.getZ()+.5);
                    mob.getNavigation().stop();mob.setTarget(null);
                }
                var target=players.stream().filter(p->!p.isCreative()&&p.distanceToSqr(Vec3.atCenterOf(home))<=Math.pow(rules().integer("leash_radius"),2)).min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);mob.setTarget(target);
                for(var p:players)if(p.distanceToSqr(mob)<=Math.pow(rules().integer("leash_radius"),2))member(p).participated=true;
            }
        }
        tickets(level,r,chunks);
        int alive=(int)r.slots.stream().filter(s->s.spawned&&!s.dead).count();
        for(var group:map.groups()){
            var encounter=r.encounters.get(group.id());
            if(groupDone(r,group.id())){if(!encounter.state.equals("DONE")){encounter.state="DONE";notify(level.getServer(),r,group.name()+"已清除。");changed=true;}continue;}
            if(!eligible(r,map,group))continue;
            boolean near=players.stream().anyMatch(p->group.actors().stream().anyMatch(a->p.distanceToSqr(Vec3.atCenterOf(pos(map,a.pos())))<=Math.pow(rules().integer("trigger_radius"),2)));
            if(encounter.state.equals("WAITING")&&near){encounter.state="NOTICE";encounter.notice=rules().integer("spawn_notice_ticks");notify(level.getServer(),r,"已接近"+group.name()+"，守敌即将出现。");changed=true;}
            if(encounter.state.equals("NOTICE")){encounter.notice-=rules().integer("tick_interval");if(encounter.notice<=0){encounter.state="ACTIVE";changed=true;}}
            if(!encounter.state.equals("ACTIVE"))continue;
            for(var slot:r.slots)if(slot.group.equals(group.id())&&!slot.spawned&&!slot.dead&&alive<rules().integer("max_active_mobs")){
                var at=pos(map,group.actors().get(slot.index).pos());chunks.add(new ChunkPos(at));tickets(level,r,chunks);
                if(spawn(level,r,slot,group.actors().get(slot.index))){alive++;changed=true;}
            }
        }
        if(state(level.getServer()).goal(r,map)&&!r.state.equals("GOAL")){r.state="GOAL";notify(level.getServer(),r,"必经守敌已清除。请前往出口磁石验核，领取本轮报酬。");changed=true;}
        if(changed)save(level.getServer());
        var bar=BARS.computeIfAbsent(r.id,id->new ServerBossEvent(Component.literal(map.name()),BossEvent.BossBarColor.GREEN,BossEvent.BossBarOverlay.PROGRESS));
        bar.removeAllPlayers();players.forEach(bar::addPlayer);
        long total=r.slots.stream().filter(s->map.groups().stream().anyMatch(g->g.id().equals(s.group)&&g.required())).count();
        long dead=r.slots.stream().filter(s->s.dead&&map.groups().stream().anyMatch(g->g.id().equals(s.group)&&g.required())).count();
        String next=map.groups().stream().filter(QuestMap.Group::required).filter(g->!groupDone(r,g.id())).min(Comparator.comparingInt(QuestMap.Group::stage)).map(QuestMap.Group::name).orElse("前往出口验核");
        bar.setName(Component.literal(map.name()+"："+dead+"/"+total+"；"+next));bar.setProgress(total==0?0:(float)dead/total);
    }
    private static void tick(ServerTickEvent.Post event){
        var server=event.getServer();
        for(var entry:new HashMap<>(RESCUE).entrySet()){
            var p=server.getPlayerList().getPlayer(entry.getKey());RESCUE.remove(entry.getKey());if(p==null)continue;
            if(entry.getValue()<0){leaveNow(p,"FAILED");continue;}
            var r=current(p);var level=r==null?null:server.getLevel(key(r.id));var at=level==null?null:entry(level,QuestMap.get(r.map));
            if(at==null){leaveNow(p,"FAILED");continue;}
            p.teleportTo(level,at.x,at.y,at.z,Set.of(),at.yaw,at.pitch);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;p.setHealth(Math.max(1,(float)(p.getMaxHealth()*rules().number("rescue_health_fraction"))));p.invulnerableTime=rules().integer("rescue_protection_ticks");
            say(p,"已送回入口，剩余救援 "+Math.max(0,rules().integer("rescue_count")-member(p).rescues)+"次。");
        }
        for(var request:new HashMap<>(RETURNS).entrySet()){
            var p=server.getPlayerList().getPlayer(request.getKey());var data=request.getValue();
            if(p==null||!realm(p.serverLevel())||p.position().distanceToSqr(data.origin)>1){RETURNS.remove(request.getKey());if(p!=null)say(p,"移动中断了离场读条。");continue;}
            if(server.getTickCount()>=data.at){RETURNS.remove(request.getKey());leaveNow(p,data.abandon?"ABANDONED":"ACTIVE");}
        }
        boolean budgetUsed=false;
        for(var r:new ArrayList<>(state(server).instances.values())){
            var level=server.getLevel(key(r.id));
            try{
                if(r.state.equals("BUILDING")&&!budgetUsed&&level!=null){build(level,r);budgetUsed=true;}
                if(server.getTickCount()%rules().integer("tick_interval")!=0)continue;
                if(r.state.equals("LIGHTING")&&level!=null){lighting(level,r);continue;}
                var players=present(server,r);
                if(!players.isEmpty()&&r.state.equals("FAILED")){
                    for(var p:players)leaveNow(p,"FAILED");continue;
                }
                if(!players.isEmpty()){
                    r.expiresAt=0;
                    if(!Set.of("RUNNING","GOAL").contains(r.state))r.state=state(server).goal(r,QuestMap.get(r.map))?"GOAL":"RUNNING";
                    encounters(level,r,players);
                    for(var p:players){remember(p);if(p.getY()<QuestMap.get(r.map).origin()[1]-4){RESCUE.put(p.getUUID(),-1);}}
                }else if(!Set.of("BUILDING","LIGHTING").contains(r.state)){
                    if(r.state.equals("RUNNING"))r.state="SUSPENDED";
                    if(r.expiresAt==0)r.expiresAt=System.currentTimeMillis()+rules().integer("retention_millis");
                    if(level!=null){for(var s:r.slots)if(level.getEntity(s.id) instanceof Mob mob){mob.setNoAi(true);mob.setNoGravity(true);mob.setTarget(null);mob.setDeltaMovement(Vec3.ZERO);}tickets(level,r,List.of());}
                    var bar=BARS.get(r.id);if(bar!=null)bar.removeAllPlayers();
                    boolean ended=r.party.stream().noneMatch(id->{var m=state(server).members.get(id);return m!=null&&m.instance.equals(r.id)&&m.result.equals("ACTIVE");});
                    if(ended||r.state.equals("RECLAIMING")||r.expiresAt<System.currentTimeMillis())reclaim(server,r);
                }
            }catch(Exception error){r.state="FAILED";save(server);LoggerFactory.getLogger(QuestRuntime.class).error("远征运行停止 {}",r.id,error);notify(server,r,"本轮远征出现错误，进度已保留，请暂离并保留日志。");}
        }
        if(server.getTickCount()%100==0)saves(server).setDirty();
    }
    private static void leaveNow(ServerPlayer p,String result){
        var m=member(p);if(m==null)return;var r=current(p);remember(p);
        var out=outside(p);var level=p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(out.dimension)));
        if(!p.teleportTo(level,out.x,out.y,out.z,Set.of(),out.yaw,out.pitch)){say(p,"返回传送未完成，请重试。");return;}
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        if(!result.equals("ACTIVE"))m.result=result;save(p.getServer());
        if(result.equals("ACTIVE"))say(p,"已返回，守敌与本轮进度会保留24小时。");
        else if(result.equals("FAILED"))say(p,"救援已用尽，本轮远征结束。");
        else if(result.equals("SUCCESS"))say(p,"远征验核通过。报酬已登记，请回千机阁江湖榜领取。");
        else say(p,"已放弃本轮远征。");
        var bar=r==null?null:BARS.get(r.id);if(bar!=null)bar.removePlayer(p);
    }
    private static void finish(ServerPlayer p){
        var r=current(p);var m=member(p);if(r==null||m==null||!realm(p.serverLevel())||!m.result.equals("ACTIVE")||!state(p.getServer()).goal(r,QuestMap.get(r.map))){say(p,"本轮必经守敌尚未全部清除。");return;}
        var map=QuestMap.get(r.map);if(p.distanceToSqr(Vec3.atCenterOf(pos(map,map.exit())))>Math.pow(rules().integer("exit_range"),2)){say(p,"请到出口磁石附近验核。");return;}
        if(!m.participated){say(p,"尚无本轮参战记录，无法登记报酬。");return;}
        m.stones=map.stones();m.reputation=map.reputation();m.pendingReward=true;save(p.getServer());leaveNow(p,"SUCCESS");
    }
    private static boolean nearBoard(ServerPlayer p){
        if(p.containerMenu instanceof EconomyMenu menu&&!menu.linlang&&menu.stillValid(p))return true;
        var sim=WorldSimRuntime.sim().orElse(null);if(sim==null)return false;
        for(var site:sim.lifeSites())for(var place:site.places())if(place.id().equals("jianghu_board")&&EconomyRuntime.valid(p,new BlockPos(place.blockX(),place.blockY(),place.blockZ()),false,site.id(),-1))return true;
        return false;
    }
    private static void claim(ServerPlayer p){
        var m=member(p);if(m==null||!m.pendingReward||m.claimed){say(p,"没有待领的远征报酬。");return;}
        if(m.claiming){say(p,"此笔报酬的提交记录尚待核对，已停止再次发放。请保留存档和日志。");return;}
        if(!m.result.equals("SUCCESS")||!nearBoard(p)){say(p,"请到登记的千机阁江湖榜前领取报酬。");return;}
        var reward=new TaskReward(0,m.reputation,Map.of("low",m.stones));var plan=EconomyInventory.reward(p,reward,ItemStack.EMPTY,0,false);
        if(!plan.ready()){say(p,"背包空间不足，报酬仍保留在江湖榜。");return;}
        m.claiming=true;save(p.getServer());
        var sim=WorldSimRuntime.sim().orElseThrow();
        if(!sim.rewardExpedition(p.getUUID().toString(),p.getGameProfile().getName(),reward,plan::commit)){m.claiming=false;save(p.getServer());say(p,"物品发生变化，请重新办理。");return;}
        EconomyRuntime.dirty(p);p.getServer().overworld().getDataStorage().save();net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();p.getServer().getPlayerList().saveAll();
        m.pendingReward=false;m.claiming=false;m.claimed=true;save(p.getServer());say(p,"已领取远征报酬：下品灵石 "+m.stones+"，江湖声望 "+m.reputation+"。");
    }
    private static void requestLeave(ServerPlayer p,boolean abandon){
        var m=member(p);if(m==null||!m.result.equals("ACTIVE")){say(p,"当前没有进行中的远征。");return;}
        if(!realm(p.serverLevel())){if(abandon){m.result="ABANDONED";save(p.getServer());say(p,"已放弃本轮远征。");}else status(p);return;}
        RETURNS.put(p.getUUID(),new ReturnRequest(p.getServer().getTickCount()+rules().integer("return_channel_ticks"),p.position(),abandon));say(p,"正在准备离场，请停留6秒；移动或受伤会中断。");
    }
    private static void invite(ServerPlayer p,ServerPlayer other){
        var r=current(p);if(r==null||!r.state.equals("FORMING")||!r.leader.equals(p.getUUID())||r.party.size()>=rules().integer("max_party")){say(p,"只有尚未开始且未满员的队伍可以由接单者邀请。");return;}
        if(other==p||other.level()!=p.level()||p.distanceToSqr(other)>Math.pow(rules().integer("invite_range"),2)){say(p,"请邀请附近的玩家。");return;}
        INVITES.put(other.getUUID(),new Invitation(r.id,System.currentTimeMillis()+rules().integer("invite_seconds")*1000L));say(other,p.getGameProfile().getName()+"邀请你参加"+QuestMap.get(r.map).name()+"；守敌境界 "+rangeLabel(r.region,QuestMap.get(r.map).realmPool())+"。");button(other,"加入远征","/expedition accept");say(p,"邀请已发出。");
    }
    private static void accept(ServerPlayer p){
        var invitation=INVITES.remove(p.getUUID());var r=invitation==null?null:state(p.getServer()).instances.get(invitation.instance);var old=member(p);
        if(r==null||invitation.until<System.currentTimeMillis()||!r.state.equals("FORMING")||r.party.size()>=rules().integer("max_party")||old!=null&&(old.pendingReward||old.claiming||current(p)!=null&&old.result.equals("ACTIVE"))){say(p,"邀请已失效，或你仍有未处理的远征。");return;}
        var leader=p.getServer().getPlayerList().getPlayer(r.leader);
        if(leader==null||leader.level()!=p.level()||p.distanceToSqr(leader)>Math.pow(rules().integer("invite_range"),2)){say(p,"请在接单者附近加入。");return;}
        r.party.add(p.getUUID());var m=new QuestState.Member();m.instance=r.id;m.outside=location(p);state(p.getServer()).members.put(p.getUUID(),m);save(p.getServer());notify(p.getServer(),r,p.getGameProfile().getName()+"已加入，当前 "+r.party.size()+"人。");status(p);
    }
    private static void reclaim(MinecraftServer server,QuestState.Instance r)throws Exception{
        var level=server.getLevel(key(r.id));if(level!=null&&!level.players().isEmpty())return;
        r.state="RECLAIMING";save(server);
        if(level!=null){tickets(level,r,List.of());NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));level.save(null,true,false);server.forgeGetWorldMap().remove(key(r.id));server.markWorldsDirty();level.close();}
        Path root=server.getWorldPath(LevelResource.ROOT).toRealPath();Path folder=DimensionType.getStorageFolder(key(r.id),root).normalize();
        Path expected=root.resolve("dimensions/myvillage/expedition/"+r.id).normalize();
        if(!folder.equals(expected)||!folder.startsWith(root)||Files.isSymbolicLink(folder))throw new IllegalStateException("远征回收路径无效");
        if(Files.exists(folder)){
            if(!folder.toRealPath().equals(folder))throw new IllegalStateException("远征回收路径含重定向，保留目录");
            if(!Files.isRegularFile(folder.resolve(MARKER))||!Files.readString(folder.resolve(MARKER)).equals(r.id+"\n"+r.hash))throw new IllegalStateException("远征回收标记无效，保留目录");
            try(var paths=Files.walk(folder)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList()){if(Files.isSymbolicLink(path))throw new IllegalStateException("远征目录含符号链接");Files.delete(path);}}
        }
        for(UUID id:r.party){var m=state(server).members.get(id);if(m!=null&&m.instance.equals(r.id)&&m.result.equals("ACTIVE"))m.result="FAILED";}
        for(var slot:r.slots)BattleReceipts.get(server.overworld()).forget(slot.id);
        var bar=BARS.remove(r.id);if(bar!=null)bar.removeAllPlayers();PALETTES.remove(r.id);TICKETS.remove(r.id);state(server).instances.remove(r.id);save(server);
    }
    private static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("expedition")
                .executes(c->{status(c.getSource().getPlayerOrException());return 1;})
                .then(Commands.literal("status").executes(c->{status(c.getSource().getPlayerOrException());return 1;}))
                .then(Commands.literal("enter").executes(c->enter(c.getSource().getPlayerOrException())?1:0))
                .then(Commands.literal("leave").executes(c->{requestLeave(c.getSource().getPlayerOrException(),false);return 1;}))
                .then(Commands.literal("abandon").executes(c->{requestLeave(c.getSource().getPlayerOrException(),true);return 1;}))
                .then(Commands.literal("finish").executes(c->{finish(c.getSource().getPlayerOrException());return 1;}))
                .then(Commands.literal("claim").executes(c->{claim(c.getSource().getPlayerOrException());return 1;}))
                .then(Commands.literal("accept").executes(c->{accept(c.getSource().getPlayerOrException());return 1;}))
                .then(Commands.literal("invite").then(Commands.argument("player",EntityArgument.player()).executes(c->{invite(c.getSource().getPlayerOrException(),EntityArgument.getPlayer(c,"player"));return 1;}))));
        e.getDispatcher().register(Commands.literal("myvillage").requires(s->s.hasPermission(2)).then(Commands.literal("quest").then(Commands.literal("status").then(Commands.argument("player",EntityArgument.player()).executes(c->{
            var p=EntityArgument.getPlayer(c,"player");var j=new JsonObject();var r=current(p);var m=member(p);
            j.addProperty("dimension",p.level().dimension().location().toString());j.addProperty("member",m==null?"NONE":m.result);
            var local=new JsonObject();local.addProperty("natural_spawns",p.serverLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING));local.addProperty("mob_griefing",p.serverLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING));local.addProperty("fire_tick",p.serverLevel().getGameRules().getBoolean(GameRules.RULE_DOFIRETICK));j.add("local_rules",local);
            if(r!=null){j.add("instance",new Gson().toJsonTree(r));var level=p.getServer().getLevel(key(r.id));var guards=new JsonArray();if(level!=null)for(var slot:r.slots){var mob=level.getEntity(slot.id);if(mob instanceof LivingEntity living){var item=new JsonObject();item.addProperty("id",slot.id.toString());item.addProperty("health",living.getHealth());item.addProperty("max_health",living.getMaxHealth());item.addProperty("x",living.getX());item.addProperty("y",living.getY());item.addProperty("z",living.getZ());item.addProperty("realm",StatsRuntime.assignedRealm(living).map(k->k.realmId()+":"+k.stage()).orElse("NONE"));guards.add(item);}}j.add("guards",guards);}
            if(m!=null)j.add("receipt",new Gson().toJsonTree(m));c.getSource().sendSuccess(()->Component.literal("QUEST_STATUS "+j),false);return 1;
        })))));
    }
    private static void joined(EntityJoinLevelEvent e){
        if(!(e.getLevel() instanceof ServerLevel level)||!realm(level)||!(e.getEntity() instanceof Mob mob))return;
        var r=state(level.getServer()).instances.get(QuestState.identity(level.dimension().location().toString()).orElseThrow());var slot=r==null?null:slot(r,mob.getUUID());
        if(r==null||slot==null||slot.dead||!slot.spawned||!mob.getPersistentData().hasUUID(TAG)||!mob.getPersistentData().getUUID(TAG).equals(r.id))e.setCanceled(true);
    }
    private static void travel(EntityTravelToDimensionEvent e){
        if(!(e.getEntity().level() instanceof ServerLevel from))return;
        if(e.getEntity() instanceof ServerPlayer p){var to=from.getServer().getLevel(e.getDimension());if(to!=null&&!allowed(p,to))e.setCanceled(true);}
        else if(realm(from)||reserved(e.getDimension().location().toString()))e.setCanceled(true);
    }
    private static void loggedOut(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){remember(p);RETURNS.remove(p.getUUID());WAITING.remove(p.getUUID());save(p.getServer());}}
    private static void loggedIn(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p&&member(p)!=null)status(p);}
    private static void death(LivingDeathEvent e){
        if(e.isCanceled()||!(e.getEntity() instanceof ServerPlayer p)||!realm(p.serverLevel()))return;
        var m=member(p);if(m==null||!m.result.equals("ACTIVE"))return;e.setCanceled(true);m.rescues++;p.setHealth(1);p.invulnerableTime=rules().integer("rescue_protection_ticks");RETURNS.remove(p.getUUID());RESCUE.put(p.getUUID(),m.rescues>rules().integer("rescue_count")?-1:1);save(p.getServer());
    }
    private static void drops(LivingDropsEvent e){if(e.getEntity().getPersistentData().hasUUID(TAG))e.getDrops().clear();}
    private static void damage(LivingDamageEvent.Post e){if(e.getEntity() instanceof ServerPlayer p&&e.getNewDamage()>0&&RETURNS.remove(p.getUUID())!=null)say(p,"受伤中断了离场读条。");}
    private static void breakBlock(BlockEvent.BreakEvent e){if(e.getLevel() instanceof ServerLevel level&&realm(level))e.setCanceled(true);}
    private static void placeBlock(BlockEvent.EntityPlaceEvent e){if(e.getLevel() instanceof ServerLevel level&&realm(level))e.setCanceled(true);}
    private static void explosion(ExplosionEvent.Detonate e){if(e.getLevel() instanceof ServerLevel level&&realm(level))e.getAffectedBlocks().clear();}
    private static void interact(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!realm(p.serverLevel()))return;
        e.setCanceled(true);var r=current(p);if(r!=null&&e.getPos().equals(pos(QuestMap.get(r.map),QuestMap.get(r.map).exit())))finish(p);
    }
    private static void sleep(CanPlayerSleepEvent e){if(realm(e.getEntity().serverLevel()))e.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);}
}
