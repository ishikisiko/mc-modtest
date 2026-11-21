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
    private static Map<Integer, WeaponRig> companionRigs = Map.of();
    private static final ResourceLocation COMPANION = ResourceLocation.parse("yuanxian:primordial_companion_sword");
    private static final java.util.List<String> COMPANION_SHAPES = java.util.List.of("foundation", "golden_core", "nascent_soul", "spirit_transformation");

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
        var mark = com.example.myvillage.item.CompanionSwordItem.mark(stack);
        if (mark != null && companionRigs.containsKey(mark.form())) return Optional.of(companionRigs.get(mark.form()));
        return forItem(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static Optional<WeaponRig> forHeld(Player player) {
        return forStack(player.getMainHandItem());
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        AssetReader reader = (weapon, location) -> read(resourceManager, weapon, location);
        rigs = loadAll(CombatStyles.bundled(), reader);
        var base = rigs.get(COMPANION);
        if (base == null) {
            companionRigs = Map.of();
            return;
        }
        Map<Integer, WeaponRig> forms = new HashMap<>();
        for (int index = 0; index < COMPANION_SHAPES.size(); index++) {
            int form = index + 1;
            var geometry = ResourceLocation.parse("yuanxian:combat/primordial_companion_sword_"
                    + COMPANION_SHAPES.get(index) + "_geometry.json");
            load(reader, base.weapon(), base.style(), geometry).ifPresent(rig -> forms.put(form, rig));
        }
        companionRigs = Map.copyOf(forms);
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
        return load(reader, weapon, style, weapon.geometry());
    }

    private static Optional<WeaponRig> load(AssetReader reader, WeaponDefinition weapon, CombatStyleDefinition style,
            ResourceLocation geometryLocation) {
        ResourceLocation rigLocation = weapon.firstPersonRig();
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
