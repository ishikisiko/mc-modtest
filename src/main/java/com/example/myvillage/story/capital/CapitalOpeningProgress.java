package com.example.myvillage.story.capital;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;

/** Per-player immutable save; no entity/session or another player's work is stored here. */
public record CapitalOpeningProgress(String site, String stage, long revision, List<Long> planted) {
    public static final int MAX_PLANTS = 16;
    public static final CapitalOpeningProgress EMPTY = new CapitalOpeningProgress("", "new", 0, List.of());
    private static final Set<String> STAGES = Set.of("new", "working", "teaching", "practice", "complete");
    private static final Codec<CapitalOpeningProgress> RAW = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("site").forGetter(CapitalOpeningProgress::site),
            Codec.STRING.fieldOf("stage").forGetter(CapitalOpeningProgress::stage),
            Codec.LONG.fieldOf("revision").forGetter(CapitalOpeningProgress::revision),
            Codec.LONG.listOf().fieldOf("planted").forGetter(CapitalOpeningProgress::planted)
    ).apply(i, CapitalOpeningProgress::new));
    public static final Codec<CapitalOpeningProgress> CODEC = RAW.validate(p -> p.valid()
            ? DataResult.success(p) : DataResult.error(() -> "Invalid capital opening progress"));
    public CapitalOpeningProgress { planted = List.copyOf(planted); }
    public boolean valid() {
        return STAGES.contains(stage) && revision >= 0 && site.length() <= 96
                && (stage.equals("new") ? site.isEmpty() && planted.isEmpty()
                    : site.matches("[a-zA-Z0-9_.:/-]{1,96}"))
                && planted.size() <= MAX_PLANTS && new HashSet<>(planted).size() == planted.size();
    }
    public CapitalOpeningProgress accept(String site) {
        return !stage.equals("new") ? this : new CapitalOpeningProgress(site, "working", Math.incrementExact(revision), List.of());
    }
    public CapitalOpeningProgress plant(long pos, int required) {
        if (!stage.equals("working") || required < 1 || required > MAX_PLANTS || planted.contains(pos) || planted.size() >= required) return this;
        var positions = new ArrayList<>(planted); positions.add(pos);
        return new CapitalOpeningProgress(site, positions.size() == required ? "teaching" : stage,
                Math.incrementExact(revision), positions);
    }
    public CapitalOpeningProgress learned(String currentSite, boolean experienced) {
        if (stage.equals("complete") || !site.isEmpty() && !site.equals(currentSite)) return this;
        String next = experienced ? "complete" : "practice";
        if (stage.equals(next)) return this;
        return new CapitalOpeningProgress(currentSite, next, Math.incrementExact(revision), planted);
    }
    public CapitalOpeningProgress practiced() {
        return !stage.equals("practice") ? this : new CapitalOpeningProgress(site, "complete", Math.incrementExact(revision), planted);
    }
}
