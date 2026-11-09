package com.example.myvillage.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TechniqueProgress(long masteryPoints, String insight, int insightSpent, long revision, int masteryRemainder) {
    public static final TechniqueProgress ZERO = new TechniqueProgress(0);

    private record Saved(long mastery, String insight, int spent, long revision, int remainder) { }
    private static final Codec<Saved> SAVED = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("mastery_points").forGetter(Saved::mastery),
            Codec.STRING.optionalFieldOf("insight", "").forGetter(Saved::insight),
            Codec.INT.optionalFieldOf("insight_spent", 0).forGetter(Saved::spent),
            Codec.LONG.optionalFieldOf("growth_revision", 0L).forGetter(Saved::revision),
            Codec.intRange(0, 9999).optionalFieldOf("mastery_remainder", 0).forGetter(Saved::remainder)
    ).apply(i, Saved::new));
    public static final Codec<TechniqueProgress> CODEC = SAVED.comapFlatMap(TechniqueProgress::decode,
            p -> new Saved(p.masteryPoints(), p.insight(), p.insightSpent(), p.revision(), p.masteryRemainder()));

    public TechniqueProgress(long masteryPoints, String insight, int insightSpent, long revision) {
        this(masteryPoints,insight,insightSpent,revision,0);
    }

    public TechniqueProgress(long masteryPoints) { this(masteryPoints, "", 0, 0); }

    public TechniqueProgress {
        if (masteryPoints < 0 || masteryRemainder < 0 || masteryRemainder >= 10000) {
            throw new IllegalArgumentException("Technique mastery must be non-negative, got " + masteryPoints);
        }
        if (insight == null || !insight.matches("[a-z_]{0,32}") || insightSpent < 0 || insightSpent > 1000 || revision < 0)
            throw new IllegalArgumentException("Invalid technique growth account");
        if (insight.isEmpty() && insightSpent != 0) throw new IllegalArgumentException("Spent insight must name a direction");
    }

    public TechniqueProgress withMasteryPoints(long value) {
        return value == masteryPoints ? this : new TechniqueProgress(value, insight, insightSpent, revision, masteryRemainder);
    }

    public TechniqueProgress choose(String choice, int cost) {
        if (choice.isBlank() || cost < 0) throw new IllegalArgumentException("Invalid insight choice");
        return new TechniqueProgress(masteryPoints, choice, Math.addExact(insightSpent, cost), Math.incrementExact(revision),masteryRemainder);
    }

    private static DataResult<TechniqueProgress> decode(Saved saved) {
        try {
            return DataResult.success(new TechniqueProgress(saved.mastery(), saved.insight(), saved.spent(), saved.revision(),saved.remainder()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }
}
