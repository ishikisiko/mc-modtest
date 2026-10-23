package com.example.myvillage.wilderness.runtime;

import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.beast.BeastEntity;
import com.example.myvillage.entity.beast.DemonWolfEntity;
import com.example.myvillage.region.runtime.RegionCatalogLoader;
import com.example.myvillage.region.runtime.RegionPlacement;
import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimServerConfig;
import com.example.myvillage.sim.runtime.avatar.WildAvatarPlanner;
import com.example.myvillage.sim.runtime.avatar.WorldSimAvatars;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.wilderness.BeastBirth;
import com.example.myvillage.wilderness.WildernessRules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Natural wolf assignment and bounded read-only wilderness diagnostics; never loads a chunk. */
public final class WildernessRuntime {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private WildernessRuntime() { }
    public static void register() {
        WildernessRules.bundled();
        VanillaMonsterRealms.register();
        NeoForge.EVENT_BUS.addListener(WildernessRuntime::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(WildernessRuntime::onCommands);
        NeoForge.EVENT_BUS.addListener(WildernessRuntime::onServerStarted);
    }
    private static void onServerStarted(ServerStartedEvent event) {
        Set<String> catalog = RegionCatalogLoader.loadFromResourceManager(event.getServer().getResourceManager(), "myvillage")
                .catalog().stream().map(p -> p.id()).collect(Collectors.toSet());
        if (!catalog.equals(WildernessRules.bundled().regions().keySet())) {
            throw new IllegalStateException("Wilderness ranges must cover exactly the loaded region catalog");
        }
    }
    public static Optional<String> regionAt(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(Level.OVERWORLD)) return Optional.empty();
        var planned = com.example.myvillage.worldplan.runtime.WorldPlanRuntime.context(level,pos.getX(),pos.getZ());
        if(planned.isPresent())return Optional.of(planned.get().profileId());
        return RegionRuntimeService.regionAt(pos.getX(), pos.getZ())
                .filter(id -> WildernessRules.bundled().region(id).isPresent());
    }
    public static boolean checkWolfSpawnRules(EntityType<DemonWolfEntity> type, ServerLevelAccessor level,
                                               MobSpawnType reason, BlockPos pos, RandomSource random) {
        return regionAt(level.getLevel(), pos).isPresent()
                && Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }
    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof DemonWolfEntity beast)) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) return;
        ServerLevel level = event.getLevel().getLevel();
        BlockPos pos = beast.blockPosition();
        Optional<String> region = regionAt(level, pos);
        if (region.isEmpty()) { event.setSpawnCancelled(true); return; }
        // Entity RNG supplies one explicit input, exactly once, before this immutable birth is persisted.
        if (beast.wildernessBirth().isEmpty()) {
            var planned = com.example.myvillage.worldplan.runtime.WorldPlanRuntime.context(level,pos.getX(),pos.getZ());
            if(planned.isPresent() && !planned.get().core()) {
                var context=planned.get();
                RealmKey realm=WildernessRules.bundled().region(context.profileId()).orElseThrow().beast()
                        .sample(level.getSeed(),context.regionId(),beast.getRandom().nextLong());
                beast.assignWildernessBirth(new BeastBirth(realm,context.regionId(),pos.getX(),pos.getY(),pos.getZ(),true));
            } else assign(beast,region.get(),level.getSeed(),beast.getRandom().nextLong(),true);
        }
    }
    private static boolean assign(BeastEntity beast, String region, long seed, long input, boolean natural) {
        RealmKey realm = WildernessRules.bundled().region(region).orElseThrow().beast().sample(seed, region, input);
        BlockPos pos = beast.blockPosition();
        return beast.assignWildernessBirth(new BeastBirth(realm, region, pos.getX(), pos.getY(), pos.getZ(), natural));
    }
    private static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("myvillage").requires(s -> s.hasPermission(2))
                .then(Commands.literal("wilderness")
                        .then(Commands.literal("info").executes(c -> info(c.getSource(), BlockPos.containing(c.getSource().getPosition())))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(c -> info(c.getSource(), BlockPosArgument.getBlockPos(c, "pos")))))
                        .then(Commands.literal("sample").then(Commands.argument("region", StringArgumentType.word())
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(c -> sample(c.getSource(), StringArgumentType.getString(c, "region"),
                                                        LongArgumentType.getLong(c, "seed"), IntegerArgumentType.getInteger(c, "count")))))))
                        .then(Commands.literal("birth").then(Commands.argument("targets", EntityArgument.entities())
                                .executes(c -> {
                                    int n = 0;
                                    for (Entity e : EntityArgument.getEntities(c, "targets")) {
                                        if (n >= 64) break;
                                        if (e instanceof BeastEntity b) { birthInfo(c.getSource(), b); n++; }
                                    }
                                    return n;
                                })))
                        .then(Commands.literal("spawn_test").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(c -> spawnTest(c.getSource(), BlockPosArgument.getBlockPos(c, "pos"),
                                                LongArgumentType.getLong(c, "seed"))))))));
    }
    private static int info(CommandSourceStack source, BlockPos pos) {
        Optional<String> region = regionAt(source.getLevel(), pos);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", source.getLevel().dimension().location().toString());
        result.put("pos", List.of(pos.getX(), pos.getY(), pos.getZ()));
        result.put("region", region.orElse(""));
        com.example.myvillage.worldplan.runtime.WorldPlanRuntime.context(source.getLevel(),pos.getX(),pos.getZ())
                .ifPresent(c -> result.put("worldplanContext",c));
        result.put("rulesHash", WildernessRules.bundled().hash());
        result.put("settings", WildernessRules.bundled().rogues());
        region.ifPresent(id -> {
            WildernessRules.Region range = WildernessRules.bundled().region(id).orElseThrow();
            result.put("beastRange", List.of(range.beast().min(), range.beast().max()));
            result.put("rogueRange", List.of(range.rogue().min(), range.rogue().max()));
            boolean outer=com.example.myvillage.worldplan.runtime.WorldPlanRuntime.context(source.getLevel(),pos.getX(),pos.getZ()).map(c->!c.core()).orElse(false);
            Optional<WorldSim> sim = outer?Optional.empty():WorldSimRuntime.sim();
            result.put("eligibleRogues", sim.map(s -> WildAvatarPlanner.candidates(s.livingIn(id), id, range.rogue(), s.seed()).size()).orElse(0));
            result.put("projectedRogues", outer?0:WorldSimAvatars.wildCount(id));
        });
        result.put("avatarsTotal", WorldSimAvatars.totalCount());
        result.put("wildAvatarsTotal", WorldSimAvatars.wildCount(null));
        result.put("avatarsLimit", WorldSimServerConfig.maxAvatars());
        result.put("spawnDiagnostics", spawnDiagnostics(source.getLevel(), pos));
        List<Object> centers = new ArrayList<>();
        RegionRuntimeService.graph().ifPresent(g -> g.regions().forEach(r -> {
            int[] p = RegionPlacement.worldBlockFromGraph(r.posX(), r.posZ());
            centers.add(Map.of("region", r.id(), "x", p[0], "z", p[1]));
        }));
        result.put("centers", centers);
        send(source, "WILDERNESS_INFO", result);
        return 1;
    }
    /** Read the actual modified biome table only after its FULL chunk is already present. */
    private static Map<String, Object> spawnDiagnostics(ServerLevel level, BlockPos pos) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean loaded = level.getChunkSource().getChunkNow(Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16)) != null;
        result.put("loaded", loaded);
        if (!loaded) return result;
        var biome = level.getBiome(pos);
        result.put("biome", biome.unwrapKey().map(key -> key.location().toString()).orElse(""));
        result.put("difficulty", level.getDifficulty().name());
        result.put("isSpawningMonsters", level.getServer().isSpawningMonsters());
        result.put("skyLight", level.getBrightness(LightLayer.SKY, pos));
        result.put("blockLight", level.getBrightness(LightLayer.BLOCK, pos));
        result.put("skyDarken", level.getSkyDarken());
        result.put("localRawBrightness", level.getMaxLocalRawBrightness(pos));
        result.put("dayTime", level.getDayTime());
        result.put("minBuildHeight", level.getMinBuildHeight());
        result.put("surfaceHeight", level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()));
        result.put("naturalSpawningAllowed", level.isNaturalSpawningAllowed(pos));
        result.put("insideBorder", level.getWorldBorder().isWithinBounds(pos));
        result.put("spawnPos", level.getSharedSpawnPos().toShortString());
        var spawnState = level.getChunkSource().getLastSpawnState();
        if (spawnState != null) {
            result.put("spawnableChunks", spawnState.getSpawnableChunkCount());
            result.put("monsterCount", spawnState.getMobCategoryCounts().getInt(MobCategory.MONSTER));
        }
        List<Object> entries = new ArrayList<>();
        var spawns = biome.value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap();
        int wolfWeight = 0;
        for (var spawn : spawns) {
            if (spawn.type == ModEntities.DEMON_WOLF.get()) wolfWeight += spawn.getWeight().asInt();
            if (entries.size() < 64) entries.add(Map.of("entity", EntityType.getKey(spawn.type).toString(),
                    "weight", spawn.getWeight().asInt(), "minCount", spawn.minCount, "maxCount", spawn.maxCount));
        }
        result.put("monsterSpawnEntries", entries);
        result.put("monsterSpawnEntryCount", spawns.size());
        result.put("wolfSpawnWeight", wolfWeight);
        result.put("probeSeed", 0L);
        // Separate sources keep this query deterministic and never consume the world's spawning RNG.
        result.put("wolfRulesAllowed", checkWolfSpawnRules(ModEntities.DEMON_WOLF.get(), level, MobSpawnType.NATURAL, pos, RandomSource.create(0L)));
        result.put("vanillaMonsterRulesAllowed", Monster.checkMonsterSpawnRules(ModEntities.DEMON_WOLF.get(), level,
                MobSpawnType.NATURAL, pos, RandomSource.create(0L)));
        return result;
    }
    private static int sample(CommandSourceStack source, String id, long seed, int count) {
        Optional<WildernessRules.Region> region = WildernessRules.bundled().region(id);
        if (region.isEmpty()) { source.sendFailure(Component.literal("Unknown wilderness region: " + id)); return 0; }
        List<RealmKey> values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add(region.get().beast().sample(seed, id, i));
        send(source, "WILDERNESS_SAMPLE", Map.of("region", id, "seed", seed, "rulesHash", WildernessRules.bundled().hash(), "values", values));
        return count;
    }
    private static int spawnTest(CommandSourceStack source, BlockPos pos, long seed) {
        ServerLevel level = source.getLevel();
        Optional<String> region = regionAt(level, pos);
        if (region.isEmpty() || !level.isLoaded(pos)) {
            source.sendFailure(Component.literal("Diagnostic wolf requires a loaded position in a configured overworld region")); return 0;
        }
        DemonWolfEntity beast = ModEntities.DEMON_WOLF.get().create(level);
        if (beast == null) return 0;
        beast.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        if (!WorldSimAvatars.loadedArea(level, beast.getBoundingBox())
                || !level.getWorldBorder().isWithinBounds(beast.getBoundingBox()) || !level.noCollision(beast)) {
            source.sendFailure(Component.literal("Diagnostic wolf placement is obstructed")); return 0;
        }
        assign(beast, region.get(), seed, 0, false);
        if (!level.addFreshEntity(beast)) return 0;
        birthInfo(source, beast);
        return 1;
    }
    private static void birthInfo(CommandSourceStack source, BeastEntity beast) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", beast.getUUID().toString());
        result.put("birth", beast.wildernessBirth().orElse(null));
        result.put("health", beast.getHealth()); result.put("maxHealth", beast.getMaxHealth());
        result.put("attack", beast.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        send(source, "WILDERNESS_BIRTH", result);
    }
    private static void send(CommandSourceStack source, String prefix, Object value) {
        source.sendSuccess(() -> Component.literal(prefix + " " + JSON.toJson(value)), false);
    }
}
