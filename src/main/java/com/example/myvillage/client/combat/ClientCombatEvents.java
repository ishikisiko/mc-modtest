package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.combat.network.CombatAttackReceiver;
import com.example.myvillage.combat.network.CombatAttackStartPayload;
import com.example.myvillage.combat.network.CombatAttackStopPayload;
import com.example.myvillage.combat.network.CombatModeSnapshotPayload;
import com.example.myvillage.combat.network.CombatModeSnapshotReceiver;
import com.example.myvillage.combat.network.CombatModeTogglePayload;
import com.example.myvillage.combat.network.SwordAttackIntentPayload;
import com.example.myvillage.combat.session.CombatStopReason;
import com.example.myvillage.client.cultivation.ClientCultivationState;
import com.example.myvillage.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class ClientCombatEvents {
    private static final int CLIENT_INTENT_INTERVAL_TICKS = 2;
    private static final int PREDICTION_TIMEOUT_TICKS = 8;
    private static long lastAttackIntentTick = Long.MIN_VALUE;

    static {
        CombatModeSnapshotReceiver.install(ClientCombatEvents::receiveModeSnapshot);
        CombatAttackReceiver.install(
                ClientCombatEvents::receiveAttackStart,
                ClientCombatEvents::receiveAttackStop);
    }

    private ClientCombatEvents() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        while (ClientCombatKeyMappings.TOGGLE_COMBAT_MODE.consumeClick()) {
            if (player != null && player.isAlive() && minecraft.screen == null) {
                PacketDistributor.sendToServer(CombatModeTogglePayload.INSTANCE);
            }
        }
        if (player == null || minecraft.level == null) {
            return;
        }

        long tick = minecraft.level.getGameTime();
        if (ClientCombatState.predictionPending()
                && tick - ClientCombatState.predictionTick() > PREDICTION_TIMEOUT_TICKS) {
            CombatAnimationController.stop(player);
            ClientCombatState.rejectPrediction();
        }

        boolean shouldReady = ClientCombatState.mode() == CombatMode.CULTIVATION
                && player.getMainHandItem().is(ModItems.QINGFENG_SWORD.get())
                && player.isAlive()
                && !ClientCombatState.predictionPending()
                && !ClientCombatState.localActionActive()
                && ClientCultivationState.meditation()
                .map(status -> !status.state().active())
                .orElse(true);
        if (shouldReady
                && !ClientCombatState.readyAnimation()
                && !CombatAnimationController.isActive(player)) {
            if (CombatAnimationController.transition(player, BasicSwordStyle.READY_IDLE_ANIMATION)) {
                ClientCombatState.markReadyAnimation();
            }
        } else if (!shouldReady && ClientCombatState.readyAnimation()) {
            CombatAnimationController.stop(player);
            ClientCombatState.clearActionAnimation();
        }
    }

    @SubscribeEvent
    static void onAttackInput(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!event.isAttack()
                || event.getHand() != InteractionHand.MAIN_HAND
                || player == null
                || !player.isAlive()
                || minecraft.screen != null
                || ClientCombatState.mode() != CombatMode.CULTIVATION
                || !player.getMainHandItem().is(ModItems.QINGFENG_SWORD.get())) {
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(false);
        if (minecraft.level == null) {
            return;
        }
        long tick = minecraft.level.getGameTime();
        if (lastAttackIntentTick != Long.MIN_VALUE
                && tick - lastAttackIntentTick < CLIENT_INTENT_INTERVAL_TICKS) {
            return;
        }
        lastAttackIntentTick = tick;
        PacketDistributor.sendToServer(SwordAttackIntentPayload.INSTANCE);

        if (ClientCombatState.localActionActive()) {
            return;
        }
        player.swing(InteractionHand.MAIN_HAND, false);
        int predictedIndex = ClientCombatState.preparePrediction(
                tick, BasicSwordStyle.DEFINITION.comboTimeoutTicks());
        CombatAnimationController.play(
                player,
                BasicSwordStyle.DEFINITION.move(predictedIndex).animation().animationId(),
                0.0F);
        ClientCombatState.beginPrediction(tick);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (Minecraft.getInstance().player != null) {
            CombatAnimationController.stop(Minecraft.getInstance().player);
        }
        ClientCombatState.clear();
        lastAttackIntentTick = Long.MIN_VALUE;
    }

    @SubscribeEvent
    static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        CombatAnimationController.stop(event.getOldPlayer());
        ClientCombatState.clearActionAnimation();
        lastAttackIntentTick = Long.MIN_VALUE;
    }

    private static void receiveModeSnapshot(CombatModeSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean changed = ClientCombatState.replaceMode(payload.mode(), payload.revision());
        if (!changed || player == null) {
            return;
        }
        if (payload.mode() == CombatMode.CULTIVATION) {
            CombatAnimationController.play(
                    player, CombatAnimationController.SMOKE_ANIMATION, 0.0F);
        } else {
            CombatAnimationController.stop(player);
        }
    }

    private static void receiveAttackStart(CombatAttackStartPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !ClientCombatState.acceptActionRevision(
                payload.attackerEntityId(), payload.revision())) {
            return;
        }
        int moveIndex = BasicSwordStyle.DEFINITION.indexOf(payload.moveId());
        Entity entity = minecraft.level.getEntity(payload.attackerEntityId());
        if (moveIndex < 0 || !(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        boolean localPredictionPending = player == minecraft.player
                && ClientCombatState.predictionPending();
        long elapsed = Math.max(0L, minecraft.level.getGameTime() - payload.serverStartTick());
        CombatAnimationController.play(player, payload.moveId(), (float) elapsed);
        if (player == minecraft.player) {
            if (!localPredictionPending) {
                player.swing(InteractionHand.MAIN_HAND, false);
            }
            ClientCombatState.confirmPrediction(
                    (moveIndex + 1) % BasicSwordStyle.DEFINITION.moves().size());
        }
    }

    private static void receiveAttackStop(CombatAttackStopPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(payload.attackerEntityId());
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        if (payload.reason() == CombatStopReason.REJECTED && player == minecraft.player) {
            if (ClientCombatState.predictionPending()) {
                CombatAnimationController.stop(player);
                ClientCombatState.rejectPrediction();
            }
            return;
        }
        if (!ClientCombatState.acceptActionRevision(payload.attackerEntityId(), payload.revision())) {
            return;
        }
        CombatAnimationController.stop(player);
        if (resetsServerSession(payload.reason())) {
            ClientCombatState.resetActionRevision(payload.attackerEntityId());
        }
        if (player == minecraft.player) {
            if (payload.reason() == CombatStopReason.COMPLETED) {
                ClientCombatState.completeAction(minecraft.level.getGameTime());
            } else {
                ClientCombatState.clearActionAnimation();
            }
        }
    }

    private static boolean resetsServerSession(CombatStopReason reason) {
        return reason == CombatStopReason.DEATH
                || reason == CombatStopReason.LOGOUT
                || reason == CombatStopReason.DIMENSION_CHANGED
                || reason == CombatStopReason.SERVER_STOPPING;
    }
}
