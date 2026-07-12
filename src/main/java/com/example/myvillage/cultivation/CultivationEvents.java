package com.example.myvillage.cultivation;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.network.CultivationSnapshotPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CultivationEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(CultivationEvents.class);

    private CultivationEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onPlayerChangedDimension);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ModCultivationRegistries.RegistrySummary summary =
                ModCultivationRegistries.validateRequiredEntries(event.getServer().registryAccess());
        LOGGER.info("Cultivation definition registries loaded: {}", summary);
        CultivationAttachments.PROFILE.get();
        LOGGER.info(
                "Cultivation runtime registered: attachment={}, snapshot={}",
                CultivationAttachments.PROFILE.getId(),
                CultivationSnapshotPayload.TYPE.id());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event);
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event);
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event);
    }

    private static void sync(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CultivationService.syncToClient(player);
        }
    }
}
