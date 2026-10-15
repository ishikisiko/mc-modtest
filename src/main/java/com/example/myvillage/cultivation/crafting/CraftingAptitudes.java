package com.example.myvillage.cultivation.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;

/** Independent immutable saved state; profile resets and root changes never clear it. */
public record CraftingAptitudes(int version, boolean initialized, int alchemy, int forging) {
    public static final int CURRENT_VERSION = 1;
    public static final int MIN_SCORE = 0, MAX_SCORE = 100;
    public static final CraftingAptitudes EMPTY = new CraftingAptitudes(CURRENT_VERSION, false, 0, 0);
    private record Serialized(int version, boolean initialized, int alchemy, int forging) { }
    private static final Codec<Serialized> SERIALIZED = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("version").forGetter(Serialized::version),
            Codec.BOOL.fieldOf("initialized").forGetter(Serialized::initialized),
            Codec.INT.fieldOf("alchemy").forGetter(Serialized::alchemy),
            Codec.INT.fieldOf("forging").forGetter(Serialized::forging)
    ).apply(i, Serialized::new));
    public static final Codec<CraftingAptitudes> CODEC = SERIALIZED.comapFlatMap(s -> {
        try { return DataResult.success(new CraftingAptitudes(s.version(), s.initialized(), s.alchemy(), s.forging())); }
        catch (IllegalArgumentException e) { return DataResult.error(e::getMessage); }
    }, s -> new Serialized(s.version(), s.initialized(), s.alchemy(), s.forging()));

    public CraftingAptitudes {
        if (version != CURRENT_VERSION) throw new IllegalArgumentException("Unsupported crafting aptitude version");
        checkScore(alchemy); checkScore(forging);
        if (!initialized && (alchemy != 0 || forging != 0))
            throw new IllegalArgumentException("Pending aptitudes must have zero scores");
    }
    public enum Profession { ALCHEMY, FORGING }
    public static void checkScore(int score) {
        if (score < MIN_SCORE || score > MAX_SCORE) throw new IllegalArgumentException("Crafting aptitude outside format bounds");
    }
    public int score(Profession profession) {
        return switch (Objects.requireNonNull(profession, "profession")) { case ALCHEMY -> alchemy; case FORGING -> forging; };
    }
    public CraftingAptitudes withScore(Profession profession, int score) {
        if (!initialized) throw new IllegalStateException("Crafting aptitudes are not initialized");
        checkScore(score);
        return switch (Objects.requireNonNull(profession, "profession")) {
            case ALCHEMY -> new CraftingAptitudes(version, true, score, forging);
            case FORGING -> new CraftingAptitudes(version, true, alchemy, score);
        };
    }
    public int qualityCap(Profession profession, CraftingRules rules) {
        Objects.requireNonNull(profession, "profession"); Objects.requireNonNull(rules, "rules");
        return initialized ? rules.qualityCap(score(profession)) : 0;
    }
    public boolean allowsQuality(Profession profession, int requested, CraftingRules rules) {
        return initialized && requested >= 1 && requested <= qualityCap(profession, rules);
    }
}
