package com.example.myvillage.mixin;

import com.example.myvillage.combat.foundation.ControlService;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Constrain rooted horizontal intent after the normal thread/invalid-coordinate checks. */
@Mixin(value = ServerGamePacketListenerImpl.class, remap = false)
public abstract class RootedPlayerMovementMixin {
    @Shadow public ServerPlayer player;

    // Ordinal zero belongs to containsInvalidValues: leave that security check unchanged.
    @Redirect(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;getX(D)D",
                    ordinal = 1, remap = false), require = 1, allow = 1, remap = false)
    private double myvillage$rootedX(ServerboundMovePlayerPacket packet, double fallback) {
        return ControlService.constrainPlayerX(player, packet.getX(fallback));
    }

    @Redirect(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;getZ(D)D",
                    ordinal = 1, remap = false), require = 1, allow = 1, remap = false)
    private double myvillage$rootedZ(ServerboundMovePlayerPacket packet, double fallback) {
        return ControlService.constrainPlayerZ(player, packet.getZ(fallback));
    }
}
