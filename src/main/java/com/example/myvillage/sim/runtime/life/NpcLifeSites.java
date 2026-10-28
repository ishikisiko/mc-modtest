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
    public static final List<String> KINDS = List.of("rest", "work", "gather", "garden", "market", "social", "entry", "cultivate");
    private static MinecraftServer currentServer;
    private static boolean automaticAttempted;
    private static final Set<String> capitalAttempts = new HashSet<>();
    private static final Set<String> boundCapitals = new HashSet<>();

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
        return bindDescription(level, sim, id, layout(level, layoutId), origin);
    }

    private static LifeSite bindDescription(ServerLevel level, WorldSim sim, String id, JsonObject description, BlockPos origin) {
        validId(id);
        requireOverworld(level);
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
        if (plan.rules().capitals != null) throw new IllegalArgumentException("八州世界请按 cityId 访问生活站点；旧云山仓库不会迁移");
        String layoutId = plan.rules().city.lifeLayout();
        JsonObject description = layout(level, layoutId);
        if (!matchesLayout(description, plan.rules().city.layoutHash()))
            throw new SiteException("layout_mismatch");
        var city = plan.city();
        return bind(level, sim, YUNSHAN_SITE, layoutId, new BlockPos(city.x(), city.baseY(), city.z()));
    }

    /** Bundle places are the sole coordinate source; no manifest from the preview is accepted. */
    public static LifeSite capital(MinecraftServer server, WorldSim sim,
                                   com.example.myvillage.worldplan.capital.CapitalCity city) {
        ServerLevel level=server.overworld();
        com.example.myvillage.worldplan.runtime.CapitalGamePlacement.verify(level,city.bundle());
        JsonObject description=city.bundle().life();
        description.addProperty("name",city.bundle().displayName);
        return bindDescription(level,sim,city.cityId(),description,new BlockPos(city.x(),city.originY(),city.z()));
    }

    /** Only aliases whose complete tile/surface and facility fingerprints pass generation are shipped. */
    public static boolean matchesLayout(JsonObject description, String frozenHash) {
        if (description.get("worldplan_layout_hash").getAsString().equals(frozenHash)) return true;
        if (!description.has("compatible_worldplan_layout_hashes")) return false;
        for (var alias : description.getAsJsonArray("compatible_worldplan_layout_hashes"))
            if (alias.getAsString().equals(frozenHash)) return true;
        return false;
    }

    /** Called by the life runtime. Each configured interval visits only the bounded saved registry. */
    public static void tick(MinecraftServer server, WorldSim sim) {
        if (currentServer != server) {
            currentServer = server;
            automaticAttempted = false;
            capitalAttempts.clear();
            boundCapitals.clear();
        }
        WorldPlanRuntime.generator(server.overworld()).ifPresent(generator -> {
            if(generator.plan().rules().capitals==null)return;
            for(var city:generator.plan().cities()) {
                if(capitalAttempts.contains(city.cityId()))continue;
                // Visit only the small service registry, not thousands of city chunks.
                boolean visible=false;
                for(var element:city.bundle().life().getAsJsonArray("places")) {
                    var pos=element.getAsJsonObject().getAsJsonArray("pos");
                    if(loaded(server.overworld(),city.x()+pos.get(0).getAsInt(),city.z()+pos.get(2).getAsInt())){visible=true;break;}
                }
                if(!visible)continue;
                capitalAttempts.add(city.cityId());
                try {
                    LifeSite site=capital(server,sim,city);
                    boundCapitals.add(city.cityId());
                    LOGGER.info("NPC_LIFE capital={} region={} places={} residents=existing_ledger_only",site.id(),site.regionId(),site.places().size());
                }catch(RuntimeException failure) {
                    LOGGER.warn("NPC_LIFE capital registration failed city={}; no residents or stock fabricated",city.cityId(),failure);
                }
            }
        });
        // WorldSimRuntime owns the cadence. A second clock modulus can suppress every pass after restart.
        if (!automaticAttempted && sim.lifeSite(YUNSHAN_SITE).isEmpty()) {
            WorldPlanRuntime.generator(server.overworld()).ifPresent(generator -> {
                if(generator.plan().rules().capitals!=null)return;
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
        // Full D1 city worlds (V4+) retain their existing automatic commerce opt-in.
        WorldPlanRuntime.generator(server.overworld()).ifPresent(generator -> {
            if(generator.plan().rules().capitals!=null) {
                for(var city:generator.plan().cities())if(boundCapitals.contains(city.cityId()))enableCommerce(server,sim,city.cityId());
                return;
            }
            if (com.example.myvillage.worldplan.WorldPlanRules.compressed(generator.plan().rules().algorithm)
                    && sim.lifeSite(YUNSHAN_SITE).isPresent()) enableCommerce(server, sim, YUNSHAN_SITE);
        });
        for (LifeSite site : sim.lifeSites()) {
            boolean pending=WorldPlanRuntime.generator(server.overworld()).map(g->g.plan().capitalOf(site.id()).isPresent()&&!boundCapitals.contains(site.id())).orElse(false);
            if(pending)continue;
            var rules = com.example.myvillage.exploration.ExplorationRules.get();
            boolean assigned = sim.townGuide(site.id()).isPresent();
            int guide = sim.staffTownGuide(site.id(), rules.guideHome(), rules.guideWork());
            if (!assigned && guide > 0) {
                dirty(server);
                LOGGER.info("TOWN_GUIDE assigned site={} person={}", site.id(), guide);
            }
        }
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
        capitalAttempts.clear();
        boundCapitals.clear();
    }

    /** Read-only vacancy report. An assigned ledger resident is not a claim of a loaded live shop. */
    public static JsonObject capitalStatus(MinecraftServer server,WorldSim sim,
                                          com.example.myvillage.worldplan.capital.CapitalCity city) {
        JsonObject result=new JsonObject();var site=sim.lifeSite(city.cityId());
        result.addProperty("site_id",city.cityId());result.addProperty("registered",site.isPresent());
        result.addProperty("binding_verified_this_session",boundCapitals.contains(city.cityId()));
        result.addProperty("resident_source","existing_local_worldsim_people; V8 rogue genesis/entrants include Moyu independently of sect admission");
        JsonArray roles=new JsonArray();
        List<String[]> slots=new ArrayList<>();
        for(var job:sim.commerceRules().staff())slots.add(new String[]{job.role(),job.home(),job.work()});
        var guide=com.example.myvillage.exploration.ExplorationRules.get();slots.add(new String[]{"guide",guide.guideHome(),guide.guideWork()});
        var people=sim.lifeResidents();
        for(var slot:slots) {
            JsonObject row=new JsonObject();row.addProperty("role",slot[0]);
            var resident=people.stream().filter(p->p.siteId().equals(city.cityId())&&p.role().equals(slot[0])).findFirst();
            row.addProperty("assigned",resident.isPresent());
            if(resident.isPresent()){row.addProperty("person_id",resident.get().personId());row.addProperty("embodied",resident.get().embodied());row.addProperty("phase",resident.get().phase());}
            String condition="site_not_registered";
            if(site.isPresent()) {
                var home=site.get().place(slot[1]);var work=site.get().place(slot[2]);
                if(home.isEmpty()||work.isEmpty())condition="missing_places";
                else {
                    Availability h=availability(server.overworld(),home.get()),w=availability(server.overworld(),work.get());
                    row.addProperty("home",h.id());row.addProperty("work",w.id());
                    condition=h!=Availability.AVAILABLE||w!=Availability.AVAILABLE?"facility_"+(h==Availability.UNLOADED||w==Availability.UNLOADED?"unloaded":"blocked")
                        :resident.isPresent()?"assigned":"await_eligible_local_person_or_retired_role";
                }
            }
            row.addProperty("state",condition);roles.add(row);
        }
        result.add("roles",roles);return result;
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
            var fixtureState=level.getBlockState(fixture);
            if (!BuiltInRegistries.BLOCK.getKey(fixtureState.getBlock()).toString().equals(place.block()))
                return Availability.BLOCKED;
            if(fixtureState.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                    &&fixtureState.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED))return Availability.BLOCKED;
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
