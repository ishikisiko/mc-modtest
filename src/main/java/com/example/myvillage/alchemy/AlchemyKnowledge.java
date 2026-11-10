package com.example.myvillage.alchemy;

import com.example.myvillage.cultivation.CultivationAttachments;
import net.minecraft.server.level.ServerPlayer;

/** Server-only owner of formula knowledge; the menu synchronizes only its viewer's learned entries. */
public final class AlchemyKnowledge {
    private AlchemyKnowledge() { }
    public static boolean knows(ServerPlayer player, String recipe) {
        return player.getData(CultivationAttachments.FORMULAS.get()).knows(recipe);
    }
    public static boolean learn(ServerPlayer player, String recipe) {
        var definition = AlchemyRules.get().recipe(recipe);
        if (definition.isEmpty()) return false;
        int required = definition.orElseThrow().requiredAptitude();
        if (com.example.myvillage.cultivation.CultivationService.craftingAptitudes(player).alchemy() < required) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("学习这张丹方需要炼丹资质 " + required));
            return false;
        }
        var current = player.getData(CultivationAttachments.FORMULAS.get());
        if (current.knows(recipe)) return false;
        player.setData(CultivationAttachments.FORMULAS.get(), current.learn(recipe));
        return true;
    }
}
