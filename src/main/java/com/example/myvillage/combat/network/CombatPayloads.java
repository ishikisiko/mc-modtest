package com.example.myvillage.combat.network;

import com.example.myvillage.combat.CombatService;
import com.example.myvillage.combat.runtime.CombatDodgeService;
import com.example.myvillage.combat.session.CombatSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CombatPayloads {
    private CombatPayloads() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                CombatModeTogglePayload.TYPE,
                CombatModeTogglePayload.STREAM_CODEC,
                CombatPayloads::handleModeToggle);
        registrar.playToServer(
                SwordAttackIntentPayload.TYPE,
                SwordAttackIntentPayload.STREAM_CODEC,
                CombatPayloads::handleAttackIntent);
        registrar.playToClient(
                CombatModeSnapshotPayload.TYPE,
                CombatModeSnapshotPayload.STREAM_CODEC,
                CombatPayloads::handleModeSnapshot);
        registrar.playToClient(
                CombatAttackStartPayload.TYPE,
                CombatAttackStartPayload.STREAM_CODEC,
                CombatPayloads::handleAttackStart);
        registrar.playToClient(
                CombatAttackStopPayload.TYPE,
                CombatAttackStopPayload.STREAM_CODEC,
                CombatPayloads::handleAttackStop);
        registrar.playToClient(
                CombatHitConfirmPayload.TYPE,
                CombatHitConfirmPayload.STREAM_CODEC,
                CombatPayloads::handleHitConfirm);
        registrar.playToClient(
                CombatImpactPayload.TYPE,
                CombatImpactPayload.STREAM_CODEC,
                CombatPayloads::handleImpact);
        registrar.playToServer(
                CombatDodgeIntentPayload.TYPE,
                CombatDodgeIntentPayload.STREAM_CODEC,
                CombatPayloads::handleDodgeIntent);
        registrar.playToClient(
                CombatDodgeStartPayload.TYPE,
                CombatDodgeStartPayload.STREAM_CODEC,
                CombatPayloads::handleDodgeStart);
    }

    private static void handleDodgeIntent(
            CombatDodgeIntentPayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> CombatDodgeService.handleIntent(player, payload.direction()));
        }
    }

    private static void handleDodgeStart(
            CombatDodgeStartPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> CombatDodgeReceiver.receiveStart(payload));
    }

    private static void handleModeToggle(
            CombatModeTogglePayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> CombatService.toggleMode(player));
        }
    }

    private static void handleAttackIntent(
            SwordAttackIntentPayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> CombatSessionManager.handleAttackIntent(player));
        }
    }

    private static void handleModeSnapshot(
            CombatModeSnapshotPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> CombatModeSnapshotReceiver.receive(payload));
    }

    private static void handleAttackStart(
            CombatAttackStartPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> CombatAttackReceiver.receiveStart(payload));
    }

    private static void handleAttackStop(
            CombatAttackStopPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> CombatAttackReceiver.receiveStop(payload));
    }

    private static void handleHitConfirm(
            CombatHitConfirmPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> CombatAttackReceiver.receiveHit(payload));
    }

    private static void handleImpact(
            CombatImpactPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> CombatAttackReceiver.receiveImpact(payload));
    }
}
