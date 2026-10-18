package com.example.myvillage.sim.runtime.life;

import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.life.LifePlace;
import com.example.myvillage.sim.life.LifeSite;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts authored building metadata to ledger-owned sites. Never places a block or requests a
 * chunk. Registry identities, inhabitants, and availability observations are saved by WorldSim.
 */
public final class NpcLifeSites {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcLifeSites.class);
    public static final String YUNSHAN_SITE = "yunshan";
    public static final String PLANNED_LAYOUT = "yunshan_planned_v1";
    private static final ResourceLocation MANIFEST = ResourceLocation.fromNamespaceAndPath(
            "myvillage", "npc_life/places.json");
    public static final List<String> KINDS = List.of("rest", "work", "gather", "market", "social", "entry", "cultivate");
    private static MinecraftServer currentServer;
    private static boolean automaticAttempted;

    private NpcLifeSites() {}

    public enum Availability {
        AVAILABLE, BLOCKED, UNLOADED;
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    /** A localized command error; technical resource parse failures use the ordinary diagnostic. */
    public static final class SiteException extends IllegalArgumentException {
        private final String key;
        private final Object[] arguments;
        public SiteException(String key, Object... arguments) {
            super(key);
            this.key = key;
            this.arguments = arguments.clone();
        }
        public String key() { return key; }
        public Object[] arguments() { return arguments.clone(); }
    }

    public static List<String> layouts(ServerLevel level) {
        List<String> ids = new ArrayList<>();
        for (var element : catalogue(level)) ids.add(element.getAsJsonObject().get("id").getAsString());
        return List.copyOf(ids);
    }

    private static JsonArray catalogue(ServerLevel level) {
        var resource = level.getServer().getResourceManager().getResource(MANIFEST)
                .orElseThrow(() -> new SiteException("missing_manifest"));
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("NPC place manifest schema");
            return root.getAsJsonArray("layouts");
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read NPC place metadata", failure);
        }
    }

    private static JsonObject layout(ServerLevel level, String id) {
        for (var element : catalogue(level)) {
            JsonObject candidate = element.getAsJsonObject();
            if (candidate.get("id").getAsString().equals(id)) return candidate;
        }
        throw new SiteException("unknown_layout", id);
    }

    /** Construct first, then register once through the facade; no partial layout writes. */
    public static LifeSite bind(ServerLevel level, WorldSim sim, String id, String layoutId, BlockPos origin) {
        validId(id);
        requireOverworld(level);
        JsonObject description = layout(level, layoutId);
        String region = region(sim, origin);
        List<LifePlace> places = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (var element : description.getAsJsonArray("places")) {
            JsonObject point = element.getAsJsonObject();
            String placeId = point.get("id").getAsString();
            validId(placeId);
            if (!ids.add(placeId)) throw new IllegalArgumentException("Duplicate place " + placeId);
            String kind = point.get("kind").getAsString();
            if (!KINDS.contains(kind)) throw new IllegalArgumentException("Unsupported place kind " + kind);
            BlockPos at = translated(origin, point.getAsJsonArray("pos"));
            validatePosition(level, sim, region, at);
            String block = point.get("block").getAsString();
            BlockPos fixture = block.isEmpty() ? BlockPos.ZERO : translated(origin, point.getAsJsonArray("block_pos"));
            if (!block.isEmpty()) {
                validatePosition(level, sim, region, fixture);
                ResourceLocation parsed = ResourceLocation.tryParse(block);
                if (parsed == null || !BuiltInRegistries.BLOCK.containsKey(parsed))
                    throw new IllegalArgumentException("Unknown facility block " + block);
            }
            places.add(new LifePlace(placeId, kind, at.getX(), at.getY(), at.getZ(),
                    point.get("capacity").getAsInt(), block, fixture.getX(), fixture.getY(), fixture.getZ()));
        }
        LifeSite site = new LifeSite(id, description.get("name").getAsString(), level.dimension().location().toString(),
                region, origin.getX(), origin.getY(), origin.getZ(), List.copyOf(places));
        Optional<LifeSite> existing = sim.lifeSite(id);
        if (existing.isPresent() && !sameBoundLayout(existing.get(), site)) throw new SiteException("site_conflict", id);
        if (existing.isEmpty()) {
            if (!sim.registerLifeSite(site)) throw new SiteException("site_conflict", id);
            dirty(level.getServer());
        }
        // Existing sites may contain a manually added gather point. Rebinding never drops it.
        LifeSite bound = sim.lifeSite(id).orElseThrow();
        observe(level.getServer(), sim, bound);
        return bound;
    }

    private static boolean sameBoundLayout(LifeSite existing, LifeSite candidate) {
        return existing.dimension().equals(candidate.dimension()) && existing.regionId().equals(candidate.regionId())
                && existing.anchorX() == candidate.anchorX() && existing.anchorY() == candidate.anchorY()
                && existing.anchorZ() == candidate.anchorZ()
                && candidate.places().stream().allMatch(p -> existing.place(p.id()).filter(p::equals).isPresent());
    }

    /** Manual stations are observed in-place; gathering is an explicit finite ledger allowance. */
    public static LifePlace add(ServerLevel level, WorldSim sim, String siteId, String placeId, String kind, BlockPos at) {
        validId(placeId);
        LifeSite site = sim.lifeSite(siteId).orElseThrow(() -> new SiteException("unknown_site", siteId));
        if (!level.dimension().location().toString().equals(site.dimension())) throw new SiteException("wrong_dimension");
        if (!KINDS.contains(kind)) throw new SiteException("unknown_kind", kind);
        validatePosition(level, sim, site.regionId(), at);
        LifePlace place = LifePlace.free(placeId, kind, at.getX(), at.getY(), at.getZ());
        Availability condition = availability(level, place);
        if (condition != Availability.AVAILABLE) throw new SiteException("place_unavailable", placeId, condition.id());
        Optional<LifePlace> existing = site.place(placeId);
        if (existing.isPresent() && !existing.get().equals(place)) throw new SiteException("place_conflict", placeId);
        if (existing.isEmpty()) {
            if (!sim.addLifePlace(siteId, place)) throw new SiteException("place_conflict", placeId);
            dirty(level.getServer());
        }
        if (sim.setLifePlaceAvailability(siteId, placeId, condition.id())) dirty(level.getServer());
        return place;
    }

    public static LifeSite yunshan(MinecraftServer server, WorldSim sim) {
        ServerLevel level = server.overworld();
        var generator = WorldPlanRuntime.generator(level).orElseThrow(() -> new SiteException("no_planned_world"));
        var plan = generator.plan();
        String layoutId = plan.rules().city.lifeLayout();
        JsonObject description = layout(level, layoutId);
        if (!description.get("worldplan_layout_hash").getAsString().equals(plan.rules().city.layoutHash()))
            throw new SiteException("layout_mismatch");
        var city = plan.city();
        return bind(level, sim, YUNSHAN_SITE, layoutId, new BlockPos(city.x(), city.baseY(), city.z()));
    }

    /** Called by the life runtime. Each configured interval visits only the bounded saved registry. */
    public static void tick(MinecraftServer server, WorldSim sim) {
        if (currentServer != server) {
            currentServer = server;
            automaticAttempted = false;
        }
        // WorldSimRuntime owns the cadence. A second clock modulus can suppress every pass after restart.
        if (!automaticAttempted && sim.lifeSite(YUNSHAN_SITE).isEmpty()) {
            WorldPlanRuntime.generator(server.overworld()).ifPresent(generator -> {
                var city = generator.plan().city();
                boolean visible = false;
                for (int z = 0; z < city.depth() && !visible; z += 16)
                    for (int x = 0; x < city.width() && !visible; x += 16)
                        visible = loaded(server.overworld(), city.x() + x, city.z() + z);
                if (!visible) return;
                automaticAttempted = true;
                try {
                    LifeSite site = yunshan(server, sim);
                    LOGGER.info("NPC_LIFE registered planned site={} places={} residents_added=0", site.id(), site.places().size());
                } catch (RuntimeException failure) {
                    LOGGER.warn("NPC_LIFE optional Yunshan registration unavailable; manual commands remain available", failure);
                }
            });
        }
        for (LifeSite site : sim.lifeSites()) observe(server, sim, site);
        // Only new V4 cities opt in automatically. Old saves need an explicit commerce command.
        WorldPlanRuntime.generator(server.overworld()).ifPresent(generator -> {
            if (generator.plan().rules().algorithm.equals(com.example.myvillage.worldplan.WorldPlanRules.ALGORITHM)
                    && sim.lifeSite(YUNSHAN_SITE).isPresent()) enableCommerce(server, sim, YUNSHAN_SITE);
        });
    }

    /** Inventory seed and recruitment are idempotent; a dead proprietor is never re-created. */
    public static List<Integer> enableCommerce(MinecraftServer server, WorldSim sim, String siteId) {
        boolean initialized = sim.initializeCommerce(siteId);
        var before = sim.commerce(siteId).orElseThrow();
        List<Integer> people = sim.staffCommerce(siteId);
        if (initialized || before.revision() != sim.commerce(siteId).orElseThrow().revision()) dirty(server);
        return people;
    }

    public static void reset() {
        currentServer = null;
        automaticAttempted = false;
    }

    public static void observe(MinecraftServer server, WorldSim sim, LifeSite site) {
        ServerLevel level = level(server, site);
        boolean changed = false;
        for (LifePlace place : site.places()) {
            Availability condition = level == null ? Availability.UNLOADED : availability(level, place);
            changed |= sim.setLifePlaceAvailability(site.id(), place.id(), condition.id());
        }
        if (changed) dirty(server);
    }

    public static ServerLevel level(MinecraftServer server, LifeSite site) {
        ResourceLocation dimension = ResourceLocation.tryParse(site.dimension());
        return dimension == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }

    /**
     * Static facility availability, excluding entity occupancy (the ledger owns reservations).
     * Collision neighbours are checked first so shape queries cannot request an adjacent chunk.
     */
    public static Availability availability(ServerLevel level, LifePlace place) {
        int x = place.x(), y = place.y(), z = place.z();
        if (y <= level.getMinBuildHeight() || y + 2 >= level.getMaxBuildHeight()) return Availability.BLOCKED;
        for (int cx = Math.floorDiv(x - 1, 16); cx <= Math.floorDiv(x + 1, 16); cx++) {
            for (int cz = Math.floorDiv(z - 1, 16); cz <= Math.floorDiv(z + 1, 16); cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) return Availability.UNLOADED;
            }
        }
        if (!place.block().isEmpty() && !loaded(level, place.blockX(), place.blockZ())) return Availability.UNLOADED;
        BlockPos at = new BlockPos(x, y, z);
        AABB body = new AABB(x + .2, y, z + .2, x + .8, y + 1.9, z + .8);
        if (!level.getWorldBorder().isWithinBounds(body)
                || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)
                || !level.getFluidState(at.below()).isEmpty() || !level.getFluidState(at).isEmpty()
                || !level.getFluidState(at.above()).isEmpty()) return Availability.BLOCKED;
        // getBlockCollisions deliberately avoids considering the occupying NPC as an obstruction.
        for (var shape : level.getBlockCollisions(null, body)) if (!shape.isEmpty()) return Availability.BLOCKED;
        if (!place.block().isEmpty()) {
            BlockPos fixture = new BlockPos(place.blockX(), place.blockY(), place.blockZ());
            if (!BuiltInRegistries.BLOCK.getKey(level.getBlockState(fixture).getBlock()).toString().equals(place.block()))
                return Availability.BLOCKED;
        }
        return Availability.AVAILABLE;
    }

    private static boolean loaded(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunkNow(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) != null;
    }

    private static String region(WorldSim sim, BlockPos at) {
        return RegionRuntimeService.regionAt(at.getX(), at.getZ()).filter(sim::hasRegion)
                .orElseThrow(() -> new SiteException("outside_region"));
    }

    private static void validatePosition(ServerLevel level, WorldSim sim, String region, BlockPos at) {
        if (!level.getWorldBorder().isWithinBounds(at) || at.getY() <= level.getMinBuildHeight()
                || at.getY() + 2 >= level.getMaxBuildHeight()) throw new SiteException("outside_border");
        if (!region.equals(region(sim, at))) throw new SiteException("outside_region");
    }

    private static void requireOverworld(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) throw new SiteException("wrong_dimension");
    }

    public static void validId(String id) {
        if (!id.matches("[a-z0-9_]+")) throw new SiteException("invalid_id", id);
    }

    private static BlockPos translated(BlockPos origin, JsonArray delta) {
        if (delta.size() != 3) throw new IllegalArgumentException("Place coordinate needs three values");
        return new BlockPos(Math.addExact(origin.getX(), delta.get(0).getAsInt()),
                Math.addExact(origin.getY(), delta.get(1).getAsInt()), Math.addExact(origin.getZ(), delta.get(2).getAsInt()));
    }

    static void dirty(MinecraftServer server) { WorldSimSavedData.get(server.overworld()).setDirty(); }
}
