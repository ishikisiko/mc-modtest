package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.elemental.ElementalEffects;
import com.example.myvillage.combat.elemental.ElementalRules;
import com.example.myvillage.combat.foundation.HealingRuntime;
import com.example.myvillage.combat.foundation.ShieldRuntime;
import com.example.myvillage.combat.session.CombatSession;
import com.example.myvillage.cultivation.data.HeartLinkRules;
import com.example.myvillage.cultivation.data.HeartMethod.Slot;
import com.example.myvillage.cultivation.data.TechniqueTraits;
import com.example.myvillage.cultivation.data.TechniqueTraits.ContactState;
import com.example.myvillage.cultivation.fate.FateDefenses;
import com.example.myvillage.cultivation.fate.ability.FateAbilityCooldowns;
import com.example.myvillage.cultivation.technique.HeartPassives;
import com.example.myvillage.cultivation.technique.HeartTechniques;
import com.example.myvillage.stats.DamageType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** 从真实命中和受伤收据触发装备心法，沿用现有状态来源和每次攻击的伤害结算。 */
public final class HeartCombatLinks {
    private static final ResourceLocation ATTACK = id("heart_link_attack");
    private static final ResourceLocation DEFENSE = id("heart_link_defense");
    private static final ResourceLocation SPIRIT = id("heart_link_spirit");

    record Effect(double multiplier, ContactState charge, Predicate<Boolean> settle) { }

    public static final class Hit {
        private final CombatSession.HeartContact contact;
        private final Effect attack;
        private final Effect spirit;

        Hit(CombatSession.HeartContact contact, Effect attack, Effect spirit) {
            this.contact = contact;
            this.attack = attack;
            this.spirit = spirit;
        }

        public CombatSession.HeartContact contact() { return contact; }
        public double multiplier() { return attack == null ? 1 : attack.multiplier(); }

        public void accepted() {
            boolean resolveAttack = attack != null && contact.ready(Slot.ATTACK);
            boolean resolveSpirit = spirit != null && contact.ready(Slot.SPIRIT);
            if (!resolveAttack && !resolveSpirit) return;
            contact.begin();
            try {
                boolean attackApplied = resolveAttack && attack.settle().test(false);
                if (attackApplied) contact.use(Slot.ATTACK);
                if (resolveSpirit) {
                    boolean paidOnThisHit = attackApplied && attack.charge() == spirit.charge();
                    if (spirit.settle().test(paidOnThisHit)) contact.use(Slot.SPIRIT);
                }
            } finally {
                contact.end();
            }
        }
    }

    private HeartCombatLinks() { }

    public static Hit prepare(LivingEntity actor, Entity target, String family, DamageType element, CombatSession session) {
        var contact = session.heartContact();
        if (contact == null || !HeartTechniques.supports(actor) || !CombatCaster.active(actor)
                || !(CombatTargets.owner(target) instanceof LivingEntity enemy)) return null;

        var rules = TechniqueTraits.heartLinks();
        Effect attack = contact.ready(Slot.ATTACK) ? prepareAttack(actor, target, enemy, family, element, session, rules, contact)
                : null;
        Effect spirit = contact.ready(Slot.SPIRIT) ? prepareSpirit(actor, target, family, element, session, rules, contact, attack)
                : null;
        return attack == null && spirit == null ? null : new Hit(contact, attack, spirit);
    }

    private static Effect prepareAttack(LivingEntity actor, Entity target, LivingEntity enemy, String family, DamageType element,
                                        CombatSession session, HeartLinkRules rules, CombatSession.HeartContact contact) {
        ResourceLocation heart = HeartTechniques.equippedId(actor, Slot.ATTACK);
        var attack = heart == null ? null : rules.attack().get(heart);
        if (attack == null || !attack.route().matches(family, element) || !triggered(attack.trigger(), actor, target, session)
                || attack.requires() != null && !targetHas(attack.requires(), actor, enemy)
                || conflicts(contact.technique(), attack.state(), attack.use() == HeartLinkRules.AttackUse.GRANT, false)) return null;
        var state = attack.state();
        if (attack.use() == HeartLinkRules.AttackUse.GRANT) {
            if (!canGrant(actor, enemy, state)) return null;
            return prepared(actor, heart, Slot.ATTACK, ATTACK, attack.cooldown(), 1, null,
                    ignored -> grant(actor, enemy, attack, rules));
        }
        if (state.casterState() ? !hasCharge(actor, state) : !targetHas(state, actor, enemy)) return null;
        double bonus = HeartPassives.stackBonus(actor, state, attack.percent() / 100.0);
        return prepared(actor, heart, Slot.ATTACK, ATTACK, attack.cooldown(), 1 + bonus,
                state.casterState() ? state : null,
                ignored -> state.casterState() ? takeCharge(actor, state) : takeTargetState(actor, target, enemy, state));
    }

    private static Effect prepareSpirit(LivingEntity actor, Entity target, String family, DamageType element,
                                        CombatSession session, HeartLinkRules rules, CombatSession.HeartContact contact, Effect attack) {
        if (!actor.isShiftKeyDown() || !session.paidArt() || CombatCaster.power(actor) >= CombatCaster.capacity(actor)) return null;
        ResourceLocation spiritHeart = HeartTechniques.equippedId(actor, Slot.SPIRIT);
        var spirit = spiritHeart == null ? null : rules.spirit().get(spiritHeart);
        if (spirit == null || !spirit.route().matches(family, element)
                || spirit.route() == HeartLinkRules.Route.SPEAR && !closeSpear(actor, target)
                || conflicts(contact.technique(), spirit.state(), false, false)
                || !hasCharge(actor, spirit.state())) return null;
        // 自己补充该状态的招式只能用本击已经支付的资源回灵。
        boolean replenishesCharge = conflicts(contact.technique(), spirit.state(), false, true);
        if (replenishesCharge && (attack == null || attack.charge() != spirit.state())) return null;
        return prepared(actor, spiritHeart, Slot.SPIRIT, SPIRIT, rules.spiritCooldown(), 1, spirit.state(), paidOnThisHit -> {
            if (CombatCaster.power(actor) >= CombatCaster.capacity(actor)
                    || !paidOnThisHit && (replenishesCharge || !takeCharge(actor, spirit.state()))) return false;
            return CombatCaster.recover(actor, spirit.qi());
        });
    }

    private static Effect prepared(LivingEntity actor, ResourceLocation heart, Slot slot, ResourceLocation channel,
                                   int cooldown, double multiplier, ContactState charge, Predicate<Boolean> effect) {
        var world = CombatCaster.level(actor).getServer().overworld();
        var cooldowns = FateAbilityCooldowns.get(world);
        UUID owner = HeartTechniques.cooldownOwner(actor);
        long now = world.getGameTime();
        if (cooldowns.remaining(owner, channel, now) > 0) return null;
        var level = actor.level();
        var hand = actor.getMainHandItem();
        return new Effect(multiplier, charge, paidOnThisHit -> {
            if (actor.level() != level || actor.getMainHandItem() != hand || !CombatCaster.active(actor)
                    || !heart.equals(HeartTechniques.equippedId(actor, slot))
                    || cooldowns.remaining(owner, channel, now) > 0 || !effect.test(paidOnThisHit)) return false;
            cooldowns.start(owner, channel, now, cooldown);
            return true;
        });
    }

    public static boolean defend(LivingEntity actor, DamageSource damage, ResourceLocation heart,
                                 ResourceLocation shieldSource, FateAbilityCooldowns cooldowns, UUID owner, long now) {
        var rules = TechniqueTraits.heartLinks();
        var defense = rules.defense().get(heart);
        if (defense == null || cooldowns.remaining(owner, DEFENSE, now) > 0) return false;
        var route = defense.route();
        if (route.element() == null && !route.matches(weaponFamily(actor), null)) return false;
        if (defense.use() == HeartLinkRules.DefenseUse.RETALIATE) {
            if (!(damage.getEntity() instanceof LivingEntity enemy) || damage.getDirectEntity() != enemy
                    || targetHas(defense.state(), actor, enemy)
                    || !CombatTraitStates.grant(actor, enemy, CombatTraitStates.Kind.FIST_UNBALANCE)) return false;
        } else {
            if (!actor.isShiftKeyDown() || !guardPayment(actor, heart, shieldSource, defense)) return false;
        }
        cooldowns.start(owner, DEFENSE, now, rules.defenseCooldown());
        return true;
    }

    private static boolean guardPayment(LivingEntity actor, ResourceLocation heart, ResourceLocation shieldSource,
                                        HeartLinkRules.Defense defense) {
        boolean ward = defense.state() == ContactState.WARD;
        var element = defense.route().element();
        double remaining = ward ? ElementalEffects.wardRemaining(actor, element) : hasCharge(actor, defense.state()) ? 1 : 0;
        if (remaining <= 0) return false;
        float amount = (float) (actor.getMaxHealth() * remaining * defense.healthPercent() / 100);
        BooleanSupplier pay = () -> ward ? ElementalEffects.consumeWard(actor, element) : takeCharge(actor, defense.state());
        if (defense.use() == HeartLinkRules.DefenseUse.SHIELD)
            return ShieldRuntime.grantPaid(actor, shieldSource, amount, TechniqueTraits.integer("short_shield_ticks"), pay);
        return HealingRuntime.healCapped(actor, amount, heart) > 0 && pay.getAsBoolean();
    }

    private static boolean triggered(HeartLinkRules.Trigger trigger, LivingEntity actor, Entity target, CombatSession session) {
        return switch (trigger) {
            case PAID -> session.paidArt();
            case BASIC -> !session.isExternal() && actor.onGround();
            case BASIC_TIP -> !session.isExternal() && CombatDisciplines.spearTip(actor, target);
            case SNEAK_PAID -> session.paidArt() && actor.isShiftKeyDown();
            case SNEAK_CLOSE -> !session.isExternal() && actor.isShiftKeyDown() && closeSpear(actor, target);
        };
    }

    private static boolean closeSpear(LivingEntity actor, Entity target) {
        double minimum = DisciplineRules.get().spearMin();
        return target.getBoundingBox().distanceToSqr(actor.position()) < minimum * minimum;
    }

    private static boolean conflicts(ResourceLocation art, ContactState state, boolean grant, boolean spendForRecovery) {
        if (art == null) return false;
        for (var trait : TechniqueTraits.of(art)) {
            if (!trait.id().equals(state.tag())) continue;
            switch (trait.role()) {
                case "获得", "施加", "延续" -> { if (grant || spendForRecovery) return true; }
                case "消耗", "转化" -> { if (!grant) return true; }
                case "转移" -> { return true; }
                default -> { }
            }
        }
        return false;
    }

    private static boolean canGrant(LivingEntity actor, LivingEntity target, ContactState state) {
        if (!state.casterState()) {
            return target.isAlive() && !FateDefenses.immune(target) && !targetHas(state, actor, target) && switch (state) {
                case ARMOR_BREAK -> target.getAttributeValue(Attributes.ARMOR) > 0;
                case EARTH_UNBALANCE -> target.onGround();
                default -> true;
            };
        }
        if (state == ContactState.SWORD_INTENT)
            return CombatDisciplines.stacks(actor, "sword") < DisciplineRules.get().sword().maximum();
        if (state == ContactState.SPEAR_STANCE)
            return !CombatTraitStates.active(actor, actor, CombatTraitStates.Kind.SPEAR_STANCE);
        var element = TechniqueContactTraits.element(state);
        return element != null && ElementalEffects.charges(actor, element)
                < ElementalRules.integer("charge_maximum", 1, 6);
    }

    private static boolean grant(LivingEntity actor, LivingEntity enemy, HeartLinkRules.Attack rule, HeartLinkRules config) {
        var state = rule.state();
        if (!canGrant(actor, enemy, state)) return false;
        if (state == ContactState.SWORD_INTENT) {
            CombatDisciplines.gainFromArt(actor, "sword");
            return true;
        }
        if (state == ContactState.SPEAR_STANCE)
            return CombatTraitStates.grant(actor, actor, CombatTraitStates.Kind.SPEAR_STANCE);
        var element = TechniqueContactTraits.element(state);
        if (element != null) {
            ElementalEffects.gain(actor, element);
            return true;
        }
        if (!enemy.isAlive()) return false;
        return switch (state) {
            case ARMOR_BREAK -> SwordArmorBreak.apply(enemy, rule.percent() / 100.0, config.armorTicks());
            case CHILL -> ElementalEffects.waterChill(actor, enemy, TechniqueTraits.integer("contact_chill_ticks"),
                    TechniqueTraits.number("contact_chill_fraction"));
            case EARTH_UNBALANCE, FIST_UNBALANCE -> CombatTraitStates.grant(actor, enemy, TechniqueContactTraits.mark(state));
            default -> false;
        };
    }

    private static boolean targetHas(ContactState state, LivingEntity actor, LivingEntity enemy) {
        var kind = TechniqueContactTraits.mark(state);
        if (kind != null) return (state != ContactState.EARTH_UNBALANCE || enemy.onGround())
                && CombatTraitStates.active(actor, enemy, kind);
        return switch (state) {
            case ARMOR_BREAK -> SwordArmorBreak.active(enemy);
            case CHILL -> ElementalEffects.hasWaterChill(actor, enemy);
            case EROSION -> ElementalEffects.harvestDamage(actor, enemy, DamageType.WOOD) > 0;
            case SCORCH -> ElementalEffects.harvestDamage(actor, enemy, DamageType.FIRE) > 0;
            default -> false;
        };
    }

    private static boolean takeTargetState(LivingEntity actor, Entity target, LivingEntity enemy, ContactState state) {
        if (!enemy.isAlive()) return true;
        if (state == ContactState.CHILL) return ElementalEffects.consumeWaterChill(actor, enemy);
        var kind = TechniqueContactTraits.mark(state);
        return kind != null && CombatTraitStates.consume(actor, target, kind);
    }

    private static boolean hasCharge(LivingEntity actor, ContactState state) {
        String weapon = TechniqueContactTraits.weapon(state);
        if (weapon != null) return CombatDisciplines.stacks(actor, weapon) > 0;
        var element = TechniqueContactTraits.element(state);
        return element != null && ElementalEffects.charges(actor, element) > 0;
    }

    private static boolean takeCharge(LivingEntity actor, ContactState state) {
        String weapon = TechniqueContactTraits.weapon(state);
        if (weapon != null) return CombatDisciplines.consumeOne(actor, weapon);
        var element = TechniqueContactTraits.element(state);
        return element != null && ElementalEffects.consumeOne(actor, element);
    }

    private static String weaponFamily(LivingEntity actor) {
        return com.example.myvillage.combat.definition.CombatStyles.bundled().weapon(actor.getMainHandItem())
                .flatMap(com.example.myvillage.combat.definition.WeaponDefinition::family).orElse("");
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("yuanxian", path); }
}
