package com.example.myvillage.combat.session;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.art.GoldenCoreArt;
import com.example.myvillage.combat.elemental.ElementalEffects;
import com.example.myvillage.combat.elemental.ElementalRules;
import com.example.myvillage.combat.foundation.ControlService;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.runtime.CombatDisciplines;
import com.example.myvillage.combat.runtime.CombatDodgeService;
import com.example.myvillage.combat.runtime.CombatFeedbackService;
import com.example.myvillage.combat.runtime.CombatHitResolver;
import com.example.myvillage.combat.runtime.CombatTargets;
import com.example.myvillage.combat.runtime.StaggerResistant;
import com.example.myvillage.cultivation.technique.HeartTechniques;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.runtime.StatsRuntime;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

final class RealmArtCombat {
    private RealmArtCombat() {}

    static double base(LivingEntity caster, DamageType element, boolean paid) {
        return ElementalEffects.base(caster, element) + StatsRuntime.heartAttackAddition(caster)
                + HeartTechniques.attackExtra(caster, "", element, true, paid);
    }

    static double pvpDamage(LivingEntity caster, Entity target) {
        return caster instanceof ServerPlayer && CombatTargets.owner(target) instanceof ServerPlayer
                ? GoldenCoreArt.rules().pvpDamage() : 1;
    }

    static float hit(LivingEntity caster, Entity target, ArtDefinition art,
                       CombatSession session, double multiplier, double base) {
        var template = art.move(0, 1);
        var move = CombatDisciplines.scale(template, multiplier / template.damageMultiplier(), 0);
        var source = art.elementType().map(element -> ElementalRules.source(caster, element)).orElse(null);
        var result = CombatDamageService.applyRealmArt(caster, target, move, session, pvpDamage(caster, target), source, base);
        if (result.successful()) {
            var contact = new CombatHitResolver.TargetContact(target, target.distanceToSqr(caster), GoldenCoreGeometry.point(target));
            CombatFeedbackService.hit(caster, move, session.revision(), List.of(contact));
        }
        return result.actualDamage();
    }

    static boolean canDisplace(LivingEntity caster, LivingEntity target) {
        return CombatTargets.owner(target) == target && target.isAlive() && !CombatDodgeService.isDodging(target) && !ControlService.isRooted(target)
                && !StaggerResistant.resists(target) && ControlService.canControl(caster, target);
    }

    static double displacementFactor(LivingEntity caster, LivingEntity target) {
        double pvp = caster instanceof ServerPlayer && target instanceof ServerPlayer ? GoldenCoreArt.rules().pvpDisplacement() : 1;
        return pvp * (1 - Math.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1));
    }
}
