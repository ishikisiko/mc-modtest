package com.example.myvillage.combat.foundation;

import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatReactionService;
import com.example.myvillage.combat.runtime.StaggerResistant;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 服务器控制状态；根缠不取消实体 tick，也不禁用受伤、视角、攻击或全部 AI。 */
public final class ControlService {
    private static final ResourceLocation ROOT_SPEED = ResourceLocation.parse("myvillage:foundation_root_speed");
    private static final ResourceLocation SLOW_SPEED = ResourceLocation.parse("myvillage:foundation_slow_speed");
    private static final Map<UUID, Tracked> TARGETS = new HashMap<>();
    private static boolean registered;

    private ControlService() { }

    public static void register() {
        if (registered) return;
        ControlRules.get();
        registered = true;
        NeoForge.EVENT_BUS.addListener(ControlService::tick);
        NeoForge.EVENT_BUS.addListener(ControlService::entityPre);
        NeoForge.EVENT_BUS.addListener(ControlService::entityPost);
        NeoForge.EVENT_BUS.addListener(ControlService::playerPre);
        NeoForge.EVENT_BUS.addListener(ControlService::playerPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ControlService::death);
        NeoForge.EVENT_BUS.addListener(ControlService::leave);
        NeoForge.EVENT_BUS.addListener(ControlService::logout);
        NeoForge.EVENT_BUS.addListener(ControlService::dimension);
        NeoForge.EVENT_BUS.addListener(ControlService::respawn);
        NeoForge.EVENT_BUS.addListener(ControlService::mount);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ControlService::knockback);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ControlService::teleport);
        NeoForge.EVENT_BUS.addListener(ControlService::stopping);
    }

    public static boolean canControl(ServerPlayer source, LivingEntity target) {
        if (source == null || target == null || !writable(source) || !ready(target)) return false;
        return policy(source, target).allowed();
    }

    private static ControlRules.TargetPolicy policy(ServerPlayer source, LivingEntity target) {
        boolean sameLevel = source.level() == target.level();
        boolean pvp = !(target instanceof ServerPlayer player) || source.getServer().isPvpAllowed()
                && source.canHarmPlayer(player) && player.canHarmPlayer(source) && !player.isCreative();
        return new ControlRules.TargetPolicy(source.isAlive() && !source.isRemoved() && !source.isSpectator(),
                target.isAlive() && !target.isRemoved() && !target.isSpectator() && target.isAttackable(),
                sameLevel, source == target || source.isAlliedTo(target) || target.isAlliedTo(source),
                sameLevel && CombatActors.mayHarm(source, target), pvp,
                sameLevel && source.hasLineOfSight(target), CombatReactionService.isControlImmune(target),
                StaggerResistant.resists(target), target.isPassenger() || target.isVehicle());
    }

    public static boolean root(ServerPlayer source, LivingEntity target, ResourceLocation effect, int ticks) {
        if (effect == null || ticks <= 0 || !canControl(source, target)) return false;
        ControlRules rules = ControlRules.get();
        Tracked tracked = track(target);
        if (tracked == null) return false;
        long now = clock(target);
        int duration = rules.rootTicks(ticks, tracked.state.rootStacks(now, rules.repeatWindowTicks()), target.getMaxHealth());
        boolean wasRooted = tracked.state.has(ControlState.Kind.ROOT, now);
        if (!tracked.state.put(ControlState.Kind.ROOT, source.getUUID(), effect.toString(), now,
                duration, 1, rules.maximumSources())) return false;
        tracked.state.recordRoot(now, rules.repeatWindowTicks());
        if (!wasRooted) {
            tracked.anchorX = target.getX();
            tracked.anchorZ = target.getZ();
            tracked.forcedMotion = target.getDeltaMovement().multiply(1, 0, 1);
        }
        refresh(tracked, now);
        holdHorizontal(tracked);
        if (!wasRooted && target instanceof ServerPlayer player) {
            // 专用属性同步配合速度包与一次位置纠正，防止客户端仍自由奔跑。
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.connection.teleport(tracked.anchorX, player.getY(), tracked.anchorZ, player.getYRot(), player.getXRot());
        }
        return true;
    }

    public static boolean slow(ServerPlayer source, LivingEntity target, ResourceLocation effect, int ticks, double fraction) {
        if (effect == null || ticks <= 0 || !Double.isFinite(fraction) || fraction <= 0 || fraction > 1
                || !canControl(source, target)) return false;
        ControlRules rules = ControlRules.get();
        Tracked tracked = track(target);
        if (tracked == null || !tracked.state.put(ControlState.Kind.SLOW, source.getUUID(), effect.toString(), clock(target),
                Math.min(ticks, rules.maximumTicks()), Math.min(fraction, rules.maximumSlow()), rules.maximumSources())) return false;
        refresh(tracked, clock(target));
        return true;
    }

    public static boolean grantPoise(LivingEntity target, ResourceLocation effect, int ticks) {
        if (!writable(target) || effect == null || ticks <= 0) return false;
        ControlRules rules = ControlRules.get();
        Tracked tracked = track(target);
        return tracked != null && tracked.state.put(ControlState.Kind.POISE, target.getUUID(), effect.toString(), clock(target),
                Math.min(ticks, rules.maximumTicks()), 0, rules.maximumSources());
    }

    /** 仅移除本模块不利控制及旧硬直，不修改无关属性或药水。 */
    public static int cleanse(LivingEntity target) {
        if (!writable(target)) return 0;
        Tracked tracked = find(target);
        int removed = 0;
        if (tracked != null) {
            tracked.state.expire(clock(target));
            removed = tracked.state.cleanse();
            refresh(tracked, clock(target));
        }
        if (CombatReactionService.cleanse(target)) removed++;
        return removed;
    }

    public static int revoke(ServerPlayer source, LivingEntity target, ResourceLocation effect) {
        if (source == null || effect == null || !writable(target)) return 0;
        Tracked tracked = find(target);
        if (tracked == null) return 0;
        int removed = tracked.state.revoke(source.getUUID(), effect.toString());
        refresh(tracked, clock(target));
        return removed;
    }

    public static boolean isRooted(LivingEntity target) { return rootRemainingTicks(target) > 0; }
    public static boolean isPoised(LivingEntity target) { return poiseRemainingTicks(target) > 0; }
    public static int rootRemainingTicks(LivingEntity target) { return remaining(target, ControlState.Kind.ROOT); }
    public static int poiseRemainingTicks(LivingEntity target) { return remaining(target, ControlState.Kind.POISE); }
    public static int slowRemainingTicks(LivingEntity target) { return remaining(target, ControlState.Kind.SLOW); }

    private static int remaining(LivingEntity target, ControlState.Kind kind) {
        if (!writable(target)) return 0;
        Tracked tracked = find(target);
        return tracked == null ? 0 : tracked.state.remaining(kind, clock(target));
    }

    public record Snapshot(int rootTicks, int slowTicks, int poiseTicks, double slowFraction,
            List<ControlState.SourceSnapshot> sources) {
        public Snapshot { sources = List.copyOf(sources); }
    }

    public static Snapshot snapshot(LivingEntity target) {
        Tracked tracked = ready(target) ? find(target) : null;
        if (tracked == null) return new Snapshot(0, 0, 0, 0, List.of());
        long now = clock(target);
        return new Snapshot(tracked.state.remaining(ControlState.Kind.ROOT, now),
                tracked.state.remaining(ControlState.Kind.SLOW, now), tracked.state.remaining(ControlState.Kind.POISE, now),
                tracked.state.strongestSlow(now), tracked.state.snapshot(now));
    }

    /** 只约束水平意图，原版继续处理 Y、视角、着地与非法坐标。 */
    public static double constrainPlayerX(ServerPlayer player, double requestedX) {
        Tracked tracked = find(player);
        return isRooted(player) && tracked != null ? tracked.anchorX : requestedX;
    }
    public static double constrainPlayerZ(ServerPlayer player, double requestedZ) {
        Tracked tracked = find(player);
        return isRooted(player) && tracked != null ? tracked.anchorZ : requestedZ;
    }

    /** 服务器已确认的击退仍可移动根缠目标，客户端不能自行声明外力。 */
    public static void allowImpulse(Entity target, Vec3 impulse) {
        if (!(target instanceof LivingEntity living) || !writable(living) || !isRooted(living) || impulse == null
                || !Double.isFinite(impulse.x) || !Double.isFinite(impulse.z)) return;
        Tracked tracked = find(living);
        if (tracked != null) tracked.forcedMotion = new Vec3(impulse.x, 0, impulse.z);
    }

    public static void forget(Entity entity) {
        if (entity == null || entity.level().isClientSide() || entity.getServer() == null || !entity.getServer().isSameThread()) return;
        UUID id = entity.getUUID();
        Tracked removed = TARGETS.remove(id);
        if (removed != null) release(removed);
        for (Tracked tracked : List.copyOf(TARGETS.values())) {
            tracked.state.forgetCaster(id);
            refresh(tracked, clock(tracked.target));
        }
    }

    public static void clear() {
        if (TARGETS.values().stream().anyMatch(tracked -> tracked.target.getServer() == null
                || !tracked.target.getServer().isSameThread())) return;
        for (Tracked tracked : TARGETS.values()) release(tracked);
        TARGETS.clear();
    }

    private static boolean ready(LivingEntity target) {
        return target != null && !target.level().isClientSide() && target.isAlive() && !target.isRemoved();
    }
    private static boolean writable(LivingEntity target) {
        return ready(target) && target.getServer() != null && target.getServer().isSameThread();
    }
    private static long clock(LivingEntity target) { return target.getServer().overworld().getGameTime(); }
    private static Tracked find(LivingEntity target) {
        if (target == null || target.level().isClientSide()) return null;
        Tracked tracked = TARGETS.get(target.getUUID());
        return tracked != null && tracked.target == target && tracked.level == target.level() ? tracked : null;
    }
    private static Tracked track(LivingEntity target) {
        Tracked tracked = find(target);
        if (tracked != null) return tracked;
        if (TARGETS.size() >= ControlRules.get().maximumTargets()) return null;
        Tracked stale = TARGETS.get(target.getUUID());
        if (stale != null) release(stale);
        tracked = new Tracked(target);
        TARGETS.put(target.getUUID(), tracked);
        return tracked;
    }

    private static void refresh(Tracked tracked, long now) {
        boolean rooted = tracked.state.has(ControlState.Kind.ROOT, now);
        double slow = tracked.state.strongestSlow(now);
        speed(tracked.target.getAttribute(Attributes.MOVEMENT_SPEED), ROOT_SPEED, rooted ? -1 : 0);
        speed(tracked.target.getAttribute(Attributes.FLYING_SPEED), ROOT_SPEED, rooted ? -1 : 0);
        speed(tracked.target.getAttribute(Attributes.MOVEMENT_SPEED), SLOW_SPEED, -slow);
        speed(tracked.target.getAttribute(Attributes.FLYING_SPEED), SLOW_SPEED, -slow);
        if (!rooted && tracked.rooted) releaseForce(tracked);
        tracked.rooted = rooted;
    }
    private static void speed(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null) return;
        AttributeModifier existing = attribute.getModifier(id);
        if (amount == 0) {
            if (existing != null) attribute.removeModifier(id);
        } else if (existing == null || existing.amount() != amount) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
    private static void release(Tracked tracked) {
        if (tracked.rooted) releaseForce(tracked);
        tracked.rooted = false;
        speed(tracked.target.getAttribute(Attributes.MOVEMENT_SPEED), ROOT_SPEED, 0);
        speed(tracked.target.getAttribute(Attributes.FLYING_SPEED), ROOT_SPEED, 0);
        speed(tracked.target.getAttribute(Attributes.MOVEMENT_SPEED), SLOW_SPEED, 0);
        speed(tracked.target.getAttribute(Attributes.FLYING_SPEED), SLOW_SPEED, 0);
    }

    private static void releaseForce(Tracked tracked) {
        LivingEntity target = tracked.target;
        if (ready(target) && tracked.forcedMotion.horizontalDistanceSqr() > 0) {
            target.setDeltaMovement(tracked.forcedMotion.x, target.getDeltaMovement().y, tracked.forcedMotion.z);
            target.hasImpulse = true;
            if (target instanceof ServerPlayer player)
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            else target.hurtMarked = true;
        }
        tracked.forcedMotion = Vec3.ZERO;
    }

    private static void holdHorizontal(Tracked tracked) {
        LivingEntity target = tracked.target;
        target.setDeltaMovement(0, target.getDeltaMovement().y, 0);
        target.setXxa(0);
        target.setZza(0);
        double epsilon = ControlRules.get().correctionEpsilon();
        if (Math.abs(target.getX() - tracked.anchorX) <= epsilon && Math.abs(target.getZ() - tracked.anchorZ) <= epsilon) return;
        if (target instanceof ServerPlayer player)
            player.connection.teleport(tracked.anchorX, target.getY(), tracked.anchorZ, target.getYRot(), target.getXRot());
        else target.setPos(tracked.anchorX, target.getY(), tracked.anchorZ);
    }

    private static void enforce(LivingEntity entity) {
        Tracked tracked = find(entity);
        if (tracked == null || !ready(entity)) return;
        refresh(tracked, clock(entity));
        if (tracked.state.has(ControlState.Kind.ROOT, clock(entity))) holdHorizontal(tracked);
    }

    private static void tick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        ControlRules rules = ControlRules.get();
        for (Tracked tracked : List.copyOf(TARGETS.values())) {
            LivingEntity target = tracked.target;
            if (!ready(target) || target.level() != tracked.level) { forget(target); continue; }
            tracked.state.expire(now);
            // 已离线施法者、改为友军或关闭 PvP 时撤销对应来源；霸体来源独立保留。
            tracked.state.pruneHostileSources(id -> {
                ServerPlayer source = event.getServer().getPlayerList().getPlayer(id);
                return source != null && source.isAlive() && !source.isSpectator() && CombatActors.mayHarm(source, target)
                        && (!(target instanceof ServerPlayer other) || event.getServer().isPvpAllowed()
                        && source.canHarmPlayer(other) && other.canHarmPlayer(source));
            });
            refresh(tracked, now);
            if (tracked.state.has(ControlState.Kind.ROOT, now)) {
                holdHorizontal(tracked);
                Vec3 force = tracked.forcedMotion;
                if (force.horizontalDistanceSqr() > rules.forceEpsilon() * rules.forceEpsilon()) {
                    // 仅服务器认可的外力按碰撞推进锚点，水平移动包始终被约束到该锚点。
                    boolean grounded = target.onGround();
                    target.move(MoverType.SELF, force);
                    target.setOnGround(grounded);
                    tracked.anchorX = target.getX();
                    tracked.anchorZ = target.getZ();
                    tracked.forcedMotion = target.horizontalCollision ? Vec3.ZERO
                            : force.scale(grounded ? rules.groundRetention() : rules.airRetention());
                    if (target instanceof ServerPlayer player)
                        player.connection.teleport(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                } else tracked.forcedMotion = Vec3.ZERO;
            }
            if (tracked.state.idle(now, rules.repeatWindowTicks())) {
                release(tracked);
                TARGETS.remove(target.getUUID(), tracked);
            }
        }
    }

    private static void entityPre(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity living) enforce(living);
    }
    private static void entityPost(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living) enforce(living);
    }
    private static void playerPre(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) enforce(player);
    }
    private static void playerPost(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) enforce(player);
    }
    private static void knockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        if (isPoised(target)) {
            event.setCanceled(true);
            return;
        }
        if (!isRooted(target)) return;
        double strength = event.getStrength() * (1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        Vec3 direction = new Vec3(event.getRatioX(), 0, event.getRatioZ()).normalize();
        if (!(strength > 0) || direction.lengthSqr() == 0) return;
        allowImpulse(target, target.getDeltaMovement().multiply(.5, 0, .5).subtract(direction.scale(strength)));
    }
    private static void death(LivingDeathEvent event) { forget(event.getEntity()); }
    private static void leave(EntityLeaveLevelEvent event) { forget(event.getEntity()); }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { forget(event.getEntity()); }
    private static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { forget(event.getEntity()); }
    private static void respawn(PlayerEvent.PlayerRespawnEvent event) { forget(event.getEntity()); }
    private static void mount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof LivingEntity living && isRooted(living))
            event.setCanceled(true);
    }
    private static void teleport(EntityTeleportEvent event) {
        // 合法服务器传送显式结束暂态控制，避免随后被旧锚点拉回。
        if (!event.getEntity().level().isClientSide()) forget(event.getEntity());
    }
    private static void stopping(ServerStoppingEvent event) { clear(); }

    private static final class Tracked {
        private final LivingEntity target;
        private final net.minecraft.world.level.Level level;
        private final ControlState state = new ControlState();
        private double anchorX;
        private double anchorZ;
        private Vec3 forcedMotion = Vec3.ZERO;
        private boolean rooted;
        private Tracked(LivingEntity target) { this.target = target; this.level = target.level(); }
    }
}
