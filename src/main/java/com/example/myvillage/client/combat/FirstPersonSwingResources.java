package com.example.myvillage.client.combat;

import com.example.myvillage.combat.definition.CombatStyleDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.definition.WeaponDefinition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads each registered weapon's first-person swing rig and weapon geometry contract (the client
 * assets its weapon entry names) on every client resource reload, so F3+T picks up edits to
 * either. A missing or invalid file leaves only that weapon on the ordinary vanilla held-item pose
 * (no rig arm, no custom grip); other weapons keep their rigs.
 */
final class FirstPersonSwingResources implements ResourceManagerReloadListener {
    static final FirstPersonSwingResources INSTANCE = new FirstPersonSwingResources();

    private static final Logger LOGGER = LoggerFactory.getLogger(FirstPersonSwingResources.class);
    private static Map<ResourceLocation, WeaponRig> rigs = Map.of();

    private FirstPersonSwingResources() {
    }

    /** The loaded rig of a weapon item, with the weapon entry and its style. */
    static Optional<WeaponRig> forItem(ResourceLocation itemId) {
        return Optional.ofNullable(rigs.get(itemId));
    }

    static Optional<WeaponRig> forStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return forItem(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static Optional<WeaponRig> forHeld(Player player) {
        return forStack(player.getMainHandItem());
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        rigs = loadAll(CombatStyles.bundled(), (weapon, location) -> read(resourceManager, weapon, location));
    }

    /** Reads one asset of a weapon; empty (after logging why) when it is missing or unreadable. */
    @FunctionalInterface
    interface AssetReader {
        Optional<JsonObject> read(WeaponDefinition weapon, ResourceLocation location);
    }

    /**
     * Loads every weapon's rig and geometry independently: a weapon whose files are missing or
     * invalid is left out (it keeps the vanilla hold) and the others still load.
     */
    static Map<ResourceLocation, WeaponRig> loadAll(CombatStyles styles, AssetReader reader) {
        Map<ResourceLocation, WeaponRig> loaded = new HashMap<>();
        for (WeaponDefinition weapon : styles.weapons()) {
            CombatStyleDefinition style = styles.style(weapon.style()).orElseThrow();
            load(reader, weapon, style).ifPresent(rig -> loaded.put(weapon.item(), rig));
        }
        return Map.copyOf(loaded);
    }

    private static Optional<WeaponRig> load(
            AssetReader reader,
            WeaponDefinition weapon,
            CombatStyleDefinition style) {
        ResourceLocation rigLocation = weapon.firstPersonRig();
        ResourceLocation geometryLocation = weapon.geometry();
        Optional<JsonObject> geometryJson = reader.read(weapon, geometryLocation);
        Optional<JsonObject> rigJson = reader.read(weapon, rigLocation);
        if (geometryJson.isEmpty() || rigJson.isEmpty()) {
            return Optional.empty();
        }
        WeaponGeometry geometry;
        try {
            geometry = WeaponGeometry.parse(geometryJson.get());
        } catch (RuntimeException exception) {
            LOGGER.error("Invalid weapon geometry {}; {} uses the vanilla hold",
                    geometryLocation, weapon.item(), exception);
            return Optional.empty();
        }
        try {
            FirstPersonSwing swing = FirstPersonSwing.parse(rigJson.get(), style, geometry);
            LOGGER.info("Loaded first-person swing rig {} ({} moves) with weapon geometry {}",
                    rigLocation, swing.moves().size(), geometryLocation);
            return Optional.of(new WeaponRig(weapon, style, swing));
        } catch (RuntimeException exception) {
            LOGGER.error("Invalid first-person swing rig {}; {} uses the vanilla hold",
                    rigLocation, weapon.item(), exception);
            return Optional.empty();
        }
    }

    private static Optional<JsonObject> read(
            ResourceManager resourceManager,
            WeaponDefinition weapon,
            ResourceLocation location) {
        Optional<Resource> resource = resourceManager.getResource(location);
        if (resource.isEmpty()) {
            LOGGER.error("First-person resource {} is missing; {} uses the vanilla hold", location, weapon.item());
            return Optional.empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception exception) {
            LOGGER.error("Unreadable first-person resource {}; {} uses the vanilla hold",
                    location, weapon.item(), exception);
            return Optional.empty();
        }
    }

    /** One weapon's loaded first-person presentation: its entry, its style, and its parsed rig. */
    record WeaponRig(WeaponDefinition weapon, CombatStyleDefinition style, FirstPersonSwing swing) {
        WeaponRig {
            Objects.requireNonNull(weapon, "weapon");
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(swing, "swing");
        }
    }
}
