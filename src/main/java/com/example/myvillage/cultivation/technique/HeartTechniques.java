package com.example.myvillage.cultivation.technique;

import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.elemental.ElementalRules;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.HeartMethod;
import com.example.myvillage.cultivation.data.HeartMethod.Slot;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueEffects;
import com.example.myvillage.cultivation.growth.TechniqueGrowth;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRules;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.runtime.StatsRuntime;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

public final class HeartTechniques {
    private HeartTechniques() { }

    public static Optional<HeartMethod> method(TechniqueDefinition definition) {
        return definition == null ? Optional.empty() : definition.effects().flatMap(TechniqueEffects::core).flatMap(TechniqueEffects.Core::heart);
    }

    public static Slot slot(TechniqueDefinition definition) {
        return method(definition).map(HeartMethod::slot).orElse(Slot.SPIRIT);
    }

    private static Optional<TechniqueDefinition> equipped(RegistryAccess access, CultivationProfile profile, Slot slot) {
        return profile.equippedHeart(slot).flatMap(id -> ModCultivationRegistries.technique(access, id))
                .filter(definition -> method(definition).map(HeartMethod::slot).orElse(null) == slot);
    }

    public static Optional<HeartMethod.Spirit> spirit(RegistryAccess access, CultivationProfile profile) {
        return equipped(access, profile, Slot.SPIRIT).flatMap(HeartTechniques::method).map(HeartMethod.Spirit.class::cast);
    }

    /** A combat heart left in the primary slot by an earlier profile gives no spirit bonus. */
    public static boolean usesFixedSpirit(RegistryAccess access, CultivationProfile profile) {
        return profile.activeCoreTechnique().flatMap(id -> ModCultivationRegistries.technique(access, id))
                .flatMap(HeartTechniques::method).isPresent();
    }

    public static int growth(CultivationProfile profile, TechniqueGrowthRules.Effect effect) {
        return profile.activeCoreTechnique().map(id -> TechniqueGrowth.bonus(
                TechniqueGrowthRules.bundled(), id, profile, effect)).orElse(0);
    }

    public static double attack(LivingEntity actor, String weapon, DamageType spell, boolean art, boolean paid) {
        if (!(actor instanceof ServerPlayer player)) return 0;
        return equipped(player.registryAccess(), CultivationService.getProfile(player), Slot.ATTACK)
                .map(definition -> attackBonus(definition, (HeartMethod.Attack) method(definition).orElseThrow(),
                        weapon, spell, art, paid)).orElse(0.0);
    }

    public static double attackBonus(TechniqueDefinition definition, HeartMethod.Attack method, String weapon,
            DamageType spell, boolean art, boolean paid) {
        if (method.paidOnly() && (!art || !paid)) return 0;
        boolean matches = switch (method.direction()) {
            case GENERAL -> spell != null || !weapon.isEmpty();
            case SPELL -> matchesElement(definition, spell);
            default -> spell == null && method.direction().matchesWeapon(weapon);
        };
        return matches ? (art ? method.art() : method.basic()) : 0;
    }

    public static double defense(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player)) return 0;
        return equipped(player.registryAccess(), CultivationService.getProfile(player), Slot.DEFENSE)
                .map(definition -> {
                    var method = (HeartMethod.Defense) method(definition).orElseThrow();
                    String weapon = method.direction().weapon() ? CombatStyles.bundled().weapon(player.getMainHandItem())
                            .flatMap(w -> w.family()).orElse("") : "";
                    boolean melee = source.getEntity() == source.getDirectEntity()
                            && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.STING));
                    boolean ranged = source.is(DamageTypeTags.IS_PROJECTILE) || ElementalRules.type(source).isPresent();
                    return defenseBonus(definition, method, weapon, StatsRuntime.mappedType(source).orElse(null), melee, ranged);
                }).orElse(0.0);
    }

    public static double defenseBonus(TechniqueDefinition definition, HeartMethod.Defense method, String weapon,
            DamageType incoming, boolean melee, boolean ranged) {
        if (method.direction() == HeartMethod.Direction.GENERAL) return method.base();
        if (method.direction() == HeartMethod.Direction.SPELL) {
            return matchesElement(definition, incoming) ? method.base() : 0;
        }
        if (!method.direction().matchesWeapon(weapon)) return 0;
        return switch (method.guard()) {
            case NONE -> method.base();
            case MELEE -> melee ? method.reaction() : method.base();
            case RANGED -> ranged ? method.reaction() : method.base();
        };
    }

    private static boolean matchesElement(TechniqueDefinition definition, DamageType damage) {
        return damage != null && damage != DamageType.PHYSICAL && (definition.elements().isEmpty()
                || definition.elements().contains(ResourceLocation.fromNamespaceAndPath("yuanxian", damage.id())));
    }
}
