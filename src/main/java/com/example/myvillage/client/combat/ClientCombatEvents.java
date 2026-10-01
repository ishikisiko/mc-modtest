package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatStyleDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.network.CombatAttackReceiver;
import com.example.myvillage.combat.network.CombatAttackStartPayload;
import com.example.myvillage.combat.network.CombatAttackStopPayload;
import com.example.myvillage.combat.network.CombatHitConfirmPayload;
import com.example.myvillage.combat.network.CombatImpactPayload;
import com.example.myvillage.combat.network.CombatModeSnapshotPayload;
import com.example.myvillage.combat.network.CombatModeSnapshotReceiver;
import com.example.myvillage.combat.network.CombatModeTogglePayload;
import com.example.myvillage.combat.network.SwordAttackIntentPayload;
import com.example.myvillage.combat.session.CombatStopReason;
import com.example.myvillage.client.cultivation.ClientCultivationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class ClientCombatEvents {
    private static final int CLIENT_INTENT_INTERVAL_TICKS = 2;
    private static final int PREDICTION_TIMEOUT_TICKS = 8;
    /**
     * After the server ends the move a chained prediction left, its START for the chained move
     * is sent in the same server tick; allow this many client ticks before giving up on it.
     */
    private static final int CHAIN_CONFIRM_GRACE_TICKS = 2;
    private static long lastAttackIntentTick = Long.MIN_VALUE;

    static {
        CombatModeSnapshotReceiver.install(ClientCombatEvents::receiveModeSnapshot);
        CombatAttackReceiver.install(
                ClientCombatEvents::receiveAttackStart,
                ClientCombatEvents::receiveAttackStop,
                ClientCombatEvents::receiveHitConfirm,
                ClientCombatEvents::receiveImpact);
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

        FirstPersonWeaponAnimator.clientTick(player);
        long tick = minecraft.level.getGameTime();
        if (ClientCombatState.chainPredictionAbandoned(tick, CHAIN_CONFIRM_GRACE_TICKS)
                || (ClientCombatState.predictionPending()
                && tick - ClientCombatState.predictionTick() > PREDICTION_TIMEOUT_TICKS)) {
            CombatAnimationController.stop(player);
            CombatWorldTrails.stop(player.getId());
            ClientCombatState.rejectPrediction();
        }
        predictChainedMove(player, tick);
        playLocalCues(tick);
        if (CombatAnimationController.thirdPersonProbeHeld(player)) {
            // A development freeze probe owns the layer until released, stopped, or replaced.
            return;
        }

        Optional<CombatStyleDefinition> heldStyle = CombatStyles.bundled().styleFor(player.getMainHandItem());
        boolean shouldReady = ClientCombatState.mode() == CombatMode.CULTIVATION
                && heldStyle.isPresent()
                && player.isAlive()
                && !ClientCombatState.predictionPending()
                && !ClientCombatState.localActionActive()
                && ClientCultivationState.meditation()
                .map(status -> !status.state().active())
                .orElse(true);
        if (shouldReady
                && !ClientCombatState.readyAnimation()
                && !CombatAnimationController.isActive(player)) {
            ResourceLocation readyIdle = heldStyle.get().readyIdleAnimation();
            if (CombatAnimationController.transition(player, readyIdle)) {
                ClientCombatState.markReadyAnimation(readyIdle);
            }
        } else if (shouldReady
                && ClientCombatState.readyAnimation()
                && !heldStyle.get().readyIdleAnimation().equals(ClientCombatState.readyIdleAnimation())) {
            // A weapon of another style took over the guard: switch to its own ready idle.
            ResourceLocation readyIdle = heldStyle.get().readyIdleAnimation();
            if (CombatAnimationController.transition(player, readyIdle)) {
                ClientCombatState.markReadyAnimation(readyIdle);
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
                || ClientCombatState.mode() != CombatMode.CULTIVATION) {
            return;
        }
        Optional<CombatStyleDefinition> heldStyle = CombatStyles.bundled().styleFor(player.getMainHandItem());
        if (heldStyle.isEmpty()) {
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

        if (ClientCombatState.localActionActive() || ClientCombatState.predictionPending()) {
            // The server holds one click from the move's bufferStartTick; remember it here too so
            // the chained move can be predicted at the chain tick instead of waiting a round trip.
            CombatStyleDefinition localStyle = ClientCombatState.localStyle();
            if (localStyle != null) {
                ClientCombatState.bufferClick(tick, localStyle);
            }
            return;
        }
        player.swing(InteractionHand.MAIN_HAND, false);
        player.setSprinting(false);
        CombatStyleDefinition style = heldStyle.get();
        int predictedIndex = ClientCombatState.preparePrediction(tick, style);
        AttackMoveDefinition move = style.move(predictedIndex);
        CombatAnimationController.play(player, move.animation().animationId(), 0.0F);
        CombatWorldTrails.start(player, move, 0.0F, player.getYRot());
        ClientCombatState.beginPrediction(tick);
        ClientCombatState.trackLocalAction(style, predictedIndex, tick, -1L);
    }

    /**
     * Presentation-only prediction of a chained move (the server decides it the same way): the
     * local action holds a buffered click and reached its chain tick, so the next move starts
     * now on this screen. The server's STOP for the old move and START for the new one then
     * confirm it like any other prediction, or a missing START drops it.
     */
    private static void predictChainedMove(LocalPlayer player, long tick) {
        CombatStyleDefinition style = ClientCombatState.localStyle();
        if (ClientCombatState.mode() != CombatMode.CULTIVATION
                || style == null
                || !CombatStyles.bundled().styleFor(player.getMainHandItem()).filter(style::equals).isPresent()
                || !player.isAlive()) {
            return;
        }
        int nextIndex = ClientCombatState.chainDue(tick, style);
        if (nextIndex < 0) {
            return;
        }
        player.swing(InteractionHand.MAIN_HAND, false);
        player.setSprinting(false);
        AttackMoveDefinition move = style.move(nextIndex);
        CombatAnimationController.play(player, move.animation().animationId(), 0.0F);
        CombatWorldTrails.start(player, move, 0.0F, player.getYRot());
        ClientCombatState.beginChainPrediction(tick, nextIndex);
    }

    /** Camera cues on the local action's timeline: a lean as the blade starts, the step surge. */
    private static void playLocalCues(long tick) {
        Optional<AttackMoveDefinition> local = ClientCombatState.localMove();
        if (local.isEmpty()) {
            return;
        }
        AttackMoveDefinition move = local.get();
        if (ClientCombatState.reachCue(tick, Math.max(0, move.activeStartTick() - 1))) {
            CombatCameraFx.swingLean(move.camera().swingLeanDegrees());
        }
        move.step()
                .filter(step -> move.camera().stepFovSurge() > 0.0F)
                .filter(step -> ClientCombatState.reachCue(tick, step.actionTick()))
                .ifPresent(step -> CombatCameraFx.stepSurge(move.camera().stepFovSurge()));
        ClientCombatState.markCuesThrough(tick);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (Minecraft.getInstance().player != null) {
            CombatAnimationController.stop(Minecraft.getInstance().player);
        }
        ClientCombatState.clear();
        CombatWorldTrails.clear();
        CombatImpactFx.clear();
        CombatCameraFx.clear();
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
                    player, CombatAnimationController.modeEnterAnimation(player), 0.0F);
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
        Optional<CombatStyles.MoveRef> started = CombatStyles.bundled().move(payload.moveId());
        Entity entity = minecraft.level.getEntity(payload.attackerEntityId());
        if (started.isEmpty() || !(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        CombatStyleDefinition style = started.get().style();
        int moveIndex = started.get().index();
        boolean localPredictionPending = player == minecraft.player
                && ClientCombatState.predictionPending();
        long elapsed = Math.max(0L, minecraft.level.getGameTime() - payload.serverStartTick());
        CombatAnimationController.play(player, payload.moveId(), (float) elapsed);
        CombatWorldTrails.start(player, started.get().move(), (float) elapsed, payload.facingYaw());
        if (player == minecraft.player) {
            if (!localPredictionPending) {
                player.swing(InteractionHand.MAIN_HAND, false);
                player.setSprinting(false);
            }
            ClientCombatState.trackLocalAction(style, moveIndex, payload.serverStartTick(), payload.revision());
            ClientCombatState.confirmPrediction((moveIndex + 1) % style.moves().size());
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
            // A rejection during a chained prediction may answer an extra click rather than the
            // buffered one; the chained move is dropped only if the server's START never comes.
            if (ClientCombatState.predictionPending() && !ClientCombatState.chainPredictionPending()) {
                CombatAnimationController.stop(player);
                CombatWorldTrails.stop(player.getId());
                ClientCombatState.rejectPrediction();
            }
            return;
        }
        if (!ClientCombatState.acceptActionRevision(payload.attackerEntityId(), payload.revision())) {
            return;
        }
        if (player == minecraft.player
                && payload.reason() == CombatStopReason.COMPLETED
                && ClientCombatState.absorbChainSourceStop(payload.revision(), minecraft.level.getGameTime())) {
            // The end of the move this client already chained out of: keep the chained move.
            return;
        }
        CombatAnimationController.stop(player);
        if (payload.reason() != CombatStopReason.COMPLETED) {
            CombatWorldTrails.stop(player.getId());
        }
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

    private static void receiveHitConfirm(CombatHitConfirmPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null && payload.attackerEntityId() == player.getId()) {
            FirstPersonWeaponAnimator.confirmHit(player);
            CombatCameraFx.onHitConfirm(
                    ClientCombatState.localMoveFor(payload.revision()), payload.revision());
        }
    }

    /**
     * Target hit-stop, shudder and the remote attacker's animation stop. The local attacker's
     * own camera trauma comes from {@link #receiveHitConfirm} only.
     */
    private static void receiveImpact(CombatImpactPayload payload) {
        CombatImpactFx.receive(payload);
    }

    private static boolean resetsServerSession(CombatStopReason reason) {
        return reason == CombatStopReason.DEATH
                || reason == CombatStopReason.LOGOUT
                || reason == CombatStopReason.DIMENSION_CHANGED
                || reason == CombatStopReason.SERVER_STOPPING;
    }
}
