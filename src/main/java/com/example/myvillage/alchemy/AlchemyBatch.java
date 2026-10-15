package com.example.myvillage.alchemy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Frozen server decision. Reading a batch never consults randomness or the current brewer. */
public record AlchemyBatch(UUID brewer, String recipe, int score, int cap, int quality) {
    public AlchemyBatch {
        Objects.requireNonNull(brewer, "brewer");
        if (recipe == null || !recipe.matches("[a-z0-9_]+") || score < 0 || score > 100
                || cap < 1 || cap > 4 || quality < 1 || quality > cap)
            throw new IllegalArgumentException("Invalid saved alchemy batch");
    }
    public enum Rejection {
        INVALID("invalid"), BUSY("busy"), UNAWAKENED("unawakened"),
        INGREDIENTS("ingredients"), FUEL("fuel"), OUTPUT("output");
        private final String suffix;
        Rejection(String suffix) { this.suffix = suffix; }
        public String messageKey() { return "message.myvillage.alchemy.start_" + suffix; }
    }
    /** Ordered checks run before drawing an outcome, including an unconditional empty-output check. */
    public static Rejection rejection(boolean validActor, boolean active, boolean awakened,
                                      boolean ingredients, boolean fuel, boolean outputEmpty) {
        if (!validActor) return Rejection.INVALID;
        if (active) return Rejection.BUSY;
        if (!awakened) return Rejection.UNAWAKENED;
        if (!ingredients) return Rejection.INGREDIENTS;
        if (!fuel) return Rejection.FUEL;
        return outputEmpty ? null : Rejection.OUTPUT;
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1); tag.putUUID("Brewer", brewer); tag.putString("Recipe", recipe);
        tag.putInt("Score", score); tag.putInt("Cap", cap); tag.putInt("Quality", quality);
        return tag;
    }
    public static Optional<AlchemyBatch> read(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.hasUUID("Brewer")
                || !tag.contains("Recipe", Tag.TAG_STRING) || !tag.contains("Score", Tag.TAG_INT)
                || !tag.contains("Cap", Tag.TAG_INT) || !tag.contains("Quality", Tag.TAG_INT)) return Optional.empty();
        try {
            return Optional.of(new AlchemyBatch(tag.getUUID("Brewer"), tag.getString("Recipe"),
                    tag.getInt("Score"), tag.getInt("Cap"), tag.getInt("Quality")));
        } catch (IllegalArgumentException exception) { return Optional.empty(); }
    }
}
