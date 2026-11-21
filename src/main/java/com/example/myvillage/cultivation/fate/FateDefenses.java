package com.example.myvillage.cultivation.fate;

import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.runtime.CombatDodgeService;
import com.example.myvillage.cultivation.fate.ability.FateAbilityCooldowns;
import com.example.myvillage.entity.undead.UndeadArrowEntity;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.runtime.StatsRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

public final class FateDefenses {
    private static final ResourceLocation REFLECT = ResourceLocation.parse("yuanxian:earth_spirit_reflect");
    private static final ResourceLocation DISSOLVE = ResourceLocation.parse("yuanxian:water_spirit_dissolve");
    private static final ResourceLocation EARTH = ResourceLocation.parse("yuanxian:fate_earth_resistance");
    private static final ResourceLocation WATER = ResourceLocation.parse("yuanxian:fate_water_resistance");
    private static final String REFLECTED_DAMAGE = "yuanxian_reflected_damage";
    private static final Map<Projectile, Vec3> RETURN_MOTION = new WeakHashMap<>();

    private FateDefenses() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FateDefenses::impact);
        NeoForge.EVENT_BUS.addListener(FateDefenses::finishBounce);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FateDefenses::effect);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FateDefenses::dissolve);
    }

    /** All custom harmful or control effects use this before committing their status. */
    public static boolean immune(LivingEntity target) {
        return !target.level().isClientSide() && FateActors.supported(target)
                && FateService.effect(target, "harm_immunity") > 0;
    }

    private static void effect(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect().value().getCategory() == MobEffectCategory.HARMFUL
                && immune(event.getEntity())) event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    public static void refresh(LivingEntity target) {
        if (immune(target)) {
            for (var effect : java.util.List.copyOf(target.getActiveEffects())) {
                if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) target.removeEffect(effect.getEffect());
            }
        }
        resistance(target, DamageType.EARTH, EARTH, FateService.effect(target, "earth_resistance") + SacredHeart.earthResistance(target));
        resistance(target, DamageType.WATER, WATER, FateService.effect(target, "water_resistance"));
    }

    private static void resistance(LivingEntity target, DamageType type, ResourceLocation id, double amount) {
        var attribute = target.getAttribute(StatsRuntime.resistance(type));
        if (attribute == null) return;
        var modifier = attribute.getModifier(id);
        if (modifier != null && modifier.amount() == amount) return;
        attribute.removeModifier(id);
        if (amount > 0) attribute.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    public static boolean directMelee(DamageSource source) {
        return source.getDirectEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()
                && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                    || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.STING));
    }

    private static void dissolve(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (target.level().isClientSide() || !FateActors.supported(target) || !directMelee(source)
                || event.getAmount() <= 0 || CombatDamageService.isResolvingSkillHit(target)
                || target.isDamageSourceBlocked(source) || !CombatActors.mayHarm((LivingEntity) source.getEntity(), target)) return;
        if (!roll(target, DISSOLVE, "dissolve_chance", "dissolve_cooldown")) return;
        event.setCanceled(true);
        feedback(target);
    }

    private static void impact(ProjectileImpactEvent event) {
        Projectile shot = event.getProjectile();
        if (!(shot.level() instanceof ServerLevel level) || reflected(shot)
                || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity defender) || !defender.isAlive()
                || !FateActors.supported(defender) || !(shot.getOwner() instanceof LivingEntity attacker)
                || !CombatActors.mayHarm(attacker, defender) || CombatDodgeService.isDodging(defender)
                || FateService.effect(defender, "reflect_chance") <= 0 || shot.getDeltaMovement().lengthSqr() < 1.0e-6) return;
        DamageSource source;
        if (shot instanceof AbstractArrow arrow && (arrow instanceof Arrow || arrow instanceof SpectralArrow
                || arrow instanceof UndeadArrowEntity undead && undead.directShot())) {
            source = defender.damageSources().arrow(arrow, attacker);
        } else if (shot instanceof SmallFireball fireball) {
            source = defender.damageSources().fireball(fireball, attacker);
        } else return;
        if (defender.isInvulnerableTo(source) || defender.isDamageSourceBlocked(source)
                || source.is(DamageTypeTags.IS_FIRE) && (defender.fireImmune() || defender.hasEffect(MobEffects.FIRE_RESISTANCE))) return;
        float raw;
        if (shot instanceof UndeadArrowEntity undead) raw = undead.directDamage(attacker);
        else if (shot instanceof AbstractArrow arrow) {
            double base = arrow.getBaseDamage();
            if (arrow.getWeaponItem() != null) base = EnchantmentHelper.modifyDamage(level, arrow.getWeaponItem(), defender, source, (float) base);
            int damage = Mth.ceil(Mth.clamp(arrow.getDeltaMovement().length() * base, 0, Integer.MAX_VALUE));
            raw = arrow.isCritArrow() ? Math.min(Integer.MAX_VALUE, damage + (long) arrow.getRandom().nextInt(damage / 2 + 2)) : damage;
        } else raw = 5;
        if (raw <= 0 || !roll(defender, REFLECT, "reflect_chance", "reflect_cooldown")) return;
        double returned = raw * FateRuntime.attackFactor(attacker, defender, source) * FateService.effect(defender, "reflect_fraction");
        if (shot instanceof AbstractArrow arrow) {
            arrow.setCritArrow(false);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        }
        shot.getPersistentData().putFloat(REFLECTED_DAMAGE, (float) returned);
        shot.setOwner(defender);
        Vec3 incoming = shot.getDeltaMovement();
        Vec3 reversed = incoming.scale(-1);
        Vec3 origin = shot.position();
        // Vanilla projectile hits may store the feet of the target instead of the contact point.
        Vec3 contact = defender.getBoundingBox().inflate(.3).clip(origin, origin.add(incoming)).orElse(origin);
        shot.setPos(contact.add(reversed.normalize().scale(.04)));
        // Finish the impact tick at contact so the next tick sweeps for blocks on the return path.
        shot.setDeltaMovement(Vec3.ZERO);
        RETURN_MOTION.put(shot, reversed);
        event.setCanceled(true);
        feedback(defender);
    }

    private static void finishBounce(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Projectile shot) || shot.level().isClientSide()) return;
        Vec3 motion = RETURN_MOTION.remove(shot);
        if (motion != null && !shot.isRemoved()) {
            shot.setDeltaMovement(motion);
            shot.hasImpulse = true;
        }
    }

    public static boolean reflected(Projectile arrow) { return arrow.getPersistentData().contains(REFLECTED_DAMAGE); }

    public static float reflectedDamage(Projectile arrow) { return arrow.getPersistentData().getFloat(REFLECTED_DAMAGE); }

    private static boolean roll(LivingEntity target, ResourceLocation id, String chanceKey, String cooldownKey) {
        double chance = FateService.effect(target, chanceKey);
        int ticks = (int) FateService.effect(target, cooldownKey);
        if (chance <= 0 || ticks <= 0) return false;
        var clock = target.getServer().overworld();
        var cooldowns = FateAbilityCooldowns.get(clock);
        var owner = FateActors.identity(target);
        long now = clock.getGameTime();
        if (cooldowns.remaining(owner, id, now) > 0 || target.getRandom().nextDouble() >= chance) return false;
        cooldowns.start(owner, id, now, ticks);
        return true;
    }

    private static void feedback(LivingEntity target) {
        var level = (ServerLevel) target.level();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON,
                target.getX(), target.getY(.55), target.getZ(), 12, .3, .45, .3, .075);
    }
}
