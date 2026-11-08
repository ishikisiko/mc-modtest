package com.example.myvillage.aptitude;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;

/** 兵器资质、培养进度与逐人领取记录；玩家写入只经 CultivationService。 */
public record AptitudeState(boolean initialized, AptitudeValues scores, Map<String, Long> progress,
        Map<String, Long> receipts, Optional<AptitudeTraining> training) {
    public static final AptitudeState EMPTY = new AptitudeState(false, AptitudeValues.EMPTY, Map.of(), Map.of(), Optional.empty());
    private static final Codec<Map<String, Long>> COUNTERS = Codec.unboundedMap(Codec.STRING, Codec.LONG.validate(value -> value >= 0 ? com.mojang.serialization.DataResult.success(value) : com.mojang.serialization.DataResult.error(() -> "Negative aptitude counter")));
    public static final Codec<AptitudeState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("initialized").forGetter(AptitudeState::initialized),
            AptitudeValues.CODEC.fieldOf("scores").forGetter(AptitudeState::scores),
            COUNTERS.fieldOf("progress").forGetter(AptitudeState::progress),
            COUNTERS.fieldOf("receipts").forGetter(AptitudeState::receipts),
            AptitudeTraining.CODEC.optionalFieldOf("training").forGetter(AptitudeState::training)
    ).apply(i, AptitudeState::new));
    public AptitudeState {
        progress = Map.copyOf(progress); receipts = Map.copyOf(receipts);
        if (progress.size() > 64 || receipts.size() > 4096 || progress.values().stream().anyMatch(v -> v < 0)
                || receipts.values().stream().anyMatch(v -> v < 0) || !initialized && (!scores.values().isEmpty() || training.isPresent()))
            throw new IllegalArgumentException("Invalid aptitude state");
    }
    public AptitudeState withScores(AptitudeValues next) { return new AptitudeState(true, next, progress, receipts, training); }
    public AptitudeState withTraining(Optional<AptitudeTraining> next) { return new AptitudeState(initialized, scores, progress, receipts, next); }
    public AptitudeState counter(boolean receipt, String key, long value) {
        var next = new java.util.TreeMap<>(receipt ? receipts : progress); next.put(key, value);
        return new AptitudeState(initialized, scores, receipt ? progress : next, receipt ? next : receipts, training);
    }
}
