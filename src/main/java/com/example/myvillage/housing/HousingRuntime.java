package com.example.myvillage.housing;

import com.example.myvillage.command.ModCommands;
import com.example.myvillage.courtyard.CourtyardAssemblyBlock;
import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import com.example.myvillage.worldplan.capital.CapitalCity;
import com.example.myvillage.worldplan.runtime.PlannedChunkGenerator;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.LoggerFactory;

/** 宅地房契、报价、预检和施工只在服务器修改。 */
public final class HousingRuntime {
    public record Site(CapitalCity city,HousingPlot plot,UUID stewardId){
        public String key(){return city.cityId()+"/"+plot.id();}
        public BlockPos world(BlockPos p){return p.offset(city.x(),city.originY(),city.z());}
        public BlockPos origin(){return world(new BlockPos(plot.minX(),plot.floor(),plot.minZ()));}
    }
    private record Session(Site site,UUID nonce,long expires,int claimCost,int buildCost,String currency){}
    private static final class Job {
        final Site site;final HousingSavedData.Title title;final boolean unpaid;final UUID requester;
        final List<ChunkPos> chunks=new ArrayList<>();final int cost;final String currency;long deadline;
        Job(Site site,HousingSavedData.Title title,boolean unpaid,UUID requester,int cost,String currency,long now){
            this.site=site;this.title=title;this.unpaid=unpaid;this.requester=requester;this.cost=cost;this.currency=currency;deadline=now+2400;
        }
    }
    private static final TicketType<String> TICKET=TicketType.create("yuanxian_housing",String::compareTo);
    private static final Map<ServerLevel,List<Site>> SITES=new WeakHashMap<>();
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<String,Job> JOBS=new LinkedHashMap<>();
    private HousingRuntime(){}
    public static void register(){
        NeoForge.EVENT_BUS.addListener(HousingRuntime::tick);
        NeoForge.EVENT_BUS.addListener(HousingRuntime::breakBlock);
        NeoForge.EVENT_BUS.addListener(HousingRuntime::placeBlock);
        NeoForge.EVENT_BUS.addListener(HousingRuntime::rightClick);
        NeoForge.EVENT_BUS.addListener(HousingRuntime::explode);
        NeoForge.EVENT_BUS.addListener(HousingRuntime::commands);
        NeoForge.EVENT_BUS.addListener(HousingRuntime::stopped);
    }
    public static List<Site> sites(ServerLevel level){
        if(level.dimension()!=Level.OVERWORLD||!(level.getChunkSource().getGenerator() instanceof PlannedChunkGenerator g))return List.of();
        return SITES.computeIfAbsent(level,l->{var result=new ArrayList<Site>();for(var city:g.plan().cities())for(var plot:city.bundle().housingPlots){
            String key=level.getSeed()+":"+city.cityId()+":"+city.x()+":"+city.z()+":"+plot.id();
            result.add(new Site(city,plot,UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))));
        }return List.copyOf(result);});
    }
    public static boolean open(ServerPlayer player,NpcEntity npc){
        for(var site:sites(player.serverLevel()))if(site.stewardId().equals(npc.getUUID())&&!npc.isLedgerAvatar()
                &&npc.distanceToSqr(Vec3.atBottomCenterOf(site.world(site.plot.steward())))<1){offer(player,site,"");return true;}
        return false;
    }
    private static void offer(ServerPlayer p,Site site,String message){
        var title=HousingSavedData.get(p.getServer()).titles.get(site.key());UUID nonce=UUID.randomUUID();
        SESSIONS.put(p.getUUID(),new Session(site,nonce,p.serverLevel().getGameTime()+6000,HousingConfig.CLAIM_COST.get(),HousingConfig.BUILD_COST.get(),HousingConfig.CURRENCY.get()));
        String status=title==null?"unclaimed":title.status;
        if(JOBS.containsKey(site.key())&&JOBS.get(site.key()).unpaid)status="preparing";
        Item currency=currency(HousingConfig.CURRENCY.get());
        PacketDistributor.sendToPlayer(p,new HousingOffer(nonce,site.key(),site.plot.name(),site.origin(),
            site.plot.width(),site.plot.depth(),status,
            title!=null&&title.owner.equals(p.getUUID()),HousingConfig.CLAIM_COST.get(),HousingConfig.BUILD_COST.get(),
            currency==null?"无效灵石配置":currency.getDescriptionId(),message.isEmpty()&&title!=null&&!title.error.isEmpty()?title.error:message,
            title==null?"":title.house,title==null?0:title.turn,title==null?0:title.offsetX,title==null?0:title.offsetZ));
    }
    private static Item currency(String id){
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null||!BuiltInRegistries.ITEM.containsKey(key))return null;
        var item=BuiltInRegistries.ITEM.get(key);return item==net.minecraft.world.item.Items.AIR?null:item;
    }
    private static boolean pay(ServerPlayer player,int count,String id){
        if(count==0)return true;Item item=currency(id);if(item==null)return false;
        var access=SpiritStoneSources.open(player);var copies=access.copies();
        if(!SpiritStoneStorage.remove(copies,s->s.is(item),count)||!access.commit(copies))return false;
        player.containerMenu.broadcastChanges();return true;
    }
    public static boolean fits(HousingTemplates.Info info,int turn,int x,int z,int width,int depth){
        if(turn<0||turn>3||x<0||z<0)return false;int w=(turn%2==0?info.width():info.depth()),d=(turn%2==0?info.depth():info.width());
        return w<=width&&d<=depth&&x<=width-w&&z<=depth-d;
    }
    public static void choose(ServerPlayer p,HousingIntent intent){
        var session=SESSIONS.get(p.getUUID());if(session==null||!session.nonce.equals(intent.nonce()))return;
        SESSIONS.remove(p.getUUID());var site=session.site;long now=p.serverLevel().getGameTime();
        if(session.expires<now||!p.isAlive()||p.isSpectator()||p.level().dimension()!=Level.OVERWORLD
                ||p.distanceToSqr(Vec3.atBottomCenterOf(site.world(site.plot.steward())))>144)return;
        if(session.claimCost!=HousingConfig.CLAIM_COST.get()||session.buildCost!=HousingConfig.BUILD_COST.get()||!session.currency.equals(HousingConfig.CURRENCY.get())){offer(p,site,"费用配置已更新，请查看当前费用后再确认。");return;}
        var data=HousingSavedData.get(p.getServer());var title=data.titles.get(site.key());
        if(intent.claim()){
            if(title!=null){offer(p,site,"这块宅地已经有房契。");return;}
            if(!pay(p,HousingConfig.CLAIM_COST.get(),HousingConfig.CURRENCY.get())){offer(p,site,"灵石不足，宅地尚未领取。");return;}
            data.titles.put(site.key(),new HousingSavedData.Title(p.getUUID(),p.getGameProfile().getName()));data.setDirty();
            offer(p,site,"宅地已记在你名下。可以选择住宅模板，也可以保留空地自行搭建和种植。");return;
        }
        if(title==null||!title.owner.equals(p.getUUID())){offer(p,site,"建房需要这块宅地的房契。");return;}
        if(JOBS.containsKey(site.key())||!Set.of("empty","blocked").contains(title.status)){offer(p,site,"当前施工状态不接受新的建房请求。");return;}
        boolean resume=title.status.equals("blocked");HousingTemplates.Info info;
        try{info=HousingTemplates.info(resume?title.house:intent.house());}catch(IllegalArgumentException e){offer(p,site,"住宅模板无效。");return;}
        int turn=resume?title.turn:intent.turn(),x=resume?title.offsetX:intent.x(),z=resume?title.offsetZ:intent.z();
        if(!info.role().equals("player")||!fits(info,turn,x,z,site.plot.width(),site.plot.depth())){offer(p,site,"住宅超出宅地边界，请调整朝向或位置。");return;}
        var build=new HousingSavedData.Title(title.owner,title.ownerName);
        build.house=info.id();build.turn=turn;build.offsetX=x;build.offsetZ=z;build.status="building";build.cursor=resume?title.cursor:0;
        if(resume){data.titles.put(site.key(),build);data.setDirty();}
        start(p.serverLevel(),new Job(site,build,!resume,p.getUUID(),HousingConfig.BUILD_COST.get(),HousingConfig.CURRENCY.get(),now));
        offer(p,site,resume?"继续原来的施工，本次不再收费。":"正在加载施工范围并检查地面，检查通过后才会收取配置中的建房费用。");
    }
    private static void start(ServerLevel level,Job job){
        var info=HousingTemplates.info(job.title.house);int w=job.title.turn%2==0?info.width():info.depth(),d=job.title.turn%2==0?info.depth():info.width();
        BlockPos min=job.site.origin().offset(job.title.offsetX,0,job.title.offsetZ),max=min.offset(w-1,0,d-1);
        JOBS.put(job.site.key(),job);
        for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)for(int x=min.getX()>>4;x<=max.getX()>>4;x++){
            var chunk=new ChunkPos(x,z);job.chunks.add(chunk);level.getChunkSource().addRegionTicket(TICKET,chunk,2,job.site.key());
        }
    }
    private static BlockPos at(Job job,BlockPos local){var info=HousingTemplates.info(job.title.house);return job.site.origin().offset(job.title.offsetX,0,job.title.offsetZ).offset(HousingTemplates.rotate(local,info.width(),info.depth(),job.title.turn));}
    private static boolean natural(BlockState state,int y){
        if(state.isAir()||state.canBeReplaced()&&state.getFluidState().isEmpty())return true;
        return y<0&&(state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.DIRT)||state.is(Blocks.STONE)||state.is(Blocks.COARSE_DIRT)||state.is(Blocks.ROOTED_DIRT));
    }
    private static String preflight(ServerLevel level,Job job,HousingTemplates.Template template){
        if(!level.getServer().getWorldData().worldGenOptions().generateStructures())return "此世界已关闭结构生成。";
        var info=template.info();int w=job.title.turn%2==0?info.width():info.depth(),d=job.title.turn%2==0?info.depth():info.width();
        var origin=job.site.origin().offset(job.title.offsetX,0,job.title.offsetZ);
        if(!level.getEntities((Entity)null,new AABB(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+w,origin.getY()+info.height(),origin.getZ()+d),entity->entity.isAlive()&&!entity.isSpectator()).isEmpty())return "施工范围内有人或生物，请离开范围后重试。";
        for(var column:template.columns())for(int y=column.bottom();y<column.top();y++){
            BlockPos p=at(job,new BlockPos(column.x(),y,column.z()));BlockState previous=level.getBlockState(p);
            if(!level.isInWorldBounds(p)||!level.getWorldBorder().isWithinBounds(p))return "施工范围超出世界边界。";
            if(level.getBlockEntity(p)!=null||!natural(previous,y))return "施工范围已有方块或种植，未开始建造："+p.toShortString()+"。请移动住宅位置，或自行清理后重试。";
            if(!previous.isAir()&&HousingSavedData.get(level.getServer()).edits.getOrDefault(job.site.key(),Set.of()).contains(p.asLong()))return "施工范围保留了玩家放置的方块，未开始建造："+p.toShortString();
        }
        return null;
    }
    private static void finish(ServerLevel level,Job job,String error){
        JOBS.remove(job.site.key());for(var chunk:job.chunks)level.getChunkSource().removeRegionTicket(TICKET,chunk,2,job.site.key());
        var data=HousingSavedData.get(level.getServer());
        if(!job.unpaid||data.titles.get(job.site.key())==job.title){job.title.status=error==null?"built":"blocked";job.title.error=error==null?"":error;data.setDirty();}
        var owner=level.getServer().getPlayerList().getPlayer(job.title.owner);
        if(owner!=null)owner.displayClientMessage(Component.literal(error==null?"住宅已经建好，可以自由改建。剩余宅地保留给自行搭建和种植。":error),false);
        var session=owner==null?null:SESSIONS.get(owner.getUUID());
        if(session!=null&&session.site.key().equals(job.site.key())&&owner.distanceToSqr(Vec3.atBottomCenterOf(job.site.world(job.site.plot.steward())))<=144)offer(owner,job.site,error==null?"住宅已建好。":error);
    }
    private static void advance(ServerLevel level,Job job){
        if(level.getGameTime()>job.deadline){finish(level,job,"施工范围加载超时，进度已保留。");return;}
        for(var chunk:job.chunks)if(level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null)return;
        var template=HousingTemplates.get(job.title.house);var data=HousingSavedData.get(level.getServer());
        if(job.unpaid&&data.titles.get(job.site.key())!=job.title){
            var owner=level.getServer().getPlayerList().getPlayer(job.requester);
            if(owner==null||owner.serverLevel()!=level){finish(level,job,"本次建房尚未付款，已取消。");return;}
            String error=preflight(level,job,template);if(error!=null){finish(level,job,error);return;}
            if(!pay(owner,job.cost,job.currency)){finish(level,job,"建房灵石不足，尚未开始施工。");return;}
            data.titles.put(job.site.key(),job.title);data.setDirty();
        }
        int budget=HousingConfig.BLOCKS_PER_TICK.get();var rotation=HousingTemplates.rotation(job.title.turn);
        while(job.title.cursor<template.units().size()){
            var unit=template.units().get(job.title.cursor);if(unit.size()>budget)return;
            // 原生结构更新后同一单元重试仍然安全；外部命令写入的非模板方块会停止施工。
            for(var cell:unit){
                BlockPos p=at(job,cell.pos());BlockState target=cell.state().rotate(rotation),old=level.getBlockState(p);
                if(!old.equals(target)&&!natural(old,cell.pos().getY())){finish(level,job,"施工遇到新放置的方块，已停止并保留进度："+p.toShortString());return;}
                if(!old.equals(target)&&!level.isUnobstructed(target,p,net.minecraft.world.phys.shapes.CollisionContext.empty()))return;
            }
            CourtyardAssemblyBlock.beginEdit();
            try{for(var cell:unit){BlockPos p=at(job,cell.pos());BlockState target=cell.state().rotate(rotation);if(!level.getBlockState(p).equals(target)&&!level.setBlock(p,target,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))throw new IllegalStateException("施工写入失败 "+p);}}
            finally{CourtyardAssemblyBlock.endEdit();}
            job.title.cursor++;data.setDirty();budget-=unit.size();
        }
        for(var entry:template.blocks().entrySet())if(!entry.getValue().getFluidState().isEmpty()){
            BlockPos p=at(job,entry.getKey());var fluid=level.getFluidState(p);level.scheduleTick(p,fluid.getType(),fluid.getType().getTickDelay(level));
        }
        finish(level,job,null);
    }
    private static boolean locked(ServerLevel level,BlockPos pos){
        var data=HousingSavedData.get(level.getServer());
        for(var site:sites(level)){
            var t=data.titles.get(site.key());Job pending=JOBS.get(site.key());if(pending!=null)t=pending.title;
            if(t==null||!t.status.equals("building"))continue;
            var info=HousingTemplates.info(t.house);int w=t.turn%2==0?info.width():info.depth(),d=t.turn%2==0?info.depth():info.width();var p=site.origin().offset(t.offsetX,0,t.offsetZ);
            if(pos.getX()>=p.getX()&&pos.getX()<p.getX()+w&&pos.getZ()>=p.getZ()&&pos.getZ()<p.getZ()+d&&pos.getY()>=p.getY()-2&&pos.getY()<p.getY()+info.height())return true;
        }
        return false;
    }
    private static void changed(ServerLevel level,BlockPos pos){
        var data=HousingSavedData.get(level.getServer());
        for(var site:sites(level)){
            var origin=site.origin();var plot=site.plot;
            if(pos.getX()<origin.getX()||pos.getX()>=origin.getX()+plot.width()||pos.getZ()<origin.getZ()||pos.getZ()>=origin.getZ()+plot.depth()||pos.getY()<origin.getY()-2||pos.getY()>=origin.getY()+64)continue;
            if(data.edits.computeIfAbsent(site.key(),k->new HashSet<>()).add(pos.asLong()))data.setDirty();
        }
    }
    private static void breakBlock(BlockEvent.BreakEvent e){if(e.getLevel() instanceof ServerLevel l){if(locked(l,e.getPos()))e.setCanceled(true);else if(!e.isCanceled())changed(l,e.getPos());}}
    private static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.isCanceled()) return;
        var positions = event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots().stream().map(snapshot -> snapshot.getPos()).toList()
                : java.util.List.of(event.getPos());
        if (positions.stream().anyMatch(pos -> locked(level, pos))) {
            event.setCanceled(true);
        } else {
            positions.forEach(pos -> changed(level, pos));
        }
    }
    private static void rightClick(PlayerInteractEvent.RightClickBlock e){if(e.getLevel() instanceof ServerLevel l&&locked(l,e.getPos()))e.setCanceled(true);}
    private static void explode(ExplosionEvent.Detonate e){if(e.getLevel() instanceof ServerLevel l)e.getAffectedBlocks().removeIf(p->locked(l,p));}
    private static void tick(ServerTickEvent.Post e){
        ServerLevel level=e.getServer().overworld();if(level==null)return;
        if(e.getServer().getTickCount()%20==0){
            var data=HousingSavedData.get(e.getServer());
            for(var site:sites(level)){
                var title=data.titles.get(site.key());if(title!=null&&title.status.equals("building")&&!JOBS.containsKey(site.key()))start(level,new Job(site,title,false,title.owner,0,"",level.getGameTime()));
                BlockPos at=site.world(site.plot.steward());if(!level.isPositionEntityTicking(at))continue;
                Entity existing=level.getEntity(site.stewardId());NpcEntity npc;
                if(existing==null){npc=ModEntities.CULTIVATOR.get().create(level);if(npc==null)throw new IllegalStateException("无法创建宅地执事");
                    npc.setUUID(site.stewardId());npc.setPersistenceRequired();npc.setNoAi(true);npc.setInvulnerable(true);npc.setCustomName(Component.literal("宅地执事"));npc.setCustomNameVisible(true);
                    npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);if(!level.addFreshEntity(npc))throw new IllegalStateException("宅地执事加入失败");
                }else if(existing instanceof NpcEntity n)npc=n;else throw new IllegalStateException("宅地执事身份冲突");
                npc.setNoAi(true);npc.setInvulnerable(true);npc.setDeltaMovement(Vec3.ZERO);if(npc.distanceToSqr(Vec3.atBottomCenterOf(at))>.01)npc.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);
            }
            SESSIONS.entrySet().removeIf(entry->entry.getValue().expires<level.getGameTime());
        }
        for(var job:List.copyOf(JOBS.values()))try{advance(level,job);}catch(RuntimeException failure){LoggerFactory.getLogger(HousingRuntime.class).error("住宅施工失败 {}",job.site.key(),failure);finish(level,job,"施工发生错误，进度已保留，修复后可继续施工。");}
    }
    private static void commands(RegisterCommandsEvent e){e.getDispatcher().register(ModCommands.root().then(Commands.literal("housing").executes(c->{
        var data=HousingSavedData.get(c.getSource().getServer());for(var site:sites(c.getSource().getServer().overworld())){var t=data.titles.get(site.key());
            c.getSource().sendSuccess(()->Component.literal(site.city.bundle().displayName+"宅地：执事位于 "+site.world(site.plot.steward()).toShortString()+"；"+(t==null?"尚未领取":t.ownerName+"，"+t.status)),false);
        }return 1;
    })));}
    private static void stopped(ServerStoppedEvent e){SITES.clear();SESSIONS.clear();JOBS.clear();}
}
