package com.example.myvillage.cultivation.technique;

import com.example.myvillage.combat.art.ArtMath;
import com.example.myvillage.combat.elemental.ElementalEffects;
import com.example.myvillage.combat.foundation.HealingRuntime;
import com.example.myvillage.combat.foundation.ShieldRuntime;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatCaster;
import com.example.myvillage.combat.runtime.CombatDisciplines;
import com.example.myvillage.combat.runtime.CombatTraitStates;
import com.example.myvillage.combat.runtime.HeartCombatLinks;
import com.example.myvillage.cultivation.data.HeartMethod.Slot;
import com.example.myvillage.cultivation.data.TechniqueTraits;
import com.example.myvillage.cultivation.fate.ability.FateAbilityCooldowns;
import com.example.myvillage.stats.DamageType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Conversions performed by the heart method currently equipped in the relevant slot. */
public final class HeartTraitRuntime {
    private static final ResourceLocation IRON_MOUNTAIN = id("tie_shan_hu_ti_jue");
    private static final ResourceLocation EARLY_FIST_GUARD = id("houtu_zhenjing");
    private static final ResourceLocation EARLY_SPEAR_GUARD = id("fushan_gong");
    private static final ResourceLocation EARTH_GUARD = id("xirang_zaohua_jing");
    private static final ResourceLocation EARLY_EARTH_GUARD = id("shijia_xuangong");
    private static final Map<ResourceLocation, String> WATER_WARD_RECOVERY = Map.of(
            id("qingxi_jue"), "water_ward_qi_early", id("wanchuan_guihai_jue"), "water_ward_qi");
    private static final ResourceLocation SHORT_SHIELD_COOLDOWN = id("ancestral_short_shield");
    private static final Map<ResourceLocation, Integer> SHIELD_CHAPTERS = Map.of(
            id("jinlin_cuiti_jue"), 1, id("gengjin_bumie_ti"), 2, id("wanjie_jinshen_jue"), 3);
    private static final Map<ResourceLocation, Integer> EROSION_CHAPTERS = Map.of(
            id("yimu_shenghua_jue"), 1, id("jiayi_changsheng_jing"), 2, id("qingdi_daozang"), 3,
            id("qing_di_yang_ying_pian"), 4, id("qing_di_chang_sheng_lu"), 5);

    private HeartTraitRuntime() { }

    static float ancestralRemainder(LivingEntity actor, ResourceLocation technique, ResourceLocation source) {
        return SHIELD_CHAPTERS.getOrDefault(technique, 0) == 3 ? remainingShield(actor, source) : 0;
    }

    static void afterHostileHit(LivingEntity actor, DamageSource damage, ResourceLocation technique, ResourceLocation source,
                                FateAbilityCooldowns cooldowns, UUID owner, long now) {
        if (!frontal(actor, damage) || remainingShield(actor, source) > 0) return;
        int ticks = TechniqueTraits.integer("short_shield_ticks");
        if (SHIELD_CHAPTERS.containsKey(technique)) {
            float amount = (float) Math.min(TechniqueTraits.number("ancestral_shield_maximum"),
                    actor.getMaxHealth() * TechniqueTraits.number("ancestral_shield_fraction"));
            if (cooldowns.remaining(owner, SHORT_SHIELD_COOLDOWN, now) == 0
                    && ShieldRuntime.grant(actor, source, amount, ticks)) {
                cooldowns.start(owner, SHORT_SHIELD_COOLDOWN, now, TechniqueTraits.integer("ancestral_shield_cooldown_ticks"));
                notice(actor, "金身传承生成短盾");
            }
        } else if ((technique.equals(IRON_MOUNTAIN) || technique.equals(EARLY_FIST_GUARD)) && actor.isShiftKeyDown()) {
            double strength = technique.equals(EARLY_FIST_GUARD) ? TechniqueTraits.number("early_fist_shield_ratio") : 1;
            float amount = (float) (actor.getMaxHealth() * strength * TechniqueTraits.number("fist_shield_fraction"));
            if (ShieldRuntime.grantPaid(actor, source, amount, ticks,
                    () -> CombatDisciplines.consumeStacks(actor, "fist") > 0)) notice(actor, "蓄劲转为护盾");
        } else if (technique.equals(EARLY_SPEAR_GUARD) && actor.isShiftKeyDown()) {
            float amount = (float) (actor.getMaxHealth() * TechniqueTraits.number("spear_stance_shield_fraction"));
            if (ShieldRuntime.grantPaid(actor, source, amount, ticks,
                    () -> CombatTraitStates.consume(actor, actor, CombatTraitStates.Kind.SPEAR_STANCE))) notice(actor, "拒马架势转为护盾");
        } else if (technique.equals(EARTH_GUARD) || technique.equals(EARLY_EARTH_GUARD)) {
            double strength = technique.equals(EARLY_EARTH_GUARD) ? TechniqueTraits.number("early_earth_shield_ratio") : 1;
            double remaining = ElementalEffects.wardRemaining(actor, DamageType.EARTH);
            float amount = (float) (remaining * strength * Math.min(TechniqueTraits.number("earth_ward_shield_maximum"),
                    actor.getMaxHealth() * TechniqueTraits.number("earth_ward_shield_fraction")));
            if (ShieldRuntime.grantPaid(actor, source, amount, ticks,
                    () -> ElementalEffects.consumeWard(actor, DamageType.EARTH))) notice(actor, "厚土护体转为护盾");
        } else if (HeartCombatLinks.defend(actor, damage, technique, source, cooldowns, owner, now)) {
            notice(actor, "防守心法已结算");
        }
    }

    static boolean spendShieldAgainstKnockback(LivingEntity actor, ResourceLocation source) {
        if (actor.level().isClientSide() || !actor.isShiftKeyDown() || !HeartTechniques.supports(actor)) return false;
        var technique = CombatCaster.profile(actor).equippedHeart(Slot.DEFENSE).orElse(null);
        if (technique == null || SHIELD_CHAPTERS.getOrDefault(technique, 0) < 2
                || !ShieldRuntime.removeSource(actor, source)) return false;
        notice(actor, "余盾抵消击退");
        return true;
    }

    public static void afterDodge(LivingEntity actor, DamageSource source, float amount) {
        if (amount <= 0 || !(source.getEntity() instanceof LivingEntity attacker) || !CombatActors.mayHarm(attacker, actor)
                || !CombatCaster.active(actor)
                || ElementalEffects.wardRemaining(actor, DamageType.WATER) <= 0
                || CombatCaster.power(actor) >= CombatCaster.capacity(actor)) return;
        String amountSetting = CombatCaster.profile(actor).equippedHeart(Slot.SPIRIT)
                .map(WATER_WARD_RECOVERY::get).orElse(null);
        if (amountSetting != null && CombatCaster.recover(actor, TechniqueTraits.integer(amountSetting))
                && ElementalEffects.consumeWard(actor, DamageType.WATER)) notice(actor, "流水护体转为灵力");
    }

    public static boolean convertWoodPulse(LivingEntity actor, ElementalEffects.Pulse pulse, boolean fullPowerAtStart) {
        if (pulse == null || !CombatCaster.active(actor)) return false;
        ResourceLocation id = CombatCaster.profile(actor).equippedHeart(Slot.SPIRIT).orElse(null);
        Integer chapter = id == null ? null : EROSION_CHAPTERS.get(id);
        if (chapter == null) return false;
        var injured = healingTargets(actor, chapter);
        boolean emergency = chapter >= 4 && !injured.isEmpty()
                && injured.getFirst().getHealth() / injured.getFirst().getMaxHealth()
                     <= TechniqueTraits.number("wood_emergency_health");
        if (!emergency && (!fullPowerAtStart || injured.isEmpty())
                && CombatCaster.power(actor) < CombatCaster.capacity(actor)
                && CombatCaster.recover(actor, TechniqueTraits.integer("wood_qi")) && pulse.spend()) {
            notice(actor, "侵蚀转为灵力");
            return true;
        }
        if (injured.isEmpty()) return false;
        int limit = Math.min(injured.size(), chapter >= 5 ? TechniqueTraits.integer("wood_support_targets") : 1);
        float share = (float) (Math.clamp(pulse.damage(), TechniqueTraits.number("wood_heal_minimum"),
                TechniqueTraits.number("wood_heal_maximum")) / limit);
        float healed = 0;
        for (int i = 0; i < limit; i++) healed += HealingRuntime.heal(injured.get(i), share, id);
        if (healed <= 0 || !pulse.spend()) return false;
        notice(actor, "侵蚀转为治疗");
        return true;
    }

    private static List<LivingEntity> healingTargets(LivingEntity actor, int chapter) {
        if (chapter == 1) return List.of();
        if (chapter == 2) return injured(actor) ? List.of(actor) : List.of();
        double radius = TechniqueTraits.number("wood_support_radius");
        return ((ServerLevel) actor.level()).getEntitiesOfClass(LivingEntity.class, actor.getBoundingBox().inflate(radius),
                        target -> injured(target) && !target.isSpectator() && CombatActors.allied(actor, target)
                                && target.distanceToSqr(actor) <= radius * radius && (target == actor || actor.hasLineOfSight(target)))
                .stream().sorted(Comparator.comparingDouble((LivingEntity target) -> target.getHealth() / target.getMaxHealth())
                        .thenComparingInt(LivingEntity::getId)).toList();
    }

    private static boolean injured(LivingEntity target) {
        return target.isAlive() && target.getHealth() < target.getMaxHealth();
    }

    private static boolean frontal(LivingEntity actor, DamageSource source) {
        var position = source.getSourcePosition();
        if (position == null) return false;
        var direction = position.subtract(actor.position());
        return ArtMath.inArc(direction.x, direction.z, actor.getYRot(), TechniqueTraits.number("frontal_degrees"));
    }

    private static float remainingShield(LivingEntity actor, ResourceLocation source) {
        return ShieldRuntime.sources(actor).stream().filter(entry -> entry.source().equals(source))
                .map(entry -> entry.amount()).findFirst().orElse(0.0F);
    }

    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("yuanxian", name); }
    private static void notice(LivingEntity actor, String message) {
        CombatCaster.message(actor, Component.literal(message));
    }
}
