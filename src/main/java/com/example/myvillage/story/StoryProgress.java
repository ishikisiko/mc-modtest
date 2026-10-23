package com.example.myvillage.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.HashSet;

/** Immutable player save. Sessions are deliberately absent; death uses attachment copyOnDeath. */
public record StoryProgress(int schema, String chapter, int definitionVersion, String site,
        String dimension, String stage, long revision, List<String> evidence, List<String> journal,
        List<String> receipts) {
    public static final String CHAPTER = "qianzhen_chapter_1";
    public static final StoryProgress EMPTY = new StoryProgress(1, CHAPTER, 1, "", "", "new", 0, List.of(), List.of(), List.of());
    private static final Codec<List<String>> IDS = Codec.STRING.listOf().validate(v ->
            v.size() <= 32 && new HashSet<>(v).size() == v.size() && v.stream().allMatch(StoryProgress::id)
                    ? DataResult.success(v) : DataResult.error(() -> "Invalid story IDs"));
    private static final Codec<List<String>> JOURNAL = Codec.STRING.listOf().validate(v ->
            v.size() <= 32 && v.stream().allMatch(s -> s.length() <= 256)
                    ? DataResult.success(v) : DataResult.error(() -> "Invalid story journal"));
    private static final Codec<StoryProgress> RAW = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0,1).optionalFieldOf("schema", 0).forGetter(StoryProgress::schema),
            Codec.STRING.optionalFieldOf("chapter", CHAPTER).forGetter(StoryProgress::chapter),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("definition_version", 1).forGetter(StoryProgress::definitionVersion),
            Codec.STRING.optionalFieldOf("site", "").forGetter(StoryProgress::site),
            Codec.STRING.optionalFieldOf("dimension", "").forGetter(StoryProgress::dimension),
            Codec.STRING.optionalFieldOf("stage", "new").forGetter(StoryProgress::stage),
            Codec.LONG.optionalFieldOf("revision", 0L).forGetter(StoryProgress::revision),
            IDS.optionalFieldOf("evidence", List.of()).forGetter(StoryProgress::evidence),
            JOURNAL.optionalFieldOf("journal", List.of()).forGetter(StoryProgress::journal),
            IDS.optionalFieldOf("receipts", List.of()).forGetter(StoryProgress::receipts)
    ).apply(i, StoryProgress::new));
    public static final Codec<StoryProgress> CODEC = RAW.validate(p -> p.valid()
            ? DataResult.success(p.schema == 0 ? new StoryProgress(1,p.chapter,p.definitionVersion,p.site,p.dimension,p.stage,p.revision,p.evidence,p.journal,p.receipts) : p)
            : DataResult.error(() -> "Invalid story progress"));
    public StoryProgress { evidence=List.copyOf(evidence); journal=List.copyOf(journal); receipts=List.copyOf(receipts); }
    public static boolean id(String s) { return s != null && s.matches("[a-z0-9_]{1,64}"); }
    private boolean valid() {
        return id(chapter) && id(stage) && site.length()<=128 && dimension.length()<=128 && revision>=0
                && evidence.size()<=32 && receipts.size()<=32 && journal.size()<=32;
    }
    public boolean accepted() { return !stage.equals("new"); }
    public boolean completed() { return stage.equals("complete"); }
}
