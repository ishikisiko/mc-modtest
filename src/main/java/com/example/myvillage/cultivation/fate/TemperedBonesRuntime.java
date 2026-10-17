package com.example.myvillage.cultivation.fate;

import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.runtime.CombatReactionService;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.stats.runtime.RealmArmorRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-only damage accounting. No timers, offline grants or duplicate player NBT. */
public final class TemperedBonesRuntime {
    // Container identity also separates nested hurt calls. Values never retain their weak key.
    private static final Map<DamageContainer, DamageFrame> DAMAGE = new WeakHashMap<>();
    private static final List<Pulse> PULSES = new ArrayList<>();

    private TemperedBonesRuntime() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TemperedBonesRuntime::incoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TemperedBonesRuntime::beforeDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TemperedBonesRuntime::shieldBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TemperedBonesRuntime::damaged);
        NeoForge.EVENT_BUS.addListener(TemperedBonesRuntime::tick);
    }

    private static void incoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        ServerPlayer armed = null;
        double primaryFraction = 1;
        DamageSource source = event.getSource();
        // Vanilla sword/axe/tool/fist attacks and authored sword/spear melee share player_attack.
        // External sword qi uses that source too, so its hurt-call scope must explicitly exclude it.
        if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker
                && source.is(DamageTypes.PLAYER_ATTACK) && !CombatDamageService.isResolvingSkillHit(target)
                && !attacker.isAlliedTo(target) && !target.isAlliedTo(attacker) && attacker != target
                && event.getAmount() > 0) {
            TemperedBonesState state = FateService.state(attacker).temperedBones();
            double bonus = TemperedBonesEffects.bonusDamage(attacker.getAttributeValue(Attributes.ATTACK_DAMAGE),
                    FateService.effect(attacker, "bones_damage_bonus"), state.ready(), true);
            double enhanced = event.getAmount() + bonus;
            if (bonus > 0 && Double.isFinite(enhanced) && enhanced <= Float.MAX_VALUE) {
                primaryFraction = event.getAmount() / enhanced;
                event.setAmount((float) enhanced);
                armed = attacker;
            }
        }
        DAMAGE.put(event.getContainer(), new DamageFrame(target.getHealth(), armed, primaryFraction));
    }

    private static void beforeDamage(LivingDamageEvent.Pre event) {
        DamageFrame frame = DAMAGE.get(event.getContainer());
        if (frame != null) DAMAGE.put(event.getContainer(),
                new DamageFrame(event.getEntity().getHealth(), frame.armedAttacker(), frame.primaryFraction()));
        // This stage is after accepted hurt/i-frame/armor checks. A positive residual really lands,
        // even if vanilla absorption will take it all and therefore no Post event is fired.
        accepted(event.getContainer(), event.getNewDamage(), 0);
    }

    private static void shieldBlock(LivingShieldBlockEvent event) {
        if (event.getBlocked()) accepted(event.getDamageContainer(), 0, event.getBlockedDamage());
    }

    /** A separate innate shield reports absorption after its immutable pool update has committed. */
    public static void shieldAbsorbed(DamageContainer container, double blocked) {
        accepted(container, 0, blocked);
    }

    private static void accepted(DamageContainer container, double residual, double shieldBlocked) {
        if (!TemperedBonesEffects.acceptedDamage(residual, shieldBlocked)) return;
        DamageFrame frame = DAMAGE.get(container);
        if (frame == null || frame.armedAttacker() == null) return;
        trigger(frame.armedAttacker());
        // Keep primary attribution for the defender, but no later stage can replay the trigger.
        DAMAGE.put(container, new DamageFrame(frame.healthBefore(), null, frame.primaryFraction()));
    }

    public static void damaged(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageFrame frame = DAMAGE.remove(RealmArmorRuntime.currentContainer(target));
        if (frame == null) return;
        double actual = TemperedBonesEffects.primaryHealthLoss(frame.healthBefore(), target.getHealth(), 1);
        if (event.getNewDamage() > 0 && actual > 0 && frame.armedAttacker() != null)
            trigger(frame.armedAttacker());
        if (!(target instanceof ServerPlayer player) || !player.isAlive() || event.getNewDamage() <= 0) return;
        boolean enemy = enemyDirectAttack(event.getSource(), player);
        double lost = TemperedBonesEffects.primaryHealthLoss(frame.healthBefore(), player.getHealth(), frame.primaryFraction());
        FateState latest = FateService.state(player);
        TemperedBonesState previous = latest.temperedBones();
        TemperedBonesState next = TemperedBonesEffects.charge(previous, lost, player.getMaxHealth(),
                FateService.effect(player, "bones_threshold"), enemy);
        if (!next.equals(previous)) {
            CultivationService.replaceFates(player, latest.withTemperedBones(next), false);
            if (next.ready() && !previous.ready())
                player.displayClientMessage(Component.translatable("fate.myvillage.tempered_bones.ready"), true);
        }
    }

    private static void trigger(ServerPlayer player) {
        // A preceding listener may have changed an unrelated fate; always re-read the whole state.
        FateState latest = FateService.state(player);
        TemperedBonesState next = TemperedBonesEffects.consume(latest.temperedBones(), true);
        if (next.equals(latest.temperedBones())) return;
        CultivationService.replaceFates(player, latest.withTemperedBones(next), false);
        player.displayClientMessage(Component.translatable("fate.myvillage.tempered_bones.trigger"), true);
        double radius = FateService.effect(player, "bones_knockback_radius");
        double strength = FateService.effect(player, "bones_knockback_strength");
        if (radius > 0 && strength > 0)
            PULSES.add(new Pulse(player.getUUID(), player.serverLevel(), player.position(), radius, strength));
    }

    private static boolean enemyDirectAttack(DamageSource source, ServerPlayer player) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || !enemy(attacker, player)) return false;
        if (source.getDirectEntity() == attacker)
            return source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                    || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.STING);
        return source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() == attacker;
    }

    private static boolean enemy(LivingEntity target, ServerPlayer player) {
        if (target == player || target.isAlliedTo(player) || player.isAlliedTo(target)) return false;
        if (target instanceof ServerPlayer other)
            return player.getServer().isPvpAllowed() && player.canHarmPlayer(other) && other.canHarmPlayer(player);
        return target instanceof Enemy || target instanceof Mob mob && mob.getTarget() == player;
    }

    private static void tick(ServerTickEvent.Post event) {
        if (PULSES.isEmpty()) return;
        // Apply after the original hit's vanilla/authored reaction, so its impulse cannot erase the pulse.
        List<Pulse> due = List.copyOf(PULSES);
        PULSES.clear();
        for (Pulse pulse : due) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(pulse.owner());
            if (player == null || !player.isAlive() || player.serverLevel() != pulse.level()) continue;
            var area = player.getBoundingBox().move(pulse.center().subtract(player.position())).inflate(pulse.radius());
            for (LivingEntity target : pulse.level().getEntitiesOfClass(LivingEntity.class, area,
                    entity -> entity.isAlive() && enemy(entity, player)
                            && entity.position().distanceToSqr(pulse.center()) <= pulse.radius() * pulse.radius())) {
                Vec3 away = target.position().subtract(pulse.center());
                if (away.horizontalDistanceSqr() == 0) away = player.getLookAngle();
                Vec3 oldMotion = target.getDeltaMovement();
                target.knockback(pulse.strength(), -away.x, -away.z);
                if (target.getDeltaMovement().equals(oldMotion)) continue;
                if (CombatReactionService.holdAdditionalImpulse(target, target.getDeltaMovement().subtract(oldMotion))) {
                    target.setDeltaMovement(oldMotion);
                    continue;
                }
                target.hurtMarked = true;
                if (target instanceof ServerPlayer other) {
                    other.connection.send(new ClientboundSetEntityMotionPacket(other));
                    other.hurtMarked = false;
                    other.setDeltaMovement(oldMotion);
                }
            }
        }
    }

    public static void reset(ServerPlayer player) {
        FateState latest = FateService.state(player);
        if (!latest.temperedBones().equals(TemperedBonesState.EMPTY))
            CultivationService.replaceFates(player, latest.withTemperedBones(TemperedBonesState.EMPTY), false);
        forget(player);
    }

    /** Charge is persisted as-is; leaving never creates or pays out charge. */
    public static void forget(ServerPlayer player) {
        PULSES.removeIf(pulse -> pulse.owner().equals(player.getUUID()));
        DAMAGE.values().removeIf(frame -> frame.armedAttacker() == player);
    }

    public static void stop() { DAMAGE.clear(); PULSES.clear(); }

    private record DamageFrame(float healthBefore, ServerPlayer armedAttacker, double primaryFraction) { }
    private record Pulse(UUID owner, ServerLevel level, Vec3 center, double radius, double strength) { }
}
