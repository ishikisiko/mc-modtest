package com.example.myvillage.cultivation.growth;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRules.Effect;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRules.Technique;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRules.Transfer;
import net.minecraft.resources.ResourceLocation;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

/** Pure growth decisions. A single best authored relationship supplies all transfer benefits. */
public final class TechniqueGrowth {
    private TechniqueGrowth() { }
    public record Practice(CultivationProfile replacement, int stoneCost, long gained) { }
    public record Choice(CultivationProfile replacement, int stoneCost, int insightCost) { }

    public static Optional<Technique> definition(TechniqueGrowthRules rules, ResourceLocation id,
            Function<ResourceLocation, TechniqueDefinition> lookup) {
        TechniqueDefinition actual = lookup.apply(id);
        return rules.find(id).filter(t -> actual != null && actual.category() == t.category());
    }

    public static boolean mastered(Technique spec, TechniqueProgress progress) {
        return progress != null && progress.masteryPoints() >= spec.cap();
    }

    public static int availableInsight(TechniqueGrowthRules rules, Technique spec, TechniqueProgress progress) {
        return Math.max(0, spec.stage(progress.masteryPoints()) * rules.insightPerStage() - progress.insightSpent());
    }

    /** Stored mastery above today's cap is retained, but cannot earn more or produce larger effects. */
    public static TechniqueProgress gain(Technique spec, TechniqueProgress progress, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative mastery award");
        long room = Math.max(0, spec.cap() - progress.masteryPoints());
        return progress.withMasteryPoints(progress.masteryPoints() + Math.min(room, amount));
    }

    public static Optional<Practice> practice(TechniqueGrowthRules rules, ResourceLocation id,
            CultivationProfile profile, Function<ResourceLocation, TechniqueDefinition> lookup) {
        var spec = definition(rules, id, lookup).orElse(null);
        var progress = profile.learnedTechniques().get(id);
        if (spec == null || progress == null || mastered(spec, progress)) return Optional.empty();
        TechniqueProgress next = gain(spec, progress, spec.practiceGain());
        return Optional.of(new Practice(profile.withTechniqueProgress(id, next), spec.practiceStones(),
                next.masteryPoints() - progress.masteryPoints()));
    }

    public static Optional<Transfer> support(TechniqueGrowthRules rules, ResourceLocation target,
            CultivationProfile profile, Function<ResourceLocation, TechniqueDefinition> lookup) {
        var spec = definition(rules, target, lookup).orElse(null);
        if (spec == null) return Optional.empty();
        return spec.transfers().stream().filter(link -> {
            var source = definition(rules, link.source(), lookup).orElse(null);
            return source != null && source.category() == spec.category()
                    && mastered(source, profile.learnedTechniques().get(link.source()));
        }).sorted(Comparator.comparingInt(Transfer::studyBonusBp).reversed()
                .thenComparing(t -> t.source().toString())).findFirst();
    }

    /** Redirect the existing meditation mastery clock to the sampled running core, with a cap. */
    public static CultivationProfile meditationMastery(TechniqueGrowthRules rules, CultivationProfile before,
            CultivationProfile settled, long earned, Function<ResourceLocation, TechniqueDefinition> lookup) {
        var basic = com.example.myvillage.cultivation.data.ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID;
        ResourceLocation target = before.activeCoreTechnique().orElse(basic);
        var spec = definition(rules, target, lookup).orElse(null);
        var progress = before.learnedTechniques().get(target);
        if (spec == null || spec.category() != com.example.myvillage.cultivation.data.TechniqueCategory.CORE || progress == null) return settled;
        return settled.withTechniqueProgress(basic, before.learnedTechniques().get(basic))
                .withTechniqueProgress(target, gain(spec, progress, earned));
    }

    public static int firstInsightCost(TechniqueGrowthRules rules, ResourceLocation id,
            CultivationProfile profile, Function<ResourceLocation, TechniqueDefinition> lookup) {
        return rules.insightCost() - support(rules, id, profile, lookup).map(Transfer::firstInsightDiscount).orElse(0);
    }

    /** A matching revision prevents delayed duplicate choices from spending twice. */
    public static Optional<Choice> choose(TechniqueGrowthRules rules, ResourceLocation id, int choice,
            long expectedRevision, CultivationProfile profile, Function<ResourceLocation, TechniqueDefinition> lookup) {
        var spec = definition(rules, id, lookup).orElse(null);
        var progress = profile.learnedTechniques().get(id);
        if (spec == null || progress == null || choice < 0 || choice >= spec.insights().size()
                || progress.revision() != expectedRevision || progress.revision() == Long.MAX_VALUE) return Optional.empty();
        String selected = spec.insights().get(choice).id();
        if (progress.insight().equals(selected)) return Optional.empty();
        boolean first = progress.insight().isEmpty();
        if (first && spec.stage(progress.masteryPoints()) < rules.insightUnlockStage()) return Optional.empty();
        int cost = first ? firstInsightCost(rules, id, profile, lookup) : 0;
        if (availableInsight(rules, spec, progress) < cost) return Optional.empty();
        return Optional.of(new Choice(profile.withTechniqueProgress(id, progress.choose(selected, cost)),
                first ? 0 : rules.respecStones(), cost));
    }

    /** Applied once, after a real manual finishes. Previously learned techniques never re-inherit. */
    public static CultivationProfile learn(TechniqueGrowthRules rules, ResourceLocation id,
            CultivationProfile profile, Function<ResourceLocation, TechniqueDefinition> lookup) {
        TechniqueDefinition definition = lookup.apply(id);
        if (definition == null) throw new IllegalArgumentException("Unregistered technique");
        var learned = profile.learnTechnique(id, definition.category());
        var spec = definition(rules, id, lookup).orElse(null);
        var support = support(rules, id, profile, lookup).orElse(null);
        if (spec == null || support == null || !definition.previous().equals(Optional.of(support.source()))) return learned;
        var previous = profile.learnedTechniques().get(support.source());
        String insight = support.inheritInsight() && spec.insight(previous.insight()).isPresent() ? previous.insight() : "";
        return learned.withTechniqueProgress(id, new TechniqueProgress(support.inheritedMastery(), insight, 0, 0));
    }

    public static int multiplier(TechniqueGrowthRules rules, ResourceLocation id, CultivationProfile profile, Effect effect) {
        var spec = rules.find(id).orElse(null);
        var progress = profile.learnedTechniques().get(id);
        if (spec == null || progress == null) return TechniqueGrowthRules.UNIT;
        int insight = spec.insight(progress.insight()).map(i -> i.bonuses().getOrDefault(effect, 0)).orElse(0);
        return TechniqueGrowthRules.UNIT + spec.bonus(effect, progress.masteryPoints()) + insight;
    }

    /** Costs round up, remain positive, and overflow fails before any payment is made. */
    public static long cost(long baseCost, int multiplier) {
        if (baseCost < 0 || multiplier <= 0) throw new IllegalArgumentException("Invalid growth cost");
        if (baseCost == 0) return 0;
        long scaled = Math.multiplyExact(baseCost, multiplier);
        return Math.max(1, scaled / TechniqueGrowthRules.UNIT + (scaled % TechniqueGrowthRules.UNIT == 0 ? 0 : 1));
    }
}
