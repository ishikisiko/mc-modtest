package com.example.myvillage.mixin;

import com.example.myvillage.cultivation.fate.PhoenixRuntime;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=PlayerList.class,remap=false)
public abstract class PhoenixPlayerListMixin {
    @Inject(method="remove",at=@At("TAIL"))
    private void myvillage$retainDisconnectedEmber(ServerPlayer player,CallbackInfo ci){PhoenixRuntime.disconnected(player);}
    @Inject(method="load",at=@At("HEAD"))
    private void myvillage$saveBeforeLogin(ServerPlayer player,CallbackInfoReturnable<Optional<CompoundTag>> ci){PhoenixRuntime.beforeLogin(player);}
}
