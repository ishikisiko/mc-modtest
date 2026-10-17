package com.example.myvillage.sim.runtime.life;

import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.life.LifePlace;
import com.example.myvillage.sim.life.LifeResidentView;
import com.example.myvillage.sim.life.LifeRules;
import com.example.myvillage.sim.life.LifeSite;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.avatar.WorldSimAvatars;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Executes one ledger resident's movement. It never awards work, changes identity, creates an
 * entity, loads a chunk, or decides a new activity. Epoch and revision checks belong to WorldSim.
 */
public final class NpcLifeGoal extends Goal {
    /** Vanilla coordinate/block navigation allocates FOLLOW_RANGE + 8 before pathfinding. */
    private static final int VANILLA_PATH_PADDING = 8;

    private final NpcEntity npc;
    private final long epoch;
    private final double nativeFollowRange;
    private long revision = Long.MIN_VALUE;
    private long failedRevision = Long.MIN_VALUE;
    private long movingTicks;
    private long stillTicks;
    private long untilRepath;
    private Vec3 lastProgress;
    private BlockPos routeEntry;
    private final Set<BlockPos> visitedEntries = new HashSet<>();

    public NpcLifeGoal(NpcEntity npc, long epoch) {
        this.npc = npc;
        this.epoch = epoch;
        this.nativeFollowRange = npc.getAttributeValue(Attributes.FOLLOW_RANGE);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(npc.level() instanceof ServerLevel) || npc.isRemoved() || !npc.isAlive()
                || !WorldSimAvatars.isManagedLife(npc, epoch)) {
            return false;
        }
        return WorldSimRuntime.sim().flatMap(sim -> sim.life(npc.ledgerPersonId()))
                .filter(life -> life.embodied() && life.epoch() == epoch).isPresent();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        untilRepath = 0;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
    }

    /**
     * Rechecks only a current job immediately before the ledger settles time. This also covers a
     * newly projected worker that has not received an entity tick yet; it never advances movement
     * timers or begins a path a second time.
     */
    public void validateWorkplaceBeforeSettlement() {
        if (!canUse()) return;
        boolean working = WorldSimRuntime.sim().flatMap(sim -> sim.life(npc.ledgerPersonId()))
                .filter(life -> "working".equals(life.phase())).isPresent();
        if (working) tick();
    }

    @Override
    public void tick() {
        WorldSim sim = WorldSimRuntime.sim().orElse(null);
        if (sim == null || !(npc.level() instanceof ServerLevel level)) {
            stop();
            return;
        }
        LifeResidentView life = sim.life(npc.ledgerPersonId()).orElse(null);
        if (life == null || !life.embodied() || life.epoch() != epoch
                || !WorldSimAvatars.isManagedLife(npc, epoch)) {
            stop();
            return;
        }
        if (revision != life.revision()) {
            revision = life.revision();
            movingTicks = 0;
            stillTicks = 0;
            untilRepath = 0;
            lastProgress = npc.position();
            routeEntry = null;
            visitedEntries.clear();
            npc.getNavigation().stop();
        }
        if (sim.scheduler().paused() || life.pauseTicks() > 0 || "waiting".equals(life.phase())
                || failedRevision == revision) {
            stop();
            untilRepath = 0;
            return;
        }

        var site = sim.lifeSite(life.siteId()).orElse(null);
        LifePlace target = site == null ? null : site.place(life.targetId()).orElse(null);
        if (target == null || !site.dimension().equals(level.dimension().location().toString())) {
            fail(sim, level, life, "stale_target");
            return;
        }
        LifeRules rules = sim.lifeRules();
        var availability = NpcLifeSites.availability(level, target);
        if (sim.setLifePlaceAvailability(life.siteId(), target.id(), availability.id())) {
            dirty(level);
        }
        if ("unloaded".equals(availability.id())) {
            stop();
            untilRepath = 0;
            if ("working".equals(life.phase())) {
                // An embodied job cannot keep earning while its workplace cannot be checked.
                // Ordinary distance withdrawal preserves progress through releaseLife instead.
                fail(sim, level, life, "target_unloaded");
                return;
            }
            if (++movingTicks >= rules.pathTimeoutTicks()) {
                fail(sim, level, life, "target_unloaded");
            }
            return;
        }
        if (!"available".equals(availability.id())) {
            String cause = !target.block().isEmpty()
                    && level.getChunkSource().getChunkNow(Math.floorDiv(target.blockX(), 16),
                            Math.floorDiv(target.blockZ(), 16)) != null
                    && !target.block().equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(
                            new BlockPos(target.blockX(), target.blockY(), target.blockZ())).getBlock()).toString())
                    ? "facility_missing" : "target_blocked";
            fail(sim, level, life, cause);
            return;
        }

        double distance = npc.distanceToSqr(target.x() + .5, target.y(), target.z() + .5);
        double arrival = rules.arrivalDistance();
        // The ledger checkpoints whole feet blocks. Enter that block before committing arrival,
        // so a later projection at its center cannot restart in an adjacent, out-of-range cell.
        if (distance <= arrival * arrival && npc.blockPosition().equals(new BlockPos(target.x(), target.y(), target.z()))) {
            stop();
            if ("moving".equals(life.phase())) {
                boolean changed = WorldSimAvatars.checkpointLifeAtSafeFeet(sim, npc, epoch);
                changed |= sim.arriveLife(life.personId(), epoch, revision);
                if (changed) dirty(level);
            }
            return;
        }
        if ("working".equals(life.phase())) {
            // Work never continues after a body is moved away from its actual workplace.
            fail(sim, level, life, "target_blocked");
            return;
        }
        if (!"moving".equals(life.phase())) {
            stop();
            return;
        }
        if (distance > rules.maxPathDistance() * rules.maxPathDistance()) {
            fail(sim, level, life, "too_far");
            return;
        }
        if (++movingTicks >= rules.pathTimeoutTicks()) {
            fail(sim, level, life, "path_timeout");
            return;
        }

        // Guard the whole actual vanilla navigation cache, not the configured maximum trip length.
        // A shrinking loaded area triggers a smaller local route instead of requiring far chunks.
        if (!loadedPathArea(level, npc.getAttributeValue(Attributes.FOLLOW_RANGE))) {
            stop();
            untilRepath = 0;
        }
        double progressDistance = Math.min(arrival, npc.getBbWidth());
        if (lastProgress == null || npc.position().distanceToSqr(lastProgress)
                >= progressDistance * progressDistance) {
            stillTicks = 0;
            lastProgress = npc.position();
        } else if (++stillTicks >= rules.stuckTicks()) {
            fail(sim, level, life, "stuck");
            return;
        }
        if (--untilRepath <= 0) {
            untilRepath = rules.pathRetryTicks();
            if (!fitPathRange(level, rules, Math.sqrt(distance))) {
                stop();
                return;
            }
            // The four-argument overload uses block accuracy 1 and may finish at an adjacent
            // block. A LifePlace is a feet position: only a path to its own block can arrive.
            if (!moveAlongLoadedRoute(level, site, target, rules)) {
                // Failed path creation is not a successful arrival; bounded retries precede failure.
                if (stillTicks + rules.pathRetryTicks() >= rules.stuckTicks()) {
                    fail(sim, level, life, "path_unavailable");
                }
            }
        }
    }

    /**
     * A partial vanilla path minimizes straight-line distance, which can strand a resident at an
     * upstairs wall: reaching the stairs first means walking away from the final target. Try the
     * site's authored entry stations as reachable intermediate destinations. These are movement
     * waypoints only; only the original target's exact arrival check above may start paid work.
     * All searches use the same already-checked loaded cache and bounded node budget.
     */
    private boolean moveAlongLoadedRoute(ServerLevel level, LifeSite site, LifePlace target, LifeRules rules) {
        if (routeEntry != null && npc.blockPosition().equals(routeEntry)
                && npc.position().distanceToSqr(Vec3.atBottomCenterOf(routeEntry))
                    <= rules.arrivalDistance() * rules.arrivalDistance()) {
            visitedEntries.add(routeEntry);
            routeEntry = null;
        }
        if (routeEntry != null) {
            return npc.getNavigation().moveTo(routeEntry.getX() + .5, routeEntry.getY(),
                    routeEntry.getZ() + .5, 0, rules.walkSpeed());
        }
        // A same-target unfinished path is cached by vanilla, including a dead-end partial path.
        npc.getNavigation().stop();
        BlockPos destination = new BlockPos(target.x(), target.y(), target.z());
        Path direct = npc.getNavigation().createPath(destination, 0);
        if (direct != null && direct.canReach()) return npc.getNavigation().moveTo(direct, rules.walkSpeed());

        var entries = site.places().stream().filter(p -> p.kind().equals("entry"))
                .filter(p -> NpcLifeSites.availability(level, p) == NpcLifeSites.Availability.AVAILABLE)
                .map(p -> new BlockPos(p.x(), p.y(), p.z()))
                .filter(p -> !p.equals(destination) && !p.equals(npc.blockPosition()) && !visitedEntries.contains(p))
                .filter(p -> npc.position().distanceTo(Vec3.atBottomCenterOf(p)) <= rules.maxPathDistance())
                .sorted(Comparator.comparingDouble((BlockPos p) ->
                        npc.position().distanceTo(Vec3.atBottomCenterOf(p)) + Math.sqrt(p.distSqr(destination)))
                        .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ))
                .toList();
        for (BlockPos entry : entries) {
            Path path = npc.getNavigation().createPath(entry, 0);
            if (path != null && path.canReach()) {
                routeEntry = entry;
                return npc.getNavigation().moveTo(path, rules.walkSpeed());
            }
        }
        return npc.getNavigation().moveTo(direct, rules.walkSpeed());
    }

    private void fail(WorldSim sim, ServerLevel level, LifeResidentView life, String reason) {
        stop();
        if (failedRevision == life.revision()) return;
        failedRevision = life.revision();
        boolean changed = WorldSimAvatars.checkpointLifeAtSafeFeet(sim, npc, epoch);
        changed |= sim.failLife(life.personId(), epoch, life.revision(), reason);
        if (changed) dirty(level);
    }

    /**
     * Choose a useful local search, then reduce it by whole chunk widths until its cache is loaded.
     * Vanilla may return a partial path for a distant target; later searches continue it, while only
     * the distance check against the real LifePlace can commit arrival.
     */
    private boolean fitPathRange(ServerLevel level, LifeRules rules, double targetDistance) {
        double minimum = Math.min(nativeFollowRange, rules.maxPathDistance());
        double wanted = Math.min(rules.maxPathDistance(), Math.max(minimum, targetDistance + nativeFollowRange));
        for (double range = wanted; ; range = Math.max(minimum, range - 16)) {
            if (loadedPathArea(level, range)) {
                var follow = npc.getAttribute(Attributes.FOLLOW_RANGE);
                if (follow != null) follow.setBaseValue(range);
                // PathFinder's initial node budget was fixed when navigation was constructed.
                // Scale that bounded budget with the loaded range used for this local search.
                npc.getNavigation().setMaxVisitedNodesMultiplier((float) Math.max(1.0,
                        range / Math.max(1.0, nativeFollowRange)));
                return true;
            }
            if (range <= minimum) return false;
        }
    }

    private boolean loadedPathArea(ServerLevel level, double followRange) {
        double reach = followRange + VANILLA_PATH_PADDING;
        return WorldSimAvatars.loadedArea(level, new AABB(npc.getX() - reach, npc.getY(), npc.getZ() - reach,
                npc.getX() + reach, npc.getY() + npc.getBbHeight(), npc.getZ() + reach));
    }

    private static void dirty(ServerLevel level) {
        WorldSimSavedData.get(level.getServer().overworld()).setDirty();
    }
}
