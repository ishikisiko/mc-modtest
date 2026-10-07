package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.sect.SectGenerator;
import com.example.myvillage.sect.SectMountain;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.WorldSimServerConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Framed auto-realization of sect gates near a player (P4-lite, player sect entry slice 1). Every
 * {@value #PASS_TICKS} ticks, while the ledger is active, avatars are enabled and the server config
 * {@code avatars.auto_realize_gates} is on (read on use; off, nothing is queued, a running build
 * finishes, and the admin {@code world sect <id> build} works either way), an active sect whose gate
 * is not realized and not given up this session and lies within
 * {@code rules.player.gates.realize_radius} (planar) of an overworld player becomes a candidate; the
 * nearest one (ties to the smaller id, {@link GateRealizePlan#pick}) is built, one compound at a time:
 *
 * <ol>
 *   <li><b>checked</b>: the chunks of the worldgen-style build area (site plus mountain margin,
 *       {@link SectGenerator#worldgenBuildArea}) that are loaded now are looked at for players'
 *       traces (below); chunks not loaded are left to the second look;</li>
 *   <li><b>queued</b>: those chunks get a non-persistent region ticket and load in the background;
 *       meanwhile the generator's own surface of the area ({@link SectGenerator#generatorSurface},
 *       {@code WORLD_SURFACE_WG}) is sampled a few milliseconds per tick. Chunks still missing after
 *       {@value #LOAD_TIMEOUT_TICKS} ticks fail the build ({@code chunks_not_loaded}); nothing loads
 *       synchronously. Once all are in, every chunk is looked at for players' traces again;</li>
 *   <li><b>started</b>: the anchor's y is the generator's surface at the gate, and the plan, the
 *       natural surface and the derived mountain are computed once from that surface
 *       ({@link SectGenerator#prepare(ServerLevel, long, String, BlockPos, SectMountain.NaturalHeight)})
 *       with the same seed and variant as the build command, so the blocks of a broken-off earlier
 *       build do not lift the compound when it is retried;</li>
 *   <li><b>clips</b>: each tick {@code clips_per_tick} chunk clips run the realizer restricted to
 *       their chunk ({@link SectGenerator.FramedSite#realizeClip}, the worldgen slicing);</li>
 *   <li><b>done</b>: tickets released, the {@link GateRealizations} record written, the gate marked
 *       realized, the ledger save dirtied and the avatars told ({@link WorldSimAvatars#gateChanged}).</li>
 * </ol>
 *
 * <p><b>Players' traces.</b> The automatic build never levels what players made. A chunk of the
 * build area keeps it away when the chunk has been inhabited (players nearby,
 * {@code LevelChunk.getInhabitedTime}) for more than {@code rules.player.gates.inhabited_ticks_max}
 * ticks, holds any block entity of a mod other than {@code minecraft} and {@code myvillage}, or holds
 * more than {@code rules.player.gates.player_block_entities_max} vanilla block entities. Vanilla block
 * entities that worldgen places on its own are not counted: spawners, trial spawners, vaults, bee
 * nests and hives, suspicious sand and gravel, sculk sensors, catalysts and shriekers, and any
 * container still holding its unrolled loot table (dungeon, temple and ruin chests, decorated
 * pots). The decision is {@link GateRealizePlan#playerPresence}. A gate kept away logs
 * {@code state=skipped reason=inhabited|player_blocks chunk=<cx> <cz>}, tells the players within the
 * radius ({@code message.myvillage.world.gate.skipped}), and joins the failed set
 * ({@code world gates retry} clears it).
 *
 * <p>A player leaving does not stop a build. A failure releases the tickets, leaves the gate
 * unrealized (no record) and is not retried this server session. Every step logs one
 * {@code GATE_REALIZE sect=<id> state=...} line (the capture scripts read them).
 */
public final class GateRealizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GateRealizer.class);
    static final int PASS_TICKS = 20;
    /** Longest wait for the build area's chunks to load in the background; then the build fails. */
    static final int LOAD_TIMEOUT_TICKS = 1200;
    /** A {@code state=clip} line every this many clips, and for the last one. */
    static final int LOG_EVERY_CLIPS = 8;
    /** Time per tick spent sampling the generator's surface (it runs the terrain noise per column). */
    private static final long SAMPLE_BUDGET_NANOS = 8_000_000L;
    /** Ticket distance 1: the build chunks block-ticking, their neighbours loaded (light and shapes at the edges). */
    private static final int TICKET_DISTANCE = 1;
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("myvillage_gate_realize", Comparator.comparingLong(ChunkPos::toLong));
    static final String MESSAGE_KEY = "message.myvillage.world.gate.";
    /** Vanilla block entity types worldgen places by itself (registry paths under {@code minecraft:}). */
    private static final Set<String> WORLDGEN_BLOCK_ENTITIES = Set.of("mob_spawner", "trial_spawner", "vault",
            "beehive", "brushable_block", "sculk_sensor", "calibrated_sculk_sensor", "sculk_catalyst",
            "sculk_shrieker");

    private static final class Job {
        final WorldSim sim;
        final int sectId;
        final String sectName;
        final int gateX;
        final int gateZ;
        final long seed;
        final String variant;
        final SectGenerator.BuildArea area;
        final List<GateRealizePlan.ChunkClip> clips;
        final List<ChunkPos> tickets = new ArrayList<>();
        /** The generator's surface of the build area, column (i, j) at {@code i * depth + j}. */
        final int[] surface;
        int sampled;
        int loadTicks;
        BlockPos anchor;
        SectGenerator.FramedSite site;
        int next;
        long startNanos;

        Job(WorldSim sim, SectView sect, long seed, String variant, SectGenerator.BuildArea area,
            List<GateRealizePlan.ChunkClip> clips) {
            this.sim = sim;
            this.sectId = sect.id();
            this.sectName = sect.name();
            this.gateX = sect.gateX();
            this.gateZ = sect.gateZ();
            this.seed = seed;
            this.variant = variant;
            this.area = area;
            this.clips = clips;
            this.surface = new int[area.width() * area.depth()];
        }
    }

    /** A chunk where players left a trace, and which trace. */
    private record Trace(int chunkX, int chunkZ, String reason) {
    }

    private static Job job;
    private static final Set<Integer> FAILED = new TreeSet<>();
    private static int ticks;

    private GateRealizer() {
    }

    /** Subscribes the tick listener. Called from {@code WorldSimRuntime.register()}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(GateRealizer::onServerStarted);
        NeoForge.EVENT_BUS.addListener(GateRealizer::onServerTick);
        NeoForge.EVENT_BUS.addListener(GateRealizer::onServerStopping);
    }

    static void onServerStarted(ServerStartedEvent event) {
        job = null;
        FAILED.clear();
        ticks = 0;
    }

    static void onServerStopping(ServerStoppingEvent event) {
        Job j = job;
        job = null;
        FAILED.clear();
        if (j != null) {
            try {
                releaseTickets(event.getServer().overworld(), j);
            } catch (RuntimeException ex) {
                LOGGER.warn("Gate realizer: releasing the chunk tickets of sect {} failed", j.sectId, ex);
            }
            LOGGER.info("GATE_REALIZE sect={} state=cancelled reason=server_stopping", j.sectId);
        }
    }

    static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    /**
     * One server tick: advances the current job (loading and sampling, or the next
     * {@code clips_per_tick} clips), or, with no job, every {@value #PASS_TICKS} ticks looks for a
     * gate to realize.
     */
    public static void tick(MinecraftServer server) {
        ticks++;
        Job j = job;
        if (j != null) {
            try {
                step(server, j);
            } catch (RuntimeException ex) {
                fail(server, j, ex.getClass().getSimpleName(), ex);
            }
            return;
        }
        if (ticks % PASS_TICKS != 0) {
            return;
        }
        try {
            scan(server);
        } catch (RuntimeException ex) {
            Job started = job;
            if (started != null) {
                fail(server, started, ex.getClass().getSimpleName(), ex);
            } else {
                LOGGER.error("Gate realizer: the pass failed", ex);
            }
        }
    }

    /** Whether the compound of this sect is being realized now (the build command refuses it meanwhile). */
    public static boolean busy(int sectId) {
        Job j = job;
        return j != null && j.sectId == sectId;
    }

    /**
     * A one-line state for commands and logs: {@code idle}, {@code building sect <id> clip <i>/<n>}
     * (with the load ticks and the surface sampled while loading), and {@code failed: [ids]} for the
     * gates given up or skipped this session.
     */
    public static String status() {
        Job j = job;
        String failed = FAILED.isEmpty() ? "" : "failed: " + FAILED;
        if (j == null) {
            return failed.isEmpty() ? "idle" : failed;
        }
        StringBuilder out = new StringBuilder("building sect ").append(j.sectId)
                .append(" clip ").append(j.next).append('/').append(j.clips.size());
        if (j.site == null) {
            out.append(" (loading chunks, ").append(j.loadTicks).append(" ticks; surface ")
                    .append(j.sampled).append('/').append(j.surface.length).append(')');
        }
        if (!failed.isEmpty()) {
            out.append("; ").append(failed);
        }
        return out.toString();
    }

    /** Sects given up this session, in id order. */
    static List<Integer> failedSects() {
        return List.copyOf(FAILED);
    }

    /** Lets the failed gates be tried again; returns how many there were. */
    static int clearFailed() {
        int n = FAILED.size();
        FAILED.clear();
        return n;
    }

    // ------------------------------------------------------------------ the pass

    private static Optional<Rules.PlayerGates> gatesRules() {
        return WorldSimRuntime.data().map(SimData::rules).map(Rules::player).map(Rules.Player::gates);
    }

    private static void scan(MinecraftServer server) {
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty() || !WorldSimServerConfig.avatarsEnabled() || !WorldSimServerConfig.autoRealizeGates()) {
            return;
        }
        Optional<Rules.PlayerGates> gates = gatesRules();
        if (gates.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        WorldSim sim = active.get();
        int radius = gates.get().realizeRadius();
        List<GateRealizePlan.Candidate> candidates = new ArrayList<>();
        for (SectView sect : sim.sects(false)) {
            if (!sect.state().equals("active") || sect.gateRealized() || FAILED.contains(sect.id())) {
                continue;
            }
            double nearest = Double.MAX_VALUE;
            for (ServerPlayer player : players) {
                nearest = Math.min(nearest,
                        GateRealizePlan.planarDistance(player.getX(), player.getZ(), sect.gateX(), sect.gateZ()));
            }
            if (GateRealizePlan.withinRadius(nearest, radius)) {
                candidates.add(new GateRealizePlan.Candidate(sect.id(), nearest));
            }
        }
        Optional<GateRealizePlan.Candidate> pick = GateRealizePlan.pick(candidates);
        if (pick.isEmpty()) {
            return;
        }
        Optional<SectView> sect = sim.sect(pick.get().sectId());
        if (sect.isPresent()) {
            begin(level, sim, sect.get(), pick.get().distance(), gates.get());
        }
    }

    private static void begin(ServerLevel level, WorldSim sim, SectView sect, double distance,
                              Rules.PlayerGates rules) {
        // the build area depends on the gate's x/z only; the anchor's y is the generator's surface
        SectGenerator.BuildArea area = SectGenerator.worldgenBuildArea(
                SectGenerator.baseFor(new BlockPos(sect.gateX(), 0, sect.gateZ())));
        List<GateRealizePlan.ChunkClip> clips =
                GateRealizePlan.clips(area.x0(), area.z0(), area.width(), area.depth());
        // a first look at the chunks loaded now, before loading the rest for nothing
        Optional<Trace> trace = playerTrace(level, clips, rules);
        if (trace.isPresent()) {
            skip(level, sect.id(), sect.name(), sect.gateX(), sect.gateZ(), trace.get());
            return;
        }
        long seed = GateBuilder.seed(level.getSeed(), sect.id());
        String variant = GateBuilder.variant(level.getSeed(), sect.id());
        Job j = new Job(sim, sect, seed, variant, area, clips);
        job = j;
        for (GateRealizePlan.ChunkClip clip : clips) {
            ChunkPos pos = new ChunkPos(clip.chunkX(), clip.chunkZ());
            level.getChunkSource().addRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
            j.tickets.add(pos);
        }
        LOGGER.info("GATE_REALIZE sect={} state=queued gate={} {} distance={} chunks={}", j.sectId, j.gateX, j.gateZ,
                Math.round(distance), clips.size());
    }

    private static void step(MinecraftServer server, Job j) {
        WorldSim sim = WorldSimRuntime.sim().orElse(null);
        if (sim != j.sim) {
            cancel(server.overworld(), j, "ledger_changed");
            return;
        }
        SectView sect = sim.sect(j.sectId).orElse(null);
        if (sect == null || !sect.state().equals("active") || sect.gateX() != j.gateX || sect.gateZ() != j.gateZ) {
            cancel(server.overworld(), j, "sect_changed");
            return;
        }
        if (sect.gateRealized()) {
            cancel(server.overworld(), j, "already_realized");
            return;
        }
        ServerLevel level = server.overworld();
        if (j.site == null) {
            j.loadTicks++;
            int loaded = 0;
            for (ChunkPos pos : j.tickets) {
                if (level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                    loaded++;
                }
            }
            boolean sampled = sampleSurface(level, j);
            if (loaded < j.tickets.size()) {
                if (j.loadTicks >= LOAD_TIMEOUT_TICKS) {
                    LOGGER.warn("Gate realizer: {} of {} chunks of sect {} still not loaded after {} ticks",
                            j.tickets.size() - loaded, j.tickets.size(), j.sectId, j.loadTicks);
                    fail(server, j, "chunks_not_loaded", null);
                }
                return;
            }
            if (!sampled) {
                return;
            }
            Optional<Trace> trace = gatesRules().flatMap(rules -> playerTrace(level, j.clips, rules));
            if (trace.isPresent()) {
                releaseTickets(level, j);
                if (job == j) {
                    job = null;
                }
                skip(level, j.sectId, j.sectName, j.gateX, j.gateZ, trace.get());
                return;
            }
            start(level, j, loaded);
            return;
        }
        int perTick = Math.max(1, gatesRules().map(Rules.PlayerGates::clipsPerTick).orElse(1));
        int n = j.clips.size();
        for (int k = 0; k < perTick && j.next < n; k++) {
            GateRealizePlan.ChunkClip clip = j.clips.get(j.next);
            j.site.realizeClip(clip.x0(), clip.z0(), clip.x1(), clip.z1());
            j.next++;
            if (j.next % LOG_EVERY_CLIPS == 0 || j.next == n) {
                LOGGER.info("GATE_REALIZE sect={} state=clip {}/{}", j.sectId, j.next, n);
            }
        }
        if (j.next >= n) {
            finish(level, sim, j);
        }
    }

    /** Samples the generator's surface for up to {@link #SAMPLE_BUDGET_NANOS}; true once the whole area is in. */
    private static boolean sampleSurface(ServerLevel level, Job j) {
        int total = j.surface.length;
        int depth = j.area.depth();
        long deadline = System.nanoTime() + SAMPLE_BUDGET_NANOS;
        while (j.sampled < total) {
            int i = j.sampled / depth;
            int k = j.sampled % depth;
            j.surface[j.sampled] = SectGenerator.generatorSurface(level, j.area.x0() + i, j.area.z0() + k);
            j.sampled++;
            if ((j.sampled & 63) == 0 && System.nanoTime() > deadline) {
                break;
            }
        }
        return j.sampled >= total;
    }

    private static void start(ServerLevel level, Job j, int loaded) {
        SectGenerator.BuildArea area = j.area;
        Map<Long, Integer> outside = new HashMap<>();
        SectMountain.NaturalHeight worldSurface = (x, z) -> {
            int i = x - area.x0();
            int k = z - area.z0();
            if (i >= 0 && i < area.width() && k >= 0 && k < area.depth()) {
                return j.surface[i * area.depth() + k];
            }
            return outside.computeIfAbsent(ChunkPos.asLong(x, z), key -> SectGenerator.generatorSurface(level, x, z));
        };
        BlockPos anchor = new BlockPos(j.gateX, worldSurface.at(j.gateX, j.gateZ), j.gateZ);
        j.anchor = anchor;
        j.site = SectGenerator.prepare(level, j.seed, j.variant, anchor, worldSurface);
        j.startNanos = System.nanoTime();
        broadcast(level, j.sectName, j.gateX, j.gateZ, "forming");
        LOGGER.info("GATE_REALIZE sect={} state=started anchor={} {} {} clips={} seed={} variant={} "
                        + "load_ticks={} preloaded={}/{}",
                j.sectId, anchor.getX(), anchor.getY(), anchor.getZ(), j.clips.size(), j.seed, j.variant,
                j.loadTicks, loaded, j.tickets.size());
    }

    private static void finish(ServerLevel level, WorldSim sim, Job j) {
        GateBuilder.placeShelves(level, j.sectId, j.seed, j.anchor, j.variant);
        releaseTickets(level, j);
        job = null;
        GateRealizations.get(level).put(new GateRealizations.Gate(j.sectId, j.anchor, j.seed, j.variant, sim.day()));
        sim.markGateRealized(j.sectId, true);
        WorldSimSavedData.get(level).setDirty();
        WorldSimAvatars.gateChanged(j.sectId);
        broadcast(level, j.sectName, j.gateX, j.gateZ, "formed");
        double seconds = (System.nanoTime() - j.startNanos) / 1e9;
        LOGGER.info("GATE_REALIZE sect={} state=done seconds={} clips={} blocks~={} written={}", j.sectId,
                String.format(Locale.ROOT, "%.1f", seconds), j.clips.size(), j.site.blocksPlaced(),
                j.site.blocksWritten());
    }

    private static void cancel(ServerLevel level, Job j, String reason) {
        releaseTickets(level, j);
        if (job == j) {
            job = null;
        }
        LOGGER.info("GATE_REALIZE sect={} state=cancelled reason={}", j.sectId, reason);
    }

    /** Gives a gate up this session without building anything: players have left traces in its area. */
    private static void skip(ServerLevel level, int sectId, String sectName, int gateX, int gateZ, Trace trace) {
        FAILED.add(sectId);
        LOGGER.info("GATE_REALIZE sect={} state=skipped reason={} chunk={} {}", sectId, trace.reason(),
                trace.chunkX(), trace.chunkZ());
        try {
            broadcast(level, sectName, gateX, gateZ, "skipped");
        } catch (RuntimeException ex) {
            LOGGER.warn("Gate realizer: telling the players about skipped sect {} failed", sectId, ex);
        }
    }

    /**
     * Rolls the gate back to unrealized and gives it up for this session. {@code reason} is logged
     * ({@code chunks_not_loaded}, or the exception's class); {@code ex} may be null.
     */
    private static void fail(MinecraftServer server, Job j, String reason, RuntimeException ex) {
        if (ex != null) {
            LOGGER.error("Gate realizer: realizing the compound of sect {} ({}) failed at clip {}/{}", j.sectId,
                    j.sectName, j.next, j.clips.size(), ex);
        }
        if (job == j) {
            job = null;
        }
        FAILED.add(j.sectId);
        ServerLevel level = server.overworld();
        try {
            releaseTickets(level, j);
        } catch (RuntimeException inner) {
            LOGGER.warn("Gate realizer: releasing the chunk tickets of sect {} failed", j.sectId, inner);
        }
        try {
            WorldSimRuntime.sim().filter(s -> s == j.sim && s.sect(j.sectId).isPresent())
                    .ifPresent(s -> s.markGateRealized(j.sectId, false));
            WorldSimSavedData.get(level).setDirty();
            GateRealizations.get(level).remove(j.sectId);
            WorldSimAvatars.gateChanged(j.sectId);
        } catch (RuntimeException inner) {
            LOGGER.warn("Gate realizer: rolling back the gate of sect {} failed", j.sectId, inner);
        }
        LOGGER.info("GATE_REALIZE sect={} state=failed reason={}", j.sectId, reason);
        try {
            broadcast(level, j.sectName, j.gateX, j.gateZ, "failed");
        } catch (RuntimeException ignored) {
            // the rollback is what matters
        }
    }

    private static void releaseTickets(ServerLevel level, Job j) {
        for (ChunkPos pos : j.tickets) {
            level.getChunkSource().removeRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
        }
        j.tickets.clear();
    }

    // ------------------------------------------------------------------ players' traces

    /**
     * The first loaded chunk of the build area where players left a trace ({@link GateRealizePlan#playerPresence});
     * chunks that are not loaded (or not generated) are passed over.
     */
    private static Optional<Trace> playerTrace(ServerLevel level, List<GateRealizePlan.ChunkClip> clips,
                                               Rules.PlayerGates rules) {
        List<GateRealizePlan.ChunkFacts> facts = new ArrayList<>();
        List<GateRealizePlan.ChunkClip> where = new ArrayList<>();
        for (GateRealizePlan.ChunkClip clip : clips) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(clip.chunkX(), clip.chunkZ());
            if (chunk == null) {
                continue;
            }
            int vanilla = 0;
            int mod = 0;
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                String namespace = id == null ? "" : id.getNamespace();
                if (ResourceLocation.DEFAULT_NAMESPACE.equals(namespace)) {
                    if (!placedByWorldgen(be, id.getPath())) {
                        vanilla++;
                    }
                } else if (!MyVillageMod.MOD_ID.equals(namespace)) {
                    mod++;
                }
            }
            facts.add(new GateRealizePlan.ChunkFacts(chunk.getInhabitedTime(), vanilla, mod));
            where.add(clip);
        }
        return GateRealizePlan.playerPresence(facts, rules.inhabitedTicksMax(), rules.playerBlockEntitiesMax())
                .map(p -> new Trace(where.get(p.index()).chunkX(), where.get(p.index()).chunkZ(), p.reason()));
    }

    /** A vanilla block entity worldgen places by itself, or a container whose loot was never rolled. */
    private static boolean placedByWorldgen(BlockEntity be, String path) {
        return WORLDGEN_BLOCK_ENTITIES.contains(path)
                || (be instanceof RandomizableContainer container && container.getLootTable() != null);
    }

    /** A chat line ({@code message.myvillage.world.gate.<what>}, the sect name) to the players within the radius. */
    private static void broadcast(ServerLevel level, String sectName, int gateX, int gateZ, String what) {
        int radius = gatesRules().map(Rules.PlayerGates::realizeRadius).orElse(0);
        Component line = Component.translatable(MESSAGE_KEY + what, sectName);
        for (ServerPlayer player : level.players()) {
            if (GateRealizePlan.withinRadius(
                    GateRealizePlan.planarDistance(player.getX(), player.getZ(), gateX, gateZ), radius)) {
                player.sendSystemMessage(line);
            }
        }
    }
}
