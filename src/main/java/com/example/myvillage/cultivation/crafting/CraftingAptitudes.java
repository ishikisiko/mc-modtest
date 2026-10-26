package com.example.myvillage.cultivation.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;

/** Independent immutable saved state; profile resets and root changes never clear it. */
public record CraftingAptitudes(int version, boolean initialized, int alchemy, int forging, int talisman) {
    public static final int CURRENT_VERSION = 1;
    public static final int MIN_SCORE = 0, MAX_SCORE = 100;
    public static final CraftingAptitudes EMPTY = new CraftingAptitudes(CURRENT_VERSION, false, 0, 0, -1);
    private record Serialized(int version, boolean initialized, int alchemy, int forging, int talisman) { }
    private static final Codec<Serialized> SERIALIZED = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("version").forGetter(Serialized::version),
            Codec.BOOL.fieldOf("initialized").forGetter(Serialized::initialized),
            Codec.INT.fieldOf("alchemy").forGetter(Serialized::alchemy),
            Codec.INT.fieldOf("forging").forGetter(Serialized::forging),
            Codec.intRange(-1, 100).optionalFieldOf("talisman", -1).forGetter(Serialized::talisman)
    ).apply(i, Serialized::new));
    public static final Codec<CraftingAptitudes> CODEC = SERIALIZED.comapFlatMap(s -> {
        try { return DataResult.success(new CraftingAptitudes(s.version(), s.initialized(), s.alchemy(), s.forging(), s.talisman())); }
        catch (IllegalArgumentException e) { return DataResult.error(e::getMessage); }
    }, s -> new Serialized(s.version(), s.initialized(), s.alchemy(), s.forging(), s.talisman()));

    /** Source/serialized v1 compatibility: only the added profession is pending. */
    public CraftingAptitudes(int version, boolean initialized, int alchemy, int forging) {
        this(version, initialized, alchemy, forging, -1);
    }
    public boolean talismanInitialized() { return initialized && talisman >= 0; }
    public CraftingAptitudes {
        if (version != CURRENT_VERSION) throw new IllegalArgumentException("Unsupported crafting aptitude version");
        checkScore(alchemy); checkScore(forging);
        if (talisman < -1 || talisman > MAX_SCORE || !initialized && talisman != -1)
            throw new IllegalArgumentException("Invalid talisman aptitude");
        if (!initialized && (alchemy != 0 || forging != 0))
            throw new IllegalArgumentException("Pending aptitudes must have zero scores");
    }
    public enum Profession { ALCHEMY, FORGING, TALISMAN }
    public static void checkScore(int score) {
        if (score < MIN_SCORE || score > MAX_SCORE) throw new IllegalArgumentException("Crafting aptitude outside format bounds");
    }
    public int score(Profession profession) {
        return switch (Objects.requireNonNull(profession, "profession")) { case ALCHEMY -> alchemy; case FORGING -> forging; case TALISMAN -> Math.max(0, talisman); };
    }
    public CraftingAptitudes withScore(Profession profession, int score) {
        if (!initialized) throw new IllegalStateException("Crafting aptitudes are not initialized");
        checkScore(score);
        return switch (Objects.requireNonNull(profession, "profession")) {
            case ALCHEMY -> new CraftingAptitudes(version, true, score, forging, talisman);
            case FORGING -> new CraftingAptitudes(version, true, alchemy, score, talisman);
            case TALISMAN -> new CraftingAptitudes(version, true, alchemy, forging, score);
        };
    }
    public int qualityCap(Profession profession, CraftingRules rules) {
        Objects.requireNonNull(profession, "profession"); Objects.requireNonNull(rules, "rules");
        return initialized && (profession != Profession.TALISMAN || talismanInitialized()) ? rules.qualityCap(score(profession)) : 0;
    }
    public boolean allowsQuality(Profession profession, int requested, CraftingRules rules) {
        return initialized && requested >= 1 && requested <= qualityCap(profession, rules);
    }
}
