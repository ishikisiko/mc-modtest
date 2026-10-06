package com.example.myvillage.cultivation.technique;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.Rules;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The running core technique's meditation gain factor, in integer basis points (10000 = ×1.0) so
 * settlement stays deterministic. Factor = grade cultivation multiplier + element match bonus (the
 * bonus only when the spiritual root has at least {@code roots.element_threshold_bp} in any of the
 * technique's elements), the same additive formula as the ledger's
 * {@code sim.engine.Cultivation.techniqueFactor}. Grade 0 (凡阶, Basic Breathing) is exactly ×1.0 with no element bonus. The numbers come
 * only from {@code world_sim/rules.json}; with no running technique, an unregistered one, or no
 * world-sim data the factor is ×1.0.
 */
public final class CoreTechniqueFactor {
    public static final int UNIT_BASIS_POINTS = 10_000;

    private CoreTechniqueFactor() {
    }

    /** The progress factor for {@code profile}'s running core technique. */
    public static int progressBasisPoints(
            CultivationProfile profile,
            Function<ResourceLocation, TechniqueDefinition> lookup,
            Optional<Rules> rules) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(rules, "rules");
        if (profile.activeCoreTechnique().isEmpty() || rules.isEmpty()) {
            return UNIT_BASIS_POINTS;
        }
        TechniqueDefinition technique = lookup.apply(profile.activeCoreTechnique().get());
        if (technique == null || !technique.isCore()) {
            return UNIT_BASIS_POINTS;
        }
        return progressBasisPoints(
                technique, profile.spiritualRoot(), rules.get().techniques(), rules.get().roots());
    }

    /** The progress factor of {@code technique} for a cultivator with {@code root}. */
    public static int progressBasisPoints(
            TechniqueDefinition technique,
            Optional<SpiritualRoot> root,
            Rules.Techniques techniques,
            Rules.Roots roots) {
        Objects.requireNonNull(technique, "technique");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(techniques, "techniques");
        Objects.requireNonNull(roots, "roots");
        if (technique.grade() <= 0) {
            return UNIT_BASIS_POINTS;
        }
        int gradeIndex = technique.grade() - 1;
        if (gradeIndex >= ContentTables.GRADE_ORDER.size()) {
            return UNIT_BASIS_POINTS;
        }
        Rules.GradeFactors grade = techniques.grades().get(ContentTables.GRADE_ORDER.get(gradeIndex));
        if (grade == null) {
            return UNIT_BASIS_POINTS;
        }
        long gradeBasisPoints = basisPoints(grade.cultivation());
        long bonusBasisPoints = elementMatch(technique, root, roots)
                ? basisPoints(techniques.elementMatchBonus())
                : 0;
        long factor = gradeBasisPoints + bonusBasisPoints;
        return (int) Math.min(Integer.MAX_VALUE, factor);
    }

    /** True when the root's affinity in any of the technique's elements reaches the threshold. */
    public static boolean elementMatch(
            TechniqueDefinition technique, Optional<SpiritualRoot> root, Rules.Roots roots) {
        if (root.isEmpty()) {
            return false;
        }
        for (ResourceLocation element : technique.elements()) {
            Integer affinity = root.get().affinitiesBasisPoints().get(element);
            if (affinity != null && affinity >= roots.elementThresholdBp()) {
                return true;
            }
        }
        return false;
    }

    /** {@code amount × factor / 10000}, rounded down; ×1.0 returns {@code amount} unchanged. */
    public static long apply(long amount, int factorBasisPoints) {
        if (factorBasisPoints < 0) {
            throw new IllegalArgumentException("Factor must be non-negative, got " + factorBasisPoints);
        }
        if (factorBasisPoints == UNIT_BASIS_POINTS || amount <= 0) {
            return amount;
        }
        return Math.multiplyExact(amount, (long) factorBasisPoints) / UNIT_BASIS_POINTS;
    }

    private static long basisPoints(double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("Factor must be finite and non-negative, got " + value);
        }
        return Math.round(value * UNIT_BASIS_POINTS);
    }
}
