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
        if (AlchemyRules.get().recipe(recipe).isEmpty()) return false;
        var current = player.getData(CultivationAttachments.FORMULAS.get());
        if (current.knows(recipe)) return false;
        player.setData(CultivationAttachments.FORMULAS.get(), current.learn(recipe));
        return true;
    }
}
