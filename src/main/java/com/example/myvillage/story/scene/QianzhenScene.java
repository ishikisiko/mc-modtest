package com.example.myvillage.story.scene;

import com.example.myvillage.story.npc.QianzhenActors;
import com.example.myvillage.story.realm.QianzhenRealms;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Personal realms and legacy deployment. Reading a placement never loads chunks or replays templates. */
public final class QianzhenScene {
    private static final Logger LOG = LoggerFactory.getLogger(QianzhenScene.class);
    private static final ResourceLocation RESOURCE = ResourceLocation.parse("myvillage:story/qianzhen_scene.json");
    private static final Map<MinecraftServer, Assets> ASSETS = new WeakHashMap<>();
    private static boolean registered;
    // Conservative protection: unknown blocks, all block entities and all entities are rejected.
    // A block state cannot distinguish a player-placed dirt block from natural dirt.
    private static final Set<Block> GROUND = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT,
            Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.STONE, Blocks.DEEPSLATE,
            Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.SAND, Blocks.RED_SAND,
            Blocks.GRAVEL, Blocks.CLAY, Blocks.SNOW_BLOCK, Blocks.MUD, Blocks.BEDROCK);
    private static final Set<Block> VEGETATION = Set.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS,
            Blocks.FERN, Blocks.LARGE_FERN, Blocks.DEAD_BUSH, Blocks.SNOW,
            Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET,
            Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP,
            Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public record Placement(String id, BlockPos origin, String version) {}
    public record Target(String id, String block, BlockPos pos, BlockPos approach) {}
    private record Assets(SceneLayout layout, Map<String, StructureTemplate> templates,
                          Map<String, BlockState> states, Map<BlockPos, SceneLayout.Target> targets) {}
    private QianzhenScene() {}

    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(QianzhenScene::commands);
        NeoForge.EVENT_BUS.addListener(QianzhenScene::started);
        NeoForge.EVENT_BUS.addListener(QianzhenScene::stopping);
    }
    private static void started(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        Assets assets = assets(level); // Strict once-per-server load; /reload intentionally does not replace geometry.
        for (ServerLevel sceneLevel : event.getServer().getAllLevels()) validateSaved(sceneLevel);
    }
    public static void validateSaved(ServerLevel level) {
        Assets assets = assets(level);
        SceneDeploymentData data = SceneDeploymentData.get(level);
        SceneLayout deployed = data.geometry(assets.layout);
        if (data.phase() != SceneDeploymentData.Phase.EMPTY
                && (!data.id().equals(deployed.id()) || !data.version().equals(deployed.version())))
            throw new IllegalStateException("Qianzhen saved scene version differs from bundled resources; restore the matching jar/world backup; no templates were replayed");
        if (data.phase() == SceneDeploymentData.Phase.PLACING || data.phase() == SceneDeploymentData.Phase.FAILED)
            LOG.error("Qianzhen deployment is {}; refusing automatic replay. Restore the independent preview world from backup.", data.phase());
    }
    private static void stopping(ServerStoppingEvent event) { ASSETS.remove(event.getServer()); }

    public static Optional<Placement> at(ServerLevel level) {
        SceneDeploymentData data = SceneDeploymentData.get(level);
        if (data.phase() != SceneDeploymentData.Phase.COMPLETE || !data.dimension().equals(level.dimension().location().toString()))
            return Optional.empty();
        SceneLayout layout = data.geometry(assets(level).layout);
        if (!data.id().equals(layout.id()) || !data.version().equals(layout.version())) return Optional.empty();
        return Optional.of(new Placement(data.id(), data.origin(), data.version()));
    }
    public static Optional<BlockPos> position(ServerLevel level, String key) {
        return at(level).flatMap(placement -> Optional.ofNullable(layout(level).positions().get(key))
                .map(station -> world(placement.origin(), station.pos())));
    }
    public static Optional<BlockPos> approach(ServerLevel level, String key) {
        return at(level).flatMap(placement -> Optional.ofNullable(layout(level).positions().get(key))
                .map(station -> world(placement.origin(), station.approach())));
    }
    /** Exact clicked position and full expected state; removing/emptying/replacing a prop invalidates it. */
    public static Optional<Target> target(ServerLevel level, BlockPos pos) {
        return at(level).flatMap(placement -> {
            Assets assets = assets(level);
            SceneLayout.Target target = layout(level).targets().stream()
                    .filter(t -> local(t.pos()).equals(pos.subtract(placement.origin()))).findFirst().orElse(null);
            if (target == null || !level.hasChunkAt(pos) || level.getBlockState(pos) != parseState(level, target.state()))
                return Optional.empty();
            return Optional.of(new Target(target.id(), target.block(), pos.immutable(), world(placement.origin(), target.approach())));
        });
    }
    public static boolean regulated(ServerLevel level) { return at(level).isPresent() && SceneDeploymentData.get(level).regulated(); }
    /** Scene-local fact; never alters the core, inscriptions, samples or another player's evidence. */
    public static boolean regulate(ServerLevel level) {
        if (at(level).isEmpty()) return false;
        SceneDeploymentData data = SceneDeploymentData.get(level);
        if (data.regulated()) return true;
        Optional<BlockPos> core = position(level, "spring_core");
        if (core.isEmpty() || target(level, core.get()).isEmpty()) return false;
        data.markRegulated();
        try { data.checkpoint(level); }
        catch (IOException failure) {
            data.undoRegulated(); LOG.error("Qianzhen shared regulation could not be persisted", failure); return false;
        }
        BlockPos p = core.get();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX()+.5, p.getY()+1.1, p.getZ()+.5, 12, .35, .35, .35, .02);
        return true;
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("myvillage").then(Commands.literal("qianzhen")
                .requires(source -> source.hasPermission(2)).then(Commands.literal("deploy")
                        .then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel(); BlockPos origin = BlockPosArgument.getBlockPos(ctx, "origin");
                            try {
                                boolean created = deploy(level, origin);
                                ctx.getSource().sendSuccess(() -> Component.literal((created ? "阡镇已部署：" : "阡镇已在此部署，未重放模板：")
                                        + level.dimension().location()+" "+origin.toShortString()), true);
                                return 1;
                            } catch (RuntimeException failure) {
                                ctx.getSource().sendFailure(Component.literal("阡镇部署拒绝："+failure.getMessage())); return 0;
                            }
                        })))));
        event.getDispatcher().register(Commands.literal("qianzhen").then(Commands.literal("where").executes(ctx -> {
            ServerLevel level = ctx.getSource().getLevel();
            if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                var own = QianzhenRealms.existing(player);
                var progress = player.getExistingData(com.example.myvillage.story.StoryAttachments.PROGRESS)
                        .orElse(com.example.myvillage.story.StoryProgress.EMPTY);
                if (own.isEmpty() && !progress.accepted()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("第一章在你的个人秘境中。使用 /qianzhen enter 进入，/qianzhen leave 返回。"), false);
                    return 1;
                }
                level = own.orElse(level);
            }
            SceneDeploymentData data = SceneDeploymentData.get(level);
            if (data.phase() != SceneDeploymentData.Phase.COMPLETE) {
                ctx.getSource().sendSuccess(() -> Component.literal("阡镇尚未完成部署。"), false); return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("阡镇："+data.dimension()+" "+data.origin().toShortString()), false); return 1;
        })));
    }

    /** Return false for an identical completed request. Failed or interrupted reservations never write again. */
    static boolean deploy(ServerLevel level, BlockPos origin) {
        if (QianzhenRealms.privateLevel(level)) throw new IllegalStateException("个人秘境由主人入口初始化，不能手动部署。");
        Assets assets = assets(level); SceneDeploymentData data = SceneDeploymentData.get(level);
        if (data.phase() != SceneDeploymentData.Phase.EMPTY) {
            if (at(level).filter(placement -> placement.origin().equals(origin)).isPresent()) return false;
            throw new IllegalStateException("本存档已有部署记录（"+data.phase()+"）；不能覆盖或重放。请使用独立新世界或恢复备份。");
        }
        if (WorldPlanRuntime.generator(level).isPresent()) throw new IllegalStateException("冻结 worldplan 世界不能部署独立阡镇，请使用独立平坦世界。");
        // Actor/geography/capacity failures must leave EMPTY and must not even generate terrain chunks.
        QianzhenActors.validateDeployment(level, assets.layout, origin);
        preflight(level, origin, assets.layout);
        data.begin(level, assets.layout, origin);
        try {
            data.checkpoint(level); // A failed durable reservation aborts before any world mutation.
            for (SceneLayout.Site site : assets.layout.sites()) {
                BlockPos p = world(origin, site.origin());
                StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true);
                if (!assets.templates.get(site.id()).placeInWorld(level, p, p, settings, level.random, FLAGS))
                    throw new IllegalStateException("结构放置失败："+site.id());
            }
            for (SceneLayout.Cell cell : assets.layout.roads()) {
                BlockPos p = world(origin, cell.pos()); BlockState state = assets.states.get(cell.state());
                if (level.getBlockState(p) != state && !level.setBlock(p, state, FLAGS))
                    throw new IllegalStateException("道路放置失败："+p.toShortString());
            }
            for (SceneLayout.Target target : assets.layout.targets())
                if (level.getBlockState(world(origin, target.pos())) != assets.states.get(target.state()))
                    throw new IllegalStateException("调查目标放置不一致："+target.id());
            // Persist chunks before marking complete; crashes never trigger automatic template repair/replay.
            level.getChunkSource().save(true);
            data.complete(); data.checkpoint(level);
        } catch (Exception failure) {
            data.failed();
            try { data.checkpoint(level); } catch (IOException saveFailure) { failure.addSuppressed(saveFailure); }
            LOG.error("Qianzhen deployment aborted; scene is locked against replay at {}", origin, failure);
            throw new IllegalStateException("部署中断，已锁定以免重复覆盖；请恢复独立世界备份。原因："+failure.getMessage(), failure);
        }
        // Fixed cross-module API; actors remain owned by WorldSim, never by this SavedData.
        QianzhenActors.ensure(level);
        return true;
    }

    private static void preflight(ServerLevel level, BlockPos origin, SceneLayout layout) {
        BlockPos min = world(origin, layout.bounds().min()), max = world(origin, layout.bounds().max());
        if (min.getY() < level.getMinBuildHeight() || max.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max))
            throw new IllegalStateException("部署超出世界高度或边界。");
        // Explicit operator action may generate these chunks; no tickets or force-load state are retained.
        for (SceneLayout.Site site : layout.sites()) {
            BlockPos a = world(origin, site.bounds().min()), b = world(origin, site.bounds().max());
            for (BlockPos p : BlockPos.betweenClosed(a, b)) checkNatural(level, p, origin.getY());
        }
        for (SceneLayout.Cell cell : layout.roads()) checkNatural(level, world(origin, cell.pos()), origin.getY());
        AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX()+1, max.getY()+1, max.getZ()+1);
        // Include non-collidable entities (items, armor stands, etc.), not just living collision checks.
        for (var entity : level.getAllEntities())
            if (!entity.isRemoved() && entity.getBoundingBox().intersects(box))
                throw new IllegalStateException("范围内有实体："+entity.getType().toShortString()+"；先移出部署区。");
    }
    private static void checkNatural(ServerLevel level, BlockPos pos, int groundY) {
        BlockState state = level.getBlockState(pos);
        boolean allowed = pos.getY() == groundY ? GROUND.contains(state.getBlock())
                : state.isAir() || VEGETATION.contains(state.getBlock());
        if (state.hasBlockEntity() || !allowed)
            throw new IllegalStateException("需要完整平坦天然地面及净空；受保护方块 "+BuiltInRegistries.BLOCK.getKey(state.getBlock())+" @ "+pos.toShortString());
    }

    public static SceneLayout layout(ServerLevel level) { return SceneDeploymentData.get(level).geometry(assets(level).layout); }
    public static String phase(ServerLevel level) { return SceneDeploymentData.get(level).phase().name(); }

    /** Fresh owner-only worlds, built in bounded stages. PLACING never silently replays after a crash. */
    public static PersonalBuild beginPersonal(ServerLevel level, BlockPos origin, int roadsPerTick) {
        if (!QianzhenRealms.privateLevel(level)) throw new IllegalArgumentException("Not a private realm");
        var a=assets(level);var data=SceneDeploymentData.get(level);
        if(data.phase()!=SceneDeploymentData.Phase.EMPTY)throw new IllegalStateException("秘境已有创建记录："+data.phase()+"；不会重置或覆盖。");
        QianzhenActors.validateDeployment(level,a.layout,origin);
        data.begin(level,a.layout,origin);
        try { data.checkpoint(level); }
        catch(IOException failure){throw new IllegalStateException("无法保存秘境创建记录",failure);}
        return new PersonalBuild(level,origin,a,data,roadsPerTick);
    }
    public static final class PersonalBuild {
        private final ServerLevel level;
        private final BlockPos origin;
        private final Assets a;
        private final SceneDeploymentData data;
        private final int roadBudget;
        private int siteIndex,roadIndex;
        private PersonalBuild(ServerLevel level,BlockPos origin,Assets a,SceneDeploymentData data,int roadBudget) {
            this.level=level;this.origin=origin;this.a=a;this.data=data;this.roadBudget=roadBudget;
        }
        public boolean step() {
            try {
                if(siteIndex<a.layout.sites().size()) {
                    var site=a.layout.sites().get(siteIndex++);
                    for(BlockPos cell:BlockPos.betweenClosed(world(origin,site.bounds().min()),world(origin,site.bounds().max())))
                        checkNatural(level,cell,origin.getY());
                    BlockPos p=world(origin,site.origin());
                    if(!a.templates.get(site.id()).placeInWorld(level,p,p,new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true),level.random,FLAGS))
                        throw new IllegalStateException("秘境建筑放置失败："+site.id());
                    return false;
                }
                int end=Math.min(a.layout.roads().size(),roadIndex+roadBudget);
                while(roadIndex<end) {
                    var cell=a.layout.roads().get(roadIndex++);BlockPos p=world(origin,cell.pos());
                    checkNatural(level,p,origin.getY());BlockState state=a.states.get(cell.state());
                    if(level.getBlockState(p)!=state&&!level.setBlock(p,state,FLAGS))throw new IllegalStateException("秘境道路放置失败");
                }
                if(roadIndex<a.layout.roads().size())return false;
                for(var target:java.util.stream.Stream.concat(a.layout.targets().stream(),a.layout.facilities().stream()).toList())if(level.getBlockState(world(origin,target.pos()))!=a.states.get(target.state()))
                    throw new IllegalStateException("秘境调查目标不一致："+target.id());
                level.getChunkSource().save(true);
                data.complete();data.checkpoint(level);
                QianzhenActors.ensure(level);
                return true;
            } catch(Exception failure) {
                data.failed();
                try {data.checkpoint(level);} catch(IOException saveFailure){failure.addSuppressed(saveFailure);}
                throw new IllegalStateException("秘境创建中断，已保留现场并锁定，不能重放。",failure);
            }
        }
    }

    private static Assets assets(ServerLevel level) {
        return ASSETS.computeIfAbsent(level.getServer(), ignored -> load(level));
    }
    private static Assets load(ServerLevel level) {
        try {
            SceneLayout layout;
            try (var in = level.getServer().getResourceManager().getResourceOrThrow(RESOURCE).open();
                 var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                layout = SceneLayout.parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
            Map<String, StructureTemplate> templates = new HashMap<>(); Map<String, BlockState> states = new HashMap<>();
            for (SceneLayout.Site site : layout.sites()) {
                ResourceLocation id = ResourceLocation.parse(site.template());
                ResourceLocation path = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structure/"+id.getPath()+".nbt");
                byte[] bytes;
                try (var in = level.getServer().getResourceManager().getResourceOrThrow(path).open()) { bytes = in.readAllBytes(); }
                if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(site.sha256()))
                    throw new IllegalStateException("Qianzhen template digest differs: "+path);
                var tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
                if (!tag.getList("entities", Tag.TAG_COMPOUND).isEmpty()) throw new IllegalStateException("Qianzhen templates cannot own entities");
                for (var raw : tag.getList("palette", Tag.TAG_COMPOUND)) {
                    var entry = (net.minecraft.nbt.CompoundTag)raw;
                    if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(entry.getString("Name"))))
                        throw new IllegalStateException("Missing required Qianzhen block: "+entry.getString("Name"));
                    var properties = entry.getCompound("Properties");
                    String suffix = properties.getAllKeys().stream().sorted()
                            .map(key -> key+"="+properties.getString(key)).collect(java.util.stream.Collectors.joining(","));
                    parseState(level, entry.getString("Name")+(suffix.isEmpty() ? "" : "["+suffix+"]"));
                }
                StructureTemplate template = level.getStructureManager().readStructure(tag);
                if (!template.getSize().equals(local(site.size()))) throw new IllegalStateException("Qianzhen template size differs: "+path);
                templates.put(site.id(), template);
            }
            for (SceneLayout.Cell cell : layout.roads()) states.computeIfAbsent(cell.state(), state -> parseState(level, state));
            Map<BlockPos, SceneLayout.Target> targets = new HashMap<>();
            for (SceneLayout.Target target : java.util.stream.Stream.concat(layout.targets().stream(),layout.facilities().stream()).toList()) {
                states.computeIfAbsent(target.state(), state -> parseState(level, state)); targets.put(local(target.pos()), target);
            }
            return new Assets(layout, Map.copyOf(templates), Map.copyOf(states), Map.copyOf(targets));
        } catch (IOException | NoSuchAlgorithmException failure) { throw new IllegalStateException("Cannot load production Qianzhen scene", failure); }
    }
    private static BlockState parseState(ServerLevel level, String text) {
        try { return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), text, false).blockState(); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) { throw new IllegalArgumentException("Invalid Qianzhen state: "+text, failure); }
    }
    private static BlockPos local(SceneLayout.Pos pos) { return new BlockPos(pos.x(), pos.y(), pos.z()); }
    private static BlockPos world(BlockPos origin, SceneLayout.Pos pos) { return origin.offset(pos.x(), pos.y(), pos.z()); }
}
