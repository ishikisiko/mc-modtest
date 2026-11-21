package com.example.myvillage.cultivation.technique;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.elemental.ElementalRules;
import com.example.myvillage.combat.foundation.ShieldRuntime;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatCaster;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.HeartMethod;
import com.example.myvillage.cultivation.data.HeartMethod.Slot;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueEffects;
import com.example.myvillage.cultivation.fate.ability.FateAbilityCooldowns;
import com.example.myvillage.cultivation.growth.TechniqueGrowth;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRules;
import com.example.myvillage.entity.npc.NpcCombatRuntime;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.runtime.StatsRuntime;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public final class HeartTechniques {
    private static final ResourceLocation DEFENSE_SHIELD =
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "defense_heart_shield");

    private HeartTechniques() { }

    public static Optional<HeartMethod> method(TechniqueDefinition definition) {
        return definition == null ? Optional.empty() : definition.effects().flatMap(TechniqueEffects::core).flatMap(TechniqueEffects.Core::heart);
    }

    public static Slot slot(TechniqueDefinition definition) {
        return method(definition).map(HeartMethod::slot).orElse(Slot.SPIRIT);
    }

    public static boolean supports(LivingEntity entity) {
        return entity instanceof ServerPlayer || entity instanceof NpcEntity;
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

    public static int spiritCapacity(RegistryAccess access, CultivationProfile profile) {
        return spirit(access, profile).map(HeartMethod.Spirit::capacity).orElse(0);
    }

    public static Recovery recovery(RegistryAccess access, CultivationProfile profile, Optional<Rules> rules) {
        int unit = CoreTechniqueFactor.UNIT_BASIS_POINTS;
        if (usesFixedSpirit(access, profile)) {
            int quarters = spirit(access, profile)
                    .map(heart -> heart.recoveryQuarters() + growth(profile, TechniqueGrowthRules.Effect.RECOVERY_QUARTERS))
                    .orElse(0);
            return new Recovery(unit, quarters);
        }
        int base = access.registry(ModCultivationRegistries.TECHNIQUES)
                .map(registry -> CoreTechniqueFactor.progressBasisPoints(profile, registry::get, rules)).orElse(unit);
        int growth = profile.activeCoreTechnique()
                .filter(id -> TechniqueGrowth.definition(TechniqueGrowthRules.bundled(), id,
                        key -> ModCultivationRegistries.technique(access, key).orElse(null)).isPresent())
                .map(id -> TechniqueGrowth.multiplier(TechniqueGrowthRules.bundled(), id, profile,
                        TechniqueGrowthRules.Effect.RECOVERY)).orElse(unit);
        return new Recovery((int) Math.min(Integer.MAX_VALUE, (long) base * growth / unit), 0);
    }

    public record Recovery(int basisPoints, int quarters) { }

    public static BaseCombat baseCombat(RegistryAccess access, CultivationProfile profile) {
        var attack = equipped(access, profile, Slot.ATTACK).flatMap(HeartTechniques::method)
                .map(HeartMethod.Attack.class::cast).orElse(null);
        var defense = equipped(access, profile, Slot.DEFENSE).flatMap(HeartTechniques::method)
                .map(HeartMethod.Defense.class::cast).orElse(null);
        return new BaseCombat(attack == null ? 0 : attack.attack(), attack == null ? 0 : attack.flatPenetration(),
                defense == null ? 0 : defense.defense(), defense == null ? 0 : defense.maxHealth(),
                defense == null ? HeartMethod.DefenseAttributes.NONE : defense.attributes());
    }

    public record BaseCombat(double attack, double flatPenetration, double defense, double maxHealth,
            HeartMethod.DefenseAttributes defenseAttributes) { }

    /** Receives the actual health change from the shared damage container after the hit settles. */
    public static void afterHealthLoss(LivingEntity target, DamageSource source, double before, double after) {
        if (!(after > 0 && after < before) || !supports(target)
                || target instanceof NpcEntity npc && !NpcCombatRuntime.managed(npc)) {
            return;
        }
        var definition = equipped(target.registryAccess(), CombatCaster.profile(target), Slot.DEFENSE).orElse(null);
        var shield = method(definition).map(HeartMethod.Defense.class::cast).flatMap(HeartMethod.Defense::shield).orElse(null);
        if (shield == null || !shield.crossesThreshold(before, after, target.getMaxHealth())
                || ShieldRuntime.bypassesShields(source)
                || !(source.getEntity() instanceof LivingEntity attacker) || !CombatActors.mayHarm(attacker, target)) {
            return;
        }

        var overworld = target.getServer().overworld();
        var cooldowns = FateAbilityCooldowns.get(overworld);
        long now = overworld.getGameTime();
        // 命簿人物离开近场时会更换实体，冷却继续使用同一人物身份。
        UUID owner = target instanceof NpcEntity npc && npc.isLedgerAvatar()
                ? UUID.nameUUIDFromBytes(("yuanxian:heart/" + npc.ledgerPersonId()).getBytes(StandardCharsets.UTF_8))
                : target.getUUID();
        if (cooldowns.remaining(owner, DEFENSE_SHIELD, now) == 0
                && ShieldRuntime.grant(target, DEFENSE_SHIELD, shield.amount(), shield.durationTicks())) {
            cooldowns.start(owner, DEFENSE_SHIELD, now, shield.cooldownTicks());
            CombatCaster.message(target, Component.translatable(definition.translationKey()).append("的护盾已生效"));
        }
    }

    public static void clearDefenseShield(LivingEntity entity) {
        ShieldRuntime.removeSource(entity, DEFENSE_SHIELD);
    }

    public static double attackExtra(LivingEntity actor, String weapon, DamageType spell, boolean art, boolean paid) {
        if (!supports(actor)) return 0;
        return equipped(actor.registryAccess(), CombatCaster.profile(actor), Slot.ATTACK)
                .map(definition -> attackExtra(definition, (HeartMethod.Attack) method(definition).orElseThrow(),
                        weapon, spell, art, paid)).orElse(0.0);
    }

    static double attackExtra(TechniqueDefinition definition, HeartMethod.Attack method, String weapon,
            DamageType spell, boolean art, boolean paid) {
        var extra = method.extra().orElse(null);
        if (extra == null || extra.artOnly() && !art || extra.paidOnly() && (!art || !paid)) return 0;
        boolean matches = switch (method.direction()) {
            case GENERAL -> true;
            case SPELL -> matchesElement(definition, spell);
            default -> spell == null && method.direction().matchesWeapon(weapon);
        };
        return matches ? extra.amount() : 0;
    }

    public static double defenseExtra(LivingEntity entity, DamageSource source) {
        if (!supports(entity)) return 0;
        return equipped(entity.registryAccess(), CombatCaster.profile(entity), Slot.DEFENSE)
                .map(definition -> {
                    var method = (HeartMethod.Defense) method(definition).orElseThrow();
                    if (method.extra().isEmpty()) return 0.0;
                    String weapon = method.direction().weapon() ? CombatStyles.bundled().weapon(entity.getMainHandItem())
                            .flatMap(w -> w.family()).orElse("") : "";
                    boolean melee = source.getEntity() == source.getDirectEntity()
                            && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.STING));
                    boolean ranged = source.is(DamageTypeTags.IS_PROJECTILE) || ElementalRules.type(source).isPresent();
                    return defenseExtra(definition, method, weapon, StatsRuntime.mappedType(source).orElse(null), melee, ranged);
                }).orElse(0.0);
    }

    static double defenseExtra(TechniqueDefinition definition, HeartMethod.Defense method, String weapon,
            DamageType incoming, boolean melee, boolean ranged) {
        var extra = method.extra().orElse(null);
        if (extra == null) return 0;
        boolean directionMatches = switch (method.direction()) {
            case GENERAL -> true;
            case SPELL -> matchesElement(definition, incoming);
            default -> method.direction().matchesWeapon(weapon);
        };
        boolean guardMatches = switch (extra.guard()) {
            case NONE -> true;
            case MELEE -> melee;
            case RANGED -> ranged;
        };
        return directionMatches && guardMatches ? extra.amount() : 0;
    }

    private static boolean matchesElement(TechniqueDefinition definition, DamageType damage) {
        return damage != null && damage != DamageType.PHYSICAL && (definition.elements().isEmpty()
                || definition.elements().contains(ResourceLocation.fromNamespaceAndPath("yuanxian", damage.id())));
    }
}
