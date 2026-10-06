package com.example.myvillage.cultivation.study;

import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition.TechniqueStudy;
import com.example.myvillage.cultivation.technique.CoreTechniqueFactor;
import com.example.myvillage.sim.data.Rules;

import java.util.Objects;
import java.util.Optional;

/**
 * Where the study numbers come from: points, gates and gate cost from the technique's {@code study} block;
 * the element bonus from {@code world_sim/rules.json} ({@code techniques.element_match_bonus} when the root
 * reaches {@code roots.element_threshold_bp} in one of the technique's elements, as for the core technique
 * factor), 0 without world-sim data. The grade multiplier never applies to study.
 */
public final class StudyRules {
    private StudyRules() {
    }

    public static TechniqueStudy of(TechniqueDefinition definition) {
        return Objects.requireNonNull(definition, "definition").study();
    }

    public static int elementBonusBasisPoints(
            TechniqueDefinition definition, Optional<SpiritualRoot> root, Optional<Rules> rules) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty() || !CoreTechniqueFactor.elementMatch(definition, root, rules.get().roots())) {
            return 0;
        }
        double bonus = rules.get().techniques().elementMatchBonus();
        if (!Double.isFinite(bonus) || bonus < 0.0D) {
            throw new IllegalArgumentException("Element match bonus must be finite and non-negative, got " + bonus);
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(bonus * StudySettlement.UNIT_BASIS_POINTS));
    }
}
