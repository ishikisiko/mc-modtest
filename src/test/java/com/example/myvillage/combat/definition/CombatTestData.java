package com.example.myvillage.combat.definition;

import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** The bundled combat data read straight from the source tree, for unit tests. */
public final class CombatTestData {
    public static final Path RESOURCES = Path.of("src/main/resources");
    public static final ResourceLocation BASIC_SWORD = ResourceLocation.fromNamespaceAndPath("myvillage", "basic_sword");
    public static final ResourceLocation QINGFENG_SWORD = ResourceLocation.fromNamespaceAndPath("myvillage", "qingfeng_sword");

    private static CombatStyles styles;

    private CombatTestData() {
    }

    public static synchronized CombatStyles styles() {
        if (styles == null) {
            styles = CombatDataLoader.load(CombatTestData::open);
        }
        return styles;
    }

    public static CombatStyleDefinition basicSword() {
        return styles().style(BASIC_SWORD).orElseThrow();
    }

    public static WeaponDefinition qingfeng() {
        return styles().weapon(QINGFENG_SWORD).orElseThrow();
    }

    /** Source path of a client asset location such as a weapon's rig or geometry. */
    public static Path assetPath(ResourceLocation location) {
        return RESOURCES.resolve("assets/" + location.getNamespace() + "/" + location.getPath());
    }

    public static InputStream open(String path) throws IOException {
        Path file = RESOURCES.resolve(path);
        return Files.exists(file) ? Files.newInputStream(file) : null;
    }
}
