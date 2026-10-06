package com.example.myvillage.combat.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One combat weapon: the item that arms a player for cultivation combat, the style it runs, and
 * the client assets for its first-person presentation. {@code firstPersonRig} and {@code geometry}
 * are client asset locations kept as plain ids; common code never resolves them.
 *
 * <p>{@code family} (optional, lower_snake_case) is the weapon family a school's
 * {@code weapon_family} names ({@code sword}, {@code spear}, {@code fist}); the data validator
 * checks it against the school files. Nothing reads it at runtime yet: it is the hook a school's
 * techniques will use to ask for "a weapon of my family" without naming an item.
 */
public record WeaponDefinition(
        ResourceLocation item,
        ResourceLocation style,
        ResourceLocation firstPersonRig,
        ResourceLocation geometry,
        Optional<String> family) {
    static final Pattern FAMILY = Pattern.compile("[a-z][a-z0-9_]*");

    public WeaponDefinition {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(firstPersonRig, "firstPersonRig");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(family, "family");
        if (family.isPresent() && !FAMILY.matcher(family.get()).matches()) {
            throw new IllegalArgumentException("Weapon family must be lower_snake_case, got \"" + family.get() + "\"");
        }
    }

    /** A weapon without a family. */
    public WeaponDefinition(
            ResourceLocation item, ResourceLocation style, ResourceLocation firstPersonRig, ResourceLocation geometry) {
        this(item, style, firstPersonRig, geometry, Optional.empty());
    }
}
