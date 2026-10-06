package com.example.myvillage.cultivation.study;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** The inventory slot a study session reads from; the server-side view of one manual stack. */
public interface ManualSlot {
    /** The technique of the valid manual in this slot, or empty when the slot no longer holds one. */
    Optional<ResourceLocation> technique();

    /** The comprehension points on the manual (0 when it has none). */
    int comprehension();

    /** Writes the comprehension points onto the manual. */
    void writeComprehension(int points);

    /** Removes one manual from the slot. */
    void consumeOne();
}
