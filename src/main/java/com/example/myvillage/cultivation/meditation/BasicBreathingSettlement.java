package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.time.CultivationTimeMath;

import java.util.Objects;

public final class BasicBreathingSettlement {
    public static final int SETTLEMENT_INTERVAL_TICKS = 10;
    public static final int SPIRIT_PROGRESS_PER_SETTLEMENT = 50;
    public static final int MASTERY_PER_YEAR = 10;

    private BasicBreathingSettlement() {
    }

    public static Accrual accrue(Remainders current, int activeTicks, long ticksPerYear) {
        Objects.requireNonNull(current, "current");
        if (activeTicks <= 0 || ticksPerYear <= 0) {
            throw new IllegalArgumentException("Active ticks and ticks per year must be positive");
        }
        Due mastery = due(current.mastery(), MASTERY_PER_YEAR, activeTicks, ticksPerYear);
        return new Accrual(
                mastery.whole(),
                new Remainders(mastery.remainder()));
    }

    public static Plan plan(
            CultivationProfile current,
            long cultivationCap,
            MeditationMode mode,
            Accrual accrual,
            boolean spiritStonesAvailable) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(accrual, "accrual");
        if (cultivationCap <= 0) {
            throw new IllegalArgumentException("Cultivation cap must be positive");
        }
        int stabilityCap = RealmStageDefinition.stabilityCapFor(cultivationCap);
        TechniqueProgress breathing = current.learnedTechniques()
                .get(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID);
        if (breathing == null) {
            throw new IllegalArgumentException("Basic Breathing must be learned before settlement");
        }

        long remainingCapacity = current.cultivationProgress() >= cultivationCap
                ? 0
                : cultivationCap - current.cultivationProgress();
        boolean spiritBatch = mode == MeditationMode.SPIRIT
                && remainingCapacity > 0
                && spiritStonesAvailable;
        boolean downgrade = mode == MeditationMode.SPIRIT
                && remainingCapacity > 0
                && !spiritStonesAvailable;
        long requestedProgress = spiritBatch
                ? SPIRIT_PROGRESS_PER_SETTLEMENT
                : current.spiritualAffinity();
        long progressApplied = Math.min(requestedProgress, remainingCapacity);
        long finalProgress = current.cultivationProgress();
        if (current.cultivationProgress() <= cultivationCap) {
            finalProgress = Math.min(
                    cultivationCap,
                    CultivationTimeMath.saturatingAdd(current.cultivationProgress(), progressApplied));
        }
        long stabilityApplied = 0;
        int finalStability = current.stability();
        if (current.cultivationProgress() >= cultivationCap
                && current.stability() < stabilityCap) {
            stabilityApplied = Math.min(
                    (long) current.spiritualAffinity(),
                    (long) stabilityCap - current.stability());
            finalStability = (int) CultivationTimeMath.saturatingAdd(
                    current.stability(), stabilityApplied);
        }
        long finalMastery = CultivationTimeMath.saturatingAdd(
                breathing.masteryPoints(), accrual.masteryDue());

        CultivationProfile replacement = current
                .withCultivationProgress(finalProgress)
                .withStability(finalStability)
                .withTechniqueMastery(
                        ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID,
                        finalMastery);
        return new Plan(replacement, spiritBatch, downgrade, progressApplied, stabilityApplied);
    }

    private static Due due(long remainder, int rate, int activeTicks, long denominator) {
        if (remainder < 0 || remainder >= denominator) {
            throw new IllegalArgumentException("Fixed-point remainder is outside the denominator");
        }
        long increment = Math.multiplyExact((long) rate, activeTicks);
        long whole = increment / denominator;
        long incrementRemainder = increment % denominator;
        if (incrementRemainder != 0 && remainder >= denominator - incrementRemainder) {
            return new Due(whole + 1, remainder - (denominator - incrementRemainder));
        }
        return new Due(whole, remainder + incrementRemainder);
    }

    public record Remainders(long mastery) {
        public static final Remainders ZERO = new Remainders(0);

        public Remainders {
            if (mastery < 0) {
                throw new IllegalArgumentException("Settlement remainders must be non-negative");
            }
        }
    }

    public record Accrual(long masteryDue, Remainders remainders) {
        public Accrual {
            if (masteryDue < 0) {
                throw new IllegalArgumentException("Settlement outputs must be non-negative");
            }
            Objects.requireNonNull(remainders, "remainders");
        }
    }

    public record Plan(
            CultivationProfile replacement,
            boolean consumeSpiritStones,
            boolean downgradeToNormal,
            long progressApplied,
            long stabilityApplied) {
        public Plan {
            Objects.requireNonNull(replacement, "replacement");
            if (progressApplied < 0 || stabilityApplied < 0) {
                throw new IllegalArgumentException("Applied cultivation values must be non-negative");
            }
            if (consumeSpiritStones && downgradeToNormal) {
                throw new IllegalArgumentException("A spirit batch cannot consume and downgrade together");
            }
        }

        public boolean profileChangedFrom(CultivationProfile current) {
            return !replacement.equals(current);
        }
    }

    private record Due(long whole, long remainder) {
    }
}
