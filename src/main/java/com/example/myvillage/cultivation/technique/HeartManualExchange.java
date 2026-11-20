package com.example.myvillage.cultivation.technique;

import com.example.myvillage.aptitude.AptitudeLearning;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueEffects;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.study.StudyMessages;
import com.example.myvillage.cultivation.study.StudyStart;
import com.example.myvillage.item.TechniqueManualItem;
import com.example.myvillage.sim.runtime.economy.EconomyInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class HeartManualExchange {
    private HeartManualExchange() { }

    public static int price(TechniqueDefinition definition) {
        return definition == null ? 0 : definition.effects().flatMap(TechniqueEffects::core).map(TechniqueEffects.Core::manualPrice).orElse(0);
    }

    public static void request(ServerPlayer player, ResourceLocation id) {
        if (!player.isAlive() || player.isSpectator() || player.containerMenu != player.inventoryMenu) return;
        if (MeditationManager.active(player) || CombatSessionManager.hudBusy(player)) {
            tell(player, "请结束当前招式或修炼，再换取秘籍。");
            return;
        }
        var registry = player.registryAccess().registry(ModCultivationRegistries.TECHNIQUES).orElse(null);
        TechniqueDefinition definition = registry == null ? null : registry.get(id);
        int price = price(definition);
        if (price == 0) return;
        if (!AptitudeLearning.accepts(player, definition, true)) return;
        var refusal = StudyStart.learnRefusal(id, CultivationService.getProfile(player), registry::get,
                MeditationManager.realmLookup(player), MeditationManager.elementLookup(player));
        if (refusal.isPresent()) {
            StudyMessages.refused(player, refusal.get(), definition, definition.previous().map(registry::get).orElse(null));
            return;
        }
        var manual = TechniqueManualItem.manualFor(player.registryAccess(), id);
        if (manual.isEmpty()) return;
        var planned = EconomyInventory.buyWithLowStones(player, manual, price);
        if (!planned.ready()) {
            tell(player, planned.result().equals("missing_items") ? "换取秘籍需要 " + price + " 颗下品灵石。" : "请在背包中留出秘籍的位置。");
            return;
        }
        if (!planned.commit()) {
            tell(player, "背包内容已变化，请重新换取。");
            return;
        }
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.literal("已换取《").append(Component.translatable(definition.translationKey()))
                .append("》并消耗 " + price + " 颗下品灵石。手持秘籍右键研读。"));
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }
}
