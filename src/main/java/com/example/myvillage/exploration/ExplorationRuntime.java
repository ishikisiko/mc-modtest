package com.example.myvillage.exploration;

import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.example.myvillage.wilderness.WildernessRules;
import com.example.myvillage.wilderness.WildernessRules.RealmRange;
import com.example.myvillage.wilderness.runtime.WildernessRuntime;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.Gson;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Read-only location/guide queries. Never teleports, locks a portal, mutates progress or loads a chunk. */
public final class ExplorationRuntime {
    private static final Logger LOG = LoggerFactory.getLogger(ExplorationRuntime.class);
    private static final Gson JSON = new Gson();
    public record Context(ExplorationRules.Location location, String region, String profile,
                          RealmRange recommended, RealmRange encounters, String reward) { }
    private record Notice(String key, long tick) { }
    private static final Map<UUID, Notice> NOTICES = new HashMap<>();
    private static final Map<UUID, Integer> TIPS = new HashMap<>();
    private ExplorationRuntime() { }

    public static void register() {
        ExplorationRules.get();
        NeoForge.EVENT_BUS.addListener(ExplorationRuntime::validateRegistries);
        NeoForge.EVENT_BUS.addListener(ExplorationRuntime::commands);
        NeoForge.EVENT_BUS.addListener(ExplorationRuntime::tick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> { NOTICES.remove(e.getEntity().getUUID()); TIPS.remove(e.getEntity().getUUID()); });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> { NOTICES.clear(); TIPS.clear(); });
    }

    private static void validateRegistries(ServerStartedEvent event) {
        var rules = ExplorationRules.get(); var registry = event.getServer().registryAccess().registryOrThrow(Registries.STRUCTURE);
        var biomes = event.getServer().registryAccess().registryOrThrow(Registries.BIOME);
        for (var l : rules.locations()) {
            for (String id : l.structures()) if (!registry.containsKey(ResourceLocation.parse(id))) throw new IllegalStateException("Unknown exploration structure " + id);
            for (String id : l.biomes()) if (!biomes.containsKey(ResourceLocation.parse(id))) throw new IllegalStateException("Unknown exploration biome " + id);
        }
        for (var pool : rules.pools().values()) for (var r : pool.entries()) {
            var item = ResourceLocation.parse(r.item());
            if (!BuiltInRegistries.ITEM.containsKey(item)) throw new IllegalStateException("Unknown exploration item " + item);
            if (!r.technique().isEmpty()) {
                var manual = com.example.myvillage.item.TechniqueManualItem.manualFor(event.getServer().registryAccess(), ResourceLocation.parse(r.technique()));
                if (manual.isEmpty() || !manual.is(BuiltInRegistries.ITEM.get(item))) throw new IllegalStateException("Invalid exploration manual " + r);
            }
        }
        for (String id : rules.advancements().values()) if (event.getServer().getAdvancements().get(ResourceLocation.parse(id)) == null)
            throw new IllegalStateException("Unknown guide advancement " + id);
    }

    /** Use the event's worldgen accessor, not its backing server, while chunks are being populated. */
    public static Optional<Context> at(ServerLevelAccessor world, BlockPos pos, String special) {
        var rules = ExplorationRules.get();
        var chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.STRUCTURE_REFERENCES, false);
        if (chunk == null) return Optional.empty();
        String dimension = world.getLevel().dimension().location().toString();
        String biome = world.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("");
        var structures = structuresAt(world, pos);
        var location = rules.locations().stream().filter(l -> !l.contextOnly() && l.dimension().equals(dimension)
                && pos.getY() >= l.minY() && pos.getY() <= l.maxY()
                && (l.structures().isEmpty() || l.structures().stream().anyMatch(structures::contains))
                && (l.biomes().isEmpty() || l.biomes().contains(biome))).findFirst().orElse(null);
        if (special != null) {
            var candidate = rules.location(special);
            if (candidate.dimension().equals(dimension) && (location == null || candidate.priority() >= location.priority())) location = candidate;
        }
        if (location == null) return Optional.empty();
        var profile = location.regional() ? WildernessRuntime.regionAt(world.getLevel(), pos) : Optional.<String>empty();
        var regional = profile.flatMap(p -> WildernessRules.bundled().region(p)).map(WildernessRules.Region::beast).orElse(null);
        String region = profile.orElse("");
        if (profile.isPresent()) region = WorldPlanRuntime.context(world.getLevel(), pos.getX(), pos.getZ()).map(c -> c.regionId()).orElse(region);
        RealmRange recommended = rules.floor(location.recommended(), regional);
        RealmRange encounters = rules.floor(location.encounters(), regional);
        // End is guidance-only in V1, even when a future regional source is added.
        return Optional.of(new Context(location, region, profile.orElse(""), recommended, encounters,
                location.reward().equals("none") ? "none" : rules.rewardPool(location.reward(), recommended.max())));
    }

    /** The vanilla StructureManager can load referenced starts. Only inspect starts already available here. */
    public static Set<String> structuresAt(ServerLevelAccessor world, BlockPos pos) {
        var chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.STRUCTURE_REFERENCES, false);
        if (chunk == null) return Set.of();
        var result = new HashSet<String>(); var registry = world.registryAccess().registryOrThrow(Registries.STRUCTURE);
        chunk.getAllReferences().forEach((structure, references) -> {
            for (long ref : references) {
                var startPos = new ChunkPos(ref);
                if (!world.hasChunk(startPos.x, startPos.z)) continue;
                var startChunk = world.getChunk(startPos.x, startPos.z, ChunkStatus.STRUCTURE_STARTS, false);
                StructureStart start = startChunk == null ? null : startChunk.getStartForStructure(structure);
                if (start != null && start.isValid() && start.getBoundingBox().isInside(pos)) {
                    var id = registry.getKey(structure); if (id != null) result.add(id.toString());
                }
            }
        });
        return Set.copyOf(result);
    }

    public static Optional<Context> current(ServerPlayer player) {
        ServerLevel world = player.serverLevel(); BlockPos pos = player.blockPosition();
        String special = world.getRaidAt(pos) != null ? ExplorationRules.get().birth("EVENT").orElse(null) : null;
        var context = at(world, pos, special);
        if (context.isPresent() && context.get().location().id().equals("trial")
                && (player.hasEffect(MobEffects.TRIAL_OMEN) || nearbyOminous(world, pos)))
            return at(world, pos, ExplorationRules.get().birth("OMINOUS_TRIAL").orElse(null));
        return context;
    }

    private static boolean nearbyOminous(ServerLevel world, BlockPos pos) {
        int radius = ExplorationRules.get().settings().contextRadius();
        for (int x = (pos.getX() - radius) >> 4; x <= (pos.getX() + radius) >> 4; x++)
            for (int z = (pos.getZ() - radius) >> 4; z <= (pos.getZ() + radius) >> 4; z++) {
                var chunk = world.getChunkSource().getChunkNow(x, z); if (chunk == null) continue;
                for (var block : chunk.getBlockEntities().values()) if (block instanceof TrialSpawnerBlockEntity trial
                        && trial.getBlockPos().distSqr(pos) <= (double) radius * radius && trial.getTrialSpawner().isOminous()) return true;
            }
        return false;
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) return;
        var settings = ExplorationRules.get().settings(); if (player.tickCount % settings.checkTicks() != 0) return;
        var context = current(player); if (context.isEmpty()) return;
        var c = context.get(); long now = player.serverLevel().getServer().overworld().getGameTime();
        String key = player.level().dimension().location() + "/" + c.region() + "/" + c.location().id();
        Notice previous = NOTICES.get(player.getUUID());
        // Keep the last announced key while cooling down: a change is announced once it remains relevant.
        if (previous != null && (previous.key().equals(key) || now - previous.tick() < settings.noticeTicks())) return;
        NOTICES.put(player.getUUID(), new Notice(key, now));
        player.sendSystemMessage(summary(c));
        player.sendSystemMessage(prepare(c));
    }

    public static Component realmText(RealmKey key) {
        if (key.realmId().equals("myvillage:mortal")) return Component.translatable("exploration.myvillage.mortal");
        if (key.realmId().equals("myvillage:qi_refining")) return Component.translatable("exploration.myvillage.qi", key.stage() + 1);
        return Component.translatable("exploration.myvillage.realm_stage", Component.translatable("cultivation.realm." + key.realmId().replace(':', '.')),
                Component.translatable("exploration.myvillage.stage." + key.stage()));
    }
    public static Component rangeText(RealmRange range) {
        return range.min().equals(range.max()) ? realmText(range.min())
                : Component.translatable("exploration.myvillage.range", realmText(range.min()), realmText(range.max()));
    }
    public static Component summary(Context c) { return Component.translatable("exploration.myvillage.notice", Component.translatable("exploration.myvillage.location." + c.location().id()), rangeText(c.recommended())); }
    public static Component prepare(Context c) { return Component.translatable("exploration.myvillage.prepare." + c.location().id()); }

    public static RealmKey playerRealm(ServerPlayer player) {
        var profile = CultivationService.getProfile(player);
        try {
            return StatsRules.bundled().playerKey(profile.realmId().toString(), profile.stageId().toString());
        } catch (IllegalArgumentException legacyDefinition) {
            // Custom/legacy cultivation definitions retain their saved profile. Advice can fall
            // back to the baseline without turning a harmless NPC conversation into an exception.
            return StatsRules.bundled().realmKey("myvillage:mortal", 0);
        }
    }
    public static Set<String> progress(ServerPlayer player) {
        var result = new HashSet<String>(); var profile = CultivationService.getProfile(player);
        if (profile.spiritualRoot().isPresent()) result.add("awakened");
        if (profile.learnedTechniques().containsKey(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)) result.add("initiated");
        ExplorationRules.get().advancements().forEach((flag, id) -> {
            var advancement = player.getServer().getAdvancements().get(ResourceLocation.parse(id));
            if (advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone()) result.add(flag);
        }); return Set.copyOf(result);
    }
    public static void guide(ServerPlayer player, Component speaker, boolean tipOnly) {
        var next = ExplorationRules.get().next(playerRealm(player), progress(player));
        if (!tipOnly) {
            player.sendSystemMessage(Component.translatable("exploration.myvillage.guide.title", speaker));
            player.sendSystemMessage(next == null ? Component.translatable("exploration.myvillage.guide.complete")
                    : Component.translatable("exploration.myvillage.guide.next", Component.translatable("exploration.myvillage.guide." + next.id())));
        }
        if (next != null) {
            int tip = TIPS.merge(player.getUUID(), 1, (a,b) -> a == Integer.MAX_VALUE ? 1 : a + b) - 1;
            String key = next.tips().get(Math.floorMod(tip, next.tips().size()));
            player.sendSystemMessage(Component.translatable("exploration.myvillage.guide.tip", Component.translatable("exploration.myvillage.tip." + key)));
        }
        player.sendSystemMessage(Component.translatable("exploration.myvillage.guide.more").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/exploration guide tips"))));
        LOG.info("EXPLORATION_GUIDE player={} next={} tipOnly={}", player.getUUID(), next == null ? "complete" : next.id(), tipOnly);
    }

    private static void commands(RegisterCommandsEvent event) {
        var command = Commands.literal("exploration")
                .executes(c -> { var player = c.getSource().getPlayerOrException(); current(player).ifPresentOrElse(ctx -> {
                    player.sendSystemMessage(summary(ctx)); player.sendSystemMessage(prepare(ctx));
                    player.sendSystemMessage(Component.translatable("exploration.myvillage.advice_only"));
                }, () -> player.sendSystemMessage(Component.translatable("exploration.myvillage.none"))); return 1; })
                .then(Commands.literal("guide").executes(c -> { var p = c.getSource().getPlayerOrException(); guide(p, Component.translatable("exploration.myvillage.guide.label"), false); return 1; })
                        .then(Commands.literal("where").executes(c -> {
                            var sim = com.example.myvillage.sim.runtime.WorldSimRuntime.sim().orElse(null); int count = 0;
                            if (sim != null) for (var site : sim.lifeSites()) {
                                var assigned = sim.townGuide(site.id());
                                if (assigned.isEmpty()) continue;
                                var life = sim.life(assigned.get()); var person = sim.person(assigned.get());
                                if (life.isEmpty() || person.isEmpty() || !life.get().role().equals("guide")) continue;
                                c.getSource().sendSuccess(() -> Component.translatable("exploration.myvillage.guide.position", person.get().name(), site.name(), life.get().x(), life.get().y(), life.get().z()), false); count++;
                            }
                            if (count == 0) c.getSource().sendFailure(Component.translatable("exploration.myvillage.guide.not_ready")); return count;
                        }))
                        .then(Commands.literal("tips").executes(c -> { var p = c.getSource().getPlayerOrException(); guide(p, Component.empty(), true); return 1; })))
                .then(Commands.literal("catalogue").executes(c -> { for (var l : ExplorationRules.get().locations()) c.getSource().sendSuccess(() -> summary(new Context(l, "", "", l.recommended(), l.encounters(), l.reward())), false); return 1; }))
                .then(Commands.literal("info").requires(s -> s.hasPermission(2)).executes(c -> {
                    var context = at(c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()), null);
                    c.getSource().sendSuccess(() -> Component.literal("EXPLORATION_INFO " + JSON.toJson(context.orElse(null))), false); return 1;
                })).build();
        // Existing /myvillage administrators retain their permission boundary. The standalone
        // read-only command is available to ordinary survival players as well.
        event.getDispatcher().getRoot().addChild(command);
        event.getDispatcher().register(Commands.literal("myvillage").then(command));
    }
}
