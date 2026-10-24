package com.example.myvillage.story.npc;

import com.example.myvillage.entity.npc.NpcCombatRuntime;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.life.LifePlace;
import com.example.myvillage.sim.life.LifeSite;
import com.example.myvillage.sim.life.StoryActorBinding;
import com.example.myvillage.sim.life.StoryCastDefinition;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.avatar.WorldSimAvatars;
import com.example.myvillage.story.scene.QianzhenScene;
import com.example.myvillage.story.realm.QianzhenRealms;
import com.example.myvillage.story.scene.SceneLayout;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Scene adapter only. WorldSim owns identity, shared rescue state and execution epochs. */
public final class QianzhenActors {
    private static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath("myvillage", "story/qianzhen_actors.json");
    private static MinecraftServer configuredServer;
    private static QianzhenActorDefinitions definitions;
    private static boolean registered;

    private QianzhenActors() {}

    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(QianzhenActors::starting);
        NeoForge.EVENT_BUS.addListener(QianzhenActors::stopping);
    }

    private static void starting(ServerStartingEvent event) { definitions(event.getServer()); }
    private static void stopping(ServerStoppingEvent event) { configuredServer = null; definitions = null; }

    private static QianzhenActorDefinitions definitions(MinecraftServer server) {
        if (server == configuredServer && definitions != null) return definitions;
        var resource = server.getResourceManager().getResource(RESOURCE)
                .orElseThrow(() -> new IllegalStateException("缺少阡镇人物定义：" + RESOURCE));
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            var parsed = QianzhenActorDefinitions.read(reader);
            definitions = parsed; configuredServer = server;
            return parsed;
        } catch (IOException e) { throw new IllegalStateException("无法读取阡镇人物定义", e); }
    }

    /** Called at ledger startup and before daily settlement, independently of player login. */
    public static void ensureAll(MinecraftServer server) {
        definitions(server); // Strictly load even before a scene is deployed. No hot reload promise.
        for (ServerLevel level : server.getAllLevels()) ensure(level);
    }

    public static void ensure(ServerLevel level) {
        var placement = QianzhenScene.at(level).orElse(null);
        if (placement == null) return;
        WorldSim sim = WorldSimRuntime.sim().orElse(null);
        if (sim == null) return;
        var cast = cast(level, sim, placement, key -> QianzhenScene.position(level, key)
                .orElseThrow(() -> new IllegalStateException("缺少阡镇人物脚部站位：" + key)));
        if (sim.ensureStoryCast(cast)) dirty(level);
    }

    /** Before scene reservation or chunk access: validate the proposed cast without any ledger/world write. */
    public static void validateDeployment(ServerLevel level, SceneLayout layout, BlockPos origin) {
        WorldSim sim = WorldSimRuntime.sim()
                .orElseThrow(() -> new IllegalStateException("命簿尚未就绪，不能部署阡镇人物"));
        var placement = new QianzhenScene.Placement(layout.id(), origin, layout.version());
        sim.validateStoryCast(cast(level, sim, placement, key -> {
            var station = layout.positions().get(key);
            if (station == null) throw new IllegalStateException("缺少阡镇人物脚部站位：" + key);
            return origin.offset(station.pos().x(), station.pos().y(), station.pos().z());
        }));
    }

    /** Same definition, geography and site signature for proposed and completed placements. */
    private static StoryCastDefinition cast(ServerLevel level, WorldSim sim, QianzhenScene.Placement placement,
                                             Function<String, BlockPos> position) {
        var defs = definitions(level.getServer());
        if (!placement.id().equals(defs.sceneId())) throw new IllegalStateException("阡镇人物与场景 ID 不匹配");
        BlockPos origin = placement.origin();
        String region = QianzhenRealms.region(level).or(() -> RegionRuntimeService.regionAt(origin.getX(), origin.getZ())).filter(sim::hasRegion)
                .orElseThrow(() -> new IllegalStateException("阡镇场景原点不在有效命簿地区"));
        var places = new ArrayList<LifePlace>();
        for (var actor : defs.actors()) {
            BlockPos initial = position.apply(actor.initialPlaceId());
            places.add(LifePlace.free(actor.initialPlaceId(), "social", initial.getX(), initial.getY(), initial.getZ()));
            places.add(LifePlace.free(actor.homeId(), "rest", initial.getX(), initial.getY(), initial.getZ()));
            if (!actor.returnPlaceId().isEmpty()) {
                BlockPos destination = position.apply(actor.returnPlaceId());
                places.add(LifePlace.free(actor.returnPlaceId(), "social", destination.getX(), destination.getY(), destination.getZ()));
            }
        }
        for (LifePlace place : places) {
            if (place.y() <= level.getMinBuildHeight() || place.y() + 2 >= level.getMaxBuildHeight()
                    || !QianzhenRealms.privateLevel(level) && !region.equals(RegionRuntimeService.regionAt(place.x(), place.z()).orElse("")))
                throw new IllegalStateException("阡镇人物站位越出地区/高度边界：" + place.id());
        }
        // Placement version is part of the immutable site signature; changing deployment cannot silently move a cast.
        var site = new LifeSite(QianzhenRealms.castId(level, defs.siteId()), defs.siteName() + "·" + placement.version(),
                level.dimension().location().toString(), region, origin.getX(), origin.getY(), origin.getZ(), places);
        return new StoryCastDefinition(QianzhenRealms.castId(level, defs.storyId()), site, defs.actors());
    }

    public static String role(NpcEntity npc) {
        if (!(npc.level() instanceof ServerLevel level) || !npc.isLedgerAvatar()) return "";
        var defs = definitions(level.getServer());
        return WorldSimRuntime.sim().flatMap(sim -> sim.storyActorForPerson(npc.ledgerPersonId()))
                .filter(actor -> actor.storyId().equals(QianzhenRealms.castId(level, defs.storyId()))).map(StoryActorBinding::actorKey).orElse("");
    }

    /** Exact shared entity plus BOTH live leases; copied NBT and old bodies cannot speak as the actor. */
    public static boolean current(NpcEntity npc) {
        if (!(npc.level() instanceof ServerLevel level) || npc.isRemoved() || !npc.isAlive() || role(npc).isEmpty()) return false;
        var sim = WorldSimRuntime.sim().orElse(null);
        if (sim == null || QianzhenScene.at(level).isEmpty() || !NpcCombatRuntime.managed(npc)) return false;
        var actor = sim.storyActorForPerson(npc.ledgerPersonId()).orElse(null);
        var life = sim.life(npc.ledgerPersonId()).orElse(null);
        return actor != null && life != null && life.embodied() && life.siteId().equals(actor.siteId())
                && sim.lifeSite(actor.siteId()).filter(s -> s.dimension().equals(level.dimension().location().toString())).isPresent()
                && WorldSimAvatars.isManagedLife(npc, life.epoch());
    }

    private static Optional<StoryActorBinding> actor(ServerLevel level, String role) {
        if (QianzhenScene.at(level).isEmpty()) return Optional.empty();
        var defs = definitions(level.getServer());
        return WorldSimRuntime.sim().flatMap(sim -> sim.storyActor(QianzhenRealms.castId(level, defs.storyId()), role))
                .filter(actor -> WorldSimRuntime.sim().flatMap(sim -> sim.lifeSite(actor.siteId()))
                        .filter(site -> site.dimension().equals(level.dimension().location().toString())).isPresent());
    }

    public static int personId(ServerLevel level, String role) { return actor(level, role).map(StoryActorBinding::personId).orElse(-1); }

    public static Optional<BlockPos> position(ServerLevel level, String role) {
        return actor(level, role).flatMap(actor -> WorldSimRuntime.sim().flatMap(sim -> sim.lifeSite(actor.siteId()))
                .flatMap(site -> site.place(actor.currentPlaceId())).map(place -> new BlockPos(place.x(), place.y(), place.z())));
    }

    public static boolean returned(ServerLevel level) { return actor(level, "zhou_he").map(StoryActorBinding::returned).orElse(false); }

    /** Shared arrangement to return home, not a claim of physically walking an escort route. */
    public static boolean returnZhouHe(ServerLevel level) {
        var actor = actor(level, "zhou_he").orElse(null);
        if (actor == null) return false;
        if (actor.returned()) return true;
        var sim = WorldSimRuntime.sim().orElse(null);
        if (sim == null || !WorldSimAvatars.withdrawPerson(actor.personId(), "qianzhen_rescue")) return false;
        boolean moved = sim.returnStoryActor(actor.storyId(), actor.actorKey(), actor.revision());
        if (moved) dirty(level);
        // Normal ensureLife owns re-projection when a player/chunk/capacity permits. Never load the destination here.
        return moved;
    }

    private static void dirty(ServerLevel level) { WorldSimSavedData.get(level.getServer().overworld()).setDirty(); }
}
