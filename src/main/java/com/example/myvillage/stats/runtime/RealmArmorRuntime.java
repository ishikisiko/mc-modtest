package com.example.myvillage.stats.runtime;

import com.example.myvillage.mixin.LivingDamageContainerAccess;
import com.example.myvillage.stats.ArmorPressure;
import com.example.myvillage.stats.StatsRules;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

/** Reads the defender assignment, never changes incoming damage or equipment. */
public final class RealmArmorRuntime {
    private static final String REALM_TAG = "myvillage:stats_realm";
    // DamageContainer uses object identity. Values contain no entity/container references.
    private static final Map<DamageContainer, ArmorTrace> ARMOR = new WeakHashMap<>();

    private RealmArmorRuntime() {}

    public record Scale(double factor, String status) {}

    public record ArmorTrace(double damageAtArmor, double defenderScale, double pressureDamage,
                             double normalizedEffectiveArmor, String assignmentStatus, Double armorOutput) {
        public ArmorTrace completed(double output) {
            return new ArmorTrace(damageAtArmor, defenderScale, pressureDamage,
                    normalizedEffectiveArmor, assignmentStatus, output);
        }
    }

    /** Exact NBT types matter: getInt alone would silently coerce other numeric types. */
    public static Scale resolveScale(CompoundTag persistent, StatsRules rules) {
        if (persistent == null || !persistent.contains(REALM_TAG)) {
            return new Scale(1, "unassigned");
        }
        if (!persistent.contains(REALM_TAG, Tag.TAG_COMPOUND)) {
            return new Scale(1, "invalid_assignment");
        }
        CompoundTag assignment = persistent.getCompound(REALM_TAG);
        if (!assignment.contains("realm", Tag.TAG_STRING) || !assignment.contains("stage", Tag.TAG_INT)) {
            return new Scale(1, "invalid_assignment");
        }
        try {
            double factor = rules.growth(rules.realmKey(assignment.getString("realm"), assignment.getInt("stage")));
            return Double.isFinite(factor) && factor >= 1
                    ? new Scale(factor, "valid") : new Scale(1, "invalid_assignment");
        } catch (IllegalArgumentException exception) {
            return new Scale(1, "invalid_assignment");
        }
    }

    public static Scale defenderScale(LivingEntity defender) {
        return resolveScale(defender.getPersistentData(), StatsRules.bundled());
    }

    public static float normalizeUnclamped(LivingEntity defender, DamageSource source, float damageAtArmor,
                                          float armor, float original, float lowerBound, float upperBound) {
        if (defender.level().isClientSide()) {
            return original;
        }
        Scale scale = defenderScale(defender);
        float normalized = ArmorPressure.normalizeUnclamped(armor, original, scale.factor());
        if (source.getEntity() instanceof LivingEntity attacker && StatsRuntime.mappedType(source).orElse(null)==com.example.myvillage.stats.DamageType.PHYSICAL) normalized *= (float)(1-com.example.myvillage.wilderness.ExpeditionCombat.armorPierce(attacker));
        DamageContainer container = currentContainer(defender);
        if (container != null && container.getSource() == source) {
            record(container, new ArmorTrace(damageAtArmor, scale.factor(), damageAtArmor / scale.factor(),
                    Mth.clamp(normalized, lowerBound, upperBound), scale.status(), null));
        }
        return normalized;
    }

    public static DamageContainer currentContainer(LivingEntity defender) {
        if (!(defender instanceof LivingDamageContainerAccess access)) {
            return null; // A plain unit-test JVM has no FML transformation.
        }
        var stack = access.myvillage$damageContainers();
        return stack.empty() ? null : stack.peek();
    }

    public static int containerDepth(LivingEntity defender) {
        return defender instanceof LivingDamageContainerAccess access ? access.myvillage$damageContainers().size() : 0;
    }

    static void record(DamageContainer container, ArmorTrace trace) {
        ARMOR.put(container, trace);
    }

    static ArmorTrace take(DamageContainer container) {
        return container == null ? null : ARMOR.remove(container);
    }

    static void clear() {
        ARMOR.clear();
    }
}
