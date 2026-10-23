package com.example.myvillage.combat.foundation;

import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.social.ActorRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** 服务器暂态领域；不持久化、不加载区块、不接收客户端参数。 */
public final class FieldRuntime {
    private record Instance(long id, UUID source, ResourceKey<Level> dimension, FieldSpec spec, long expiresAt) {}
    private static final class State {
        long nextId = 1;
        final Map<Long, Instance> instances = new LinkedHashMap<>();
        final FieldLedger ledger = new FieldLedger();
    }
    public record Snapshot(long instanceId, UUID source, ResourceLocation kind, ResourceLocation effect,
                           ResourceLocation dimension, FieldSpec.Anchor anchor, Vec3 center, double radius,
                           long expiresAt, int remainingTicks, int pulseIntervalTicks, int trackedTargets,
                           FieldSpec.Effect effectType) {}
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static boolean registered;
    private FieldRuntime() {}

    public static void register() {
        if (registered) return;
        FieldRules.bundled();
        registered = true;
        NeoForge.EVENT_BUS.addListener(FieldRuntime::onTick);
        NeoForge.EVENT_BUS.addListener(FieldRuntime::onDeath);
        NeoForge.EVENT_BUS.addListener(FieldRuntime::onLeave);
        NeoForge.EVENT_BUS.addListener(FieldRuntime::onLogout);
        NeoForge.EVENT_BUS.addListener(FieldRuntime::onDimension);
        NeoForge.EVENT_BUS.addListener(FieldRuntime::onRespawn);
        NeoForge.EVENT_BUS.addListener(FieldRuntime::onStopping);
    }

    public static FieldSpec woodRoot(double radius) { return FieldRules.bundled().woodRoot(radius); }
    public static FieldSpec healing(ResourceLocation kind, double radius, int lifetimeTicks,
                                    int pulseIntervalTicks, int maximumTargets, float amount) {
        return new FieldSpec(kind, kind, FieldSpec.Anchor.FOLLOW_SOURCE, null, radius, lifetimeTicks,
                pulseIntervalTicks, maximumTargets, FieldSpec.Effect.HEAL, 0, amount, false, FieldSpec.Targeting.ALLIES);
    }

    /** 必须在服务器线程先校验后同步扣费/创建；空场可成功，不提前触发目标效果。 */
    public static boolean canSpawn(ServerPlayer source, FieldSpec spec) {
        if (source == null || source.getServer() == null || !source.getServer().isSameThread()
                || !ownerActive(source.getServer(), source) || !FieldRules.bundled().valid(spec)) return false;
        var server = source.getServer();
        long now = clock(server);
        if (now > Long.MAX_VALUE - spec.lifetimeTicks()) return false;
        var center = center(source, spec);
        var level = source.serverLevel();
        if (!insideLoadedWorld(level, center)) return false;
        if (spec.anchor() == FieldSpec.Anchor.FIXED
                && (source.getBoundingBox().getCenter().distanceToSqr(center)
                    > FieldRules.bundled().maximumRadius() * FieldRules.bundled().maximumRadius()
                    || !visible(level, source, source.getEyePosition(), center))) return false;
        var state = STATES.get(server);
        return state == null || state.ledger.canAdd(source.getUUID(), now,
                FieldRules.bundled().maximumInstances(), FieldRules.bundled().maximumPerSource());
    }

    public static boolean spawn(ServerPlayer source, FieldSpec spec) {
        if (!canSpawn(source, spec)) return false;
        var server = source.getServer();
        var state = STATES.computeIfAbsent(server, ignored -> new State());
        long now = clock(server), id = state.nextId++;
        // 同源、同种类、同效果类型共享回执；effect ID 不能用来绕过重叠去重。
        state.ledger.add(id, new FieldLedger.Key(source.getUUID(), spec.kind().toString(),
                        spec.effectType().name(), source.level().dimension().location().toString()), now,
                spec.lifetimeTicks(), spec.pulseIntervalTicks(), spec.maximumTargets(), spec.oncePerTarget());
        state.instances.put(id, new Instance(id, source.getUUID(), source.level().dimension(), spec,
                now + spec.lifetimeTicks()));
        return true;
    }

    public static List<Snapshot> snapshot(ServerPlayer source) {
        if (source == null || source.getServer() == null) return List.of();
        return snapshot(source.getServer()).stream().filter(row -> row.source().equals(source.getUUID())).toList();
    }
    public static List<Snapshot> snapshot(MinecraftServer server) {
        var state = STATES.get(server);
        if (state == null) return List.of();
        long now = clock(server);
        return state.instances.values().stream().filter(field -> active(server, field, now)).map(field -> {
            var owner = server.getPlayerList().getPlayer(field.source());
            return new Snapshot(field.id(), field.source(), field.spec().kind(), field.spec().effect(),
                    field.dimension().location(), field.spec().anchor(), center(owner, field.spec()),
                    field.spec().radius(), field.expiresAt(), (int) (field.expiresAt() - now),
                    field.spec().pulseIntervalTicks(), state.ledger.trackedTargets(field.id()), field.spec().effectType());
        }).toList();
    }
    public static int count(ServerPlayer source) { return snapshot(source).size(); }
    public static int count(MinecraftServer server) { return snapshot(server).size(); }

    public static void tick(MinecraftServer server) {
        var state = STATES.get(server);
        if (state == null) return;
        long now = clock(server);
        int particleBudget = FieldRules.bundled().particlesPerTick();
        for (var field : List.copyOf(state.instances.values())) {
            if (!active(server, field, now)) { remove(state, field.id()); continue; }
            var owner = server.getPlayerList().getPlayer(field.source());
            var level = owner.serverLevel();
            var spec = field.spec();
            var center = center(owner, spec);
            if (state.ledger.pulse(field.id(), now)) {
                // 实体空间索引只返回已加载实体；可见性射线也先检查沿途区块。
                var targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(spec.radius()),
                        target -> eligible(owner, target, spec, center));
                targets.sort(Comparator.comparingDouble((LivingEntity target) -> target.distanceToSqr(center))
                        .thenComparingInt(Entity::getId));
                for (var target : targets) {
                    // 回调可能触发其他事件并清理领域；每个目标处理前再次检查实例和权威资格。
                    if (state.instances.get(field.id()) != field || !active(server, field, now)) break;
                    if (!eligible(owner, target, spec, center) || !state.ledger.claim(field.id(), target.getUUID(), now)) continue;
                    switch (spec.effectType()) {
                        case ROOT -> ControlService.root(owner, target, spec.effect(), spec.effectTicks());
                        case SLOW -> ControlService.slow(owner, target, spec.effect(), spec.effectTicks(), spec.magnitude());
                        case HEAL -> HealingRuntime.heal(target, spec.magnitude(), spec.effect());
                    }
                }
            }
            if (state.instances.get(field.id()) == field && now % FieldRules.bundled().particleInterval() == 0
                    && particleBudget > 0) {
                int amount = Math.min(particleBudget, FieldRules.bundled().particlesPerField());
                // 低密度原版粒子，确定点分布，避免按领域体积增长发包量。
                for (int i = 0; i < amount; i++) {
                    double angle = Math.PI * 2 * i / amount;
                    Vec3 point = center.add(Math.cos(angle) * spec.radius(), 0, Math.sin(angle) * spec.radius());
                    if (!insideLoadedWorld(level, point)) continue;
                    level.sendParticles(spec.effectType() == FieldSpec.Effect.HEAL ? ParticleTypes.HAPPY_VILLAGER
                                    : ParticleTypes.COMPOSTER, point.x, point.y, point.z, 1, 0, 0, 0, 0);
                }
                particleBudget -= amount;
            }
        }
        if (state.instances.isEmpty()) STATES.remove(server);
    }

    private static boolean eligible(ServerPlayer source, LivingEntity target, FieldSpec spec, Vec3 center) {
        if (!legal(source, target, spec) || target.getBoundingBox().getCenter().distanceToSqr(center)
                > spec.radius() * spec.radius()
                || !visible(source.serverLevel(), source, center, target.getBoundingBox().getCenter())) return false;
        // 控制模块还会执行原版 eye-to-eye 视线查询；先保证同一射线路径已加载。
        return spec.effectType() == FieldSpec.Effect.HEAL
                || visible(source.serverLevel(), source, source.getEyePosition(), target.getEyePosition());
    }

    private static boolean legal(ServerPlayer source, LivingEntity target, FieldSpec spec) {
        if (source.level() != target.level() || target.isRemoved()) return false;
        boolean self = source == target;
        boolean allied = FieldPolicy.allied(self, source.isAlliedTo(target) || target.isAlliedTo(source),
                sect(source), sect(target));
        boolean player = target instanceof Player;
        boolean mutual = !player || source.canHarmPlayer((Player) target) && ((Player) target).canHarmPlayer(source);
        return FieldPolicy.permits(spec.targeting() == FieldSpec.Targeting.ALLIES, self, allied,
                spec.targeting() == FieldSpec.Targeting.ALLIES || CombatActors.mayHarm(source, target),
                player, source.getServer().isPvpAllowed(), mutual,
                player && ((Player) target).isCreative(), target.isAlive(), target.isSpectator());
    }
    private static int sect(Entity entity) {
        var sim = WorldSimRuntime.sim().orElse(null);
        if (sim == null) return -1;
        if (entity instanceof NpcEntity npc && npc.isLedgerAvatar())
            return sim.person(npc.ledgerPersonId()).map(person -> person.sectId()).orElse(-1);
        if (entity instanceof ServerPlayer player)
            return sim.actor(ActorRef.player(player.getUUID().toString())).map(actor -> actor.sectId()).orElse(-1);
        return -1;
    }
    private static boolean ownerActive(MinecraftServer server, ServerPlayer source) {
        return source != null && server.getPlayerList().getPlayer(source.getUUID()) == source
                && source.isAlive() && !source.isRemoved() && !source.isSpectator();
    }
    private static boolean active(MinecraftServer server, Instance field, long now) {
        var owner = server.getPlayerList().getPlayer(field.source());
        boolean online = owner != null;
        boolean living = online && ownerActive(server, owner);
        boolean sameDimension = online && owner.level().dimension().equals(field.dimension());
        return FieldPolicy.alive(now, field.expiresAt(), online, living, sameDimension,
                sameDimension && insideLoadedWorld(owner.serverLevel(), center(owner, field.spec())));
    }
    private static Vec3 center(ServerPlayer source, FieldSpec spec) {
        return spec.anchor() == FieldSpec.Anchor.FIXED ? spec.fixedCenter() : source.getBoundingBox().getCenter();
    }
    private static boolean insideLoadedWorld(ServerLevel level, Vec3 point) {
        var pos = BlockPos.containing(point);
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos);
    }
    private static boolean visible(ServerLevel level, ServerPlayer source, Vec3 from, Vec3 to) {
        // clip 可能读取方块；保守检查整条线段覆盖的区块包围矩形，决不让射线强加载。
        int minX = BlockPos.containing(Math.min(from.x, to.x), from.y, from.z).getX() >> 4;
        int maxX = BlockPos.containing(Math.max(from.x, to.x), from.y, from.z).getX() >> 4;
        int minZ = BlockPos.containing(from.x, from.y, Math.min(from.z, to.z)).getZ() >> 4;
        int maxZ = BlockPos.containing(from.x, from.y, Math.max(from.z, to.z)).getZ() >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (!level.hasChunk(x, z)) return false;
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source))
                .getType() == HitResult.Type.MISS;
    }
    private static long clock(MinecraftServer server) { return server.overworld().getGameTime(); }
    private static void remove(State state, long id) { state.instances.remove(id); state.ledger.remove(id); }
    public static void forget(MinecraftServer server, UUID entity) {
        var state = STATES.get(server);
        if (state == null) return;
        state.instances.values().removeIf(field -> field.source().equals(entity));
        state.ledger.forget(entity);
        if (state.instances.isEmpty()) STATES.remove(server);
    }
    public static void clear(MinecraftServer server) { STATES.remove(server); }
    public static void clear() { STATES.clear(); }

    private static void onTick(ServerTickEvent.Post event) { tick(event.getServer()); }
    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) forget(level.getServer(), event.getEntity().getUUID());
    }
    private static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) forget(level.getServer(), event.getEntity().getUUID());
    }
    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player.getServer(), player.getUUID());
    }
    private static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player.getServer(), player.getUUID());
    }
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player.getServer(), player.getUUID());
    }
    private static void onStopping(ServerStoppingEvent event) { clear(event.getServer()); }
}
