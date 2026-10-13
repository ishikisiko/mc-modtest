package com.example.myvillage.cultivation.qi;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** All recovery tuning comes from data/myvillage/qi/rules.json. */
public record QiRules(int regenIntervalTicks, long regenAmount, int meditationMultiplier, int spendDelayTicks) {
    public static final Codec<QiRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("regen_interval_ticks").forGetter(QiRules::regenIntervalTicks),
            Codec.LONG.comapFlatMap(value -> value > 0
                    ? DataResult.success(value)
                    : DataResult.error(() -> "regen_amount must be positive"),
                    value -> value).fieldOf("regen_amount").forGetter(QiRules::regenAmount),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("meditation_multiplier").forGetter(QiRules::meditationMultiplier),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("spend_delay_ticks").forGetter(QiRules::spendDelayTicks)
    ).apply(instance, QiRules::new));

    public QiRules {
        if (regenIntervalTicks <= 0 || regenAmount <= 0 || meditationMultiplier <= 0 || spendDelayTicks < 0) {
            throw new IllegalArgumentException("Qi recovery rules contain invalid bounds");
        }
    }
}
