package com.example.myvillage.combat.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * One combat weapon: the item that arms a player for cultivation combat, the style it runs, and
 * the client assets for its first-person presentation. {@code firstPersonRig} and {@code geometry}
 * are client asset locations kept as plain ids; common code never resolves them.
 */
public record WeaponDefinition(
        ResourceLocation item,
        ResourceLocation style,
        ResourceLocation firstPersonRig,
        ResourceLocation geometry) {
    public WeaponDefinition {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(firstPersonRig, "firstPersonRig");
        Objects.requireNonNull(geometry, "geometry");
    }
}
