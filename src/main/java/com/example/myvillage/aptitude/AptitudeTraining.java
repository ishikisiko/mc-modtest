package com.example.myvillage.aptitude;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** 单个玩家当前亲自进行的传承、授业或研究；时钟使用主世界游戏时间。 */
public record AptitudeTraining(String kind, String aptitude, String target, int progress, int required,
        int reward, long deadline, String dimension, int x, int y, int z, int teacher, int ceiling) {
    public static final Codec<AptitudeTraining> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(AptitudeTraining::kind),
            Codec.STRING.fieldOf("aptitude").forGetter(AptitudeTraining::aptitude),
            Codec.STRING.fieldOf("target").forGetter(AptitudeTraining::target),
            Codec.INT.fieldOf("progress").forGetter(AptitudeTraining::progress),
            Codec.INT.fieldOf("required").forGetter(AptitudeTraining::required),
            Codec.INT.fieldOf("reward").forGetter(AptitudeTraining::reward),
            Codec.LONG.fieldOf("deadline").forGetter(AptitudeTraining::deadline),
            Codec.STRING.fieldOf("dimension").forGetter(AptitudeTraining::dimension),
            Codec.INT.fieldOf("x").forGetter(AptitudeTraining::x), Codec.INT.fieldOf("y").forGetter(AptitudeTraining::y),
            Codec.INT.fieldOf("z").forGetter(AptitudeTraining::z), Codec.INT.fieldOf("teacher").forGetter(AptitudeTraining::teacher),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("ceiling").forGetter(AptitudeTraining::ceiling)
    ).apply(i, AptitudeTraining::new));
    public AptitudeTraining(String kind, String aptitude, String target, int progress, int required,
            int reward, long deadline, String dimension, int x, int y, int z, int teacher) {
        this(kind, aptitude, target, progress, required, reward, deadline, dimension, x, y, z, teacher, Integer.MAX_VALUE);
    }
    public AptitudeTraining {
        if (!java.util.Set.of("memory", "teaching", "research").contains(kind) || !AptitudeRules.get().valid(aptitude)
                || progress < 0 || required <= 0 || progress > required || reward <= 0 || deadline < 0
                || ceiling < 0 || target.length() > 256 || dimension.length() > 128) throw new IllegalArgumentException("Invalid aptitude training");
    }
    public AptitudeTraining advance(int amount) {
        return new AptitudeTraining(kind, aptitude, target, (int)Math.min(required, (long)progress + amount), required,
                reward, deadline, dimension, x, y, z, teacher, ceiling);
    }
}
