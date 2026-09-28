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
 * Loads the first-person swing rig on every client resource reload, so F3+T picks up edits.
 * A missing or invalid file leaves Qingfeng on the ordinary vanilla held-item pose.
 */
final class FirstPersonSwingResources implements ResourceManagerReloadListener {
    static final FirstPersonSwingResources INSTANCE = new FirstPersonSwingResources();
    static final ResourceLocation LOCATION =
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, FirstPersonSwing.RESOURCE_PATH);

    private static final Logger LOGGER = LoggerFactory.getLogger(FirstPersonSwingResources.class);
    private static FirstPersonSwing current;

    private FirstPersonSwingResources() {
    }

    static Optional<FirstPersonSwing> current() {
        return Optional.ofNullable(current);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Optional<Resource> resource = resourceManager.getResource(LOCATION);
        if (resource.isEmpty()) {
            current = null;
            LOGGER.error("First-person swing rig {} is missing; Qingfeng uses the vanilla hold", LOCATION);
            return;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            current = FirstPersonSwing.parse(json, BasicSwordStyle.DEFINITION);
            LOGGER.info("Loaded first-person swing rig {} ({} moves)", LOCATION, current.moves().size());
        } catch (Exception exception) {
            current = null;
            LOGGER.error("Invalid first-person swing rig {}; Qingfeng uses the vanilla hold", LOCATION, exception);
        }
    }
}
