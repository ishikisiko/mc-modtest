package com.example.myvillage.cultivation.growth;

import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.meditation.InventoryBatchRemoval;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.technique.TechniqueRequirementEvaluator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.Function;

/** Bounded player intentions and committed combat outcomes enter the existing profile service here. */
public final class TechniqueGrowthRuntime {
    private TechniqueGrowthRuntime() { }
    public static Function<ResourceLocation, TechniqueDefinition> lookup(ServerPlayer player) {
        return id -> ModCultivationRegistries.technique(player.registryAccess(), id).orElse(null);
    }
    public static boolean eligible(ServerPlayer player, ResourceLocation id) {
        if (!player.isAlive() || player.isRemoved() || !player.gameMode.isSurvival()) return false;
        var profile = CultivationService.getProfile(player);
        var definition = lookup(player).apply(id);
        return profile.awakened() && profile.learnedTechniques().containsKey(id)
                && TechniqueGrowth.definition(TechniqueGrowthRules.bundled(), id, lookup(player)).isPresent()
                && TechniqueRequirementEvaluator.evaluate(profile, definition, MeditationManager.realmLookup(player),
                        MeditationManager.elementLookup(player)).status() == TechniqueRequirementEvaluator.Status.SATISFIED;
    }
    public static void intent(ServerPlayer player, ResourceLocation id, int action, long revision) {
        if (!eligible(player, id)) { message(player, "当前不能修习这门功法，请检查已学功法和修习条件。"); return; }
        var profile = CultivationService.getProfile(player);
        if (profile.learnedTechniques().get(id).revision() != revision) { message(player, "功法状态已更新，请重新选择。"); return; }
        if (action == 0) { MeditationManager.requestPractice(player, id); return; }
        if (action < 1 || action > 2 || MeditationManager.active(player) || CombatSessionManager.hudBusy(player)
                || player.containerMenu != player.inventoryMenu) {
            message(player, "请结束当前修习、战斗动作或容器操作后再参悟。"); return;
        }
        var rules = TechniqueGrowthRules.bundled();
        var choice = TechniqueGrowth.choose(rules, id, action - 1, revision, profile, lookup(player)).orElse(null);
        if (choice == null) { message(player, "参悟条件不足、状态已更新，或已选择此方向。"); return; }
        if (!InventoryBatchRemoval.payForProfile(player, choice.stoneCost(), choice.replacement())) {
            message(player, "下品灵石不足或提交失败，本次参悟未生效。"); return;
        }
        message(player, "已参悟「" + rules.find(id).orElseThrow().insights().get(action - 1).name() + "」，同门其他参悟方向已替换。");
    }
    public static void hit(ServerPlayer player, ResourceLocation id) {
        if (!eligible(player, id)) return;
        var rules = TechniqueGrowthRules.bundled();
        var spec = rules.find(id).orElseThrow();
        if (spec.hitGain() <= 0) return;
        CultivationService.updateProfile(player, current -> current.withTechniqueProgress(id,
                TechniqueGrowth.gain(spec, current.learnedTechniques().get(id), spec.hitGain())));
    }
    public static int multiplier(ServerPlayer player, ResourceLocation id, TechniqueGrowthRules.Effect effect) {
        if (TechniqueGrowth.definition(TechniqueGrowthRules.bundled(), id, lookup(player)).isEmpty()) return TechniqueGrowthRules.UNIT;
        return TechniqueGrowth.multiplier(TechniqueGrowthRules.bundled(), id, CultivationService.getProfile(player), effect);
    }
    public static void message(ServerPlayer player, String text) { player.sendSystemMessage(Component.literal(text)); }
}
