package com.example.myvillage.mixin;

import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 离线余烬结算仍走原生玩家保存入口，包含背包和集成服宿主数据。 */
@Mixin(value=PlayerList.class,remap=false)
public interface PhoenixPlayerListAccess {
    @Invoker("save") void myvillage$savePhoenixPlayer(ServerPlayer player);
}
