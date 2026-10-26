package com.example.myvillage.talisman;

import com.example.myvillage.combat.foundation.*;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatDamageService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 符的使用是独立服务器事务，不占普通静坐会话。展示实体不持有可拾取物。 */
public final class TalismanRuntime {
    public static final ResourceLocation SHIELD_SOURCE = ResourceLocation.parse("myvillage:talisman_ward");
    private static final ResourceLocation SNARE_SOURCE = ResourceLocation.parse("myvillage:talisman_snare");
    private static final String MARKER_TAG = "myvillage_talisman_marker";
    private static final Map<MinecraftServer, LinkedHashMap<UUID, Seal>> STATES = new WeakHashMap<>();
    private record Seal(UUID id, UUID owner, ServerLevel level, Vec3 center, int kind, ItemStack stack, long expires, Display.ItemDisplay marker) { }
    private TalismanRuntime() { }
    public static void register() {
        TalismanRules.get();
        NeoForge.EVENT_BUS.addListener(TalismanRuntime::tick);
        NeoForge.EVENT_BUS.addListener(TalismanRuntime::stop);
        NeoForge.EVENT_BUS.addListener(TalismanRuntime::leave);
        NeoForge.EVENT_BUS.addListener(TalismanRuntime::reclaim);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, TalismanRuntime::orphan);
    }
    public static int count(ServerPlayer p) {
        var state = STATES.get(p.getServer());
        return state == null ? 0 : (int) state.values().stream().filter(s -> s.owner().equals(p.getUUID())).count();
    }
    public static boolean use(ServerPlayer p, ItemStack stack, int kind) {
        if (p.getServer() == null || !p.getServer().isSameThread() || !p.isAlive() || p.isSpectator() || stack.isEmpty()) return false;
        var r = TalismanRules.get(); int quality = TalismanQuality.get(stack);
        boolean cooldown = p.getCooldowns().isOnCooldown(TalismanContent.LIGHTNING.get())
                || p.getCooldowns().isOnCooldown(TalismanContent.SNARE.get()) || p.getCooldowns().isOnCooldown(TalismanContent.WARD.get());
        if (!TalismanUsePolicy.ready(true, cooldown, CultivationService.getProfile(p).currentSpiritualPower(), r.use("activation_power")))
            return refused(p, cooldown ? "符箓尚在共用施放间隔" : "激活符箓所需灵力不足");
        if (kind == 2) {
            if (!ShieldRuntime.grantPaid(p, SHIELD_SOURCE, r.shield(quality), r.ticks("ward", "duration_ticks"),
                    () -> CultivationService.spendSpiritualPower(p, r.use("activation_power")))) return refused(p, "护盾暂不可用");
            committed(p, stack);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .7F, 1.2F);
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("护身符已生效 · 同源护盾已替换"), true); return true;
        }
        if (kind != 0 && kind != 1) return false;
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(r.use("range")));
        if (!loadedLine(p.serverLevel(), eye, end)) return refused(p, "落点沿线尚未加载");
        BlockHitResult hit = p.serverLevel().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) return refused(p, "请指向可见地面上表面");
        Vec3 center = hit.getLocation().add(0, .025, 0);
        if (!inside(p.serverLevel(), center) || !visible(p, eye, center)) return refused(p, "落点不可达");
        var state = STATES.computeIfAbsent(p.getServer(), ignored -> new LinkedHashMap<>());
        if (count(p) >= r.use("max_per_player") || state.size() >= r.use("max_server")) return refused(p, "场上符箓已达上限，先收回镇步符或等待消散");
        // Marker contains only a visual copy. The authoritative paid seal lives in this bounded registry.
        Display.ItemDisplay marker = EntityType.ITEM_DISPLAY.create(p.serverLevel());
        if (marker == null) return false;
        CompoundTag t = new CompoundTag(); t.put("item", stack.copyWithCount(1).save(p.registryAccess())); t.putString("item_display", "fixed");
        marker.load(t); marker.setPos(center.x, center.y, center.z); marker.setXRot(90); marker.addTag(MARKER_TAG);
        UUID id = marker.getUUID();
        Seal seal = new Seal(id, p.getUUID(), p.serverLevel(), center, kind, stack.copyWithCount(1),
                clock(p.getServer()) + r.ticks(kind == 0 ? "lightning" : "snare", kind == 0 ? "warning_ticks" : "lifetime_ticks"), marker);
        state.put(id, seal);
        if (!p.serverLevel().addFreshEntity(marker)) { state.remove(id); marker.discard(); return false; }
        if (!CultivationService.spendSpiritualPower(p, r.use("activation_power"))) { remove(state, seal); return refused(p, "激活符箓所需灵力不足"); }
        committed(p, stack);
        p.displayClientMessage(net.minecraft.network.chat.Component.literal(kind == 0 ? "引雷符落定 · 雷光将至" : "镇步符已布下 · 潜行空手右击可收回"), true);
        return true;
    }
    private static void committed(ServerPlayer p, ItemStack stack) {
        stack.consume(1, p);
        int ticks = TalismanRules.get().use("cooldown_ticks");
        p.getCooldowns().addCooldown(TalismanContent.LIGHTNING.get(), ticks);
        p.getCooldowns().addCooldown(TalismanContent.SNARE.get(), ticks);
        p.getCooldowns().addCooldown(TalismanContent.WARD.get(), ticks);
    }
    private static boolean refused(ServerPlayer p, String text) { p.displayClientMessage(net.minecraft.network.chat.Component.literal(text), true); return false; }
    private static long clock(MinecraftServer s) { return s.overworld().getGameTime(); }
    private static void tick(ServerTickEvent.Post e) {
        var state = STATES.get(e.getServer()); if (state == null) return;
        long now = clock(e.getServer()); var r = TalismanRules.get();
        for (Seal seal : List.copyOf(state.values())) {
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(seal.owner());
            if (p == null || !p.isAlive() || p.isSpectator() || p.serverLevel() != seal.level()
                    || !inside(seal.level(), seal.center()) || seal.marker().isRemoved()) { remove(state, seal); continue; }
            if (seal.kind() == 0 && now >= seal.expires()) {
                remove(state, seal); strike(p, seal); continue;
            }
            if (now >= seal.expires()) { remove(state, seal); continue; }
            if (now % r.use("particle_ticks") == 0) {
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4, radius = r.radius(seal.kind() == 0 ? "lightning" : "snare");
                    seal.level().sendParticles(seal.kind() == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.ENCHANT,
                            seal.center().x + Math.cos(a) * radius, seal.center().y + .02, seal.center().z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
                }
            }
            if (seal.kind() == 1) {
                var targets = seal.level().getEntitiesOfClass(LivingEntity.class, new AABB(seal.center(), seal.center()).inflate(r.radius("snare")),
                        t -> hostile(p, t) && visible(p, seal.center(), t.getBoundingBox().getCenter()) && ControlService.canControl(p, t));
                targets.sort(Comparator.comparingDouble((LivingEntity t) -> t.distanceToSqr(seal.center())).thenComparingInt(Entity::getId));
                for (var target : targets) {
                    if (ControlService.root(p, target, SNARE_SOURCE, r.rootTicks(TalismanQuality.get(seal.stack())))) {
                        remove(state, seal); break; // Exactly one eligible trigger, never a repeating field.
                    }
                }
            }
        }
        if (state.isEmpty()) STATES.remove(e.getServer());
    }
    private static void strike(ServerPlayer p, Seal seal) {
        var r = TalismanRules.get(); var level = seal.level(); var center = seal.center();
        // Vanilla visualOnly lightning still powers rods and clears copper. Use particles only.
        for (int i = 0; i <= r.ticks("lightning", "bolt_steps"); i++) {
            double part = (double) i / r.ticks("lightning", "bolt_steps");
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x + Math.sin(i * 2.4) * .16,
                    center.y + part * r.ticks("lightning", "bolt_height"), center.z + Math.cos(i * 2.4) * .16,
                    2, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + .2, center.z, 1, 0, 0, 0, 0);
        level.playSound(null, BlockPos.containing(center), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1F, 1F);
        for (var target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r.radius("lightning")))) {
            if (!hostile(p, target) || target.getBoundingBox().getCenter().distanceToSqr(center) > r.radius("lightning") * r.radius("lightning")
                    || !visible(p, center, target.getBoundingBox().getCenter())) continue;
            // Shared derived scope: one StatsRuntime mitigation pass, no recursive offensive fate procs.
            CombatDamageService.hurtDerived(p, target, level.damageSources().indirectMagic(p, p), r.damage(TalismanQuality.get(seal.stack())));
        }
    }
    static boolean hostile(ServerPlayer p, LivingEntity t) {
        boolean other = t instanceof Player;
        boolean mutual = !other || p.canHarmPlayer((Player) t) && ((Player) t).canHarmPlayer(p);
        return t.level() == p.level() && FieldPolicy.permits(false, p == t, p.isAlliedTo(t) || t.isAlliedTo(p),
                CombatActors.mayHarm(p, t), other, p.getServer().isPvpAllowed(), mutual,
                other && ((Player) t).isCreative(), t.isAlive() && t.isAttackable(), t.isSpectator());
    }
    private static boolean inside(ServerLevel l, Vec3 v) {
        BlockPos p = BlockPos.containing(v);
        return !l.isOutsideBuildHeight(p) && l.hasChunkAt(p) && l.getWorldBorder().isWithinBounds(p);
    }
    static boolean loadedLine(ServerLevel l, Vec3 a, Vec3 b) {
        int minX = Math.min(BlockPos.containing(a).getX(), BlockPos.containing(b).getX()) >> 4;
        int maxX = Math.max(BlockPos.containing(a).getX(), BlockPos.containing(b).getX()) >> 4;
        int minZ = Math.min(BlockPos.containing(a).getZ(), BlockPos.containing(b).getZ()) >> 4;
        int maxZ = Math.max(BlockPos.containing(a).getZ(), BlockPos.containing(b).getZ()) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) if (!l.hasChunk(x, z)) return false;
        return true;
    }
    private static boolean visible(ServerPlayer p, Vec3 from, Vec3 to) {
        return loadedLine(p.serverLevel(), from, to) && p.serverLevel().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }
    private static void reclaim(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown() || !p.getMainHandItem().isEmpty()) return;
        var state = STATES.get(p.getServer()); if (state == null) return;
        Vec3 point = e.getHitVec().getLocation(); double reach = TalismanRules.get().use("reclaim_range");
        Seal nearest = state.values().stream().filter(s -> s.kind() == 1 && s.owner().equals(p.getUUID()) && s.level() == p.serverLevel()
                && s.center().distanceToSqr(point) <= 1 && p.distanceToSqr(s.center()) <= reach * reach
                && visible(p, p.getEyePosition(), s.center())).min(Comparator.comparingDouble(s -> s.center().distanceToSqr(point))).orElse(null);
        if (nearest == null) return;
        ItemStack returned = nearest.stack().copy();
        if (!p.getInventory().add(returned)) { refused(p, "背包已满，无法收回地符"); return; }
        remove(state, nearest); e.setCanceled(true); e.setCancellationResult(InteractionResult.SUCCESS);
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("已收回未触发的镇步符"), true);
    }
    private static void remove(Map<UUID, Seal> state, Seal s) { state.remove(s.id()); s.marker().discard(); }
    private static void orphan(EntityJoinLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !e.getEntity().getTags().contains(MARKER_TAG)) return;
        var state = STATES.get(l.getServer());
        if (state == null || !state.containsKey(e.getEntity().getUUID()) || state.get(e.getEntity().getUUID()).marker() != e.getEntity()) { e.setCanceled(true); e.getEntity().discard(); }
    }
    private static void leave(EntityLeaveLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !e.getEntity().getTags().contains(MARKER_TAG)) return;
        var state = STATES.get(l.getServer()); if (state == null) return;
        Seal seal = state.get(e.getEntity().getUUID());
        if (seal != null && seal.marker() == e.getEntity()) state.remove(seal.id());
    }
    private static void stop(ServerStoppingEvent e) {
        var state = STATES.remove(e.getServer()); if (state != null) for (var s : state.values()) s.marker().discard();
    }
}
