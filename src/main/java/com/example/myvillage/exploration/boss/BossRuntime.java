package com.example.myvillage.exploration.boss;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Vanilla retains all AI, phase, immunity, source, armor and status-effect processing. */
public final class BossRuntime {
    public static final String BIRTH = BossAssignment.BIRTH;
    public static final String REALM = BossAssignment.REALM;
    public static final ResourceLocation HEALTH = ResourceLocation.fromNamespaceAndPath("myvillage", "exploration.boss_health");
    public static final ResourceLocation ATTACK = ResourceLocation.fromNamespaceAndPath("myvillage", "exploration.boss_attack");
    private BossRuntime() { }

    public static void register() {
        BossRules.get();
        // Covers dragon respawns and wither construction, which never finalizeSpawn, plus legacy saves.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BossRuntime::join);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BossRuntime::damage);
    }
    public static Optional<BossRules.Profile> profile(LivingEntity entity) {
        return BossAssignment.resolve(entity.getPersistentData(), entityType(entity), BossRules.get());
    }
    /** Narrow Mixin adapter: never invoked by generic heal events or the passive heal(1) path. */
    public static float witherChargeHealing(WitherBoss wither, float vanillaAmount) {
        if (wither.level().isClientSide() || profile(wither).isEmpty()) return vanillaAmount;
        AttributeInstance health = wither.getAttribute(Attributes.MAX_HEALTH);
        return health == null ? vanillaAmount
                : BossAssignment.chargeHealing(vanillaAmount, wither.getMaxHealth(), health.getBaseValue());
    }
    private static String entityType(LivingEntity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
    private static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity entity)
                || !BossAssignment.mayAssign(entity.getPersistentData())) return;
        var selected = BossRules.get().forEntity(entityType(entity));
        if (selected.isEmpty()) return;
        var p = selected.get();
        AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        // An incomplete externally supplied modifier must not be silently replaced.
        if (health == null || health.hasModifier(HEALTH) || (attack != null && attack.hasModifier(ATTACK))
                || health.getBaseValue() <= 0 || (p.attributeAttack() != null && (attack == null || attack.getBaseValue() <= 0))) return;
        float oldHealth = entity.getHealth(), oldMaximum = entity.getMaxHealth();
        add(health, HEALTH, p.health() / health.getBaseValue());
        if (p.attributeAttack() != null) add(attack, ATTACK, p.attributeAttack() / attack.getBaseValue());
        entity.setHealth(BossAssignment.adoptedHealth(oldHealth, oldMaximum, entity.getMaxHealth()));
        var birth = BossAssignment.birth(p);
        birth.putString("dimension", entity.level().dimension().location().toString());
        birth.putInt("x", entity.getBlockX()); birth.putInt("y", entity.getBlockY()); birth.putInt("z", entity.getBlockZ());
        birth.putBoolean("loaded_from_disk", event.loadedFromDisk());
        entity.getPersistentData().put(BIRTH, birth);
        entity.getPersistentData().put(REALM, BossAssignment.realm(p));
        // Saved modifiers restore max health before vanilla reads Health. Never reapply/heal on join.
    }
    private static void add(AttributeInstance attribute, ResourceLocation id, double factor) {
        attribute.addPermanentModifier(new AttributeModifier(id, factor - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        var profile = profile(attacker);
        if (profile.isEmpty()) return;
        channel(event.getSource(), attacker).map(profile.get().damage()::get)
                .ifPresent(policy -> event.setAmount(policy.apply(event.getAmount())));
        // This modifies the original hurt container exactly once, before shields/armor/resistance.
        // Attribute melee, ownerless magic and persistent wither DOT deliberately receive no factor.
    }

    private static boolean explosion(DamageSource source) {
        // Vanilla calls any explosion with both direct and indirect entities PLAYER_EXPLOSION,
        // including a wither or its skull. Ownership checks below remain mandatory.
        return source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION);
    }

    /** Exact native source + direct-owner pair; proximity or the victim's last attacker never supplies ownership. */
    public static Optional<BossRules.Channel> channel(DamageSource source, LivingEntity attacker) {
        if (source.getEntity() != attacker) return Optional.empty();
        var direct = source.getDirectEntity();
        if (attacker instanceof ElderGuardian) {
            if (direct == attacker && source.is(DamageTypes.INDIRECT_MAGIC)) return Optional.of(BossRules.Channel.GUARDIAN_BEAM);
            if (direct == attacker && source.is(DamageTypes.THORNS)) return Optional.of(BossRules.Channel.GUARDIAN_THORNS);
        } else if (attacker instanceof EnderDragon) {
            if (direct == attacker && source.is(DamageTypes.MOB_ATTACK)) return Optional.of(BossRules.Channel.DRAGON_CONTACT);
            // Both DragonFireball and perched flame create HARM clouds with the actual dragon as owner.
            if (direct instanceof AreaEffectCloud cloud && cloud.getOwner() == attacker && source.is(DamageTypes.INDIRECT_MAGIC))
                return Optional.of(BossRules.Channel.DRAGON_BREATH);
        } else if (attacker instanceof WitherBoss) {
            if (direct instanceof WitherSkull skull && skull.getOwner() == attacker) {
                if (source.is(DamageTypes.WITHER_SKULL)) return Optional.of(BossRules.Channel.WITHER_SKULL);
                if (explosion(source)) return Optional.of(BossRules.Channel.WITHER_SKULL_EXPLOSION);
            }
            if (direct == attacker && explosion(source)) return Optional.of(BossRules.Channel.WITHER_SPAWN_EXPLOSION);
        } else if (attacker instanceof Warden && direct == attacker && source.is(DamageTypes.SONIC_BOOM)) {
            return Optional.of(BossRules.Channel.WARDEN_SONIC);
        }
        return Optional.empty();
    }
}
