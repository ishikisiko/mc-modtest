package com.example.myvillage.worldplan.travel;

import com.example.myvillage.combat.foundation.ControlService;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.network.AtlasCityTravelPayload;
import com.example.myvillage.story.realm.QianzhenRealms;
import com.example.myvillage.story.scene.QianzhenScene;
import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.capital.CapitalCity;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main-thread, bounded travel jobs. Tickets schedule loading; tick polls without blocking futures. */
public final class AtlasCityTravelService {
    private static final Logger LOG = LoggerFactory.getLogger(AtlasCityTravelService.class);
    // Distance zero requests FULL availability without block/entity ticking the city.
    private static final int TICKET_DISTANCE = 0;
    private static TicketType<UUID> ticketType;
    private static final Map<UUID,Job> JOBS = new LinkedHashMap<>();
    private static final AtlasCityTravelLimiter LIMITER = new AtlasCityTravelLimiter();
    private static MinecraftServer running;
    private static AtlasCityTravelRules rules;
    private static AtlasCityTravelSafety safety;
    private static long clock;
    private AtlasCityTravelService() { }

    private static final class Job {
        final ServerPlayer player;
        final ServerLevel level;
        final WorldPlan plan;
        final CapitalCity city;
        final TicketType<UUID> ticket;
        final List<AtlasCityLanding.Point> candidates;
        final List<ChunkPos> tickets = new ArrayList<>();
        final long started;
        int next;
        Job(ServerPlayer player, WorldPlan plan, CapitalCity city) {
            this.player=player; this.level=player.serverLevel(); this.plan=plan; this.city=city; this.started=clock;
            this.ticket=ticketType;
            var at=city.entrance();
            candidates=AtlasCityLanding.candidates(new AtlasCityLanding.Point(at.x(),at.y(),at.z()),rules);
        }
    }

    static void started(MinecraftServer server) {
        stop(); running=server; clock=0;
        try (var in=server.getResourceManager().open(ResourceLocation.parse(AtlasCityTravelRules.RESOURCE));
             var reader=new InputStreamReader(in,StandardCharsets.UTF_8)) {
            rules=AtlasCityTravelRules.parse(JsonParser.parseReader(reader).getAsJsonObject());
            safety=new AtlasCityTravelSafety(rules);
            // Expiration is a bounded fallback even if an unexpected removal failure is logged.
            ticketType=TicketType.create("myvillage_atlas_city_travel",UUID::compareTo,rules.loadTimeoutTicks());
            LOG.info("ATLAS_TRAVEL state=ready enabled={} max_active={} max_chunks={} resource={}",
                    rules.enabled(),rules.maxConcurrentRequests(),rules.maxChunksPerRequest(),AtlasCityTravelRules.RESOURCE);
        } catch (Exception failure) {
            rules=null; safety=null; ticketType=null;
            LOG.error("ATLAS_TRAVEL state=disabled reason=config_invalid resource={}",AtlasCityTravelRules.RESOURCE,failure);
        }
    }

    public static void request(ServerPlayer player, AtlasCityTravelPayload intent) {
        MinecraftServer server=player.getServer();
        if (server==null || server!=running || !server.isSameThread()) return;
        if (!online(player)) return;
        if (rules==null) { say(player,"unavailable"); return; }
        var decision=LIMITER.admit(player.getUUID(),clock,rules);
        if (decision!=AtlasCityTravelLimiter.Decision.ACCEPTED) {
            String reason=switch(decision) { case IN_FLIGHT -> "in_flight"; case FULL -> "busy"; default -> "rate_limited"; };
            denied(player,intent.cityId(),reason); return;
        }
        try {
            String problem=playerProblem(player);
            if (problem!=null) { refuse(player,intent.cityId(),problem); return; }
            var generator=WorldPlanRuntime.generator(player.serverLevel()).orElse(null);
            if (generator==null) { refuse(player,intent.cityId(),"unavailable"); return; }
            WorldPlan plan=generator.plan();
            if (!plan.fingerprint().equals(intent.atlasFingerprint())) { refuse(player,intent.cityId(),"stale"); return; }
            CapitalCity city=AtlasCityTravelTarget.resolve(plan,intent.cityId(),intent.atlasFingerprint()).orElse(null);
            if (city==null) { refuse(player,intent.cityId(),"invalid_city"); return; }
            var entry=city.entrance();
            var point=new AtlasCityLanding.Point(entry.x(),entry.y(),entry.z());
            if (!player.serverLevel().getWorldBorder().isWithinBounds(AtlasCityTravelSafety.standingBody(player,point))) {
                refuse(player,intent.cityId(),"border"); return;
            }
            List<AtlasCityLanding.Chunk> chunks=AtlasCityLanding.chunks(point,rules);
            Job job=new Job(player,plan,city);
            JOBS.put(player.getUUID(),job);
            for (var chunk : chunks) {
                ChunkPos at=new ChunkPos(chunk.x(),chunk.z());
                // Record before acquisition so even a partially failed acquisition is fully released.
                job.tickets.add(at);
                job.level.getChunkSource().addRegionTicket(job.ticket,at,TICKET_DISTANCE,player.getUUID());
            }
            say(player,"preparing",city.bundle().displayName);
            LOG.info("ATLAS_TRAVEL player={} city={} plan={} state=loading chunks={} entry={} {} {}",
                    player.getUUID(),city.cityId(),plan.fingerprint(),chunks.size(),entry.x(),entry.y(),entry.z());
        } catch (RuntimeException failure) {
            Job job=JOBS.get(player.getUUID());
            if (job!=null) finish(job,"failed",null); else LIMITER.release(player.getUUID());
            LOG.error("ATLAS_TRAVEL player={} city={} state=failed reason=request_exception",player.getUUID(),intent.cityId(),failure);
            say(player,"failed");
        }
    }

    private static boolean online(ServerPlayer player) {
        return !player.hasDisconnected() && player.connection!=null && running!=null
                && running.getPlayerList().getPlayer(player.getUUID())==player;
    }
    private static String playerProblem(ServerPlayer player) {
        if (!online(player)) return "offline";
        if (!player.level().dimension().equals(Level.OVERWORLD)) return "other_dimension";
        if (!player.isAlive() || player.isRemoved() || player.isSleeping() || player.isPassenger() || player.isVehicle()
                || player.isChangingDimension() || player.isFallFlying() || player.isSpectator()
                || player.getCamera()!=player || ControlService.isRooted(player) || MeditationManager.active(player)) return "player_state";
        if (!rules.enabled()) return "disabled";
        if (!running.getWorldData().worldGenOptions().generateStructures()) return "structures_disabled";
        // A pending personal entrance must retain its own lifecycle and original return point.
        if (QianzhenRealms.existing(player).filter(level -> QianzhenScene.phase(level).equals("PLACING")).isPresent())
            return "realm_pending";
        return null;
    }

    static void tick(MinecraftServer server) {
        if (server!=running || rules==null) return;
        clock++; LIMITER.prune(clock);
        int budget=rules.candidateChecksPerTick();
        for (Job job : List.copyOf(JOBS.values())) {
            if (JOBS.get(job.player.getUUID())!=job) continue;
            try {
                String problem=playerProblem(job.player);
                if (problem!=null) { finish(job,problem,problem); continue; }
                var current=WorldPlanRuntime.generator(job.level).map(g -> g.plan()).orElse(null);
                if (job.player.serverLevel()!=job.level || current==null
                        || AtlasCityTravelTarget.resolve(current,job.city.cityId(),job.plan.fingerprint()).isEmpty()) {
                    finish(job,"stale","stale"); continue;
                }
                if (clock-job.started>=rules.loadTimeoutTicks()) { finish(job,"timeout","timeout"); continue; }
                if (!loaded(job)) continue;
                while (budget>0 && job.next<job.candidates.size()) {
                    budget--;
                    var point=job.candidates.get(job.next++);
                    if (!safety.safe(job.level,job.player,point)) continue;
                    // All mutable checks and the final actual-block check remain on the server thread.
                    problem=playerProblem(job.player);
                    if (problem!=null) { finish(job,problem,problem); break; }
                    boolean moved=job.player.teleportTo(job.level,point.x()+.5,point.y(),point.z()+.5,Set.of(),
                            entryYaw(job.city),0);
                    if (moved) {
                        job.player.setDeltaMovement(Vec3.ZERO); job.player.fallDistance=0;
                        job.player.closeContainer();
                        LOG.info("ATLAS_TRAVEL player={} city={} plan={} state=arrived x={} y={} z={} checked={}",
                                job.player.getUUID(),job.city.cityId(),job.plan.fingerprint(),point.x()+.5,point.y(),point.z()+.5,job.next);
                        finish(job,"arrived",null); say(job.player,"arrived",job.city.bundle().displayName);
                    } else finish(job,"failed","failed");
                    break;
                }
                if (JOBS.get(job.player.getUUID())==job && job.next==job.candidates.size())
                    finish(job,"no_safe_landing","no_safe_landing");
            } catch (RuntimeException failure) {
                LOG.error("ATLAS_TRAVEL player={} city={} state=failed reason=tick_exception",job.player.getUUID(),job.city.cityId(),failure);
                finish(job,"failed","failed");
            }
        }
    }

    private static float entryYaw(CapitalCity city) {
        return switch(city.bundle().entrance().facing()) {
            case "north" -> 180; case "east" -> -90; case "west" -> 90; default -> 0;
        };
    }
    private static boolean loaded(Job job) {
        for (ChunkPos at : job.tickets) if (job.level.getChunkSource().getChunkNow(at.x,at.z)==null) return false;
        return true;
    }
    private static void refuse(ServerPlayer player, String city, String reason) {
        LIMITER.release(player.getUUID()); denied(player,city,reason);
    }
    private static void denied(ServerPlayer player, String city, String reason) {
        if (!LIMITER.feedback(player.getUUID(),clock,rules)) return;
        say(player,reason);
        LOG.info("ATLAS_TRAVEL player={} city={} state=denied reason={}",player.getUUID(),city,reason);
    }
    private static void finish(Job job, String reason, String message) {
        JOBS.remove(job.player.getUUID(),job); LIMITER.release(job.player.getUUID());
        int count=job.tickets.size(), failed=0;
        for (ChunkPos at : job.tickets) {
            try { job.level.getChunkSource().removeRegionTicket(job.ticket,at,TICKET_DISTANCE,job.player.getUUID()); }
            catch (RuntimeException failure) {
                failed++;
                LOG.error("ATLAS_TRAVEL player={} city={} state=ticket_release_failed chunk={}",job.player.getUUID(),job.city.cityId(),at,failure);
            }
        }
        job.tickets.clear();
        LOG.info("ATLAS_TRAVEL player={} city={} state={} reason={} tickets={} removed={} removal_failures={} active={}",
                job.player.getUUID(),job.city.cityId(),failed==0?"released":"release_incomplete",reason,count,count-failed,failed,JOBS.size());
        if (message!=null && online(job.player)) say(job.player,message);
    }
    static void forget(UUID id, String reason) {
        Job job=JOBS.get(id);
        if (job!=null) finish(job,reason,null);
        LIMITER.forget(id);
    }
    static void stop() {
        for (Job job : List.copyOf(JOBS.values())) finish(job,"server_stopping",null);
        JOBS.clear(); LIMITER.clear(); rules=null; safety=null; ticketType=null; running=null; clock=0;
    }
    private static void say(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.myvillage.atlas.travel."+key,args),false);
    }
}
