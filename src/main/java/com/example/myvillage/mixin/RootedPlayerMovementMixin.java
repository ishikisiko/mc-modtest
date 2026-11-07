package com.example.myvillage.mixin;

import com.example.myvillage.combat.foundation.ControlService;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Constrain rooted horizontal intent after the normal thread/invalid-coordinate checks. */
@Mixin(value = ServerGamePacketListenerImpl.class, remap = false)
public abstract class RootedPlayerMovementMixin {
    @Shadow public ServerPlayer player;
    @Unique private net.minecraft.world.phys.Vec3 myvillage$emberTarget;

    // Ordinal zero belongs to containsInvalidValues: leave that security check unchanged.
    @Redirect(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;getX(D)D",
                    ordinal = 1, remap = false), require = 1, allow = 1, remap = false)
    private double myvillage$rootedX(ServerboundMovePlayerPacket packet, double fallback) {
        double x=ControlService.constrainPlayerX(player, packet.getX(fallback));
        myvillage$emberTarget=com.example.myvillage.cultivation.fate.PhoenixRuntime.constrainMovement(player,
                new net.minecraft.world.phys.Vec3(x,packet.getY(player.getY()),ControlService.constrainPlayerZ(player,packet.getZ(player.getZ()))));
        return myvillage$emberTarget.x;
    }

    @Redirect(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;getZ(D)D",
                    ordinal = 1, remap = false), require = 1, allow = 1, remap = false)
    private double myvillage$rootedZ(ServerboundMovePlayerPacket packet, double fallback) {
        if(com.example.myvillage.cultivation.fate.PhoenixRuntime.active(player)&&myvillage$emberTarget!=null)return myvillage$emberTarget.z;
        return ControlService.constrainPlayerZ(player, packet.getZ(fallback));
    }

    @Redirect(method="handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;getY(D)D",ordinal=1,remap=false),require=1,allow=1,remap=false)
    private double myvillage$emberY(ServerboundMovePlayerPacket packet,double fallback) {
        if(com.example.myvillage.cultivation.fate.PhoenixRuntime.active(player)&&myvillage$emberTarget!=null)return myvillage$emberTarget.y;
        return packet.getY(fallback);
    }
}
