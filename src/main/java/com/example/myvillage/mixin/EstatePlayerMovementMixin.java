package com.example.myvillage.mixin;

import com.example.myvillage.estate.EstateRuntime;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ServerGamePacketListenerImpl.class,remap=false)
public abstract class EstatePlayerMovementMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="teleport(DDDFFLjava/util/Set;)V",at=@At("HEAD"),cancellable=true)
    private void myvillage$estateConnectionTeleport(double x,double y,double z,float yaw,float pitch,
            java.util.Set<net.minecraft.world.entity.RelativeMovement> relative,CallbackInfo ci) {
        if(EstateRuntime.blocked(player,player.serverLevel(),x,z))ci.cancel();
    }
    @Inject(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",shift=At.Shift.AFTER),cancellable=true)
    private void myvillage$estateMove(ServerboundMovePlayerPacket packet,CallbackInfo ci) {
        double x=packet.getX(player.getX()),z=packet.getZ(player.getZ());
        if(Double.isFinite(x)&&Double.isFinite(z)&&EstateRuntime.movement(player,x,z))ci.cancel();
    }
    @Inject(method="handleMoveVehicle",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",shift=At.Shift.AFTER),cancellable=true)
    private void myvillage$estateVehicle(ServerboundMoveVehiclePacket packet,CallbackInfo ci) {
        if(Double.isFinite(packet.getX())&&Double.isFinite(packet.getZ())&&EstateRuntime.movement(player,packet.getX(),packet.getZ()))ci.cancel();
    }
}
