package com.example.myvillage.cultivation.fate;

import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.runtime.DerivedSwordWaves;
import com.example.myvillage.cultivation.CultivationService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.LoggerFactory;

/** A canceled enemy attack earns one opening; a real accepted sword hit spends it exactly once. */
public final class SwordHeartRuntime {
    private record VanillaCritical(Entity target, long tick, ItemStack weapon) { }
    private record Hit(LivingEntity attacker, LivingEntity target, long opening, ItemStack weapon,
                       ResourceLocation skill, float yaw, boolean vanillaCritical) { }
    private static final Map<UUID, VanillaCritical> VANILLA_CRITICALS = new HashMap<>();
    // A rejected/canceled hurt never reaches acceptance. Weak keys avoid retaining such containers.
    private static final Map<DamageContainer, Hit> HITS = new WeakHashMap<>();

    private SwordHeartRuntime() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SwordHeartRuntime::critical);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, SwordHeartRuntime::incoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SwordHeartRuntime::beforeDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SwordHeartRuntime::shieldBlock);
        NeoForge.EVENT_BUS.addListener(SwordHeartRuntime::tick);
    }

    private static FateDefinition definition(LivingEntity player) {
        if (!FateActors.valid(player)
                || !FateService.state(player).confirmed()) return null;
        var definitions = FateService.active(player);
        return FateService.state(player).selected().stream().map(definitions::get).filter(Objects::nonNull)
                .filter(d -> d.effect("heart_critical_bonus") > 0).findFirst().orElse(null);
    }

    private static boolean sword(LivingEntity player) {
        return CombatStyles.bundled().weapon(player.getMainHandItem())
                .flatMap(w -> w.family()).filter("sword"::equals).isPresent();
    }

    private static long now(LivingEntity player) { return player.getServer().overworld().getGameTime(); }

    /** Called only after CombatDodgeService really canceled this incoming hurt. Key presses never call it. */
    public static void onDodgedAttack(LivingEntity player, DamageSource source, float amount, long dodgeStartedAt) {
        FateDefinition definition = definition(player);
        if (definition == null) return;
        boolean enemy = !CombatDamageService.isDerivedHit(player)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && enemyDirectAttack(player, source);
        FateState latest = FateService.state(player);
        SwordHeartState next = SwordHeartEffects.dodged(latest.swordHeart(), now(player),
                FateActors.level(player).getGameTime(), dodgeStartedAt,
                (int) definition.effect("heart_window_ticks"), (int) definition.effect("heart_opening_ticks"),
                (int) definition.effect("heart_cooldown"), enemy, amount);
        if (next.equals(latest.swordHeart())) return;
        CultivationService.replaceFates(player, latest.withSwordHeart(next), false);
        FateActors.message(player,Component.translatable("fate.myvillage.innate_sword_heart.ready"));
        LoggerFactory.getLogger(SwordHeartRuntime.class).info("SWORD_HEART player={} t={} result=ready expires={} cooldown={}",
                player.getName().getString(), now(player), next.openingExpiresAt(), next.cooldownReadyAt());
    }

    private static boolean enemyDirectAttack(LivingEntity player, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || !enemy(attacker, player)) return false;
        // Being attributed to a living entity alone does not make fire, poison, thorns or magic direct.
        if (source.getDirectEntity() == attacker)
            return source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                    || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.STING);
        return source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() == attacker;
    }

    private static boolean enemy(LivingEntity attacker, LivingEntity player) {
        if (attacker == player || attacker.isAlliedTo(player) || player.isAlliedTo(attacker)) return false;
        if (player instanceof ServerPlayer user && attacker instanceof ServerPlayer other)
            return user.getServer().isPvpAllowed() && user.canHarmPlayer(other) && other.canHarmPlayer(user);
        if(player instanceof com.example.myvillage.entity.npc.NpcEntity)return FateActors.mayHarm(player,attacker);
        return attacker instanceof Enemy || attacker instanceof Mob mob && mob.getTarget() == player;
    }

    /** Vanilla already multiplies a natural critical before hurt; forcing it here avoids a second multiplier. */
    private static void critical(CriticalHitEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !sword(player)
                || !validTarget(player, event.getTarget())) return;
        FateDefinition definition = definition(player);
        if (definition == null) return;
        double multiplier = SwordHeartEffects.criticalMultiplier(FateService.state(player).swordHeart(),
                now(player), definition.effect("heart_critical_bonus"), true);
        if (multiplier <= 1) return;
        event.setCriticalHit(true);
        event.setDamageMultiplier(Math.max(event.getDamageMultiplier(), (float) multiplier));
        VANILLA_CRITICALS.put(player.getUUID(), new VanillaCritical(event.getTarget(),
                FateActors.level(player).getGameTime(), player.getMainHandItem()));
    }

    private static boolean validTarget(LivingEntity player, Entity target) {
        return target instanceof LivingEntity && target != player && target.isAlive()
                && !target.isAlliedTo(player) && !player.isAlliedTo(target)
                && target instanceof LivingEntity living && FateActors.mayHarm(player,living);
    }

    private static void incoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (target.level().isClientSide() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(source.getEntity() instanceof LivingEntity player) || source.getDirectEntity() != player
                || !(source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)) || !validTarget(player, target) || !sword(player)
                || CombatDamageService.isDerivedHit(target) || CombatDamageService.isResolvingSkillHit(target)
                || !Float.isFinite(event.getAmount()) || event.getAmount() <= 0) return;
        FateDefinition definition = definition(player);
        if (definition == null) return;
        SwordHeartState state = FateService.state(player).swordHeart();
        double multiplier = SwordHeartEffects.criticalMultiplier(state, now(player),
                definition.effect("heart_critical_bonus"), true);
        if (multiplier <= 1) return;
        VanillaCritical vanilla = VANILLA_CRITICALS.remove(player.getUUID());
        boolean alreadyCritical = !CombatDamageService.isResolvingHit(target) && vanilla != null
                && vanilla.target() == target && vanilla.tick() == FateActors.level(player).getGameTime()
                && vanilla.weapon() == player.getMainHandItem();
        double enhanced = event.getAmount() * (alreadyCritical ? 1 : multiplier);
        if (!Double.isFinite(enhanced) || enhanced > Float.MAX_VALUE) return;
        ResourceLocation skill = definition.derivedSkill().orElse(null);
        if (skill == null) return;
        event.setAmount((float) enhanced);
        HITS.put(event.getContainer(), new Hit(player, target, state.openingExpiresAt(),
                player.getMainHandItem(), skill, player.getYRot(), alreadyCritical));
    }

    private static void beforeDamage(LivingDamageEvent.Pre event) {
        accepted(event.getContainer(), event.getNewDamage(), 0);
    }

    private static void shieldBlock(LivingShieldBlockEvent event) {
        if (event.getBlocked()) accepted(event.getDamageContainer(), 0, event.getBlockedDamage());
    }

    /** Independent innate shields call only after their immutable shield pool has actually committed. */
    public static void shieldAbsorbed(DamageContainer container, double blocked) { accepted(container, 0, blocked); }

    private static void accepted(DamageContainer container, double residual, double blocked) {
        if (!SwordHeartEffects.acceptedDamage(residual, blocked)) return;
        Hit hit = HITS.remove(container);
        if (hit == null) return;
        LivingEntity player = hit.attacker();
        if (!player.isAlive() || player.isRemoved() || player.getMainHandItem() != hit.weapon()
                || !sword(player) || definition(player) == null) return;
        FateState latest = FateService.state(player);
        SwordHeartState next = SwordHeartEffects.consume(latest.swordHeart(), now(player), hit.opening(), true);
        if (next.equals(latest.swordHeart())) return;
        CultivationService.replaceFates(player, latest.withSwordHeart(next), false);
        if (!hit.vanillaCritical() && player instanceof ServerPlayer user) user.crit(hit.target());
        boolean wave = CombatStyles.bundled().skill(hit.skill())
                .map(skill -> DerivedSwordWaves.launch(player, skill, hit.yaw())).orElse(false);
        FateActors.message(player,Component.translatable("fate.myvillage.innate_sword_heart.trigger"));
        LoggerFactory.getLogger(SwordHeartRuntime.class).info("SWORD_HEART player={} t={} result=trigger target={} wave={}",
                player.getName().getString(), now(player), hit.target().getId(), wave);
    }

    private static void tick(ServerTickEvent.Post event) {
        // Hurt and all acceptance callbacks are synchronous; rejected containers cannot claim later.
        HITS.clear();
        VANILLA_CRITICALS.clear();
        for (LivingEntity player : FateActors.all(event.getServer())) {
            FateState latest = FateService.state(player);
            SwordHeartState next = definition(player) == null ? latest.swordHeart().clearOpening()
                    : latest.swordHeart().expire(now(player));
            if (!next.equals(latest.swordHeart()))
                CultivationService.replaceFates(player, latest.withSwordHeart(next), false);
        }
    }

    /** Logout/death/dimension/reset discard the short opening; the persistent cooldown survives. */
    public static void forget(LivingEntity player) {
        VANILLA_CRITICALS.remove(player.getUUID());
        HITS.values().removeIf(hit -> hit.attacker() == player);
        FateState latest = FateService.state(player);
        SwordHeartState next = latest.swordHeart().clearOpening();
        if (!next.equals(latest.swordHeart())) CultivationService.replaceFates(player, latest.withSwordHeart(next), false);
    }

    public static void stop() { VANILLA_CRITICALS.clear(); HITS.clear(); }
}
