package com.example.myvillage.story.entrance;

import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.story.StoryRuntime;
import com.example.myvillage.story.npc.QianzhenActors;
import com.example.myvillage.story.scene.QianzhenScene;
import com.example.myvillage.story.scene.SceneLayout;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** New-save entrance only; never moves spawn, awards items, accepts a chapter or invents a person. */
public final class QianzhenEntrance {
    private static final Logger LOG=LoggerFactory.getLogger(QianzhenEntrance.class);
    private static final ResourceLocation RESOURCE=ResourceLocation.parse("myvillage:story/qianzhen_entrance.json");
    private static final Set<MinecraftServer> FRESH=Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<MinecraftServer> WRITING=Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<UUID> HINTED=new HashSet<>(),INSIDE=new HashSet<>();
    private static boolean registered;
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE;
    private static final Set<Block> NATURAL=Set.of(Blocks.GRASS_BLOCK,Blocks.DIRT,Blocks.COARSE_DIRT,Blocks.ROOTED_DIRT,
            Blocks.PODZOL,Blocks.MYCELIUM,Blocks.STONE,Blocks.DEEPSLATE,Blocks.GRANITE,Blocks.DIORITE,Blocks.ANDESITE,
            Blocks.SAND,Blocks.RED_SAND,Blocks.GRAVEL,Blocks.CLAY,Blocks.SNOW_BLOCK,Blocks.MUD,Blocks.PACKED_MUD,
            Blocks.CALCITE,Blocks.TUFF,Blocks.DRIPSTONE_BLOCK,Blocks.POINTED_DRIPSTONE,Blocks.MOSS_BLOCK,
            Blocks.ICE,Blocks.PACKED_ICE,Blocks.BLUE_ICE,Blocks.TERRACOTTA,Blocks.WATER,Blocks.LAVA);
    private static final Set<Block> FOUNDATION=Set.of(Blocks.GRASS_BLOCK,Blocks.DIRT,Blocks.COARSE_DIRT,Blocks.ROOTED_DIRT,
            Blocks.PODZOL,Blocks.MYCELIUM,Blocks.STONE,Blocks.DEEPSLATE,Blocks.GRANITE,Blocks.DIORITE,Blocks.ANDESITE,
            Blocks.CLAY,Blocks.MUD,Blocks.PACKED_MUD,Blocks.CALCITE,Blocks.TUFF,Blocks.DRIPSTONE_BLOCK,Blocks.TERRACOTTA);
    private QianzhenEntrance() {}
    public static void register() {
        if(registered)return;registered=true;
        // receiveCanceled is needed because planned V4 already owns and cancels spawn placement.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,QianzhenEntrance::creatingSpawn);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,QianzhenEntrance::started);
        NeoForge.EVENT_BUS.addListener(QianzhenEntrance::stopping);
        NeoForge.EVENT_BUS.addListener(QianzhenEntrance::commands);
        NeoForge.EVENT_BUS.addListener(QianzhenEntrance::notice);
        NeoForge.EVENT_BUS.addListener(QianzhenEntrance::tick);
        NeoForge.EVENT_BUS.addListener(QianzhenEntrance::login);
    }
    private static void creatingSpawn(LevelEvent.CreateSpawnPosition event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!level.dimension().equals(Level.OVERWORLD))return;
        var root=level.getServer().getWorldPath(LevelResource.ROOT);
        // Integrated servers write level.dat BEFORE first creation. The uninitialized-world event plus
        // absence of region/player/scene files is the freshness proof, not level.dat absence.
        if(!Files.exists(EntranceData.path(level))&&!Files.exists(root.resolve("data/myvillage_qianzhen_scene.dat"))
                &&!hasRegionFiles(root.resolve("region"))&&!hasRegionFiles(root.resolve("playerdata")))FRESH.add(level.getServer());
    }
    private static boolean hasRegionFiles(java.nio.file.Path folder) {
        if(!Files.isDirectory(folder))return false;
        try(var files=Files.list(folder)){return files.anyMatch(Files::isRegularFile);}
        catch(java.io.IOException failure){return true;} // no proof of freshness, no terrain permission
    }
    private static String resource(ServerLevel level) {
        try(var stream=level.getServer().getResourceManager().getResourceOrThrow(RESOURCE).open();
                var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            StringBuilder s=new StringBuilder();char[] b=new char[2048];int n;
            while((n=reader.read(b))!=-1){s.append(b,0,n);if(s.length()>16_384)throw new IllegalArgumentException("入口配置超限");}
            EntranceRules.read(new StringReader(s.toString()));return s.toString();
        }catch(java.io.IOException bad){throw new IllegalStateException("缺少阡镇入口配置",bad);}
    }
    private static void started(ServerStartedEvent event) {
        ServerLevel level=event.getServer().overworld();EntranceData data=EntranceData.get(level);
        boolean fresh=FRESH.remove(event.getServer());
        if(data.phase!=EntranceData.Phase.EMPTY)return; // partial receipts never become retry permission
        if(QianzhenScene.hasDeploymentRecord(level))return; // existing scene always wins; no terrain writes
        if(!fresh)return; // old worlds are deliberately untouched
        if(!event.getServer().getPlayerList().getPlayers().isEmpty())throw new IllegalStateException("首次入口初始化时已有玩家在线");
        String raw=resource(level);EntranceRules rules=EntranceRules.read(new StringReader(raw));
        if(level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource&&!rules.initializeFlatWorlds()) {
            unavailable(level,data,"超平坦世界保留管理员显式部署；普通生存及修仙大陆新世界才自动安排入口。",0);return;
        }
        SceneLayout layout=QianzhenScene.layout(level);BlockPos spawn=level.getSharedSpawnPos();
        String region=RegionRuntimeService.regionAt(spawn.getX(),spawn.getZ()).orElse("");
        var queried=new HashSet<EntrancePlan.Cell>();long[] blockQueries={0};String reason="没有满足地形、地区和预留约束的入口候选";
        for(var candidate:rules.candidates()) {
            EntrancePlan plan;
            Map<EntrancePlan.Cell,EntranceColumnPlan> columns;
            try {
                plan=EntrancePlan.create(rules,spawn.getX(),spawn.getY()-1,spawn.getZ(),candidate,
                        layout.bounds().min().x(),layout.bounds().min().z(),layout.bounds().max().x(),layout.bounds().max().z());
                spatial(level,plan,rules,region);
                var requested=new HashSet<>(queried);requested.addAll(plan.chunks(rules));
                if(requested.size()>rules.maxChunks())throw new IllegalStateException("全部候选的累计区块查询预算已满");
                queried.addAll(requested);
                var origin=new BlockPos(plan.originX(),plan.groundY(),plan.originZ());
                QianzhenScene.validateInitialEntrance(level,origin); // cast and frozen receipt before chunks or writes
                for(var chunk:plan.chunks(rules))level.getChunk(chunk.x(),chunk.z());
                columns=preflight(level,plan,rules,layout,blockQueries);
            }catch(RuntimeException rejected){reason=Objects.toString(rejected.getMessage(),rejected.getClass().getSimpleName());continue;}
            try {
                data.begin(plan,raw);data.checkpoint(level); // durable reservation before first world mutation
                WRITING.add(event.getServer());
                prepare(level,plan,rules,columns);
                QianzhenScene.deployInitialEntrance(level,new BlockPos(plan.originX(),plan.groundY(),plan.originZ()));
                road(level,plan,rules);
                placeNotice(level,data);
                level.getChunkSource().save(true);
                data.phase=EntranceData.Phase.COMPLETE;data.setDirty();data.checkpoint(level);
                LOG.info("QIANZHEN_ENTRANCE complete spawn={} origin={} chunks={} route={}",spawn,
                        new BlockPos(plan.originX(),plan.groundY(),plan.originZ()),queried.size(),plan.route().size());return;
            }catch(Exception failed) {
                data.phase=EntranceData.Phase.FAILED;data.reason=shortReason(failed);data.setDirty();
                try{data.checkpoint(level);}catch(java.io.IOException persist){failed.addSuppressed(persist);}
                LOG.error("阡镇入口首次部署失败；已锁定，不会在重启覆盖或重试",failed);return;
            }finally{WRITING.remove(event.getServer());}
        }
        unavailable(level,data,reason,queried.size());
    }
    private static void unavailable(ServerLevel level,EntranceData data,String reason,int queriedChunks) {
        data.phase=EntranceData.Phase.UNAVAILABLE;data.reason=reason.length()>512?reason.substring(0,512):reason;data.setDirty();
        try{data.checkpoint(level);}catch(java.io.IOException failed){throw new IllegalStateException("入口不可用记录无法保存",failed);}
        LOG.warn("QIANZHEN_ENTRANCE unavailable reason={} chunks={}; no block writes",reason,queriedChunks);
    }
    private static String shortReason(Exception e) {String s=Objects.toString(e.getMessage(),e.getClass().getSimpleName());return s.substring(0,Math.min(512,s.length()));}
    public static boolean authorizesInitialDeployment(ServerLevel level,BlockPos origin) {
        var d=EntranceData.get(level);return WRITING.contains(level.getServer())&&d.phase==EntranceData.Phase.BUILDING&&d.plan!=null
                &&level.dimension().equals(Level.OVERWORLD)&&level.getServer().getPlayerList().getPlayers().isEmpty()
                &&origin.equals(new BlockPos(d.plan.originX(),d.plan.groundY(),d.plan.originZ()));
    }
    private static void spatial(ServerLevel level,EntrancePlan p,EntranceRules r,String region) {
        if(region.isEmpty()||WorldSimRuntime.sim().isEmpty())throw new IllegalStateException("出生地区或命簿尚未就绪");
        var b=p.scene();
        if(!level.getWorldBorder().isWithinBounds(new BlockPos(b.minX(),p.groundY(),b.minZ()))
                ||!level.getWorldBorder().isWithinBounds(new BlockPos(b.maxX(),p.groundY(),b.maxZ())))throw new IllegalStateException("入口超出世界边界");
        var plan=com.example.myvillage.worldplan.runtime.WorldPlanRuntime.generator(level).map(g->g.plan()).orElse(null);
        if(plan!=null&&plan.intersectsCity(b.minX(),b.minZ(),b.maxX(),b.maxZ()))throw new IllegalStateException("入口碰到冻结城市预留");
        for(var sect:WorldSimRuntime.sim().orElseThrow().sects(true)) {
            var gateArea=com.example.myvillage.sect.SectGenerator.worldgenBuildArea(
                    com.example.myvillage.sect.SectGenerator.baseFor(new BlockPos(sect.gateX(),p.groundY(),sect.gateZ())));
            if(b.minX()<=gateArea.x0()+gateArea.width()-1&&b.maxX()>=gateArea.x0()
                    &&b.minZ()<=gateArea.z0()+gateArea.depth()-1&&b.maxZ()>=gateArea.z0())
                throw new IllegalStateException("入口碰到命簿宗门完整生成范围");
            if(b.intersects(sect.gateX(),sect.gateZ(),r.gateClearance()))throw new IllegalStateException("入口碰到命簿山门预留");
            for(var c:p.roadCells(r.roadHalfWidth()+1))if((c.x()>=gateArea.x0()&&c.x()<gateArea.x0()+gateArea.width()
                    &&c.z()>=gateArea.z0()&&c.z()<gateArea.z0()+gateArea.depth())||Math.abs((long)c.x()-sect.gateX())<=r.gateClearance()&&Math.abs((long)c.z()-sect.gateZ())<=r.gateClearance())
                throw new IllegalStateException("步道碰到命簿山门预留");
        }
        for(int x=b.minX();x<=b.maxX();x++)for(int z=b.minZ();z<=b.maxZ();z++)
            if(!RegionRuntimeService.regionAt(x,z).orElse("").equals(region))throw new IllegalStateException("场景越出出生地区边界");
        for(var c:p.roadCells(r.roadHalfWidth()+1)) {
            if(!RegionRuntimeService.regionAt(c.x(),c.z()).orElse("").equals(region))throw new IllegalStateException("步道越出出生地区边界");
            if(plan!=null&&(plan.reserved(c.x(),p.groundY(),c.z())||plan.intersectsCity(c.x(),c.z(),c.x(),c.z())))throw new IllegalStateException("步道碰到冻结城市预留");
            if(!level.getWorldBorder().isWithinBounds(new BlockPos(c.x(),p.groundY(),c.z())))throw new IllegalStateException("步道越出世界边界");
        }
    }
    private static Map<EntrancePlan.Cell,EntranceColumnPlan> preflight(ServerLevel level,EntrancePlan p,EntranceRules r,SceneLayout layout,long[] queries) {
        var columns=new LinkedHashMap<EntrancePlan.Cell,EntranceColumnPlan>();
        long[] writes={layout.roads().size()};
        for(var site:layout.sites())writes[0]+=(long)site.size().x()*site.size().y()*site.size().z();
        writes[0]+=p.roadCells(r.roadHalfWidth()).size()*3L+1;
        if(p.groundY()<level.getMinBuildHeight()||p.groundY()+layout.bounds().max().y()>=level.getMaxBuildHeight())
            throw new IllegalStateException("入口高度预算不足");
        var b=p.scene();
        for(int x=b.minX();x<=b.maxX();x++)for(int z=b.minZ();z<=b.maxZ();z++)columns.put(new EntrancePlan.Cell(x,z),checkColumn(level,x,z,p.groundY(),r,layout.bounds().max().y(),queries,writes));
        for(var c:p.roadCells(r.roadHalfWidth()+1))if(!b.contains(c.x(),c.z()))columns.put(c,checkColumn(level,c.x(),c.z(),p.groundY(),r,r.clearance(),queries,writes));
        // Reserve the apply-time put() reads and the scene natural preflight too, in the same
        // query cap; template/road volumes are already included in writes. No budget is raised.
        if(queries[0]+writes[0]>r.maxBlockQueries())throw new IllegalStateException("入口预检及预计落地读写超过累计查询预算");
        for(var e:level.getAllEntities())if(!e.isRemoved()&&occupies(p,r,e.getX(),e.getZ())&&!(e instanceof Mob mob&&!mob.isPersistenceRequired()&&!mob.hasCustomName()
                &&!(mob instanceof com.example.myvillage.entity.npc.NpcEntity)&&!mob.isLeashed()))
            throw new IllegalStateException("入口候选存在需保留的实体");
        for(var c:p.roadCells(r.roadHalfWidth()+1))for(var site:layout.sites()) {
            var a=site.bounds();int x=c.x()-p.originX(),z=c.z()-p.originZ();
            if(x>=a.min().x()&&x<=a.max().x()&&z>=a.min().z()&&z<=a.max().z())
                throw new IllegalStateException("入口步道会覆盖场景建筑");
        }
        var anchor=r.roadAnchor();
        if(layout.roads().stream().noneMatch(c->c.pos().x()==anchor.x()&&c.pos().z()==anchor.z()&&c.pos().y()==0&&!c.state().equals("minecraft:air")))
            throw new IllegalStateException("入口锚点不在场景现有道路");
        return java.util.Collections.unmodifiableMap(columns);
    }
    private static EntranceColumnPlan checkColumn(ServerLevel level,int x,int z,int ground,EntranceRules r,int clear,long[] queries,long[] writes) {
        int surface=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
        var plan=EntranceColumnPlan.plan(surface,ground,clear,level.getMinBuildHeight(),level.getMaxBuildHeight(),
                r.maxCut(),r.maxFill(),r.foundationDepth(),y->{
                    if(++queries[0]>r.maxBlockQueries())throw new IllegalStateException("入口累计方块查询预算已满");
                    BlockPos at=new BlockPos(x,y,z);BlockState s=level.getBlockState(at);
                    if(s.hasBlockEntity())return EntranceColumnPlan.Kind.PROTECTED;
                    if(!s.getFluidState().isEmpty())return EntranceColumnPlan.Kind.FLUID;
                    if(s.isAir())return EntranceColumnPlan.Kind.AIR;
                    if(!natural(s))return EntranceColumnPlan.Kind.PROTECTED;
                    if(stable(s)&&s.isFaceSturdy(level,at,Direction.UP))return EntranceColumnPlan.Kind.FOUNDATION;
                    return EntranceColumnPlan.Kind.DECORATION;
                });
        writes[0]+=plan.writeBudget();
        if(writes[0]>r.maxBlockWrites())throw new IllegalStateException("入口累计方块放置预算已满");
        return plan;
    }
    private static boolean stable(BlockState s) {
        return FOUNDATION.contains(s.getBlock())||s.is(BlockTags.BASE_STONE_OVERWORLD)
                ||s.is(BlockTags.COAL_ORES)||s.is(BlockTags.IRON_ORES)||s.is(BlockTags.COPPER_ORES)||s.is(BlockTags.GOLD_ORES)
                ||s.is(BlockTags.REDSTONE_ORES)||s.is(BlockTags.LAPIS_ORES)||s.is(BlockTags.DIAMOND_ORES)||s.is(BlockTags.EMERALD_ORES);
    }
    private static boolean natural(BlockState s) {
        return s.isAir()||s.canBeReplaced()||NATURAL.contains(s.getBlock())||s.is(BlockTags.LOGS)||s.is(BlockTags.LEAVES)
                ||s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.DIRT)||s.is(BlockTags.FLOWERS)
                ||s.is(BlockTags.COAL_ORES)||s.is(BlockTags.IRON_ORES)||s.is(BlockTags.COPPER_ORES)||s.is(BlockTags.GOLD_ORES)
                ||s.is(BlockTags.REDSTONE_ORES)||s.is(BlockTags.LAPIS_ORES)||s.is(BlockTags.DIAMOND_ORES)||s.is(BlockTags.EMERALD_ORES);
    }
    private static boolean occupies(EntrancePlan p,EntranceRules r,double x,double z) {
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);if(p.scene().contains(bx,bz))return true;
        for(var c:p.route())if(Math.abs((long)c.x()-bx)<=r.roadHalfWidth()+1&&Math.abs((long)c.z()-bz)<=r.roadHalfWidth()+1)return true;
        return false;
    }
    private static void prepare(ServerLevel level,EntrancePlan p,EntranceRules r,Map<EntrancePlan.Cell,EntranceColumnPlan> columns) {
        // Only disposable naturally spawned bodies in this first-creation reservation; never items, NPCs or players.
        var remove=new ArrayList<net.minecraft.world.entity.Entity>();
        for(var e:level.getAllEntities())if(occupies(p,r,e.getX(),e.getZ()))remove.add(e);
        remove.forEach(net.minecraft.world.entity.Entity::discard);
        for(var entry:columns.entrySet())column(level,entry.getKey().x(),entry.getKey().z(),entry.getValue());
    }
    private static void road(ServerLevel level,EntrancePlan p,EntranceRules r) {
        var road=p.roadCells(r.roadHalfWidth());
        // Fill the full lane, then add an outer guard rail except at its two open ends.
        var interior=p.roadCells(r.roadHalfWidth()-1);
        for(var c:road)put(level,new BlockPos(c.x(),p.groundY(),c.z()),Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        var first=p.route().getFirst();var last=p.route().getLast();
        var rails=new HashSet<EntrancePlan.Cell>();
        for(var c:road)if(!interior.contains(c)&&Math.abs(c.x()-first.x())>r.roadHalfWidth()&&Math.abs(c.x()-last.x())>r.roadHalfWidth())rails.add(c);
        for(var c:rails) {
            var fence=Blocks.OAK_FENCE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FenceBlock.NORTH,rails.contains(new EntrancePlan.Cell(c.x(),c.z()-1)))
                    .setValue(net.minecraft.world.level.block.FenceBlock.SOUTH,rails.contains(new EntrancePlan.Cell(c.x(),c.z()+1)))
                    .setValue(net.minecraft.world.level.block.FenceBlock.WEST,rails.contains(new EntrancePlan.Cell(c.x()-1,c.z())))
                    .setValue(net.minecraft.world.level.block.FenceBlock.EAST,rails.contains(new EntrancePlan.Cell(c.x()+1,c.z())));
            put(level,new BlockPos(c.x(),p.groundY()+1,c.z()),fence);
        }
        for(int i=r.lampSpacing();i<p.route().size()-r.roadHalfWidth();i+=r.lampSpacing()) {
            var c=p.route().get(i);BlockPos at=new BlockPos(c.x(),p.groundY()+1,c.z()+r.roadHalfWidth());
            if(!interior.contains(new EntrancePlan.Cell(at.getX(),at.getZ()))) {
                if(!rails.contains(new EntrancePlan.Cell(at.getX(),at.getZ())))put(level,at,Blocks.OAK_FENCE.defaultBlockState());
                put(level,at.above(),Blocks.TORCH.defaultBlockState());
            }
        }
    }
    private static void column(ServerLevel level,int x,int z,EntranceColumnPlan plan) {
        // The preflight found and retained a real contiguous dry base. Fill every intervening cell,
        // including logs/leaves/air/cave gaps; WORLD_SURFACE is only the cached clearing ceiling.
        for(int y=plan.foundationTop()+1;y<=plan.ground();y++)put(level,new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState());
        put(level,new BlockPos(x,plan.ground(),z),Blocks.GRASS_BLOCK.defaultBlockState());
        for(int y=plan.ground()+1;y<=plan.clearTop();y++)put(level,new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
    }
    private static void put(ServerLevel level,BlockPos pos,BlockState state) {
        if(level.getBlockState(pos)!=state&&!level.setBlock(pos,state,FLAGS))throw new IllegalStateException("入口放置失败 @ "+pos.toShortString());
    }
    private static void placeNotice(ServerLevel level,EntranceData d) {
        BlockPos pos=new BlockPos(d.notice.x(),d.noticeY,d.notice.z());put(level,pos,Blocks.OAK_SIGN.defaultBlockState());
        if(!(level.getBlockEntity(pos) instanceof SignBlockEntity sign))throw new IllegalStateException("入口木牌实体缺失");
        writeSign(sign,d.rules);
    }
    private static void writeSign(SignBlockEntity sign,EntranceRules rules) {
        SignText text=new SignText();for(int i=0;i<4;i++)text=text.setMessage(i,Component.literal(rules.signLines().get(i)));
        sign.setText(text,true);sign.setText(text,false);sign.setWaxed(true);sign.setChanged();
        if(sign.getLevel()!=null)sign.getLevel().sendBlockUpdated(sign.getBlockPos(),sign.getBlockState(),sign.getBlockState(),Block.UPDATE_CLIENTS);
    }
    public static boolean discovered(ServerPlayer p) {return EntranceData.get(p.serverLevel()).discovered.contains(p.getUUID());}
    public static boolean tracking(ServerPlayer p) {return EntranceData.get(p.serverLevel()).tracking.contains(p.getUUID());}
    public static Optional<BlockPos> destination(ServerLevel level) {return QianzhenActors.position(level,"guide").or(()->QianzhenScene.position(level,"guide"));}
    private static boolean active(ServerLevel level,EntranceData d) {
        return level.dimension().equals(Level.OVERWORLD)&&(d.phase==EntranceData.Phase.COMPLETE||d.phase==EntranceData.Phase.REUSED)&&QianzhenScene.at(level).isPresent();
    }
    private static void notice(PlayerInteractEvent.RightClickBlock event) {
        if(event.getHand()!=InteractionHand.MAIN_HAND||!(event.getEntity() instanceof ServerPlayer p)||p.isSpectator()||!p.isAlive())return;
        var d=EntranceData.get(p.serverLevel());if(!active(p.serverLevel(),d)||d.notice==null)return;
        BlockPos at=new BlockPos(d.notice.x(),d.noticeY,d.notice.z());
        if(!at.equals(event.getPos())||!p.serverLevel().hasChunkAt(at)||!(p.serverLevel().getBlockEntity(at) instanceof SignBlockEntity)
                ||!p.canInteractWithBlock(at,0))return;
        Vec3 eye=p.getEyePosition(),hit=event.getHitVec().getLocation();
        var trace=p.serverLevel().clip(new ClipContext(eye,hit.add(hit.subtract(eye).normalize().scale(.01)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
        if(trace.getType()!=HitResult.Type.BLOCK||!trace.getBlockPos().equals(at))return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        d.discover(p.getUUID());offer(p,d);StoryRuntime.sync(p);
    }
    private static void offer(ServerPlayer p,EntranceData d) {
        p.sendSystemMessage(Component.literal(d.rules.recruitment()));
        p.sendSystemMessage(Component.literal(d.phase==EntranceData.Phase.COMPLETE?d.rules.safety():"这是旧场景的招募木牌；没有自动铺路，请自行准备行程。"));
        p.sendSystemMessage(Component.literal("[记下药庐方向]").withStyle(s->s.withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/qianzhen recruitment track"))));
    }
    private static void tick(ServerTickEvent.Post event) {
        var level=event.getServer().overworld();var d=EntranceData.get(level);
        if(!active(level,d)||d.rules==null||level.getGameTime()%d.rules.passTicks()!=0)return;
        var present=new HashSet<UUID>();
        for(var p:event.getServer().getPlayerList().getPlayers()) {
            present.add(p.getUUID());boolean inside=onPath(p,d);
            if(inside&&INSIDE.add(p.getUUID()))p.sendSystemMessage(Component.literal(d.rules.enteredProtection()));
            if(!inside&&INSIDE.remove(p.getUUID()))p.sendSystemMessage(Component.literal(d.rules.leftProtection()));
            if(p.level()!=level||d.notice==null||discovered(p)||HINTED.contains(p.getUUID()))continue;
            BlockPos at=new BlockPos(d.notice.x(),d.noticeY,d.notice.z());
            if(level.hasChunkAt(at)&&level.getBlockEntity(at) instanceof SignBlockEntity&&p.distanceToSqr(Vec3.atCenterOf(at))<=(double)d.rules.hintRadius()*d.rules.hintRadius()) {
                HINTED.add(p.getUUID());p.sendSystemMessage(Component.literal(d.rules.nearbyHint()));
            }
        }
        INSIDE.retainAll(present);HINTED.retainAll(present);
    }
    private static boolean onPath(ServerPlayer p,EntranceData d) {
        if(p.isSpectator()||!p.isAlive()||d.phase!=EntranceData.Phase.COMPLETE||d.plan==null||p.level()!=p.getServer().overworld())return false;
        BlockPos feet=p.blockPosition();return d.plan.onRoad(feet.getX(),feet.getY(),feet.getZ(),d.rules)
                &&p.serverLevel().hasChunkAt(feet.below())&&p.serverLevel().getBlockState(feet.below()).isFaceSturdy(p.serverLevel(),feet.below(),Direction.UP);
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        var d=EntranceData.get(p.serverLevel());
        if(d.phase==EntranceData.Phase.UNAVAILABLE||d.phase==EntranceData.Phase.FAILED||d.phase==EntranceData.Phase.BUILDING)
            p.sendSystemMessage(Component.literal("本世界没有可用的阡镇招募入口（"+d.phase+"）："+d.reason+"。生存照常；入口不会在重启自动重建，请服主检查部署记录。"));
    }
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("qianzhen").then(Commands.literal("recruitment")
                .executes(c->{var p=c.getSource().getPlayerOrException();var d=EntranceData.get(p.serverLevel());
                    if(!discovered(p)){c.getSource().sendFailure(Component.literal("你还没有亲自读到招募消息。"));return 0;}offer(p,d);return 1;})
                .then(Commands.literal("track").executes(c->{var p=c.getSource().getPlayerOrException();var d=EntranceData.get(p.serverLevel());
                    if(!discovered(p)||!active(p.serverLevel(),d)){c.getSource().sendFailure(Component.literal("尚未发现有效招募木牌。"));return 0;}
                    d.track(p.getUUID(),true);var at=destination(p.serverLevel()).orElseThrow();
                    p.sendSystemMessage(Component.literal("已记下阡镇路亭程砚的位置："+at.toShortString()+"。到场交谈，再自行决定是否求职。"));StoryRuntime.sync(p);return 1;}))
                .then(Commands.literal("stop").executes(c->{var p=c.getSource().getPlayerOrException();var d=EntranceData.get(p.serverLevel());
                    if(discovered(p))d.track(p.getUUID(),false);StoryRuntime.sync(p);return 1;}))));
        event.getDispatcher().register(Commands.literal("myvillage").then(Commands.literal("qianzhen").requires(s->s.hasPermission(2))
                .then(Commands.literal("entrance").then(Commands.literal("status").executes(c->{
                    var level=c.getSource().getServer().overworld();var d=EntranceData.get(level);
                    c.getSource().sendSuccess(()->Component.literal("QIANZHEN_ENTRANCE "+status(level,d)),false);return 1;}))
                .then(Commands.literal("bind_notice").then(Commands.argument("pos",BlockPosArgument.blockPos()).executes(c->{
                    var level=c.getSource().getLevel();var d=EntranceData.get(level);BlockPos at=BlockPosArgument.getBlockPos(c,"pos");
                    if((d.phase!=EntranceData.Phase.EMPTY&&!(d.phase==EntranceData.Phase.UNAVAILABLE&&d.plan==null))||!level.dimension().equals(Level.OVERWORLD)||QianzhenScene.at(level).isEmpty()
                            ||!level.hasChunkAt(at)||!(level.getBlockEntity(at) instanceof SignBlockEntity sign)) {
                        c.getSource().sendFailure(Component.literal("只可为已有完整阡镇绑定一块已放好的主世界木牌；已有入口或部署失败时不能替换。"));return 0;}
                    String raw=resource(level);var r=EntranceRules.read(new StringReader(raw));
                    if(at.distManhattan(destination(level).orElseThrow())>r.maxRouteLength()) {
                        c.getSource().sendFailure(Component.literal("木牌离程砚过远；请安排近处的实际步行入口。"));return 0;}
                    d.phase=EntranceData.Phase.REUSED;d.rules=r;d.frozenRules=raw;d.notice=new EntrancePlan.Cell(at.getX(),at.getZ());d.noticeY=at.getY();d.setDirty();
                    try{d.checkpoint(level);}catch(java.io.IOException bad){throw new IllegalStateException("木牌绑定不能保存",bad);}
                    writeSign(sign,r);c.getSource().sendSuccess(()->Component.literal("已复用现有阡镇并绑定木牌；未放置建筑或道路。"),true);return 1;
                }))))));
    }
    private static String status(ServerLevel level,EntranceData d) {
        var o=new com.google.gson.JsonObject();o.addProperty("phase",d.phase.name());o.addProperty("reason",d.reason);
        o.addProperty("dimension",Level.OVERWORLD.location().toString());
        o.addProperty("scene_version",QianzhenScene.at(level).map(QianzhenScene.Placement::version).orElse(""));
        if(d.notice!=null)o.add("notice",coordinates(new BlockPos(d.notice.x(),d.noticeY,d.notice.z())));
        if(d.plan!=null) {
            o.add("origin",coordinates(new BlockPos(d.plan.originX(),d.plan.groundY(),d.plan.originZ())));
            o.addProperty("route_length",d.plan.route().size());o.addProperty("chunk_count",d.plan.chunks(d.rules).size());
        }
        destination(level).ifPresent(p->o.add("guide",coordinates(p)));return o.toString();
    }
    private static com.google.gson.JsonArray coordinates(BlockPos p) {
        var a=new com.google.gson.JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;
    }
    private static void stopping(ServerStoppingEvent e) {FRESH.remove(e.getServer());WRITING.remove(e.getServer());HINTED.clear();INSIDE.clear();}
}
