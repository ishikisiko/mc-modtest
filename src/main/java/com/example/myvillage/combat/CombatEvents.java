package com.example.myvillage.combat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.runtime.CombatReactionService;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.combat.session.CombatStopReason;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CombatEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(CombatEvents.class);

    private CombatEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CombatEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onEntityMount);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onLivingKnockBack);
        NeoForge.EVENT_BUS.addListener(CombatReactionService::onEntityTickPre);
        NeoForge.EVENT_BUS.addListener(CombatReactionService::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onEntityLeaveLevel);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        CombatAttachments.PREFERENCE.get();
        CombatService.clearRuntime();
        CombatSessionManager.clearAll(event.getServer(), CombatStopReason.SERVER_STOPPING);
        CombatReactionService.clearAll();
        LOGGER.info(
                "Combat foundation registered: attachment={}, style={}, moves={}",
                CombatAttachments.PREFERENCE.getId(),
                com.example.myvillage.combat.definition.BasicSwordStyle.DEFINITION.id(),
                com.example.myvillage.combat.definition.BasicSwordStyle.DEFINITION.moves().size());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CombatService.syncToClient(player);
        }
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CombatSessionManager.removeRuntime(player.getUUID(), true);
            CombatSessionManager.clearCommitment(player);
            CombatReactionService.clear(player);
            CombatService.syncToClient(player);
        }
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CombatSessionManager.interrupt(player, CombatStopReason.DIMENSION_CHANGED, false);
            CombatSessionManager.removeRuntime(player.getUUID(), true);
            CombatService.syncToClient(player);
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CombatSessionManager.interrupt(player, CombatStopReason.LOGOUT, false);
            CombatSessionManager.removeRuntime(player.getUUID(), true);
            CombatService.onPlayerRemoved(player.getUUID());
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        CombatReactionService.clear(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            CombatSessionManager.interrupt(player, CombatStopReason.DEATH, false);
            CombatSessionManager.removeRuntime(player.getUUID(), true);
        }
    }

    private static void onEntityMount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof ServerPlayer player) {
            CombatSessionManager.interrupt(player, CombatStopReason.MOUNTED, true);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        CombatSessionManager.tick(event.getServer());
        CombatReactionService.tick();
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        CombatSessionManager.clearAll(event.getServer(), CombatStopReason.SERVER_STOPPING);
        CombatReactionService.clearAll();
        CombatService.clearRuntime();
    }

    /** Our hits replace vanilla's hurt knockback with the move's own reaction impulse. */
    private static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (CombatDamageService.isResolvingHit(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Unload, removal and dimension change all leave the level; drop any freeze or stun. */
    private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            CombatReactionService.clear(event.getEntity());
        }
    }
}
