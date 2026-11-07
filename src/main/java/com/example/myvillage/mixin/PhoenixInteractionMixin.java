package com.example.myvillage.mixin;

import com.example.myvillage.cultivation.fate.PhoenixRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 先经过原版线程检查，才拒绝余烬中的背包修改、丢物和换手。 */
@Mixin(value=ServerGamePacketListenerImpl.class,remap=false)
public abstract class PhoenixInteractionMixin {
    @Shadow public ServerPlayer player;
    @Inject(method={"handleContainerClick","handlePlayerAction","handleSetCarriedItem","handlePickItem","handlePlaceRecipe",
            "handleContainerButtonClick","handleSetCreativeModeSlot","handlePlayerCommand","handlePlayerAbilities"},
            at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",shift=At.Shift.AFTER),cancellable=true)
    private void myvillage$emberCannotUseInventory(CallbackInfo ci) {
        if(PhoenixRuntime.active(player)) {
            player.containerMenu.broadcastFullState();
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(player.getInventory().selected));ci.cancel();
        }
    }
    @Inject(method={"updateBookContents","signBook","updateSignText"},at=@At("HEAD"),cancellable=true)
    private void myvillage$emberCannotFinishFilteredEdits(CallbackInfo ci) {
        if(PhoenixRuntime.active(player))ci.cancel();
    }
}
