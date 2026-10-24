package com.example.myvillage.mixin;

import com.example.myvillage.story.realm.QianzhenRealms;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Saved player dimensions are checked before the first login/chunk packet, not by a later kick. */
@Mixin(value=PlayerList.class,remap=false)
public abstract class RealmLoginAccessMixin {
    @Redirect(method="placeNewPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"),require=1,allow=1)
    private ServerLevel myvillage$privateLogin(MinecraftServer server,ResourceKey<Level> dimension,Connection connection,ServerPlayer player,CommonListenerCookie cookie){
        return QianzhenRealms.loginLevel(server,dimension,player);
    }
    @ModifyExpressionValue(method="respawn",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/event/entity/player/PlayerRespawnPositionEvent;getDimensionTransition()Lnet/minecraft/world/level/portal/DimensionTransition;"),require=1,allow=1)
    private DimensionTransition myvillage$finalRespawnOwner(DimensionTransition transition,ServerPlayer player,boolean keepInventory,Entity.RemovalReason reason){
        return QianzhenRealms.respawn(player,transition);
    }
}
