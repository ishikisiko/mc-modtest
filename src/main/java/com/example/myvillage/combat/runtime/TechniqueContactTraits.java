package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.elemental.ElementalEffects;
import com.example.myvillage.cultivation.data.TechniqueTraits;
import com.example.myvillage.cultivation.data.TechniqueTraits.ContactRole;
import com.example.myvillage.cultivation.data.TechniqueTraits.ContactRule;
import com.example.myvillage.cultivation.data.TechniqueTraits.ContactState;
import com.example.myvillage.cultivation.technique.HeartPassives;
import com.example.myvillage.stats.DamageType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Direct technique contacts share the same eligibility and positive-damage commit rules. */
public final class TechniqueContactTraits {
    public record Receipt(List<ContactRule> rules, ContactState spend, double multiplier, boolean firstHit) {
        public void accepted(LivingEntity caster, Entity contact, double elementalBase) {
            if (spend != null) consume(spend, caster, contact);
            for (var rule : rules) {
                if (rule.role() == ContactRole.GAIN) grant(rule.state(), caster, contact, elementalBase, firstHit);
            }
        }
    }

    private static final Receipt NONE = new Receipt(List.of(), null, 1, false);

    private TechniqueContactTraits() { }

    public static Receipt prepare(LivingEntity caster, Entity contact, ResourceLocation art, boolean firstHit) {
        return prepare(caster, contact, art, firstHit, true);
    }

    public static Receipt prepare(LivingEntity caster, Entity contact, ResourceLocation art,
                                  boolean firstHit, boolean canSpendSpearForce) {
        var rules = TechniqueTraits.contactRules(art);
        if (rules.isEmpty()) return NONE;
        for (var rule : rules) {
            if (rule.role() == ContactRole.GAIN || !canSpendSpearForce && rule.state() == ContactState.SPEAR_FORCE) continue;
            var state = rule.state();
            int stacks = available(state, caster, contact, firstHit);
            if (stacks == 0) continue;
            boolean pool = weapon(state) != null || element(state) != null;
            double bonus = TechniqueTraits.number(pool ? "contact_pool_bonus" : "contact_status_bonus");
            if (pool) bonus = HeartPassives.stackBonus(caster, state, bonus);
            return new Receipt(rules, rule.role() == ContactRole.SPEND ? state : null,
                    1 + bonus * stacks, firstHit);
        }
        return new Receipt(rules, null, 1, firstHit);
    }

    private static int available(ContactState state, LivingEntity caster, Entity contact, boolean firstHit) {
        if (state == ContactState.SPEAR_FORCE)
            return CombatDisciplines.spearTip(caster, contact) ? CombatDisciplines.stacks(caster, "spear") : 0;
        if (state.casterState() && !firstHit) return 0;
        String family = weapon(state);
        if (family != null) return CombatDisciplines.stacks(caster, family);
        DamageType type = element(state);
        if (type != null) return ElementalEffects.charges(caster, type);
        if (state == ContactState.SPEAR_STANCE)
            return CombatTraitStates.active(caster, caster, CombatTraitStates.Kind.SPEAR_STANCE) ? 1 : 0;
        if (!(CombatTargets.owner(contact) instanceof LivingEntity target)) return 0;
        var kind = mark(state);
        if (kind != null) {
            return (state != ContactState.EARTH_UNBALANCE || target.onGround())
                    && CombatTraitStates.active(caster, target, kind) ? 1 : 0;
        }
        return switch (state) {
            case ARMOR_BREAK -> SwordArmorBreak.active(target) ? 1 : 0;
            case CHILL -> ElementalEffects.hasWaterChill(caster, target) ? 1 : 0;
            default -> 0;
        };
    }

    private static void consume(ContactState state, LivingEntity caster, Entity contact) {
        String family = weapon(state);
        if (family != null) {
            CombatDisciplines.consumeStacks(caster, family);
            return;
        }
        DamageType type = element(state);
        if (type != null) {
            ElementalEffects.consume(caster, type);
            return;
        }
        if (state == ContactState.SPEAR_STANCE) {
            CombatTraitStates.consume(caster, caster, CombatTraitStates.Kind.SPEAR_STANCE);
        } else if (mark(state) != null) {
            CombatTraitStates.consume(caster, contact, mark(state));
        } else if (state == ContactState.CHILL && CombatTargets.owner(contact) instanceof LivingEntity target) {
            ElementalEffects.consumeWaterChill(caster, target);
        }
    }

    private static void grant(ContactState state, LivingEntity caster, Entity contact, double base, boolean firstHit) {
        if (state.casterState()) {
            if (!firstHit) return;
            String family = weapon(state);
            DamageType type = element(state);
            if (state == ContactState.SPEAR_STANCE) {
                CombatTraitStates.grant(caster, caster, CombatTraitStates.Kind.SPEAR_STANCE);
            } else if (family != null) {
                CombatDisciplines.gainFromArt(caster, family);
            } else if (type != null) {
                ElementalEffects.gain(caster, type);
            }
            return;
        }
        if (!(CombatTargets.owner(contact) instanceof LivingEntity target) || !target.isAlive()) return;
        switch (state) {
            case PURSUIT -> {
                if (firstHit) CombatTraitStates.grant(caster, target, CombatTraitStates.Kind.PURSUIT);
            }
            case EARTH_UNBALANCE -> {
                if (target.onGround()) CombatTraitStates.grant(caster, target, CombatTraitStates.Kind.EARTH_UNBALANCE);
            }
            case FIST_UNBALANCE -> CombatTraitStates.grant(caster, target, CombatTraitStates.Kind.FIST_UNBALANCE);
            case CHILL -> ElementalEffects.waterChill(caster, target,
                    TechniqueTraits.integer("contact_chill_ticks"), TechniqueTraits.number("contact_chill_fraction"));
            case EROSION -> ElementalEffects.seed(caster, contact, DamageType.WOOD, base);
            default -> { }
        }
    }

    static String weapon(ContactState state) {
        return switch (state) {
            case SWORD_INTENT -> "sword";
            case SPEAR_FORCE -> "spear";
            case FIST_FORCE -> "fist";
            default -> null;
        };
    }

    static DamageType element(ContactState state) {
        return switch (state) {
            case METAL_FORCE -> DamageType.METAL;
            case WOOD_FORCE -> DamageType.WOOD;
            case WATER_FORCE -> DamageType.WATER;
            case FIRE_FORCE -> DamageType.FIRE;
            case EARTH_FORCE -> DamageType.EARTH;
            default -> null;
        };
    }

    static CombatTraitStates.Kind mark(ContactState state) {
        return switch (state) {
            case PURSUIT -> CombatTraitStates.Kind.PURSUIT;
            case FIST_UNBALANCE -> CombatTraitStates.Kind.FIST_UNBALANCE;
            case EARTH_UNBALANCE -> CombatTraitStates.Kind.EARTH_UNBALANCE;
            default -> null;
        };
    }
}
