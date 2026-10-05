package com.example.myvillage.entity.beast;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** The bundled beast data read straight from the source tree, for unit tests. */
final class BeastTestData {
    static final Path RESOURCES = Path.of("src/main/resources");
    static final ResourceLocation DEMON_WOLF = ResourceLocation.fromNamespaceAndPath("myvillage", "demon_wolf");
    static final ResourceLocation BITE = ResourceLocation.fromNamespaceAndPath("myvillage", "demon_wolf_bite");
    static final ResourceLocation POUNCE = ResourceLocation.fromNamespaceAndPath("myvillage", "demon_wolf_pounce");

    private BeastTestData() {
    }

    static List<BeastDefinition> load() {
        return BeastDataLoader.load(BeastTestData::open);
    }

    static BeastDefinition demonWolf() {
        return new BeastDefinitions(load()).require(DEMON_WOLF);
    }

    static BeastMoveDefinition bite() {
        return demonWolf().move(BITE).orElseThrow();
    }

    static BeastMoveDefinition pounce() {
        return demonWolf().move(POUNCE).orElseThrow();
    }

    static InputStream open(String path) throws IOException {
        Path file = RESOURCES.resolve(path);
        return Files.exists(file) ? Files.newInputStream(file) : null;
    }
}
