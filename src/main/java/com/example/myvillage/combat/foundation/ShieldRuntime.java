package com.example.myvillage.combat.foundation;

import com.example.myvillage.cultivation.fate.SwordHeartRuntime;
import com.example.myvillage.cultivation.fate.TemperedBonesRuntime;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 服务器暂态护盾适配层；原版黄心和吞丹持久池继续由各自系统持有。 */
public final class ShieldRuntime {
    private static final Map<LivingEntity, ShieldLedger> LEDGERS = new IdentityHashMap<>();
    private static final TagKey<DamageType> BYPASSES_SHIELDS = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("myvillage", "bypasses_shields"));
    private static boolean registered;

    private ShieldRuntime() { }

    /** 必须在 StatsRuntime、PillAegisRuntime 之后、两种攻击天命收据之前注册。 */
    public static void register() {
        if (registered) return;
        ShieldRules.get();
        registered = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ShieldRuntime::damage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ShieldRuntime::death);
        NeoForge.EVENT_BUS.addListener(ShieldRuntime::tick);
        NeoForge.EVENT_BUS.addListener(ShieldRuntime::leave);
        NeoForge.EVENT_BUS.addListener(ShieldRuntime::logout);
        NeoForge.EVENT_BUS.addListener(ShieldRuntime::dimension);
        NeoForge.EVENT_BUS.addListener(ShieldRuntime::clonePlayer);
        NeoForge.EVENT_BUS.addListener(ShieldRuntime::stopping);
    }

    /** 只接受服务器线程；同源覆盖，异源受数量／总量限制，不会撤销其他来源。 */
    public static boolean grant(LivingEntity target, ResourceLocation source, float amount, int durationTicks) {
        if (!usable(target) || source == null || !Float.isFinite(amount) || amount <= 0 || durationTicks <= 0)
            return false;
        long now = now(target);
        ShieldLedger ledger = LEDGERS.get(target);
        if (ledger == null) {
            prune();
            if (LEDGERS.size() >= ShieldRules.get().maxTrackedEntities()) return false;
            ledger = new ShieldLedger(ShieldRules.get());
            if (!ledger.grant(source, amount, durationTicks, now)) return false;
            LEDGERS.put(target, ledger);
            return true;
        }
        return ledger.grant(source, amount, durationTicks, now);
    }

    /**
     * Prepare a separate ledger, then pay and install on the same server thread.
     * Rejection/capacity failure never calls payment; payment failure leaves every source unchanged.
     * The payment callback must only commit its resource, without granting/removing shields.
     */
    public static boolean grantPaid(LivingEntity target, ResourceLocation source, float amount,
            int durationTicks, java.util.function.BooleanSupplier payment) {
        if (!usable(target) || source == null || !Float.isFinite(amount) || amount <= 0 || durationTicks <= 0 || payment == null)
            return false;
        prune();
        ShieldLedger current = LEDGERS.get(target);
        if (current == null && LEDGERS.size() >= ShieldRules.get().maxTrackedEntities()) return false;
        ShieldLedger ledger = current == null ? new ShieldLedger(ShieldRules.get()) : current;
        if (!ledger.grantPaid(source, amount, durationTicks, now(target), payment)) return false;
        LEDGERS.put(target, ledger);
        return true;
    }

    public static boolean removeSource(LivingEntity target, ResourceLocation source) {
        if (!serverThread(target) || source == null) return false;
        ShieldLedger ledger = LEDGERS.get(target);
        if (ledger == null) return false;
        boolean removed = ledger.removeSource(source, now(target));
        if (ledger.isEmpty()) LEDGERS.remove(target);
        return removed;
    }

    /** 仅新暂态池的有效余额；不包含吞丹护盾和原版 absorption。 */
    public static float total(LivingEntity target) {
        if (!serverThread(target)) return 0;
        if (!usable(target)) {
            LEDGERS.remove(target);
            return 0;
        }
        ShieldLedger ledger = LEDGERS.get(target);
        if (ledger == null) return 0;
        float total = ledger.total(now(target));
        if (ledger.isEmpty()) LEDGERS.remove(target);
        return total;
    }

    /** 分来源的只读诊断快照，按实际消费顺序返回绝对到期 tick。 */
    public static List<ShieldLedger.Entry> sources(LivingEntity target) {
        if (!serverThread(target) || total(target) <= 0) return List.of();
        ShieldLedger ledger = LEDGERS.get(target);
        return ledger == null ? List.of() : ledger.entries(now(target));
    }

    public static void clear(LivingEntity target) {
        if (serverThread(target)) LEDGERS.remove(target);
    }

    /** 与旧吞丹护盾共用同一绕过策略；显式 ID 防止数据包移除原版标签。 */
    public static boolean bypassesShields(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(BYPASSES_SHIELDS)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL);
    }

    /** 在余额提交后报告同一 container，攻击天命即使面对全额护盾也只消费一次。 */
    public static void reportAbsorbed(DamageContainer container, float blocked) {
        if (!Float.isFinite(blocked) || blocked <= 0) return;
        TemperedBonesRuntime.shieldAbsorbed(container, blocked);
        SwordHeartRuntime.shieldAbsorbed(container, blocked);
    }

    private static void damage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (!usable(target)) return;
        ShieldLedger ledger = LEDGERS.get(target);
        if (ledger == null) return;
        var result = ledger.absorb(event.getNewDamage(), now(target), bypassesShields(event.getSource()));
        if (ledger.isEmpty()) LEDGERS.remove(target);
        if (result.blocked() <= 0) return;
        event.setNewDamage(result.damage());
        reportAbsorbed(event.getContainer(), result.blocked());
    }

    private static boolean serverThread(LivingEntity target) {
        return target != null && target.level() instanceof ServerLevel level && level.getServer().isSameThread();
    }

    private static boolean usable(LivingEntity target) {
        return serverThread(target) && target.isAlive() && !target.isRemoved()
                && !(target instanceof Player player && player.isSpectator());
    }

    private static long now(LivingEntity target) { return target.getServer().overworld().getGameTime(); }

    private static void prune() {
        LEDGERS.entrySet().removeIf(entry -> {
            if (!usable(entry.getKey())) return true;
            entry.getValue().expire(now(entry.getKey()));
            return entry.getValue().isEmpty();
        });
    }

    private static void tick(ServerTickEvent.Post event) { prune(); }
    private static void death(LivingDeathEvent event) { clear(event.getEntity()); }
    private static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity target) clear(target);
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    private static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    private static void clonePlayer(PlayerEvent.Clone event) {
        clear(event.getOriginal());
        clear(event.getEntity());
    }
    private static void stopping(ServerStoppingEvent event) { stop(); }

    /** 停服只清暂态池，不撤销事件注册，兼容同一 JVM 再开服务器。 */
    public static void stop() { LEDGERS.clear(); }
}
