package com.example.myvillage.combat.definition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The combat style and weapon registry. Styles and weapons come from the bundled data (see
 * {@link CombatDataLoader}), loaded once from the mod's own resources on first use, on the client
 * and the dedicated server alike, so both hold the same data without a sync payload.
 *
 * <p>A held item is a combat weapon only when it has a weapon entry; the entry names the style it
 * runs. Move ids are unique across styles, so a move id alone identifies its style and index.
 */
public final class CombatStyles {
    private static volatile CombatStyles bundled;

    private final List<CombatStyleDefinition> styles;
    private final List<WeaponDefinition> weapons;
    private final Map<ResourceLocation, CombatStyleDefinition> stylesById = new LinkedHashMap<>();
    private final Map<ResourceLocation, WeaponDefinition> weaponsByItem = new LinkedHashMap<>();
    private final Map<ResourceLocation, MoveRef> movesById = new HashMap<>();
    private final Set<ResourceLocation> readyIdleAnimations;

    public CombatStyles(List<CombatStyleDefinition> styles, List<WeaponDefinition> weapons) {
        this.styles = List.copyOf(Objects.requireNonNull(styles, "styles"));
        this.weapons = List.copyOf(Objects.requireNonNull(weapons, "weapons"));
        if (this.styles.isEmpty()) {
            throw new IllegalArgumentException("At least one combat style is required");
        }
        for (CombatStyleDefinition style : this.styles) {
            if (stylesById.putIfAbsent(style.id(), style) != null) {
                throw new IllegalArgumentException("Duplicate combat style " + style.id());
            }
            for (int index = 0; index < style.moves().size(); index++) {
                AttackMoveDefinition move = style.move(index);
                MoveRef previous = movesById.putIfAbsent(move.id(), new MoveRef(style, index, move));
                if (previous != null) {
                    throw new IllegalArgumentException("Move " + move.id() + " is declared by both "
                            + previous.style().id() + " and " + style.id());
                }
            }
        }
        for (WeaponDefinition weapon : this.weapons) {
            if (!stylesById.containsKey(weapon.style())) {
                throw new IllegalArgumentException("Weapon " + weapon.item() + " names unknown style " + weapon.style());
            }
            if (weaponsByItem.putIfAbsent(weapon.item(), weapon) != null) {
                throw new IllegalArgumentException("Duplicate weapon entry for item " + weapon.item());
            }
        }
        readyIdleAnimations = this.styles.stream()
                .map(CombatStyleDefinition::readyIdleAnimation)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The bundled registry, loaded from the classpath on first use. A load failure throws
     * {@link CombatDataException}; it is a startup error, never a silent fallback.
     */
    public static CombatStyles bundled() {
        CombatStyles current = bundled;
        if (current == null) {
            synchronized (CombatStyles.class) {
                current = bundled;
                if (current == null) {
                    current = CombatDataLoader.load(CombatStyles::openClasspath);
                    bundled = current;
                }
            }
        }
        return current;
    }

    private static InputStream openClasspath(String path) {
        return CombatStyles.class.getResourceAsStream("/" + path);
    }

    public List<CombatStyleDefinition> styles() {
        return styles;
    }

    public List<WeaponDefinition> weapons() {
        return weapons;
    }

    /** The first style the index lists, for presentation that must play without a weapon. */
    public CombatStyleDefinition defaultStyle() {
        return styles.getFirst();
    }

    public Optional<CombatStyleDefinition> style(ResourceLocation styleId) {
        return Optional.ofNullable(stylesById.get(styleId));
    }

    public Optional<WeaponDefinition> weapon(ResourceLocation itemId) {
        return Optional.ofNullable(weaponsByItem.get(itemId));
    }

    public Optional<CombatStyleDefinition> styleForItem(ResourceLocation itemId) {
        return weapon(itemId).map(weapon -> stylesById.get(weapon.style()));
    }

    /** The weapon entry for a held stack; empty for an empty stack or an item without an entry. */
    public Optional<WeaponDefinition> weapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return weapon(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public Optional<CombatStyleDefinition> styleFor(ItemStack stack) {
        return weapon(stack).map(weapon -> stylesById.get(weapon.style()));
    }

    public boolean isWeapon(ItemStack stack) {
        return weapon(stack).isPresent();
    }

    public Optional<MoveRef> move(ResourceLocation moveId) {
        return Optional.ofNullable(movesById.get(moveId));
    }

    public boolean isMove(ResourceLocation animationId) {
        return movesById.containsKey(animationId);
    }

    public boolean isReadyIdle(ResourceLocation animationId) {
        return readyIdleAnimations.contains(animationId);
    }

    /** A move with the style that declares it and its index in that style's combo. */
    public record MoveRef(CombatStyleDefinition style, int index, AttackMoveDefinition move) {
        public MoveRef {
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(move, "move");
        }
    }
}
