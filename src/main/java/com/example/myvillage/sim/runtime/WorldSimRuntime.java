package com.example.myvillage.sim.runtime;

import com.example.myvillage.cultivation.time.CultivationCalendarSavedData;
import com.example.myvillage.cultivation.time.CultivationServerConfig;
import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.WorldSim;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server side of the world ledger (命簿). The ledger in {@link WorldSim} is the only authority; this
 * class loads or creates it, feeds it the cultivation calendar and keeps it saved.
 *
 * <p><b>Start.</b> After the region runtime (registration order in {@code MyVillageMod}): load the
 * world-sim data through the server's resource manager, take the region graph from
 * {@link RegionRuntimeService#graph()}, then restore the saved ledger, or run genesis once for a
 * world that has none (old worlds included) with the configured tier, which is fixed in the save
 * from then on. Any failure (no region graph, bad data, a save this version cannot read) is logged
 * and leaves the ledger inactive for the session; the save on disk is never overwritten then.
 *
 * <p><b>Tick.</b> Each server tick feeds the calendar's day index to the core's scheduler and settles
 * at most one sim day, then hands the day's events to the {@link DayListener}s (rumors). Pausing stops
 * settlement only; the calendar keeps running and resuming does not catch up.
 *
 * <p><b>Save.</b> The save data is marked dirty after every change (settled day, scheduler change,
 * pause, advance) and serialises the live ledger whenever the world saves, the final save at stop
 * included.
 */
public final class WorldSimRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldSimRuntime.class);
    private static final List<DayListener> LISTENERS = new CopyOnWriteArrayList<>();

    private static MinecraftServer server;
    private static WorldSimDriver driver;
    private static WorldSimSavedData saved;
    private static SimData data;
    private static String inactiveReason = "the server has not started";

    /** Told about every settled day, from the tick and from {@code advance}. */
    @FunctionalInterface
    public interface DayListener {
        void onDaySettled(MinecraftServer server, WorldSim sim, List<SimEvent> events);
    }

    private WorldSimRuntime() {
    }

    /** Registers the lifecycle listeners; call after the region runtime's. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(WorldSimRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(WorldSimRuntime::onServerTick);
        NeoForge.EVENT_BUS.addListener(WorldSimRuntime::onServerStopping);
        WorldSimRumors.register();
    }

    public static void addListener(DayListener listener) {
        LISTENERS.add(listener);
    }

    // ------------------------------------------------------------------ lifecycle

    static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        driver = null;
        saved = null;
        data = null;
        ServerLevel overworld = server.overworld();
        Optional<RegionGraph> graph = RegionRuntimeService.graph();
        if (graph.isEmpty()) {
            deactivate("the region runtime is not loaded");
            LOGGER.error("World sim inactive this session: the region runtime failed to load (see its error above)");
            return;
        }
        try {
            data = WorldSim.loadData(opener(server.getResourceManager()));
        } catch (RuntimeException ex) {
            deactivate("the world-sim data failed to load: " + ex.getMessage());
            LOGGER.error("World sim inactive this session: its data failed to load", ex);
            return;
        }
        saved = WorldSimSavedData.get(overworld);
        int daysPerYear = CultivationServerConfig.scale().daysPerYear();
        WorldSim sim;
        if (saved.newerFormat()) {
            deactivate("the save was written by a newer version (format " + saved.format() + ")");
            LOGGER.error("World sim inactive this session: {} is in wrapper format {}, newer than {}; "
                    + "the save is left untouched", WorldSimSavedData.DATA_NAME, saved.format(), WorldSimSavedData.FORMAT);
            saved = null;
            return;
        }
        if (saved.hasPayload()) {
            long t0 = System.nanoTime();
            try {
                sim = WorldSim.fromBytes(saved.payload(), graph.get(), data);
            } catch (RuntimeException ex) {
                deactivate("the saved ledger cannot be restored: " + ex.getMessage());
                LOGGER.error("World sim inactive this session: the saved ledger (tier {}) cannot be restored; "
                        + "the save is left untouched", saved.tier(), ex);
                saved = null;
                return;
            }
            LOGGER.info("World sim loaded: tier {}, sim day {}, {} living, {} active sects, settlement {} ({} ms)",
                    sim.tierId(), sim.day(), sim.overview(daysPerYear).population(),
                    sim.overview(daysPerYear).activeSects(), sim.scheduler().paused() ? "paused" : "running",
                    (System.nanoTime() - t0) / 1_000_000);
            String configured = WorldSimServerConfig.tier();
            if (!configured.equals(sim.tierId())) {
                LOGGER.info("World sim: configured tier '{}' is ignored; this world's tier '{}' was fixed at genesis",
                        configured, sim.tierId());
            }
        } else {
            if (!saved.loadedFromDisk() && Files.exists(dataFile(server))) {
                deactivate("the save file exists but could not be read");
                LOGGER.error("World sim inactive this session: {} exists but could not be read (see the error above); "
                        + "genesis is refused so it is not overwritten", dataFile(server));
                saved = null;
                return;
            }
            String tier = WorldSimServerConfig.tier();
            long seed = overworld.getSeed();
            long t0 = System.nanoTime();
            try {
                sim = WorldSim.genesis(seed, graph.get(), data, tier, daysPerYear);
            } catch (RuntimeException ex) {
                deactivate("genesis failed: " + ex.getMessage());
                LOGGER.error("World sim inactive this session: genesis failed (tier {}, seed {})", tier, seed, ex);
                saved = null;
                return;
            }
            LOGGER.info("World sim genesis for this world: tier {}, seed {}, {} prehistory days at {} days/year, "
                            + "{} living, {} active sects, took {} ms; the tier is now fixed in the save",
                    tier, seed, sim.prehistoryDays(), daysPerYear, sim.overview(daysPerYear).population(),
                    sim.overview(daysPerYear).activeSects(), (System.nanoTime() - t0) / 1_000_000);
        }
        WorldSim live = sim;
        driver = new WorldSimDriver(live);
        saved.attach(() -> new WorldSimSavedData.Snapshot(live.tierId(), live.scheduler().paused(), live.toBytes()));
        inactiveReason = null;
    }

    static void onServerTick(ServerTickEvent.Post event) {
        WorldSimDriver d = driver;
        if (d == null) {
            return;
        }
        MinecraftServer s = event.getServer();
        CultivationServerConfig.Scale scale = CultivationServerConfig.scale();
        List<SimEvent> events;
        try {
            events = d.tick(calendarDay(s), WorldSimServerConfig.catchUpCapDays(), scale.daysPerYear());
        } catch (RuntimeException ex) {
            fail("settlement", ex);
            return;
        }
        if (d.consumeSchedulerChange() || events != null) {
            saved.setDirty();
        }
        if (events != null) {
            publish(s, d.sim(), events);
        }
    }

    static void onServerStopping(ServerStoppingEvent event) {
        // The final world save runs after this event; the save data keeps its live binding for it.
        if (saved != null && saved.attached()) {
            saved.setDirty();
        }
        driver = null;
        saved = null;
        data = null;
        server = null;
        deactivate("the server is stopping");
    }

    // ------------------------------------------------------------------ queries and admin actions

    /** The live driver, or empty while the ledger is inactive. */
    public static Optional<WorldSimDriver> driver() {
        return Optional.ofNullable(driver);
    }

    /** The live ledger, or empty while it is inactive. */
    public static Optional<WorldSim> sim() {
        WorldSimDriver d = driver;
        return d == null ? Optional.empty() : Optional.of(d.sim());
    }

    /** Why the ledger is inactive (null when active). */
    public static String inactiveReason() {
        return inactiveReason;
    }

    public static Optional<SimData> data() {
        return Optional.ofNullable(data);
    }

    public static int daysPerYear() {
        return CultivationServerConfig.scale().daysPerYear();
    }

    /** The cultivation calendar's day index (elapsed calendar ticks / ticks per day). */
    public static long calendarDay(MinecraftServer s) {
        long elapsed = CultivationCalendarSavedData.get(s.overworld()).elapsedCalendarTicks();
        return elapsed / CultivationServerConfig.scale().ticksPerDay();
    }

    /** The display name of a region of the cached graph, or the id itself. */
    public static String regionName(String regionId) {
        if (regionId == null || regionId.isEmpty()) {
            return "";
        }
        Optional<RegionGraph> graph = RegionRuntimeService.graph();
        if (graph.isPresent()) {
            for (GenRegion r : graph.get().regions()) {
                if (r.id().equals(regionId)) {
                    return r.displayName();
                }
            }
        }
        return regionId;
    }

    /** Pauses or resumes settlement (persisted with the ledger). Returns false if it already was. */
    public static boolean setPaused(boolean paused) {
        WorldSimDriver d = requireActive();
        if (d.paused() == paused) {
            return false;
        }
        d.setPaused(paused);
        d.consumeSchedulerChange();
        saved.setDirty();
        LOGGER.info("World sim settlement {} by command at sim day {}", paused ? "paused" : "resumed", d.sim().day());
        return true;
    }

    /** Settles {@code days} days now; returns the number of events. */
    public static long advance(int days, Consumer<List<SimEvent>> perDay) {
        WorldSimDriver d = requireActive();
        MinecraftServer s = server;
        long events;
        try {
            events = d.advance(days, daysPerYear(), day -> {
                perDay.accept(day);
                publish(s, d.sim(), day);
            });
        } catch (RuntimeException ex) {
            if (ex instanceof IllegalArgumentException) {
                throw ex;
            }
            fail("advance", ex);
            throw ex;
        }
        saved.setDirty();
        return events;
    }

    // ------------------------------------------------------------------ internals

    private static WorldSimDriver requireActive() {
        WorldSimDriver d = driver;
        if (d == null) {
            throw new IllegalStateException("the world ledger is inactive: " + inactiveReason);
        }
        return d;
    }

    private static void publish(MinecraftServer s, WorldSim sim, List<SimEvent> events) {
        if (s == null || events.isEmpty()) {
            return;
        }
        for (DayListener listener : LISTENERS) {
            try {
                listener.onDaySettled(s, sim, events);
            } catch (RuntimeException ex) {
                LOGGER.error("World sim day listener {} failed", listener, ex);
            }
        }
    }

    private static void fail(String what, RuntimeException ex) {
        LOGGER.error("World sim {} failed at sim day {}; the ledger is inactive until restart and its save keeps "
                + "the last checkpoint", what, driver == null ? "?" : driver.sim().day(), ex);
        if (saved != null) {
            saved.detach();
        }
        driver = null;
        deactivate(what + " failed: " + ex);
    }

    private static void deactivate(String reason) {
        inactiveReason = reason;
    }

    private static Path dataFile(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("data").resolve(WorldSimSavedData.DATA_NAME + ".dat");
    }

    /** Opens {@code data/<namespace>/<path>} through the server's (datapack) resource manager. */
    static SimData.ResourceOpener opener(ResourceManager resources) {
        return path -> {
            if (!path.startsWith("data/")) {
                return null;
            }
            String rest = path.substring("data/".length());
            int slash = rest.indexOf('/');
            if (slash <= 0) {
                return null;
            }
            ResourceLocation id = ResourceLocation.tryBuild(rest.substring(0, slash), rest.substring(slash + 1));
            if (id == null) {
                return null;
            }
            Optional<Resource> resource = resources.getResource(id);
            return resource.isPresent() ? resource.get().open() : null;
        };
    }
}
