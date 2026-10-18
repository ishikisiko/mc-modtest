package com.example.myvillage.stats.runtime;

import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.combat.runtime.CombatTargets;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.stats.DamageMath;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.RealmStats;
import com.example.myvillage.stats.StatsRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-only numerical adapter. Vanilla still owns hurt, armor, effects and absorption. */
public final class StatsRuntime {
    public static final ResourceLocation REALM_HEALTH = id("stats.realm_health");
    public static final ResourceLocation REALM_ATTACK = id("stats.realm_attack");
    private static final String REALM_TAG = "myvillage:stats_realm";
    private static final Logger LOGGER = LoggerFactory.getLogger(StatsRuntime.class);
    // One last trace and one unconsumed calculation per entity; weak keys do not retain worlds.
    private static final Map<LivingEntity, DamageTrace> LAST_DAMAGE = new WeakHashMap<>();
    private static final Map<LivingEntity, Outgoing> OUTGOING = new WeakHashMap<>();
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
    private static final Map<DamageContainer, DamageTrace> INCOMING = new WeakHashMap<>();

    private StatsRuntime() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(StatsRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(StatsRuntime::onServerStopping);
        // Run resistance after other pre modifiers, then sample the actual health delta
        // before unrelated post-hit effects can heal or further hurt the entity.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, StatsRuntime::beforeDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, StatsRuntime::afterDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, StatsRuntime::onPlayerClone);
    }

    private static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            // Vanilla restoreFrom copies bases, then clamps the copied Health before this
            // event. Read the original body, after NeoForge has copied the profile attachment.
            float originalHealth = event.getOriginal().getHealth();
            float originalMaximum = event.getOriginal().getMaxHealth();
            refreshPlayer(player);
            player.setHealth(RealmHealthPolicy.respawned(
                    originalHealth, originalMaximum, player.getMaxHealth(), true));
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        StatsRules rules = StatsRules.bundled();
        var realms = event.getServer().registryAccess().registryOrThrow(ModCultivationRegistries.REALMS);
        for (var realmId : realms.keySet()) {
            for (var stage : realms.get(realmId).stages()) {
                rules.playerKey(realmId.toString(), stage.id().toString());
            }
        }
        LOGGER.info("Realm stats loaded: rules={}", rules.ruleHash());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        LAST_DAMAGE.clear();
        OUTGOING.clear();
        ACTIVE.remove();
        INCOMING.clear();
        RealmArmorRuntime.clear();
    }

    public static void refreshPlayer(ServerPlayer player) {
        com.example.myvillage.cultivation.fate.FateRuntime.refresh(player);
        var profile = CultivationService.getProfile(player);
        try {
            RealmKey key = StatsRules.bundled().playerKey(
                    profile.realmId().toString(), profile.stageId().toString());
            applyRealm(player, key.realmId(), key.stage());
        } catch (IllegalArgumentException exception) {
            // A legacy datapack profile remains intact. Only our own contributions disappear.
            clearRealm(player);
            LOGGER.warn("Stats unavailable for {} realm={} stage={}: {}",
                    player.getUUID(), profile.realmId(), profile.stageId(), exception.getMessage());
        }
    }

    /** Saved modifiers restore max health before vanilla reads current Health from entity NBT. */
    public static void applyRealm(LivingEntity entity, String canonicalRealm, int zeroBasedStage) {
        applyRealm(entity, canonicalRealm, zeroBasedStage, false);
    }

    /** Native base attributes can exceed the authored beast envelope; effective caps still apply. */
    public static void applyVanillaRealm(LivingEntity entity, String canonicalRealm, int zeroBasedStage) {
        applyRealm(entity, canonicalRealm, zeroBasedStage, true);
    }

    private static void applyRealm(LivingEntity entity, String canonicalRealm, int zeroBasedStage, boolean vanilla) {
        requireServer(entity);
        StatsRules rules = StatsRules.bundled();
        RealmKey key = rules.realmKey(canonicalRealm, zeroBasedStage);
        AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (health == null) {
            throw new IllegalArgumentException("Entity has no max health attribute");
        }
        double baseHealth = health.getBaseValue(), baseAttack = attack == null ? 0 : attack.getBaseValue();
        RealmStats.Derived derived = vanilla ? RealmStats.vanillaDerived(rules, key, baseHealth, baseAttack)
                : RealmStats.derived(rules, key, baseHealth, baseAttack);
        float oldMax = entity.getMaxHealth();
        float oldHealth = entity.getHealth();
        RealmAttributeModifiers.replace(health, REALM_HEALTH, vanilla ? derived.health() / baseHealth : derived.factor());
        RealmAttributeModifiers.replace(attack, REALM_ATTACK,
                vanilla && baseAttack > 0 ? derived.attack() / baseAttack : derived.factor());
        float current = RealmHealthPolicy.refreshed(oldHealth, oldMax, entity.getMaxHealth());
        if (current != oldHealth) {
            entity.setHealth(current);
        }
        CompoundTag assignment = new CompoundTag();
        assignment.putString("realm", key.realmId());
        assignment.putInt("stage", key.stage());
        entity.getPersistentData().put(REALM_TAG, assignment);
    }

    public static void clearRealm(LivingEntity entity) {
        requireServer(entity);
        float oldMaximum = entity.getMaxHealth();
        float health = entity.getHealth();
        remove(entity.getAttribute(Attributes.MAX_HEALTH), REALM_HEALTH);
        remove(entity.getAttribute(Attributes.ATTACK_DAMAGE), REALM_ATTACK);
        entity.getPersistentData().remove(REALM_TAG);
        float next = RealmHealthPolicy.refreshed(health, oldMaximum, entity.getMaxHealth());
        if (next != health) {
            entity.setHealth(next);
        }
    }

    private static void remove(AttributeInstance attribute, ResourceLocation id) {
        if (attribute != null) {
            attribute.removeModifier(id);
        }
    }

    public static float outgoing(LivingEntity attacker, double base, double move) {
        requireServer(attacker);
        DamageMath.Trace trace = DamageMath.calculate(base,
                value(attacker, ModStatsAttributes.DAMAGE_FLAT), move,
                List.of(value(attacker, ModStatsAttributes.DAMAGE_BONUS)),
                value(attacker, ModStatsAttributes.DAMAGE_MULTIPLIER), StatsRules.bundled());
        OUTGOING.put(attacker, new Outgoing(attacker.level().getGameTime(), trace));
        return (float) trace.requestedDamage();
    }

    /** Associates outgoing coefficients with precisely this hurt call, including nested hits. */
    public static boolean hurt(LivingEntity attacker, Entity target, DamageSource source, float requestedDamage) {
        requireServer(attacker);
        Outgoing outgoing = OUTGOING.remove(attacker);
        Scope previous = ACTIVE.get();
        DamageMath.Trace calculation = outgoing != null && outgoing.tick() == attacker.level().getGameTime()
                ? outgoing.calculation() : null;
        Entity owner = CombatTargets.owner(target);
        int depth = owner instanceof LivingEntity living ? RealmArmorRuntime.containerDepth(living) + 1 : 0;
        ACTIVE.set(new Scope(owner, source, calculation, depth));
        try {
            return target.hurt(source, requestedDamage);
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    private static void beforeDamage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        Optional<DamageType> type = mappedType(source);
        double postVanilla = event.getNewDamage();
        DamageMath.Mitigation mitigation = null;
        if (type.isPresent()) {
            double penetration = source.getEntity() instanceof LivingEntity attacker
                    ? selectedPenetration(type.get(), attribute -> value(attacker, attribute)) : 0;
            mitigation = DamageMath.mitigate(Math.max(0, postVanilla),
                    value(target, resistance(type.get())), penetration, StatsRules.bundled());
            event.setNewDamage((float) mitigation.damage());
        }
        Scope scope = ACTIVE.get();
        DamageMath.Trace calculation = scope != null && scope.target() == target && scope.source() == source
                && scope.containerDepth() == RealmArmorRuntime.containerDepth(target)
                ? scope.calculation() : null;
        RealmArmorRuntime.ArmorTrace armor = RealmArmorRuntime.take(event.getContainer());
        if (armor != null) {
            armor = armor.completed(armor.damageAtArmor() - event.getContainer().getReduction(DamageContainer.Reduction.ARMOR));
        }
        DamageTrace trace = new DamageTrace(target.level().getGameTime(),
                source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("unregistered"),
                type.map(DamageType::id).orElse("untouched"), calculation, event.getOriginalDamage(),
                postVanilla, event.getNewDamage(), mitigation, null, null, null,
                target.getHealth(), null, StatsRules.bundled().ruleHash(), armor);
        INCOMING.put(event.getContainer(), trace);
        LAST_DAMAGE.put(target, trace);
    }

    private static void afterDamage(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        DamageTrace trace = INCOMING.remove(RealmArmorRuntime.currentContainer(event.getEntity()));
        if (trace == null) {
            return;
        }
        float healthAfter = event.getEntity().getHealth();
        LAST_DAMAGE.put(event.getEntity(), new DamageTrace(trace.tick(), trace.sourceId(), trace.type(),
                trace.outgoing(), trace.requestedDamage(), trace.postVanilla(), trace.afterResistance(),
                trace.mitigation(), (double) event.getReduction(
                        net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION),
                (double) event.getNewDamage(), (double) Math.max(0, trace.healthBefore() - healthAfter),
                trace.healthBefore(), (double) healthAfter, trace.ruleHash(), trace.armor()));
    }

    /** Only explicitly mapped attack/fire sources participate; environmental/unknown damage does not. */
    public static Optional<DamageType> mappedType(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return Optional.empty();
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return Optional.of(DamageType.FIRE);
        }
        if (source.is(DamageTypeTags.IS_PLAYER_ATTACK) || source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.STING)) {
            return Optional.of(DamageType.PHYSICAL);
        }
        return Optional.empty();
    }

    public static Holder<Attribute> resistance(DamageType type) {
        return switch (type) {
            case PHYSICAL -> ModStatsAttributes.PHYSICAL_RESISTANCE;
            case METAL -> ModStatsAttributes.METAL_RESISTANCE;
            case WOOD -> ModStatsAttributes.WOOD_RESISTANCE;
            case WATER -> ModStatsAttributes.WATER_RESISTANCE;
            case FIRE -> ModStatsAttributes.FIRE_RESISTANCE;
            case EARTH -> ModStatsAttributes.EARTH_RESISTANCE;
        };
    }

    public static Holder<Attribute> penetration(DamageType type) {
        return switch (type) {
            case PHYSICAL -> ModStatsAttributes.PHYSICAL_RESISTANCE_PENETRATION;
            case METAL -> ModStatsAttributes.METAL_RESISTANCE_PENETRATION;
            case WOOD -> ModStatsAttributes.WOOD_RESISTANCE_PENETRATION;
            case WATER -> ModStatsAttributes.WATER_RESISTANCE_PENETRATION;
            case FIRE -> ModStatsAttributes.FIRE_RESISTANCE_PENETRATION;
            case EARTH -> ModStatsAttributes.EARTH_RESISTANCE_PENETRATION;
        };
    }

    static double selectedPenetration(DamageType type, ToDoubleFunction<Holder<Attribute>> reader) {
        return reader.applyAsDouble(penetration(type));
    }

    public static double value(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? attribute.value().getDefaultValue() : instance.getValue();
    }

    public static Optional<DamageTrace> lastTrace(LivingEntity entity) {
        return Optional.ofNullable(LAST_DAMAGE.get(entity));
    }

    public static Map<String, Object> info(LivingEntity entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityId", entity.getUUID().toString());
        result.put("entityType", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        result.put("health", entity.getHealth());
        result.put("maxHealth", entity.getMaxHealth());
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        result.put("attack", attack == null ? null : attack.getValue());
        CompoundTag assignment = entity.getPersistentData().getCompound(REALM_TAG);
        result.put("realm", assignment.isEmpty() ? "unassigned" : assignment.getString("realm"));
        result.put("stage", assignment.isEmpty() ? null : assignment.getInt("stage"));
        result.put("factor", assignment.isEmpty() ? 1 : StatsRules.bundled().growth(
                StatsRules.bundled().realmKey(assignment.getString("realm"), assignment.getInt("stage"))));
        Map<String, Double> attributes = new LinkedHashMap<>();
        for (var attribute : ModStatsAttributes.ALL) {
            attributes.put(attribute.getId().toString(), value(entity, attribute));
        }
        result.put("attributes", attributes);
        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("max_health", attributeBreakdown(entity, Attributes.MAX_HEALTH));
        sources.put("attack_damage", attributeBreakdown(entity, Attributes.ATTACK_DAMAGE));
        sources.put("armor", attributeBreakdown(entity, Attributes.ARMOR));
        sources.put("armor_toughness", attributeBreakdown(entity, Attributes.ARMOR_TOUGHNESS));
        sources.put("movement_speed", attributeBreakdown(entity, Attributes.MOVEMENT_SPEED));
        sources.put("knockback_resistance", attributeBreakdown(entity, Attributes.KNOCKBACK_RESISTANCE));
        for (var attribute : ModStatsAttributes.ALL) {
            sources.put(attribute.getId().toString(), attributeBreakdown(entity, attribute));
        }
        result.put("sources", sources);
        result.put("ruleHash", StatsRules.bundled().ruleHash());
        return result;
    }

    /** Read-only source identities make realm, equipment and temporary contributions distinguishable. */
    private static Map<String, Object> attributeBreakdown(LivingEntity entity, Holder<Attribute> type) {
        AttributeInstance attribute = entity.getAttribute(type);
        if (attribute == null) return Map.of("available", false);
        var modifiers = attribute.getModifiers().stream()
                .sorted(java.util.Comparator.comparing(modifier -> modifier.id().toString()))
                .limit(32)
                .map(modifier -> Map.<String, Object>of("id", modifier.id().toString(),
                        "amount", modifier.amount(), "operation", modifier.operation().name()))
                .toList();
        return Map.of("base", attribute.getBaseValue(), "effective", attribute.getValue(),
                "modifiers", modifiers, "modifierCount", attribute.getModifiers().size());
    }

    private static void requireServer(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            throw new IllegalStateException("Stats mutation and outgoing calculation are server-owned");
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }

    private record Outgoing(long tick, DamageMath.Trace calculation) {}
    private record Scope(Entity target, DamageSource source, DamageMath.Trace calculation, int containerDepth) {}

    public record DamageTrace(long tick, String sourceId, String type, DamageMath.Trace outgoing,
                              double requestedDamage, double postVanilla, double afterResistance,
                              DamageMath.Mitigation mitigation, Double absorptionReduction,
                              Double appliedToHealth, Double actualHealthLoss,
                              double healthBefore, Double healthAfter, String ruleHash,
                              RealmArmorRuntime.ArmorTrace armor) {}
}
