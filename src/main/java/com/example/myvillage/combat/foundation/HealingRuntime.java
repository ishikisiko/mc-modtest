package com.example.myvillage.combat.foundation;

import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.cultivation.fate.FateService;
import com.example.myvillage.cultivation.fate.TemperedBonesRuntime;
import com.example.myvillage.stats.runtime.RealmArmorRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 服务器统一治疗入口与主伤害吸血；不产生伤害，不重复执行任何伤害减免。 */
public final class HealingRuntime {
    public static final ResourceLocation UNATTRIBUTED = id("healing/unattributed");
    public static final ResourceLocation WOOD_LIFESTEAL = id("fate/wood_spirit_body");
    private static final Map<UUID, Tracked> STATES = new HashMap<>();
    // 精确容器身份区分嵌套命中；值仅保存数值及 UUID，不持有实体或弱键。
    private static final Map<DamageContainer, Receipt> RECEIPTS = new WeakHashMap<>();
    private static final ThreadLocal<HealContext> CONTEXT = new ThreadLocal<>();
    private static boolean registered;

    private HealingRuntime() { }

    public static void register() {
        if (registered) return;
        HealingRules.get();
        registered = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, HealingRuntime::healing);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, HealingRuntime::beforeDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, HealingRuntime::afterDamage);
        NeoForge.EVENT_BUS.addListener(HealingRuntime::tick);
        NeoForge.EVENT_BUS.addListener(HealingRuntime::logout);
        NeoForge.EVENT_BUS.addListener(HealingRuntime::changedDimension);
        NeoForge.EVENT_BUS.addListener(HealingRuntime::clonePlayer);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, HealingRuntime::modeChanged);
        NeoForge.EVENT_BUS.addListener(HealingRuntime::leftLevel);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, HealingRuntime::death);
        NeoForge.EVENT_BUS.addListener(HealingRuntime::stopping);
    }

    /** 来源明确的服务器治疗，返回实际恢复的生命，不可复活尸体。 */
    public static float heal(LivingEntity target, float amount, ResourceLocation source) {
        return heal(target, amount, source, HealingRules.get().maximumHealingPerCall());
    }

    private static float heal(LivingEntity target, float amount, ResourceLocation source, double finalLimit) {
        if (!serverTarget(target) || source == null || !Float.isFinite(amount) || amount <= 0
                || !Double.isFinite(finalLimit) || finalLimit <= 0) return 0;
        double maximum = target.getMaxHealth();
        float before = target.getHealth();
        if (!Double.isFinite(maximum) || maximum <= before || !Float.isFinite(before) || before <= 0) return 0;
        double limit = Math.min(finalLimit, HealingRules.get().maximumHealingPerCall());
        double upperHealth = Math.min(maximum, before + limit);
        HealContext previous = CONTEXT.get();
        CONTEXT.set(new HealContext(target.getUUID(), target.level().dimension(), source, limit));
        try {
            target.heal(HealingMath.floorFloat(Math.min(amount, HealingRules.get().maximumHealingPerCall())));
        } finally {
            if (previous == null) CONTEXT.remove(); else CONTEXT.set(previous);
            // 事件之后仍以实得生命封顶，后注册的增疗监听器和 float 舍入都不能绕过吸血上限。
            if (target.isAlive() && !target.isRemoved() && target.getHealth() > upperHealth)
                target.setHealth(HealingMath.floorFloat(upperHealth));
        }
        if (!target.isAlive() || target.isRemoved()) return 0;
        return Math.max(0, target.getHealth() - before);
    }

    /** 正值增疗、负值减疗；同源覆盖并刷新期限，容量不足拒绝新增而不挤掉其他来源。 */
    public static boolean modifier(LivingEntity target, ResourceLocation source, double fraction, int durationTicks) {
        if (!serverTarget(target) || source == null) return false;
        var state = state(target, true);
        if (state == null) return false;
        boolean accepted = state.modifiers().modifier(source.toString(), fraction, durationTicks, clock(target), HealingRules.get());
        if (!accepted && state.modifiers().empty() && state.window().expired(clock(target), HealingRules.get().lifestealWindowTicks()))
            STATES.remove(target.getUUID(), state);
        return accepted;
    }

    public static void clear(LivingEntity target, ResourceLocation source) {
        if (target == null || source == null || !serverThread(target)) return;
        var state = state(target, false);
        if (state != null) state.modifiers().clear(source.toString());
    }

    public static void clear(LivingEntity target) {
        if (target != null && serverThread(target)) forget(target.getUUID());
    }

    public static void clear() { STATES.clear(); RECEIPTS.clear(); CONTEXT.remove(); }

    /** 旧调用的来源保持“未归属”，不猜测丹药、饥饿或再生效果的身份。 */
    public static ResourceLocation currentSource(LivingEntity target) {
        HealContext context = CONTEXT.get();
        return matches(context, target) ? context.source() : UNATTRIBUTED;
    }

    private static void healing(LivingHealEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!serverTarget(target)) {
            event.setCanceled(true);
            return;
        }
        var state = state(target, false);
        double multiplier = state == null ? 1 : state.modifiers().multiplier(clock(target), HealingRules.get());
        HealContext context = CONTEXT.get();
        double limit = matches(context, target) ? context.limit() : HealingRules.get().maximumHealingPerCall();
        double amount = HealingMath.adjusted(event.getAmount(), multiplier, target.getHealth(), target.getMaxHealth(), limit);
        if (amount <= 0) event.setCanceled(true);
        else event.setAmount(HealingMath.floorFloat(amount));
    }

    private static void beforeDamage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (!serverTarget(target)) return;
        if (CombatDamageService.beginPrimaryHealingReceipt(target, event.getSource(), event.getContainer())) return;
        ServerPlayer attacker = directAttacker(event.getSource());
        if (attacker == null || CombatDamageService.isResolvingHit(target)
                || CombatDamageService.isDerivedHit(target) || !validAttacker(attacker)
                || !CombatActors.mayHarm(attacker, target) || !pvpAllowed(attacker, target)
                || HealingMath.lifesteal(1, FateService.effect(attacker, "lifesteal_fraction")) <= 0) return;
        if (RECEIPTS.size() < HealingRules.get().maximumDamageReceipts())
            RECEIPTS.put(event.getContainer(), new Receipt(target.getUUID(), attacker.getUUID(), target.getHealth()));
    }

    private static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageContainer container = RealmArmorRuntime.currentContainer(target);
        // 自定义主命中仅冻结，等 hurt 返回后消费；原版仍在此处直接结算。
        if (CombatDamageService.freezePrimaryHealingReceipt(target, event.getSource(), container,
                event.getNewDamage(), TemperedBonesRuntime.primaryFraction(container))) return;
        Receipt receipt = RECEIPTS.remove(container);
        ServerPlayer attacker = directAttacker(event.getSource());
        if (receipt == null || attacker == null || !receipt.target().equals(target.getUUID())
                || !receipt.attacker().equals(attacker.getUUID()) || CombatDamageService.isResolvingHit(target)
                || CombatDamageService.isDerivedHit(target)) return;
        // Post 的 newDamage 再封顶，避免其他监听器在本次期间造成的额外扣血混入收据。
        double loss = HealingMath.primaryLoss(receipt.healthBefore(), target.getHealth(), 1);
        double actual = Math.min(Math.max(0, event.getNewDamage()), loss)
                * TemperedBonesRuntime.primaryFraction(container);
        primaryHit(attacker, target, actual);
    }

    /**
     * 可信主伤害收据入口。调用者必须先完成友军、PvP、墙体及目标校验，只传实际主生命损失。
     * 自定义命中在 Post 冻结精确容器收据，hurt 作用域结束后消费一次；其事件路径不即时治疗。
     * 允许有效的击杀收据，但死亡／离线／创造／旁观攻击者不能获得治疗。
     */
    public static void primaryHit(ServerPlayer attacker, LivingEntity target, double actualPrimaryHealthLoss) {
        if (!validAttacker(attacker) || target == null || target == attacker || target.isRemoved()
                || target.level() != attacker.level() || attacker.isAlliedTo(target) || target.isAlliedTo(attacker)
                || !pvpAllowed(attacker, target) || CombatDamageService.isDerivedHit(target)
                || target.isAlive() && !CombatActors.mayHarm(attacker, target)) return;
        double raw = HealingMath.lifesteal(actualPrimaryHealthLoss, FateService.effect(attacker, "lifesteal_fraction"));
        if (raw <= 0 || attacker.getHealth() >= attacker.getMaxHealth()) return;
        var state = state(attacker, true);
        if (state == null) return;
        var reservation = state.window().reserve(clock(attacker), attacker.getMaxHealth(),
                FateService.effect(attacker, "lifesteal_cap_fraction"), HealingRules.get().lifestealWindowTicks());
        if (reservation == null) return;
        float before = attacker.getHealth();
        try {
            heal(attacker, HealingMath.floorFloat(raw), WOOD_LIFESTEAL, reservation.amount());
        } finally {
            double actual = attacker.isAlive() ? Math.max(0, (double) attacker.getHealth() - before) : 0;
            state.window().settle(reservation, actual);
        }
    }

    private static ServerPlayer directAttacker(DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return null;
        if (source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player) return player;
        if (source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() == player) return player;
        return null;
    }

    private static boolean pvpAllowed(ServerPlayer attacker, LivingEntity target) {
        if (!(target instanceof Player player)) return true;
        return !player.isCreative() && !player.isSpectator() && attacker.getServer().isPvpAllowed()
                && attacker.canHarmPlayer(player) && player.canHarmPlayer(attacker);
    }

    private static boolean validAttacker(ServerPlayer player) {
        return serverTarget(player) && !player.isCreative() && !player.isSpectator()
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static boolean serverThread(LivingEntity target) {
        return target != null && target.level() instanceof ServerLevel level && level.getServer().isSameThread();
    }

    private static boolean serverTarget(LivingEntity target) {
        return serverThread(target) && target.isAlive() && !target.isRemoved();
    }

    private static boolean matches(HealContext context, LivingEntity target) {
        return context != null && target != null && context.target().equals(target.getUUID())
                && context.dimension().equals(target.level().dimension());
    }

    private static Tracked state(LivingEntity target, boolean create) {
        var previous = STATES.get(target.getUUID());
        if (previous != null && !previous.dimension().equals(target.level().dimension())) {
            STATES.remove(target.getUUID());
            previous = null;
        }
        if (previous == null && create && STATES.size() < HealingRules.get().maximumTrackedEntities()) {
            previous = new Tracked(target.level().dimension(), new HealingModifiers(), new HealingWindow());
            STATES.put(target.getUUID(), previous);
        }
        return previous;
    }

    private static long clock(LivingEntity target) { return target.getServer().overworld().getGameTime(); }

    private static void tick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        STATES.entrySet().removeIf(entry -> {
            var state = entry.getValue();
            var level = event.getServer().getLevel(state.dimension());
            if (level == null || !(level.getEntity(entry.getKey()) instanceof LivingEntity entity)
                    || !entity.isAlive() || entity.isRemoved()) return true;
            state.modifiers().expire(now);
            return state.modifiers().empty() && state.window().expired(now, HealingRules.get().lifestealWindowTicks());
        });
        // 完全吸收时可能无 Post；只保留当前 tick 的短期收据。
        RECEIPTS.clear();
    }

    private static void forget(UUID id) {
        STATES.remove(id);
        RECEIPTS.values().removeIf(receipt -> receipt.target().equals(id) || receipt.attacker().equals(id));
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    private static void clonePlayer(PlayerEvent.Clone event) { clear(event.getOriginal()); clear(event.getEntity()); }
    private static void modeChanged(PlayerEvent.PlayerChangeGameModeEvent event) { clear(event.getEntity()); }
    private static void death(LivingDeathEvent event) { clear(event.getEntity()); }
    private static void leftLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living) clear(living);
    }
    private static void stopping(ServerStoppingEvent event) { clear(); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("myvillage", path); }

    /**
     * 单次同步 hurt 的吸血回执。身份比较不使用 equals，也不读取 hurt 返回后的生命或最后 trace。
     * 容器仅在当前调用栈内持有；没有 Post（例如全吸收）时消费为零。
     */
    public static final class PrimaryReceipt {
        private final Object target;
        private final Object source;
        private final int depth;
        private Object container;
        private double healthBefore;
        private double loss;
        private boolean frozen;
        private boolean consumed;

        public PrimaryReceipt(Object target, Object source, int depth) {
            this.target = target;
            this.source = source;
            this.depth = depth;
        }

        public boolean begin(Object target, Object source, Object container, int depth, double healthBefore) {
            if (this.target != target || this.source != source || this.depth != depth || container == null
                    || this.container != null || consumed || !Double.isFinite(healthBefore) || healthBefore <= 0) return false;
            this.container = container;
            this.healthBefore = healthBefore;
            return true;
        }

        public boolean freeze(Object target, Object source, Object container, double healthAfter,
                double finalDamage, double primaryFraction) {
            if (this.target != target || this.source != source || container == null || this.container != container) return false;
            if (!frozen && !consumed) {
                frozen = true;
                // Post newDamage 已剔除吸收；实际生命差及前血量排除过量，精确容器比例剔除淬骨追加。
                if (Double.isFinite(finalDamage) && finalDamage > 0 && Double.isFinite(primaryFraction)
                        && primaryFraction > 0 && primaryFraction <= 1)
                    loss = Math.min(finalDamage, HealingMath.primaryLoss(healthBefore, healthAfter, 1)) * primaryFraction;
            }
            return true;
        }

        public double consume() {
            if (consumed) return 0;
            consumed = true;
            return frozen ? loss : 0;
        }
    }

    private record Tracked(ResourceKey<Level> dimension, HealingModifiers modifiers, HealingWindow window) { }
    private record Receipt(UUID target, UUID attacker, float healthBefore) { }
    private record HealContext(UUID target, ResourceKey<Level> dimension, ResourceLocation source, double limit) { }
}
