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
 *
 * <p>{@code paired} (optional, default false; 0.39.1) means one item is a pair worn on both hands,
 * like the Xuantie gauntlet: the client draws the same item model mirrored on the off hand while
 * the off-hand slot is empty (third person and the first-person free off hand). Presentation only;
 * the server never reads it.
 */
public record WeaponDefinition(
        ResourceLocation item,
        ResourceLocation style,
        ResourceLocation firstPersonRig,
        ResourceLocation geometry,
        Optional<String> family,
        boolean paired) {
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

    /** A single (not paired) weapon. */
    public WeaponDefinition(
            ResourceLocation item,
            ResourceLocation style,
            ResourceLocation firstPersonRig,
            ResourceLocation geometry,
            Optional<String> family) {
        this(item, style, firstPersonRig, geometry, family, false);
    }

    /** A single weapon without a family. */
    public WeaponDefinition(
            ResourceLocation item, ResourceLocation style, ResourceLocation firstPersonRig, ResourceLocation geometry) {
        this(item, style, firstPersonRig, geometry, Optional.empty(), false);
    }
}
