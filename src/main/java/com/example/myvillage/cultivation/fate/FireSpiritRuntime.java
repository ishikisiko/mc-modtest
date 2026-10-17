package com.example.myvillage.cultivation.fate;

import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.stats.runtime.RealmArmorRuntime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Attributed fire damage with bounded transient work and independently saved burst cooldowns. */
public final class FireSpiritRuntime {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("myvillage", "fire_spirit"));
    private static final int MAX_TRACKED_PAIRS = 4096;
    private static final Logger LOGGER = LoggerFactory.getLogger(FireSpiritRuntime.class);
    private static final Map<Pair, Tracked> TARGETS = new HashMap<>();
    // Keys are exact nested damage-container identities; values never retain entities or their key.
    private static final Map<DamageContainer, Double> HEALTH = new WeakHashMap<>();

    private FireSpiritRuntime() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, FireSpiritRuntime::beforeDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, FireSpiritRuntime::afterDamage);
        NeoForge.EVENT_BUS.addListener(FireSpiritRuntime::tick);
        NeoForge.EVENT_BUS.addListener(FireSpiritRuntime::logout);
        NeoForge.EVENT_BUS.addListener(FireSpiritRuntime::changedDimension);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FireSpiritRuntime::death);
        NeoForge.EVENT_BUS.addListener(FireSpiritRuntime::stopping);
    }

    private static void beforeDamage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || CombatDamageService.isResolvingHit(target)
                || CombatDamageService.isDerivedHit(target) || directAttacker(event.getSource()) == null) return;
        // Sampling HP rather than event.newDamage automatically excludes every absorption layer.
        if (HEALTH.size() < MAX_TRACKED_PAIRS)
            HEALTH.put(event.getContainer(), (double) target.getHealth());
    }

    private static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageContainer container = RealmArmorRuntime.currentContainer(target);
        Double before = HEALTH.remove(container);
        ServerPlayer attacker = directAttacker(event.getSource());
        if (before == null || attacker == null || CombatDamageService.isResolvingHit(target)
                || CombatDamageService.isDerivedHit(target)) return;
        double loss = FireSpiritTarget.primaryLoss(before, target.getHealth(),
                TemperedBonesRuntime.primaryFraction(container));
        directHit(attacker, target, loss, attacker.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    private static ServerPlayer directAttacker(DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return null;
        if (source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player) return player;
        if (source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() == player) return player;
        return null; // Thorns, environmental fire, summons and unclassified indirect sources never count.
    }

    /**
     * Called once after a logical authored primary hit, outside its hurt scope. The caller supplies
     * actual primary HP loss (clamped for overkill and excluding bonus retaliation), never requested
     * damage or one call per element. Vanilla melee/projectiles use the independent event fallback.
     */
    public static void directHit(ServerPlayer attacker, LivingEntity target,
            double actualPrimaryHealthLoss, double weaponAttackAttribute) {
        if (!valid(attacker, target) || !Double.isFinite(actualPrimaryHealthLoss)
                || actualPrimaryHealthLoss <= 0 || CombatDamageService.isDerivedHit(target)) return;
        double fraction = FateService.effect(attacker, "fire_burn_fraction");
        int duration = (int) FateService.effect(attacker, "fire_burn_ticks");
        int interval = (int) FateService.effect(attacker, "fire_burn_interval_ticks");
        int required = (int) FateService.effect(attacker, "fire_burst_hits");
        int cooldown = (int) FateService.effect(attacker, "fire_burst_cooldown_ticks");
        double multiplier = FateService.effect(attacker, "fire_burst_multiplier");
        if (!(fraction > 0) || duration <= 0 || interval <= 0 || required <= 0 || cooldown <= 0 || !(multiplier > 0)) return;
        Pair key = new Pair(attacker.getUUID(), target.getUUID());
        Tracked tracked = TARGETS.get(key);
        if (tracked == null) {
            if (TARGETS.size() >= MAX_TRACKED_PAIRS || TARGETS.keySet().stream()
                    .filter(pair -> pair.owner().equals(attacker.getUUID())).count() >= FireSpiritCooldownState.MAX_TARGETS) return;
            tracked = new Tracked(attacker.level().dimension().location(), new FireSpiritTarget());
            TARGETS.put(key, tracked);
        }
        long now = clock(attacker.getServer());
        tracked.state().burn(now, actualPrimaryHealthLoss, fraction, duration, interval);
        FateState latest = FateService.state(attacker);
        FireSpiritCooldownState saved = latest.fireSpirit();
        if (tracked.state().hit(required, saved.canRecord(target.getUUID(), now), weaponAttackAttribute, multiplier)) {
            // Commit before the deferred damage can execute; logout/death cannot reset this deadline.
            CultivationService.replaceFates(attacker,
                    latest.withFireSpirit(saved.record(target.getUUID(), now, cooldown)), false);
            LOGGER.info("FIRE_SPIRIT_BURST owner={} target={} tick={} ready_at={} weapon_damage={}",
                    attacker.getUUID(), target.getUUID(), now, saved.record(target.getUUID(), now, cooldown)
                            .readyAt(target.getUUID()), weaponAttackAttribute);
        }
        particles(attacker.serverLevel(), target, false);
    }

    private static boolean valid(ServerPlayer attacker, LivingEntity target) {
        if (!attacker.isAlive() || attacker.isRemoved() || attacker.isSpectator() || !target.isAlive()
                || target.isRemoved() || target == attacker || attacker.level() != target.level()
                || attacker.isAlliedTo(target) || target.isAlliedTo(attacker)) return false;
        return !(target instanceof ServerPlayer other) || attacker.getServer().isPvpAllowed()
                && attacker.canHarmPlayer(other) && other.canHarmPlayer(attacker) && !other.isSpectator();
    }

    private static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = clock(server);
        // No entity or level references survive in the ledger; lookup never loads target chunks.
        // A derived killing blow can synchronously remove a ledger in the death listener.
        for (var entry : List.copyOf(TARGETS.entrySet())) {
            ServerPlayer attacker = server.getPlayerList().getPlayer(entry.getKey().owner());
            if (attacker == null || !attacker.level().dimension().location().equals(entry.getValue().dimension())
                    || !(attacker.serverLevel().getEntity(entry.getKey().target()) instanceof LivingEntity target)
                    || !valid(attacker, target) || FateService.effect(attacker, "fire_burn_fraction") <= 0) {
                TARGETS.remove(entry.getKey(), entry.getValue());
                continue;
            }
            var due = entry.getValue().state().drain(now);
            if (due.burn() > 0) hurt(attacker, target, due.burn(), "burn");
            if (due.burst() > 0 && valid(attacker, target)) {
                hurt(attacker, target, due.burst(), "burst");
                particles(attacker.serverLevel(), target, true);
            }
            if (entry.getValue().state().idle()) TARGETS.remove(entry.getKey(), entry.getValue());
        }
        // No Post is guaranteed when every point is absorbed. Prevent stale weak receipts accumulating.
        HEALTH.clear();
    }

    private static void hurt(ServerPlayer attacker, LivingEntity target, double amount, String kind) {
        var holder = attacker.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE_TYPE);
        // No outgoing() call: the fixed derived amount passes armor/fire resistance exactly once.
        float before = target.getHealth();
        boolean accepted = CombatDamageService.hurtDerived(attacker, target, new DamageSource(holder, attacker), (float) amount);
        LOGGER.info("FIRE_SPIRIT_DAMAGE owner={} target={} tick={} kind={} requested={} actual={} accepted={}",
                attacker.getUUID(), target.getUUID(), clock(attacker.getServer()), kind, amount,
                FireSpiritTarget.primaryLoss(before, target.getHealth(), 1), accepted);
    }

    private static void particles(ServerLevel level, LivingEntity target, boolean burst) {
        level.sendParticles(burst ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                target.getX(), target.getY() + target.getBbHeight() * .5, target.getZ(), burst ? 12 : 2,
                target.getBbWidth() * .25, target.getBbHeight() * .25, target.getBbWidth() * .25, .01);
    }

    private static long clock(MinecraftServer server) { return server.overworld().getGameTime(); }
    public static void forget(ServerPlayer player) {
        TARGETS.keySet().removeIf(pair -> pair.owner().equals(player.getUUID()) || pair.target().equals(player.getUUID()));
    }
    public static void clear() { TARGETS.clear(); HEALTH.clear(); }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }
    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }
    private static void death(LivingDeathEvent event) {
        UUID id = event.getEntity().getUUID();
        TARGETS.keySet().removeIf(pair -> pair.owner().equals(id) || pair.target().equals(id));
    }
    private static void stopping(ServerStoppingEvent event) { clear(); }
    private record Pair(UUID owner, UUID target) { }
    private record Tracked(ResourceLocation dimension, FireSpiritTarget state) { }
}
