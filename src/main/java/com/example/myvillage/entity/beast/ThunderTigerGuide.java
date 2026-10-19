package com.example.myvillage.entity.beast;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Player-readable encounter advice; no world searches, spawning or progression changes. */
public final class ThunderTigerGuide {
    private ThunderTigerGuide() {}

    public static void show(ServerPlayer player) {
        var rules = ThunderTigerRules.bundled();
        var realm = Component.translatable("world_sim.stage." + rules.encounterRealm().getPath()
                + "." + (rules.encounterStage() + 1));
        player.sendSystemMessage(Component.translatable("exploration.myvillage.tiger.title", realm));
        for (String topic : new String[]{"where", "combat", "horn", "phase", "retreat", "reward"}) {
            player.sendSystemMessage(Component.translatable("exploration.myvillage.tiger." + topic));
        }
    }
}
