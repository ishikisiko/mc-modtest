package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.Optional;

/**
 * Loads the first-person swing rig and the sword geometry contract on every client resource
 * reload, so F3+T picks up edits to either. A missing or invalid file leaves Qingfeng on the
 * ordinary vanilla held-item pose (no rig arm, no custom grip).
 */
final class FirstPersonSwingResources implements ResourceManagerReloadListener {
    static final FirstPersonSwingResources INSTANCE = new FirstPersonSwingResources();
    static final ResourceLocation LOCATION =
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, FirstPersonSwing.RESOURCE_PATH);
    static final ResourceLocation GEOMETRY_LOCATION =
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, SwordGeometry.RESOURCE_PATH);

    private static final Logger LOGGER = LoggerFactory.getLogger(FirstPersonSwingResources.class);
    private static FirstPersonSwing current;

    private FirstPersonSwingResources() {
    }

    static Optional<FirstPersonSwing> current() {
        return Optional.ofNullable(current);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        current = null;
        Optional<JsonObject> geometryJson = read(resourceManager, GEOMETRY_LOCATION);
        Optional<JsonObject> rigJson = read(resourceManager, LOCATION);
        if (geometryJson.isEmpty() || rigJson.isEmpty()) {
            return;
        }
        SwordGeometry geometry;
        try {
            geometry = SwordGeometry.parse(geometryJson.get());
        } catch (RuntimeException exception) {
            LOGGER.error("Invalid sword geometry {}; Qingfeng uses the vanilla hold", GEOMETRY_LOCATION, exception);
            return;
        }
        try {
            current = FirstPersonSwing.parse(rigJson.get(), BasicSwordStyle.DEFINITION, geometry);
            LOGGER.info("Loaded first-person swing rig {} ({} moves) with sword geometry {}",
                    LOCATION, current.moves().size(), GEOMETRY_LOCATION);
        } catch (RuntimeException exception) {
            LOGGER.error("Invalid first-person swing rig {}; Qingfeng uses the vanilla hold", LOCATION, exception);
        }
    }

    private static Optional<JsonObject> read(ResourceManager resourceManager, ResourceLocation location) {
        Optional<Resource> resource = resourceManager.getResource(location);
        if (resource.isEmpty()) {
            LOGGER.error("First-person resource {} is missing; Qingfeng uses the vanilla hold", location);
            return Optional.empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception exception) {
            LOGGER.error("Unreadable first-person resource {}; Qingfeng uses the vanilla hold", location, exception);
            return Optional.empty();
        }
    }
}
