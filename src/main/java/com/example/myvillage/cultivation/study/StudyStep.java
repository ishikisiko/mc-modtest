package com.example.myvillage.cultivation.study;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition.TechniqueStudy;
import com.example.myvillage.cultivation.meditation.StudyProgress;
import com.example.myvillage.sim.data.Rules;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One study settlement against the manual slot and the profile: the slot must still hold a valid manual of the
 * same technique, the technique must still be learnable, then {@link StudySettlement} decides the points and
 * the gate cost. Stability and the learned technique change through one profile replacement handed to the
 * committer (the caller commits through {@code CultivationService}); the points go onto the manual only after
 * that commit succeeds, and a finished manual is consumed instead.
 */
public final class StudyStep {
    private StudyStep() {
    }

    public enum Outcome {
        CONTINUE,
        MANUAL_LOST,
        REQUIREMENTS,
        GATE_BLOCKED,
        COMPLETE,
        COMMIT_FAILED
    }

    /**
     * @param progress  the progress after this step (absent when the manual or the technique was lost)
     * @param gateCost  the stability a gate costs (paid or missing)
     * @param shortfall stability still missing at a blocking gate, else 0
     * @param gatesPaid stability paid for gates in this step
     */
    public record Result(
            Outcome outcome, Optional<StudyProgress> progress, int gateCost, int shortfall, int gatesPaid) {
        public Result {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(progress, "progress");
        }

        static Result of(Outcome outcome) {
            return new Result(outcome, Optional.empty(), 0, 0, 0);
        }
    }

    @FunctionalInterface
    public interface ProfileCommitter {
        boolean commit(CultivationProfile replacement);
    }

    public static Result settle(
            ResourceLocation techniqueId,
            ManualSlot slot,
            CultivationProfile profile,
            Function<ResourceLocation, TechniqueDefinition> techniques,
            Function<ResourceLocation, RealmDefinition> realms,
            Predicate<ResourceLocation> elementAvailable,
            Optional<Rules> rules,
            ProfileCommitter committer) {
        Objects.requireNonNull(techniqueId, "techniqueId");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(techniques, "techniques");
        Objects.requireNonNull(committer, "committer");
        if (!slot.technique().equals(Optional.of(techniqueId))) {
            return Result.of(Outcome.MANUAL_LOST);
        }
        TechniqueDefinition definition = techniques.apply(techniqueId);
        if (definition == null) {
            return Result.of(Outcome.MANUAL_LOST);
        }
        if (StudyStart.learnRefusal(techniqueId, profile, techniques, realms, elementAvailable).isPresent()) {
            return Result.of(Outcome.REQUIREMENTS);
        }

        TechniqueStudy study = StudyRules.of(definition);
        int before = slot.comprehension();
        StudySettlement.Plan plan = StudySettlement.plan(
                before,
                study.points(),
                study.gates(),
                study.gateStabilityCost(),
                Math.max(0, profile.stability()),
                Math.max(0, profile.spiritualAffinity()),
                StudyRules.elementBonusBasisPoints(definition, profile.spiritualRoot(), rules));

        CultivationProfile replacement = profile;
        if (plan.stabilityCost() > 0) {
            replacement = replacement.withStability(profile.stability() - plan.stabilityCost());
        }
        if (plan.outcome() == StudySettlement.Outcome.COMPLETE) {
            replacement = replacement.learnTechnique(techniqueId, definition.category());
        }
        if (!replacement.equals(profile)) {
            boolean committed;
            try {
                committed = committer.commit(replacement);
            } catch (RuntimeException exception) {
                committed = false;
            }
            if (!committed) {
                return Result.of(Outcome.COMMIT_FAILED);
            }
        }

        StudyProgress progress = progress(techniqueId, plan.points(), study);
        Outcome outcome = switch (plan.outcome()) {
            case CONTINUE -> Outcome.CONTINUE;
            case GATE_BLOCKED -> Outcome.GATE_BLOCKED;
            case COMPLETE -> Outcome.COMPLETE;
        };
        if (outcome == Outcome.COMPLETE) {
            slot.consumeOne();
        } else if (plan.points() != before) {
            slot.writeComprehension(plan.points());
        }
        return new Result(outcome, Optional.of(progress), study.gateStabilityCost(), plan.shortfall(),
                plan.stabilityCost());
    }

    /** The status view of {@code points} on a manual of a technique studied by {@code study}. */
    public static StudyProgress progress(ResourceLocation techniqueId, int points, TechniqueStudy study) {
        Objects.requireNonNull(study, "study");
        int clamped = Math.max(0, Math.min(points, study.points()));
        return new StudyProgress(
                techniqueId,
                clamped,
                study.points(),
                StudySettlement.nextGate(clamped, study.points(), study.gates()),
                study.gateStabilityCost());
    }
}
