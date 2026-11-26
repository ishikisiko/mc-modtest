package com.example.myvillage.cultivation.technique;

import com.example.myvillage.combat.runtime.CombatCaster;
import com.example.myvillage.cultivation.data.HeartLinkRules.PassiveUse;
import com.example.myvillage.cultivation.data.HeartMethod.Slot;
import com.example.myvillage.cultivation.data.TechniqueTraits;
import com.example.myvillage.cultivation.data.TechniqueTraits.ContactState;
import com.example.myvillage.stats.DamageType;
import net.minecraft.world.entity.LivingEntity;

/** 读当前装备，给战斗公式提供系数。 */
public final class HeartPassives {
    private HeartPassives() { }

    public static int duration(LivingEntity actor, ContactState state, int baseTicks) {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(baseTicks * factor(actor, state, PassiveUse.DURATION)));
    }

    public static double stackBonus(LivingEntity actor, ContactState state, double baseBonus) {
        return baseBonus * factor(actor, state, PassiveUse.STACK_DAMAGE);
    }

    public static double shieldAbsorption(LivingEntity actor) {
        return factor(actor, ContactState.SHIELD, PassiveUse.ABSORPTION);
    }

    private static double factor(LivingEntity actor, ContactState state, PassiveUse use) {
        if (state == null || actor.level().isClientSide() || !HeartTechniques.supports(actor)) return 1;
        var profile = CombatCaster.profile(actor);
        var rules = TechniqueTraits.heartLinks().passive();
        int increase = 0;
        for (Slot slot : Slot.values()) {
            var id = profile.equippedHeart(slot).orElse(null);
            var passive = id == null ? null : rules.get(id);
            if (passive != null && passive.slot() == slot && passive.use() == use && passive.states().contains(state)
                    && id.equals(HeartTechniques.equippedId(actor, slot))) increase += passive.percent();
        }
        return 1 + increase / 100.0;
    }

    public static ContactState elementalCharge(DamageType element) {
        return switch (element) {
            case METAL -> ContactState.METAL_FORCE;
            case WOOD -> ContactState.WOOD_FORCE;
            case WATER -> ContactState.WATER_FORCE;
            case FIRE -> ContactState.FIRE_FORCE;
            case EARTH -> ContactState.EARTH_FORCE;
            default -> null;
        };
    }
}
