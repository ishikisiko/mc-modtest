package com.example.myvillage.cultivation;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import com.example.myvillage.cultivation.network.CultivationSnapshotPayload;
import com.example.myvillage.cultivation.time.CultivationTimeRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
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
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onLevelSave);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onLivingJump);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onUseItem);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onEntityMount);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onPlayerChangeGameMode);
        NeoForge.EVENT_BUS.addListener(CultivationEvents::onDatapackSync);
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
        CultivationTimeRuntime.onServerStarted(event.getServer());
        MeditationManager.onServerStarted();
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CultivationService.syncToClient(player);
            CultivationTimeRuntime.onPlayerLoggedIn(player);
            MeditationManager.notifyStatus(player);
        }
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CultivationTimeRuntime.onPlayerRespawn(player);
            CultivationService.syncToClient(player);
            MeditationManager.notifyStatus(player);
        }
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MeditationManager.requestStop(player, MeditationStopReason.DIMENSION_CHANGED);
            CultivationTimeRuntime.onPlayerChangedDimension(player);
            CultivationService.syncToClient(player);
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MeditationManager.onPlayerLoggedOut(player);
            CultivationTimeRuntime.onPlayerLoggedOut(player);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        CultivationTimeRuntime.tick(event.getServer());
        MeditationManager.tick(event.getServer());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        MeditationManager.onServerStopping(event.getServer());
        CultivationTimeRuntime.onServerStopping(event.getServer());
    }

    private static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(Level.OVERWORLD)) {
            CultivationTimeRuntime.onServerSave(level.getServer());
        }
    }

    private static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MeditationManager.requestStop(player, MeditationStopReason.JUMPED);
        }
    }

    private static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0) {
            MeditationManager.recordPositiveDamage(player);
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MeditationManager.requestStop(player, MeditationStopReason.DIED);
            CultivationTimeRuntime.onPlayerDeath(player);
        }
    }

    private static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MeditationManager.requestStop(player, MeditationStopReason.ATTACKED);
        }
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            MeditationManager.requestStop(player, MeditationStopReason.MINING);
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        stopForInteraction(event);
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        stopForInteraction(event);
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        stopForInteraction(event);
    }

    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        stopForInteraction(event);
    }

    private static void stopForInteraction(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MeditationManager.requestStop(player, MeditationStopReason.INTERACTED);
        }
    }

    private static void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MeditationManager.requestStop(player, MeditationStopReason.INTERACTED);
        }
    }

    private static void onEntityMount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof ServerPlayer player) {
            MeditationManager.requestStop(player, MeditationStopReason.MOUNTED);
        }
    }

    private static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MeditationManager.requestStop(player, MeditationStopReason.GAME_MODE_CHANGED);
        }
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            MeditationManager.cancelAllAdministrative(
                    event.getPlayerList().getServer(),
                    MeditationStopReason.DEFINITION_RELOADED);
        }
    }
}
